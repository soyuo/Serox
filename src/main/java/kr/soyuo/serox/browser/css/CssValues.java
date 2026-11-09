package kr.soyuo.serox.browser.css;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Evaluates CSS math and theme functions into plain values before a declaration is checked:
 * {@code calc()}, {@code min()}, {@code max()}, {@code clamp()} and {@code round()} over absolute
 * lengths (px, pt, rem, vw, vh) and plain numbers, and {@code light-dark()}, which picks the light
 * value because pages are drawn in the light scheme. A math function over em, % or other relative
 * units is left as written, so the check reports it.
 */
public final class CssValues {
  /** The page size relative units are judged against, as Serox opens its pages. */
  static final double VIEWPORT_WIDTH = CssParser.VIEWPORT_WIDTH;

  static final double VIEWPORT_HEIGHT = 800;

  private static final double ROOT_FONT_SIZE = 16;

  private CssValues() {}

  /** The value with every function it can evaluate replaced; never null for valid input. */
  public static String resolveFunctions(String value) {
    String lower = value.toLowerCase(Locale.ROOT);
    if (!lower.contains("(")) {
      return value;
    }
    String current = value;
    for (int round = 0; round < 8; round++) {
      int start = innermostFunction(current);
      if (start < 0) {
        break;
      }
      int nameStart = start;
      while (nameStart > 0
          && (Character.isLetter(current.charAt(nameStart - 1))
              || current.charAt(nameStart - 1) == '-')) {
        nameStart--;
      }
      String name = current.substring(nameStart, start).toLowerCase(Locale.ROOT);
      int end = closing(current, start);
      if (end < 0) {
        return value;
      }
      String body = current.substring(start + 1, end);
      String replacement = evaluate(name, body);
      if (replacement == null) {
        break;
      }
      current = current.substring(0, nameStart) + replacement + current.substring(end + 1);
    }
    return current;
  }

  /** The opening parenthesis of a function we evaluate whose arguments hold no other call. */
  private static int innermostFunction(String text) {
    String lower = text.toLowerCase(Locale.ROOT);
    int best = -1;
    for (String name : List.of("calc(", "min(", "max(", "clamp(", "round(", "light-dark(")) {
      int from = 0;
      while (true) {
        int at = lower.indexOf(name, from);
        if (at < 0) {
          break;
        }
        boolean wordStart = at == 0 || !Character.isLetter(lower.charAt(at - 1))
            && lower.charAt(at - 1) != '-';
        int open = at + name.length() - 1;
        int close = closing(text, open);
        if (wordStart && close > 0 && !hasCall(text.substring(open + 1, close))) {
          best = Math.max(best, open);
        }
        from = at + 1;
      }
    }
    return best;
  }

  private static boolean hasCall(String body) {
    for (int i = 1; i < body.length(); i++) {
      if (body.charAt(i) == '(' && Character.isLetter(body.charAt(i - 1))) {
        return true;
      }
    }
    return false;
  }

