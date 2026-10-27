package kr.soyuo.serox.core.model;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cleans text that came from the internet before it is shown, stored or sent anywhere: removes
 * markup, control and direction-override characters, collapses whitespace and bounds the length.
 */
public final class UntrustedText {
  private static final Pattern TAG = Pattern.compile("<[^>]{0,2000}>");
  private static final Pattern ENTITY = Pattern.compile("&(#x[0-9a-fA-F]{1,6}|#[0-9]{1,7}|[a-zA-Z]{2,8});");

  /** Prevents instantiation of this stateless utility. */
  private UntrustedText() {}

  /** Removes markup (as in search snippets) and then applies {@link #plain}. */
  public static String fromMarkup(String value, int maxLength) {
    return plain(decodeEntities(TAG.matcher(value).replaceAll(" ")), maxLength);
  }

  /** Removes hidden and control characters, collapses whitespace and truncates with an ellipsis. */
  public static String plain(String value, int maxLength) {
    StringBuilder out = new StringBuilder(Math.min(value.length(), maxLength + 1));
    value
        .codePoints()
        .forEach(
            codePoint -> {
              if (isHidden(codePoint)) {
                return;
              }
              out.appendCodePoint(Character.isWhitespace(codePoint) || Character.isISOControl(codePoint) ? ' ' : codePoint);
            });
    String collapsed = out.toString().replaceAll(" {2,}", " ").strip();
    if (collapsed.length() <= maxLength) {
      return collapsed;
    }
    int end = maxLength;
    if (Character.isHighSurrogate(collapsed.charAt(end - 1))) {
      end--;
    }
    return collapsed.substring(0, Math.max(0, end - 1)) + "…";
  }

  /** True when the text contains characters that can disguise what a string really says. */
  public static boolean hasHiddenCharacters(String value) {
    return value.codePoints().anyMatch(UntrustedText::isHidden);
  }

  private static boolean isHidden(int codePoint) {
    return (codePoint >= 0x200B && codePoint <= 0x200F)
        || (codePoint >= 0x202A && codePoint <= 0x202E)
        || (codePoint >= 0x2060 && codePoint <= 0x2064)
        || (codePoint >= 0x2066 && codePoint <= 0x2069)
        || codePoint == 0xFEFF
        || codePoint == 0x00AD
        || (Character.isISOControl(codePoint) && !Character.isWhitespace(codePoint));
  }

  private static String decodeEntities(String value) {
    Matcher matcher = ENTITY.matcher(value);
    StringBuilder out = new StringBuilder();
    while (matcher.find()) {
      matcher.appendReplacement(out, Matcher.quoteReplacement(decode(matcher.group(1), matcher.group())));
    }
    matcher.appendTail(out);
    return out.toString();
  }

  private static String decode(String body, String original) {
    try {
      if (body.startsWith("#x") || body.startsWith("#X")) {
        return new String(Character.toChars(Integer.parseInt(body.substring(2), 16)));
      }
      if (body.startsWith("#")) {
        return new String(Character.toChars(Integer.parseInt(body.substring(1))));
      }
    } catch (IllegalArgumentException invalidCodePoint) {
      return " ";
    }
    return switch (body) {
      case "amp" -> "&";
      case "lt" -> "<";
      case "gt" -> ">";
      case "quot" -> "\"";
      case "apos" -> "'";
      case "nbsp" -> " ";
      default -> original;
    };
  }
}
