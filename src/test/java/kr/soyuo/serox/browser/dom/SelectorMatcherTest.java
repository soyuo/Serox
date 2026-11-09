package kr.soyuo.serox.browser.dom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.util.stream.Collectors;
import kr.soyuo.serox.browser.html.HtmlDocumentParser;
import org.junit.jupiter.api.Test;

class SelectorMatcherTest {
  private static final Document PAGE =
      new HtmlDocumentParser()
          .parse(
              URI.create("https://example.test/"),
              "<html lang='en-US'><body>"
                  + "<ul id='list'>"
                  + "<li id='a' class='x first' data-k='alpha beta'>1</li>"
                  + "<li id='b' data-k='alpha-1'>2</li>"
                  + "<li id='c' class='x' data-k='ALPHA'>3</li>"
                  + "<li id='d'></li>"
                  + "</ul>"
                  + "<p id='p1'>p</p><span id='s1'>s</span><p id='p2' lang='ko'>p</p>"
                  + "<a id='l' href='/x'>l</a><input id='i' disabled>"
                  + "</body></html>");

  private static String ids(String selector) {
    return new SelectorMatcher(selector)
        .selectAll(PAGE).stream()
            .map(element -> element.attribute("id").orElse("?"))
            .filter(id -> !id.equals("?"))
            .collect(Collectors.joining(","));
  }

  @Test
  void attributeOperators() {
    assertEquals("a", ids("[data-k~=beta]"));
    assertEquals("b", ids("[data-k|=alpha]"));
    assertEquals("a,b", ids("[data-k^=alpha]"));
    assertEquals("b", ids("[data-k$='-1']"));
    assertEquals("a", ids("[data-k*=\"a b\"]"));
    assertEquals("a,b,c", ids("li[data-k^=alpha i]"));
  }

  @Test
  void combinators() {
    assertEquals("b", ids("#a + li"));
    assertEquals("b,c,d", ids("#a ~ li"));
    assertEquals("s1", ids("p + span"));
    assertEquals("a,b,c,d", ids("ul > li"));
  }

  @Test
  void structuralPseudoClasses() {
    assertEquals("a,c", ids("li:nth-child(odd)"));
    assertEquals("b,d", ids("li:nth-child(2n)"));
    assertEquals("c,d", ids("li:nth-child(n+3)"));
    assertEquals("d", ids("li:nth-last-child(1)"));
    assertEquals("d", ids("li:empty"));
    assertEquals("p1", ids("p:first-of-type"));
    assertEquals("s1", ids("span:only-of-type"));
  }

  @Test
  void logicalPseudoClasses() {
    assertEquals("b,d", ids("li:not(.x)"));
    assertEquals("a,c,p1,p2", ids(":is(li.x, p)"));
    assertEquals("a,c", ids("li:where(.x, :bogus)"));
    assertEquals("list", ids("ul:has(> li.first)"));
    assertEquals("a", ids("li:has(+ #b)"));
  }

  @Test
  void languageLinksAndForms() {
    assertEquals("p2", ids("p:lang(ko)"));
    assertEquals("p1", ids("p:lang(en)"));
    assertEquals("l", ids("a:link"));
    assertEquals("i", ids("input:disabled"));
  }

  @Test
  void interactionStatesAndPseudoElementsAreValidButNeverMatch() {
    assertEquals("", ids("li:hover"));
    assertEquals("", ids("a:focus-visible"));
    assertEquals("", ids("li::before"));
    assertEquals("", ids("li:after"));
  }

  @Test
  void invalidSelectorsAreErrors() {
    assertThrows(SelectorMatcher.SyntaxException.class, () -> new SelectorMatcher("p:bogus"));
    assertThrows(SelectorMatcher.SyntaxException.class, () -> new SelectorMatcher("p >"));
    assertThrows(SelectorMatcher.SyntaxException.class, () -> new SelectorMatcher("[x"));
    assertThrows(SelectorMatcher.SyntaxException.class, () -> new SelectorMatcher("li:not()"));
  }
}
