package kr.soyuo.serox.browser.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import kr.soyuo.serox.browser.css.CssStyleEngine;
import kr.soyuo.serox.browser.html.HtmlDocumentParser;
import org.junit.jupiter.api.Test;

class FlexWrapTest {
  private static final double WIDTH = 400;

  private static LayoutResult layout(String bodyHtml) {
    String html =
        "<html><head><style>body { margin: 0px; }</style></head><body>" + bodyHtml + "</body></html>";
    var document = new HtmlDocumentParser().parse(URI.create("https://wrap.test/"), html);
    var styles = new CssStyleEngine().compute(document);
    assertTrue(styles.unsupportedDeclarations().isEmpty(), "flex-wrap must be supported");
    return new LayoutEngine().layout(document, styles, new LayoutConstraints(WIDTH, 600, 0, 0));
  }

  private static LayoutBox box(LayoutResult result, String id) {
    return result.boxes().stream()
        .filter(box -> id.equals(box.element().attribute("id").orElse(null)))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no box " + id));
  }

  private static String item(String id, int width) {
    return "<div id=\"" + id + "\" style=\"width: " + width + "px; height: 20px\"></div>";
  }

  @Test
  void itemsThatDoNotFitMoveToTheNextLine() {
    var result =
        layout(
            "<div style=\"display: flex; flex-wrap: wrap; gap: 10px\">"
                + item("a", 150)
                + item("b", 150)
                + item("c", 150)
                + "</div>");

    assertEquals(0, box(result, "a").x(), 0.01);
    assertEquals(160, box(result, "b").x(), 0.01);
    assertEquals(0, box(result, "c").x(), 0.01);
    assertEquals(box(result, "a").y() + 30, box(result, "c").y(), 0.01);
  }

  @Test
  void containerHeightCoversEveryLine() {
    var result =
        layout(
            "<div id=\"row\" style=\"display: flex; flex-wrap: wrap; gap: 10px\">"
                + item("a", 250)
                + item("b", 250)
                + item("c", 250)
                + "</div>");

    assertEquals(80, box(result, "row").height(), 0.01);
  }

  @Test
  void eachLineIsJustifiedOnItsOwn() {
    var result =
        layout(
            "<div style=\"display: flex; flex-wrap: wrap; justify-content: flex-end\">"
                + item("a", 250)
                + item("b", 200)
                + "</div>");

    assertEquals(150, box(result, "a").x(), 0.01);
    assertEquals(200, box(result, "b").x(), 0.01);
  }

  @Test
  void nowrapKeepsEverythingOnOneLine() {
    var result =
        layout(
            "<div style=\"display: flex; flex-wrap: nowrap\">"
                + item("a", 300)
                + item("b", 300)
                + "</div>");

    assertEquals(box(result, "a").y(), box(result, "b").y(), 0.01);
  }
}
