package kr.soyuo.serox.browser.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import kr.soyuo.serox.browser.css.CssStyleEngine;
import kr.soyuo.serox.browser.html.HtmlDocumentParser;
import org.junit.jupiter.api.Test;

class PercentWidthTest {
  private static final double WIDTH = 400;

  private static LayoutResult layout(String bodyHtml) {
    String html =
        "<html><head><style>body { margin: 0px; }</style></head><body>" + bodyHtml + "</body></html>";
    var document = new HtmlDocumentParser().parse(URI.create("https://percent.test/"), html);
    var styles = new CssStyleEngine().compute(document);
    assertTrue(styles.unsupportedDeclarations().isEmpty(), "percent widths must be supported");
    return new LayoutEngine().layout(document, styles, new LayoutConstraints(WIDTH, 600, 0, 0));
  }

  private static LayoutBox box(LayoutResult result, String id) {
    return result.boxes().stream()
        .filter(box -> id.equals(box.element().attribute("id").orElse(null)))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no box " + id));
  }

  @Test
  void blockWidthIsAShareOfTheContainingBlock() {
    var result = layout("<div id=\"a\" style=\"width: 50%; height: 10px\"></div>");

    assertEquals(200, box(result, "a").width(), 0.01);
  }

  @Test
  void nestedPercentagesResolveAgainstTheirOwnParent() {
    var result =
        layout(
            "<div id=\"outer\" style=\"width: 50%\">"
                + "<div id=\"inner\" style=\"width: 50%; height: 10px\"></div></div>");

    assertEquals(200, box(result, "outer").width(), 0.01);
    assertEquals(100, box(result, "inner").width(), 0.01);
  }

  @Test
  void flexItemsTakeTheirPercentageOfTheContainer() {
    var result =
        layout(
            "<div style=\"display: flex\">"
                + "<div id=\"a\" style=\"width: 25%; height: 10px\"></div>"
                + "<div id=\"b\" style=\"width: 75%; height: 10px\"></div></div>");

    assertEquals(100, box(result, "a").width(), 0.01);
    assertEquals(300, box(result, "b").width(), 0.01);
    assertEquals(100, box(result, "b").x(), 0.01);
  }

  @Test
  void heightAndInvalidPercentagesAreStillUnsupported() {
    String html =
        "<html><body><div style=\"height: 50%; width: -5%\">x</div></body></html>";
    var document = new HtmlDocumentParser().parse(URI.create("https://percent.test/"), html);
    var styles = new CssStyleEngine().compute(document);

    assertTrue(styles.unsupportedDeclarations().contains("height: 50%"));
    assertTrue(styles.unsupportedDeclarations().contains("width: -5%"));
  }
}
