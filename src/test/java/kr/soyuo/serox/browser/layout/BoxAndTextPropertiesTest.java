package kr.soyuo.serox.browser.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;
import kr.soyuo.serox.browser.css.CssStyleEngine;
import kr.soyuo.serox.browser.css.StyleSnapshot;
import kr.soyuo.serox.browser.dom.Document;
import kr.soyuo.serox.browser.html.HtmlDocumentParser;
import kr.soyuo.serox.browser.render.DisplayListBuilder;
import kr.soyuo.serox.browser.render.PaintCommand;
import org.junit.jupiter.api.Test;

class BoxAndTextPropertiesTest {
  private record Page(Document document, StyleSnapshot styles, LayoutResult layout) {}

  private static Page page(String css, String body) {
    String html =
        "<html><head><style>body { margin: 0px } " + css + "</style></head><body>" + body
            + "</body></html>";
    Document document = new HtmlDocumentParser().parse(URI.create("https://props.test/"), html);
    StyleSnapshot styles = new CssStyleEngine().compute(document);
    LayoutResult layout =
        new LayoutEngine().layout(document, styles, new LayoutConstraints(400, 600, 0, 0));
    return new Page(document, styles, layout);
  }

  private static LayoutBox box(Page page, String id) {
    return page.layout().boxes().stream()
        .filter(box -> id.equals(box.element().attribute("id").orElse(null)))
        .findFirst()
        .orElseThrow();
  }

  private static String value(Page page, String id, String property) {
    return page.styles().styleOf(page.document().getElementById(id).orElseThrow()).value(property);
  }

  @Test
  void lineHeightSetsTheLineBox() {
    Page page = page("#a { line-height: 40px; font-size: 16px }", "<div id='a'>one</div>");
    assertEquals(40, box(page, "a").height(), 0.01);
    assertEquals(16 + (40 - 21) / 2.0, page.layout().textRuns().get(0).baselineY(), 0.01);
    Page unitless = page("#a { line-height: 2; font-size: 10px }", "<div id='a'>x</div>");
    assertEquals(20, box(unitless, "a").height(), 0.01);
  }

  @Test
  void noWrapKeepsTextOnOneLineAndPreKeepsBreaks() {
    String words = "word ".repeat(40);
    Page nowrap = page("#a { white-space: nowrap }", "<div id='a'>" + words + "</div>");
    assertEquals(1, nowrap.layout().textRuns().size());
    Page pre = page("#a { white-space: pre }", "<div id='a'>a\nb\nc</div>");
    assertEquals(3, pre.layout().textRuns().size());
  }

  @Test
  void sideBordersAndBorderBoxSizing() {
    Page page =
        page(
            "#a { width: 100px; padding: 10px; border-left: 4px solid red; box-sizing: border-box }",
            "<div id='a'>x</div>");
    LayoutBox a = box(page, "a");
    assertEquals(100, a.width(), 0.01);
    assertEquals(100 - 20 - 4, a.contentWidth(), 0.01);
    assertEquals(14, a.contentX() - a.x(), 0.01);
    var strips =
        new DisplayListBuilder()
            .build(page.layout()).commands().stream()
                .filter(PaintCommand.FillRect.class::isInstance)
                .map(PaintCommand.FillRect.class::cast)
                .filter(rect -> rect.color().red() == 255)
                .toList();
    assertEquals(1, strips.size());
    assertEquals(4, strips.get(0).width(), 0.01);
  }

  @Test
  void minAndMaxHeight() {
    Page tall = page("#a { min-height: 80px }", "<div id='a'>x</div>");
    assertEquals(80, box(tall, "a").height(), 0.01);
    Page capped = page("#a { max-height: 5px }", "<div id='a'>x</div>");
    assertEquals(5, box(capped, "a").height(), 0.01);
  }

  @Test
  void hiddenAndTransparentElementsAreNotPainted() {
    Page page =
        page(
            "#a { visibility: hidden; background: red } #b { opacity: 0 } #c { opacity: .5;"
                + " background-color: #000 }",
            "<div id='a'>a</div><div id='b'>b</div><div id='c'>c</div>");
    var commands = new DisplayListBuilder().build(page.layout()).commands();
    long texts = commands.stream().filter(PaintCommand.Text.class::isInstance).count();
    assertEquals(1, texts);
    var fill =
        commands.stream()
            .filter(PaintCommand.FillRect.class::isInstance)
            .map(PaintCommand.FillRect.class::cast)
            .findFirst()
            .orElseThrow();
    assertEquals(0.5, fill.color().opacity(), 0.001);
  }

  @Test
  void shorthandsKeywordsAndColorsResolve() {
    Page page =
        page(
            "#p { color: rgb(10, 20, 30); font: italic bold 20px/30px Georgia, serif }"
                + " #c { color: inherit; border: 2px solid currentColor; margin: 1px 2px 3px }"
                + " #d { flex-flow: column wrap; display: inline-block }",
            "<div id='p'><div id='c'>x</div><div id='d'>y</div></div>");
    assertEquals("italic", value(page, "p", "font-style"));
    assertEquals("bold", value(page, "p", "font-weight"));
    assertEquals("20px", value(page, "p", "font-size"));
    assertEquals("30px", value(page, "c", "line-height"));
    assertEquals("rgb(10, 20, 30)", value(page, "c", "color"));
    assertEquals("rgb(10, 20, 30)", value(page, "c", "border-top-color"));
    assertEquals("2px", value(page, "c", "margin-right"));
    assertEquals("3px", value(page, "c", "margin-bottom"));
    assertEquals("2px", value(page, "c", "margin-left"));
    assertEquals("column", value(page, "d", "flex-direction"));
    assertEquals("inline", value(page, "d", "display"));
    assertTrue(page.styles().unsupportedDeclarations().isEmpty(),
        page.styles().unsupportedDeclarations().toString());
  }

  @Test
  void motionPointerAndPrefixedPropertiesAreNotReported() {
    Page page =
        page(
            "#a { cursor: pointer; transition: all .2s; -webkit-mask-image: none;"
                + " user-select: none; position: sticky }",
            "<div id='a'>x</div>");
    assertEquals(List.of("position: sticky"), page.styles().unsupportedDeclarations());
  }
}
