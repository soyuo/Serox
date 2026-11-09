package kr.soyuo.serox.browser.css;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class CssParserTest {
  private static List<String> selectors(String css) {
    return new CssParser().parse(css).stream().map(CssRule::selector).toList();
  }

  @Test
  void selectorListsSplitOnlyOnTopLevelCommas() {
    assertEquals(
        List.of(":is(a, b) p", "[data-x='1,2']", "li"),
        selectors(":is(a, b) p, [data-x='1,2'], li { color: red }"));
  }

  @Test
  void rulesInsideSupportsLayerAndContainerBlocksAreRead() {
    assertEquals(
        List.of("a", "b", "c"),
        selectors(
            "@supports (display: grid) { a { color: red } }"
                + "@layer base { b { color: red } }"
                + "@layer one, two;"
                + "@container (min-width: 1px) { c { color: red } }"
                + "@font-face { font-family: x; src: url(x.woff) }"));
  }

  @Test
  void nestedRulesDoNotBreakTheFollowingRule() {
    var rules = new CssParser().parse(".card { color: red; &:hover { color: blue } } p { color: green }");
    assertEquals(List.of(".card", "p"), rules.stream().map(CssRule::selector).toList());
    assertEquals("red", rules.get(0).declarations().get("color"));
  }
}
