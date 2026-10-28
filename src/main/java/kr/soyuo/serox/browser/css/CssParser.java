package kr.soyuo.serox.browser.css;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses simple CSS rule blocks and declarations. */
public final class CssParser {
  /** The page width media queries are judged against, as Serox opens its pages wide. */
  static final double VIEWPORT_WIDTH = 1100;

  private static final Pattern WIDTH_CONDITION =
      Pattern.compile("\\(\\s*(min|max)-width\\s*:\\s*(-?[\\d.]+)(px|em|rem)\\s*\\)");

  /** Parses selector blocks in source order, ignoring malformed declarations. */
  public List<CssRule> parse(String css) {
    String source = css.replaceAll("(?s)/\\*.*?\\*/", "");
    List<CssRule> rules = new ArrayList<>();
    parseInto(source, rules);
    return List.copyOf(rules);
  }

  private void parseInto(String source, List<CssRule> rules) {
    int cursor = 0;
    while (cursor < source.length()) {
      while (cursor < source.length() && Character.isWhitespace(source.charAt(cursor))) {
        cursor++;
      }
      if (cursor >= source.length()) {
        break;
      }
      int open = source.indexOf('{', cursor);
      if (source.charAt(cursor) == '@') {
        int semicolon = source.indexOf(';', cursor);
        if (open < 0 || (semicolon >= 0 && semicolon < open)) {
          cursor = semicolon < 0 ? source.length() : semicolon + 1;
          continue;
        }
        int close = matching(source, open);
        String header = source.substring(cursor, open).trim().toLowerCase(Locale.ROOT);
        if (header.startsWith("@media") && mediaApplies(header.substring(6))) {
          parseInto(source.substring(open + 1, close), rules);
        }
        cursor = close + 1;
        continue;
      }
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
  }

  /** The index of the brace that closes the one at {@code open}, or the last index if none. */
  private static int matching(String source, int open) {
    int depth = 0;
    for (int i = open; i < source.length(); i++) {
      char c = source.charAt(i);
      if (c == '{') {
        depth++;
      } else if (c == '}') {
        depth--;
        if (depth == 0) {
          return i;
        }
      }
    }
    return source.length() - 1;
  }

  /**
   * Whether a media query holds for a wide screen. Screen and width conditions are judged; a query
   * about anything else (print, colour scheme, hover) is treated as not applying.
   */
  static boolean mediaApplies(String query) {
    String text = query.trim();
    if (text.isEmpty()) {
      return true;
    }
    for (String alternative : text.split(",")) {
      if (alternativeApplies(alternative.trim())) {
        return true;
      }
    }
    return false;
  }

  private static boolean alternativeApplies(String alternative) {
    String rest = alternative.replaceAll("\\bonly\\b", " ");
    Matcher width = WIDTH_CONDITION.matcher(rest);
    StringBuilder remaining = new StringBuilder();
    while (width.find()) {
      double value = Double.parseDouble(width.group(2));
      if (!width.group(3).equals("px")) {
        value *= 16;
      }
      boolean holds = width.group(1).equals("min") ? VIEWPORT_WIDTH >= value : VIEWPORT_WIDTH <= value;
      if (!holds) {
        return false;
      }
      width.appendReplacement(remaining, " ");
    }
    width.appendTail(remaining);
    String others = remaining.toString().replaceAll("\\band\\b", " ").trim();
    if (others.isEmpty()) {
      return true;
    }
    for (String token : others.split("\\s+")) {
      if (!token.equals("screen") && !token.equals("all")) {
        return false;
      }
    }
    return true;
  }

  /** Parses inline style declarations in source order. */
  public java.util.Map<String, String> parseDeclarations(String input) {
    var declarations = new LinkedHashMap<String, String>();
    for (String declaration : splitDeclarations(input)) {
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

  /** Splits on semicolons that are not inside parentheses or quotes, so url(data:...;...) stays whole. */
  private static List<String> splitDeclarations(String input) {
    List<String> parts = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    int depth = 0;
    char quote = 0;
    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);
      if (quote != 0) {
        if (c == quote) {
          quote = 0;
        }
      } else if (c == '"' || c == '\'') {
        quote = c;
      } else if (c == '(') {
        depth++;
      } else if (c == ')' && depth > 0) {
        depth--;
      } else if (c == ';' && depth == 0) {
        parts.add(current.toString());
        current.setLength(0);
        continue;
      }
      current.append(c);
    }
    parts.add(current.toString());
    return parts;
  }
}
