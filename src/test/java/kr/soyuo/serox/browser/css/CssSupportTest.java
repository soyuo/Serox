package kr.soyuo.serox.browser.css;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import kr.soyuo.serox.browser.dom.Document;
import kr.soyuo.serox.browser.html.HtmlDocumentParser;
import org.junit.jupiter.api.Test;

class CssSupportTest {
  private static Document page(String css, String body) {
    return new HtmlDocumentParser()
        .parse(
            URI.create("https://css.test/"),
            "<html><head><style>" + css + "</style></head><body>" + body + "</body></html>");
  }

  private static String value(Document document, String id, String property) {
    var styles = new CssStyleEngine().compute(document);
    return styles.styleOf(document.getElementById(id).orElseThrow()).value(property);
  }

  @Test
  void childAndDescendantCombinatorsMatch() {
    var document =
        page(
            "ul > li { color: red } div p { color: blue }",
            "<ul><li id='a'>x</li></ul><div><section><p id='b'>y</p></section></div>");

    assertEquals("red", value(document, "a", "color"));
    assertEquals("blue", value(document, "b", "color"));
  }

  @Test
  void firstAndLastChildMatchAndHoverIsReportedNotApplied() {
    var document =
        page(
            "li:first-child { color: red } li:last-child { color: blue } a:hover { color: green }",
            "<ul><li id='a'>1</li><li id='b'>2</li><li id='c'>3</li></ul><a id='l'>x</a>");

    var styles = new CssStyleEngine().compute(document);

    assertEquals("red", styles.styleOf(document.getElementById("a").orElseThrow()).value("color"));
    assertEquals("black", styles.styleOf(document.getElementById("b").orElseThrow()).value("color"));
    assertEquals("blue", styles.styleOf(document.getElementById("c").orElseThrow()).value("color"));
    assertTrue(styles.unsupportedDeclarations().contains("selector: a:hover"));
  }

  @Test
  void importantBeatsSpecificityAndOrder() {
    var document =
        page(
            "p { color: red !important } #a { color: blue } p { color: green }",
            "<p id='a'>x</p>");

    assertEquals("red", value(document, "a", "color"));
  }

  @Test
  void emRemAndPointLengthsBecomePixels() {
    var document =
        page(
            "body { font-size: 20px } #a { font-size: 1.5em; margin-top: 2em; padding-left: 1rem; width: 12pt }",
            "<p id='a'>x</p>");

    assertEquals("30px", value(document, "a", "font-size"));
    assertEquals("60px", value(document, "a", "margin-top"));
    assertEquals("16px", value(document, "a", "padding-left"));
    assertEquals("16px", value(document, "a", "width"));
  }

  @Test
  void aPercentFontSizeFollowsTheParent() {
    var document = page("body { font-size: 20px } #a { font-size: 50% }", "<p id='a'>x</p>");

    assertEquals("10px", value(document, "a", "font-size"));
  }

  @Test
  void customPropertiesAreInheritedAndSubstituted() {
    var document =
        page(
            "html { --main: #336699; --gap: 12px } "
                + "p { color: var(--main); padding-top: var(--gap); margin-top: var(--missing, 4px) }",
            "<p id='a'>x</p>");

    assertEquals("#336699", value(document, "a", "color"));
    assertEquals("12px", value(document, "a", "padding-top"));
    assertEquals("4px", value(document, "a", "margin-top"));
  }

  @Test
  void aMissingVariableWithoutFallbackIsReportedAndIgnored() {
    var document = page("p { color: var(--nope) }", "<p id='a'>x</p>");

    var styles = new CssStyleEngine().compute(document);

    assertEquals("black", styles.styleOf(document.getElementById("a").orElseThrow()).value("color"));
    assertTrue(styles.unsupportedDeclarations().contains("color: var(--nope)"));
  }

  @Test
  void textStyleProperties() {
    var document =
        page(
            "div { text-align: center; font-style: italic; text-transform: uppercase; "
                + "max-width: 600px; min-width: 100px }",
            "<div id='a'><p id='b'>x</p></div>");

    assertEquals("center", value(document, "b", "text-align"));
    assertEquals("italic", value(document, "b", "font-style"));
    assertEquals("uppercase", value(document, "b", "text-transform"));
    assertEquals("600px", value(document, "a", "max-width"));
    assertEquals("100px", value(document, "a", "min-width"));
  }

  @Test
  void autoMarginsAreAccepted() {
    var document = page("#a { margin: 0 auto }", "<p id='a'>x</p>");

    var styles = new CssStyleEngine().compute(document);

    assertEquals("0 auto", styles.styleOf(document.getElementById("a").orElseThrow()).value("margin"));
    assertTrue(styles.unsupportedDeclarations().isEmpty());
  }

  @Test
  void mediaQueriesAreJudgedAgainstAWideScreen() {
    var document =
        page(
            "@media (min-width: 768px) { #a { color: red } } "
                + "@media (max-width: 500px) { #a { color: blue } } "
                + "@media print { #a { color: green } } "
                + "@import url(other.css); @font-face { font-family: x; src: url(a.woff) } "
                + "#b { color: red }",
            "<p id='a'>x</p><p id='b'>y</p>");

    assertEquals("red", value(document, "a", "color"));
    assertEquals("red", value(document, "b", "color"));
  }

  @Test
  void declarationsWithSemicolonsInsideUrlStayWhole() {
    var declarations =
        new CssParser()
            .parseDeclarations("background: url(data:image/png;base64,AAAA); color: red");

    assertEquals("red", declarations.get("color"));
    assertEquals("url(data:image/png;base64,AAAA)", declarations.get("background"));
  }
}
