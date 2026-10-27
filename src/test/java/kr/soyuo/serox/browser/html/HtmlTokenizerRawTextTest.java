package kr.soyuo.serox.browser.html;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class HtmlTokenizerRawTextTest {
  @Test
  void scriptAndStyleKeepTheirTextWithoutMakingTags() {
    List<HtmlToken> tokens =
        new HtmlTokenizer().tokenize("<script>if (a < b) { x = '<b>y</b>'; }</script><p>t</p>");

    assertEquals(new HtmlToken.Text("if (a < b) { x = '<b>y</b>'; }"), tokens.get(1));
    assertEquals(new HtmlToken.EndTag("script"), tokens.get(2));
    assertEquals("p", ((HtmlToken.StartTag) tokens.get(3)).name());
  }

  @Test
  void theClosingTagIsFoundWhateverItsCase() {
    List<HtmlToken> tokens = new HtmlTokenizer().tokenize("<STYLE>a > b { }</STYLE>x");

    assertEquals(new HtmlToken.Text("a > b { }"), tokens.get(1));
    assertEquals(new HtmlToken.EndTag("style"), tokens.get(2));
  }

  @Test
  void anUnclosedScriptTakesTheRestOfTheDocument() {
    List<HtmlToken> tokens = new HtmlTokenizer().tokenize("<script>var a = 1 < 2;");

    assertEquals(2, tokens.size());
    assertEquals(new HtmlToken.Text("var a = 1 < 2;"), tokens.get(1));
  }
}