  private static int closing(String text, int open) {
    int depth = 0;
    for (int i = open; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '(') {
        depth++;
      } else if (c == ')' && --depth == 0) {
        return i;
      }
    }
    return -1;
  }

  private static String evaluate(String name, String body) {
    List<String> args = splitArguments(body);
    try {
      switch (name) {
        case "light-dark" -> {
          return args.size() == 2 ? args.get(0).strip() : null;
        }
        case "calc" -> {
          return format(expression(body));
        }
        case "min", "max" -> {
          double result = name.equals("min") ? Double.MAX_VALUE : -Double.MAX_VALUE;
          for (String arg : args) {
            double v = expression(arg);
            result = name.equals("min") ? Math.min(result, v) : Math.max(result, v);
          }
          return format(result);
        }
        case "clamp" -> {
          if (args.size() != 3) {
            return null;
          }
          double low = expression(args.get(0));
          double mid = expression(args.get(1));
          double high = expression(args.get(2));
          return format(Math.max(low, Math.min(mid, high)));
        }
        case "round" -> {
          int first = 0;
          String mode = "nearest";
          if (!args.isEmpty()
              && List.of("nearest", "up", "down", "to-zero").contains(args.get(0).strip())) {
            mode = args.get(0).strip();
            first = 1;
          }
          double v = expression(args.get(first));
          double step = args.size() > first + 1 ? expression(args.get(first + 1)) : 1;
          if (step == 0) {
            return null;
          }
          double q = v / step;
          double rounded =
              switch (mode) {
                case "up" -> Math.ceil(q);
                case "down" -> Math.floor(q);
                case "to-zero" -> q < 0 ? Math.ceil(q) : Math.floor(q);
                default -> Math.round(q);
              };
          return format(rounded * step);
        }
        default -> {
          return null;
        }
      }
    } catch (IllegalArgumentException relative) {
      return null;
    }
  }

  private static List<String> splitArguments(String body) {
    List<String> parts = new ArrayList<>();
    int depth = 0;
    int start = 0;
    for (int i = 0; i < body.length(); i++) {
      char c = body.charAt(i);
      if (c == '(') {
        depth++;
      } else if (c == ')') {
        depth--;
      } else if (c == ',' && depth == 0) {
        parts.add(body.substring(start, i));
        start = i + 1;
      }
    }
    parts.add(body.substring(start));
    return parts;
  }

  private static String format(double pixels) {
    double rounded = Math.round(pixels * 100) / 100.0;
    return (rounded == Math.rint(rounded) ? Long.toString((long) rounded) : Double.toString(rounded))
        + "px";
  }

  // ---- a small recursive-descent parser over lengths and numbers ----

  /** Pixels for an expression of absolute lengths; throws for units that need context. */
  static double expression(String text) {
    Parser parser = new Parser(text);
    Term result = parser.sum();
    parser.skipSpace();
    if (parser.pos != parser.text.length()) {
      throw new IllegalArgumentException("trailing input");
    }
    if (!result.length && result.value != 0) {
      // A bare number in a length context is not a length (except 0).
      throw new IllegalArgumentException("not a length");
    }
    return result.value;
  }

  /** A value that is either a length in pixels or a plain number. */
  private record Term(double value, boolean length) {}

  private static final class Parser {
    private final String text;
    private int pos;

    private Parser(String text) {
      this.text = text.strip().toLowerCase(Locale.ROOT);
    }

    private void skipSpace() {
      while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
        pos++;
      }
    }

    private Term sum() {
      Term left = product();
      while (true) {
        skipSpace();
        if (pos >= text.length() || (text.charAt(pos) != '+' && text.charAt(pos) != '-')) {
          return left;
        }
        char op = text.charAt(pos++);
        Term right = product();
        if (left.length != right.length && left.value != 0 && right.value != 0) {
          throw new IllegalArgumentException("mixed units");
        }
        boolean length = left.length || right.length;
        left = new Term(op == '+' ? left.value + right.value : left.value - right.value, length);
      }
    }

    private Term product() {
      Term left = factor();
      while (true) {
        skipSpace();
        if (pos >= text.length() || (text.charAt(pos) != '*' && text.charAt(pos) != '/')) {
          return left;
        }
        char op = text.charAt(pos++);
        Term right = factor();
        if (op == '*') {
          if (left.length && right.length) {
            throw new IllegalArgumentException("length times length");
          }
          left = new Term(left.value * right.value, left.length || right.length);
        } else {
          if (right.length || right.value == 0) {
            throw new IllegalArgumentException("bad division");
          }
          left = new Term(left.value / right.value, left.length);
        }
      }
    }

    private Term factor() {
      skipSpace();
      if (pos < text.length() && text.charAt(pos) == '(') {
        pos++;
        Term inner = sum();
        skipSpace();
        if (pos >= text.length() || text.charAt(pos) != ')') {
          throw new IllegalArgumentException("unclosed");
        }
        pos++;
        return inner;
      }
      int start = pos;
      if (pos < text.length() && (text.charAt(pos) == '-' || text.charAt(pos) == '+')) {
        pos++;
      }
      while (pos < text.length()
          && (Character.isDigit(text.charAt(pos)) || text.charAt(pos) == '.')) {
        pos++;
      }
      if (pos == start || (pos == start + 1 && !Character.isDigit(text.charAt(start))
          && text.charAt(start) != '.')) {
        throw new IllegalArgumentException("number expected");
      }
      double number = Double.parseDouble(text.substring(start, pos));
      int unitStart = pos;
      while (pos < text.length() && (Character.isLetter(text.charAt(pos)) || text.charAt(pos) == '%')) {
        pos++;
      }
      String unit = text.substring(unitStart, pos);
      return switch (unit) {
        case "" -> new Term(number, false);
        case "px" -> new Term(number, true);
        case "pt" -> new Term(number * 96 / 72, true);
        case "rem" -> new Term(number * ROOT_FONT_SIZE, true);
        case "vw" -> new Term(number * VIEWPORT_WIDTH / 100, true);
        case "vh" -> new Term(number * VIEWPORT_HEIGHT / 100, true);
        case "vmin" -> new Term(number * Math.min(VIEWPORT_WIDTH, VIEWPORT_HEIGHT) / 100, true);
        case "vmax" -> new Term(number * Math.max(VIEWPORT_WIDTH, VIEWPORT_HEIGHT) / 100, true);
        default -> throw new IllegalArgumentException("relative unit " + unit);
      };
    }
  }
}
