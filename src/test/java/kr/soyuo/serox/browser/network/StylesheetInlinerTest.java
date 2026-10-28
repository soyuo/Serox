package kr.soyuo.serox.browser.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import kr.soyuo.serox.browser.css.CssStyleEngine;
import kr.soyuo.serox.browser.dom.Document;
import kr.soyuo.serox.browser.html.HtmlDocumentParser;
import org.junit.jupiter.api.Test;

class StylesheetInlinerTest {
  private static Document parse(String head) {
    return new HtmlDocumentParser()
        .parse(
            URI.create("https://sheets.test/dir/page.html"),
            "<html><head>"
                + head
                + "</head><body><p id='a'>x</p><p id='b'>y</p></body></html>");
  }

  @Test
  void anOutsideSheetIsLoadedFromItsResolvedAddressAndStyles() {
    var asked = new ArrayList<URI>();
    var inliner =
        new StylesheetInliner(
            uri -> {
              asked.add(uri);
              return Optional.of("#a { color: red }");
            });
    var document = parse("<link rel='stylesheet' href='site.css'>");

    int count = inliner.inline(document);

    assertEquals(1, count);
    assertEquals(List.of(URI.create("https://sheets.test/dir/site.css")), asked);
    assertEquals(0, document.getElementsByTagName("link").size());
    var styles = new CssStyleEngine().compute(document);
    assertEquals("red", styles.styleOf(document.getElementById("a").orElseThrow()).value("color"));
  }

  @Test
  void theOrderOfSheetsAndStyleElementsIsKept() {
    var inliner = new StylesheetInliner(uri -> Optional.of("#a { color: red }"));
    var document =
        parse("<link rel='stylesheet' href='one.css'><style>#a { color: blue }</style>");

    inliner.inline(document);

    var styles = new CssStyleEngine().compute(document);
    assertEquals("blue", styles.styleOf(document.getElementById("a").orElseThrow()).value("color"));
  }

  @Test
  void printSheetsOtherTypesAndNonWebAddressesAreSkipped() {
    var asked = new ArrayList<URI>();
    var inliner =
        new StylesheetInliner(
            uri -> {
              asked.add(uri);
              return Optional.of("p { color: red }");
            });
    var document =
        parse(
            "<link rel='stylesheet' href='print.css' media='print'>"
                + "<link rel='icon' href='icon.png'>"
                + "<link rel='stylesheet' href='file:///etc/passwd'>"
                + "<link rel='stylesheet' type='text/plain' href='x.txt'>");

    assertEquals(0, inliner.inline(document));
    assertTrue(asked.isEmpty());
  }

  @Test
  void aSheetThatCannotBeLoadedIsLeftAlone() {
    var inliner = new StylesheetInliner(uri -> Optional.empty());
    var document = parse("<link rel='stylesheet' href='gone.css'>");

    assertEquals(0, inliner.inline(document));
    assertEquals(1, document.getElementsByTagName("link").size());
  }

  @Test
  void onlyAFewSheetsAreLoaded() {
    var inliner = new StylesheetInliner(uri -> Optional.of("p { color: red }"));
    var links = new StringBuilder();
    for (int i = 0; i < 20; i++) {
      links.append("<link rel='stylesheet' href='s").append(i).append(".css'>");
    }

    assertEquals(8, inliner.inline(parse(links.toString())));
  }
}
