package kr.soyuo.serox.browser.css;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Parses simple CSS rule blocks and declarations. */
public final class CssParser {
  /** Parses selector blocks in source order, ignoring malformed declarations. */
  public List<CssRule> parse(String css) {
    String source = css.replaceAll("(?s)/\\*.*?\\*/", "");
    List<CssRule> rules = new ArrayList<>();
    int cursor = 0;
    while (cursor < source.length()) {
      int open = source.indexOf('{', cursor);
      if (open < 0) {
        break;
      }
      int close = source.indexOf('}', open + 1);
      if (close < 0) {
        break;
      }
      String selectorText = source.substring(cursor, open).trim();
      if (!selectorText.isEmpty()) {
        Map<String, String> declarations = parseDeclarations(source.substring(open + 1, close));
        for (String selector : selectorText.split(",")) {
          if (!selector.isBlank() && !declarations.isEmpty()) {
            rules.add(new CssRule(selector.trim(), declarations, rules.size()));
          }
        }
      }
      cursor = close + 1;
    }
    return List.copyOf(rules);
  }

  /** Parses inline style declarations in source order. */
  public java.util.Map<String, String> parseDeclarations(String input) {
    var declarations = new LinkedHashMap<String, String>();
    for (String declaration : input.split(";")) {
      int colon = declaration.indexOf(':');
      if (colon <= 0) {
        continue;
      }
      String property = declaration.substring(0, colon).trim().toLowerCase(Locale.ROOT);
      String value = declaration.substring(colon + 1).trim();
      if (!property.isEmpty() && !value.isEmpty()) {
        declarations.put(property, value);
      }
    }
    return java.util.Collections.unmodifiableMap(declarations);
  }
}
