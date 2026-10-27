package kr.soyuo.serox.browser.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import kr.soyuo.serox.browser.css.CssStyleEngine;
import kr.soyuo.serox.browser.html.HtmlDocumentParser;
import org.junit.jupiter.api.Test;

class FlexLayoutTest {
  private static final double WIDTH = 400;

  private static LayoutResult layout(String bodyHtml) {
    String html =
        "<html><head><style>body { margin: 0px; }</style></head><body>" + bodyHtml + "</body></html>";
    var document = new HtmlDocumentParser().parse(URI.create("https://flex.test/"), html);
    var styles = new CssStyleEngine().compute(document);
    assertFalse(
        styles.unsupportedDeclarations().stream().anyMatch(entry -> entry.startsWith("display: flex")),
        "display: flex must be supported");
    return new LayoutEngine().layout(document, styles, new LayoutConstraints(WIDTH, 600, 0, 0));
  }

  private static LayoutBox box(LayoutResult result, String id) {
    return result.boxes().stream()
        .filter(box -> id.equals(box.element().attribute("id").orElse(null)))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no box " + id));
  }

  private static String item(String id, String style) {
    return "<div id=\"" + id + "\" style=\"" + style + "\"></div>";
  }

  @Test
  void rowItemsSitSideBySideSeparatedByTheGap() {
    var result =
        layout(
            "<div style=\"display: flex; gap: 10px\">"
                + item("a", "width: 100px; height: 20px")
                + item("b", "width: 100px; height: 20px")
                + item("c", "width: 100px; height: 20px")
                + "</div>");

    assertEquals(0, box(result, "a").x(), 0.01);
    assertEquals(110, box(result, "b").x(), 0.01);
    assertEquals(220, box(result, "c").x(), 0.01);
    assertEquals(box(result, "a").y(), box(result, "c").y(), 0.01);
  }

  @Test
  void justifyContentDistributesTheFreeSpace() {
    String two =
        item("a", "width: 100px; height: 20px") + item("b", "width: 100px; height: 20px");

    var centered = layout("<div style=\"display: flex; justify-content: center\">" + two + "</div>");
    assertEquals(100, box(centered, "a").x(), 0.01);
    assertEquals(200, box(centered, "b").x(), 0.01);

    var end = layout("<div style=\"display: flex; justify-content: flex-end\">" + two + "</div>");
    assertEquals(200, box(end, "a").x(), 0.01);

    var between =
        layout("<div style=\"display: flex; justify-content: space-between\">" + two + "</div>");
    assertEquals(0, box(between, "a").x(), 0.01);
    assertEquals(300, box(between, "b").x(), 0.01);

    var around =
        layout("<div style=\"display: flex; justify-content: space-around\">" + two + "</div>");
    assertEquals(50, box(around, "a").x(), 0.01);
    assertEquals(250, box(around, "b").x(), 0.01);
  }

  @Test
  void flexGrowSharesTheRemainingWidthInProportion() {
    var result =
        layout(
            "<div style=\"display: flex\">"
                + item("a", "flex-grow: 1; height: 20px")
                + item("b", "flex: 3; height: 20px")
                + "</div>");

    assertEquals(100, box(result, "a").width(), 0.01);
    assertEquals(300, box(result, "b").width(), 0.01);
    assertEquals(100, box(result, "b").x(), 0.01);
  }

  @Test
  void itemsThatDoNotFitShrinkInProportion() {
    var result =
        layout(
            "<div style=\"display: flex\">"
                + item("a", "width: 300px; height: 20px")
                + item("b", "width: 300px; height: 20px")
                + "</div>");

    assertEquals(200, box(result, "a").width(), 0.01);
    assertEquals(200, box(result, "b").width(), 0.01);
    assertEquals(200, box(result, "b").x(), 0.01);
  }

