package kr.soyuo.serox.browser.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import kr.soyuo.serox.browser.css.CssStyleEngine;
import kr.soyuo.serox.browser.html.HtmlDocumentParser;
import org.junit.jupiter.api.Test;

class TextAlignAndWidthTest {
  private static final double WIDTH = 400;

  private static LayoutResult layout(String bodyHtml) {
    String html =
        "<html><head><style>body { margin: 0px; }</style></head><body>" + bodyHtml + "</body></html>";
    var document = new HtmlDocumentParser().parse(URI.create("https://align.test/"), html);
    var styles = new CssStyleEngine().compute(document);
    assertTrue(
        styles.unsupportedDeclarations().isEmpty(), styles.unsupportedDeclarations().toString());
    return new LayoutEngine().layout(document, styles, new LayoutConstraints(WIDTH, 600, 0, 0));
  }

  private static LayoutBox box(LayoutResult result, String id) {
    return result.boxes().stream()
        .filter(box -> id.equals(box.element().attribute("id").orElse(null)))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no box " + id));
  }

  private static TextRun firstRun(LayoutResult result) {
    return result.textRuns().get(0);
  }

  @Test
  void leftAlignedTextStartsAtTheLeftEdge() {
    assertEquals(0, firstRun(layout("<p style='margin: 0px'>hi</p>")).x(), 0.01);
  }

  @Test
  void centeredTextSitsInTheMiddleOfTheLine() {
    var run = firstRun(layout("<p style='margin: 0px; text-align: center'>hi</p>"));
    double textWidth = 2 * 16 * 0.55;

    assertEquals((WIDTH - textWidth) / 2, run.x(), 0.6);
  }

  @Test
  void rightAlignedTextEndsAtTheRightEdge() {
    var run = firstRun(layout("<p style='margin: 0px; text-align: right'>hi</p>"));
    double textWidth = 2 * 16 * 0.55;

    assertEquals(WIDTH - textWidth, run.x(), 0.6);
  }

  @Test
  void maxWidthLimitsABlockAndAutoMarginsCenterIt() {
    var result =
        layout("<div id='a' style='max-width: 200px; margin: 0 auto; height: 10px'></div>");

    assertEquals(200, box(result, "a").width(), 0.01);
    assertEquals(100, box(result, "a").x(), 0.01);
  }

  @Test
  void minWidthKeepsABlockFromShrinking() {
    var result = layout("<div id='a' style='width: 50px; min-width: 120px; height: 10px'></div>");

    assertEquals(120, box(result, "a").width(), 0.01);
  }

  @Test
  void textTransformChangesTheDrawnText() {
    var upper = firstRun(layout("<p style='text-transform: uppercase'>hello world</p>"));
    var title = firstRun(layout("<p style='text-transform: capitalize'>hello world</p>"));
    var lower = firstRun(layout("<p style='text-transform: lowercase'>HELLO</p>"));

    assertEquals("HELLO WORLD", upper.text());
    assertEquals("Hello World", title.text());
    assertEquals("hello", lower.text());
  }
}
