package kr.soyuo.serox.browser.css;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The first layer of a {@code box-shadow}: offsets, blur, spread, color and inset. */
public record BoxShadow(
    double offsetX, double offsetY, double blur, double spread, CssColor color, boolean inset) {

  /** Parses the first shadow layer, or null for none or an unreadable value. */
  public static BoxShadow parse(String value) {
    String text = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    if (text.isEmpty() || text.equals("none")) {
      return null;
    }
    String first = firstLayer(text);
    boolean inset = false;
    CssColor color = new CssColor(0, 0, 0, 1);
    List<Double> lengths = new ArrayList<>();
    for (String token : tokens(first)) {
      if (token.equals("inset")) {
        inset = true;
        continue;
      }
      Double length = length(token);
      if (length != null) {
        lengths.add(length);
        continue;
      }
      var parsed = CssColor.parse(token);
      if (parsed.isEmpty()) {
        return null;
      }
      color = parsed.get();
    }
    if (lengths.size() < 2 || lengths.size() > 4) {
      return null;
    }
    return new BoxShadow(
        lengths.get(0),
        lengths.get(1),
        lengths.size() > 2 ? Math.max(0, lengths.get(2)) : 0,
        lengths.size() > 3 ? lengths.get(3) : 0,
        color,
        inset);
  }

  private static String firstLayer(String text) {
    int depth = 0;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '(') {
        depth++;
      } else if (c == ')') {
        depth--;
      } else if (c == ',' && depth == 0) {
        return text.substring(0, i);
      }
    }
    return text;
  }

  private static List<String> tokens(String text) {
    List<String> tokens = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    int depth = 0;
    for (char c : text.toCharArray()) {
      if (c == '(') {
        depth++;
      } else if (c == ')') {
        depth--;
      }
      if (Character.isWhitespace(c) && depth == 0) {
        if (!current.isEmpty()) {
          tokens.add(current.toString());
          current.setLength(0);
        }
      } else {
        current.append(c);
      }
    }
    if (!current.isEmpty()) {
      tokens.add(current.toString());
    }
    return tokens;
  }

  private static Double length(String token) {
    String number = token.endsWith("px") ? token.substring(0, token.length() - 2) : token;
    try {
      return Double.parseDouble(number);
    } catch (NumberFormatException notALength) {
      return null;
    }
  }
}
