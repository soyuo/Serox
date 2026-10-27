package kr.soyuo.serox.browser.html;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import kr.soyuo.serox.browser.dom.CommentNode;
import kr.soyuo.serox.browser.dom.Document;
import kr.soyuo.serox.browser.dom.Element;
import org.junit.jupiter.api.Test;

class HtmlDocumentParserTest {
  private final HtmlDocumentParser parser = new HtmlDocumentParser();

  @Test
  void buildsDocumentRootsAttributesAndTextWithBasicRecovery() {
    Document document =
        parser.parse(
            URI.create("https://example.test/page"),
            "<!doctype html><html><head><title>Serox &amp; Test</title></head>"
                + "<body><main id='content' class='primary featured'><p class=item data-x=1>"
                + "Alpha &lt; Beta<p>two</main><!--done--></body></html>");

    final Element main = document.getElementById("content").orElseThrow();
    assertEquals(URI.create("https://example.test/page"), document.documentUrl());
    assertEquals("Serox & Test", document.head().orElseThrow().textContent());
    assertEquals("Alpha < Betatwo", document.body().orElseThrow().textContent());
    assertEquals(2, document.getElementsByTagName("p").size());
    assertEquals(1, document.getElementsByClassName("featured").size());
    assertEquals(1, document.querySelectorAll("#content p.item[data-x='1']").size());
    assertTrue(main.parent().orElseThrow() instanceof Element);
    assertInstanceOf(CommentNode.class, document.descendants().getLast());
  }

  @Test
  void tokenizerKeepsQuotedDelimitersAndSkipsDoctype() {
    var tokens =
        new HtmlTokenizer()
            .tokenize("<!doctype html><a title=\"one > two\" href='/x'>A &amp; B</a>");

    assertEquals(3, tokens.size());
    HtmlToken.StartTag start = assertInstanceOf(HtmlToken.StartTag.class, tokens.getFirst());
    assertEquals("one > two", start.attributes().get("title"));
    assertEquals("A & B", assertInstanceOf(HtmlToken.Text.class, tokens.get(1)).value());
    assertInstanceOf(HtmlToken.EndTag.class, tokens.getLast());
  }
}
