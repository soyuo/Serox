package kr.soyuo.serox.browser.css;

import java.util.Map;
import java.util.Optional;

/** Immutable CSS values after cascade, inheritance, and initial defaults. */
public record ComputedStyle(Map<String, String> values) {
  /** Freezes the computed property map. */
  public ComputedStyle {
    values = Map.copyOf(values);
  }

  /** Returns a computed value or an empty string when missing. */
  public String value(String property) {
    return values.getOrDefault(property, "");
  }

  /** Parses a supported color property. */
  public Optional<CssColor> color(String property) {
    return CssColor.parse(value(property));
  }

  /** Parses a pixel length, returning the fallback for unsupported units. */
  public double pixels(String property, double fallback) {
    String value = value(property).trim().toLowerCase(java.util.Locale.ROOT);
    if (value.endsWith("px")) {
      value = value.substring(0, value.length() - 2).trim();
    }
    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException exception) {
      return fallback;
    }
  }
}
