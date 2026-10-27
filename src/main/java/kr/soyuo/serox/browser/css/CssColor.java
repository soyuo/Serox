package kr.soyuo.serox.browser.css;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** RGBA color used by computed styles and canvas paint commands. */
public record CssColor(int red, int green, int blue, double opacity) {
  private static final Map<String, CssColor> NAMED =
      Map.of(
          "black", new CssColor(0, 0, 0, 1),
          "white", new CssColor(255, 255, 255, 1),
          "red", new CssColor(255, 0, 0, 1),
          "green", new CssColor(0, 128, 0, 1),
          "blue", new CssColor(0, 0, 255, 1),
          "gray", new CssColor(128, 128, 128, 1),
          "grey", new CssColor(128, 128, 128, 1),
          "transparent", new CssColor(0, 0, 0, 0));

  /** Validates RGBA channels. */
  public CssColor {
    if (red < 0 || red > 255 || green < 0 || green > 255 || blue < 0 || blue > 255) {
      throw new IllegalArgumentException("color channels must be between 0 and 255");
    }
    if (opacity < 0 || opacity > 1) {
      throw new IllegalArgumentException("opacity must be between 0 and 1");
    }
  }

  /** Parses a supported named or hexadecimal CSS color. */
  public static Optional<CssColor> parse(String value) {
    String normalized = value.trim().toLowerCase(Locale.ROOT);
    CssColor named = NAMED.get(normalized);
    if (named != null) {
      return Optional.of(named);
    }
    if (!normalized.startsWith("#")) {
      return Optional.empty();
    }
    String hex = normalized.substring(1);
    try {
      if (hex.length() == 3) {
        return Optional.of(
            new CssColor(
                Integer.parseInt(hex.substring(0, 1) + hex.substring(0, 1), 16),
                Integer.parseInt(hex.substring(1, 2) + hex.substring(1, 2), 16),
                Integer.parseInt(hex.substring(2, 3) + hex.substring(2, 3), 16),
                1));
      }
      if (hex.length() == 6) {
        return Optional.of(
            new CssColor(
                Integer.parseInt(hex.substring(0, 2), 16),
                Integer.parseInt(hex.substring(2, 4), 16),
                Integer.parseInt(hex.substring(4, 6), 16),
                1));
      }
    } catch (NumberFormatException ignored) {
      return Optional.empty();
    }
    return Optional.empty();
  }
}