  @Test
  void alignItemsPlacesShorterItemsOnTheCrossAxis() {
    String items = item("tall", "width: 50px; height: 100px") + item("short", "width: 50px; height: 40px");

    var start = layout("<div style=\"display: flex; align-items: flex-start\">" + items + "</div>");
    assertEquals(0, box(start, "short").y(), 0.01);

    var center = layout("<div style=\"display: flex; align-items: center\">" + items + "</div>");
    assertEquals(30, box(center, "short").y(), 0.01);

    var end = layout("<div style=\"display: flex; align-items: flex-end\">" + items + "</div>");
    assertEquals(60, box(end, "short").y(), 0.01);
  }

  @Test
  void stretchOnlyGrowsItemsWithoutAHeightOfTheirOwn() {
    var result =
        layout(
            "<div style=\"display: flex\">"
                + item("tall", "width: 50px; height: 100px")
                + "<div id=\"auto\" style=\"width: 50px\"></div>"
                + item("fixed", "width: 50px; height: 40px")
                + "</div>");

    assertEquals(100, box(result, "auto").height(), 0.01);
    assertEquals(40, box(result, "fixed").height(), 0.01);
  }

  @Test
  void theContainerIsAsTallAsItsTallestItem() {
    var result =
        layout(
            "<div id=\"row\" style=\"display: flex\">"
                + item("a", "width: 50px; height: 100px")
                + item("b", "width: 50px; height: 40px")
                + "</div>");

    assertEquals(100, box(result, "row").height(), 0.01);
  }

  @Test
  void columnItemsStackWithTheGapAndHonourCrossAxisAlignment() {
    var stacked =
        layout(
            "<div style=\"display: flex; flex-direction: column; gap: 8px\">"
                + item("a", "height: 30px")
                + item("b", "height: 30px")
                + "</div>");
    assertEquals(0, box(stacked, "a").y(), 0.01);
    assertEquals(38, box(stacked, "b").y(), 0.01);
    assertEquals(WIDTH, box(stacked, "a").width(), 0.01);

    var centered =
        layout(
            "<div style=\"display: flex; flex-direction: column; align-items: center\">"
                + item("a", "width: 100px; height: 30px")
                + "</div>");
    assertEquals(150, box(centered, "a").x(), 0.01);
  }

  @Test
  void anItemWithoutAWidthIsAsWideAsItsContent() {
    var result =
        layout(
            "<div style=\"display: flex; gap: 10px\">"
                + "<span id=\"a\" style=\"display: block\">abc</span>"
                + item("b", "width: 50px; height: 10px")
                + "</div>");

    double textWidth = 3 * 16 * 0.55;
    assertEquals(textWidth, box(result, "a").width(), 0.5);
    assertEquals(textWidth + 10, box(result, "b").x(), 0.5);
  }

  @Test
  void flexContainersNestAndStayRepeatable() {
    String html =
        "<div style=\"display: flex; gap: 4px\">"
            + "<div id=\"left\" style=\"display: flex; flex-direction: column; width: 150px\">"
            + item("x", "height: 20px")
            + item("y", "height: 20px")
            + "</div>"
            + item("right", "flex-grow: 1; height: 10px")
            + "</div>";
    var first = layout(html);
    var second = layout(html);

    assertEquals(40, box(first, "left").height(), 0.01);
    assertEquals(154, box(first, "right").x(), 0.01);
    assertEquals(246, box(first, "right").width(), 0.01);
    assertEquals(first.boxes().size(), second.boxes().size());
    assertTrue(box(first, "y").y() > box(first, "x").y());
  }

  @Test
  void measuringAnItemLeavesNoTraceInTheDisplayList() {
    var result =
        layout(
            "<div style=\"display: flex\">"
                + "<p id=\"a\">hello world</p><p id=\"b\">again</p>"
                + "</div>");

    assertEquals(
        1L,
        result.boxes().stream().filter(box -> "a".equals(box.element().attribute("id").orElse(null))).count());
    assertEquals(2, result.textRuns().size());
  }
}
