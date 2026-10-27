package kr.soyuo.serox.core.model;

import java.util.Objects;

/** Typed, non-secret appearance preferences; credentials use a separate secure store. */
public record BrowserSettings(
    ThemeMode theme,
    String accentColor,
    int fontScalePercent,
    AiPanelPosition aiPanelPosition,
    DisplayDensity displayDensity) {
  /** Factory defaults, also used when stored values are missing or invalid. */
  public static BrowserSettings defaults() {
    return new BrowserSettings(
        ThemeMode.SYSTEM, "#2563EB", 100, AiPanelPosition.RIGHT, DisplayDensity.COMFORTABLE);
  }

  /** Validates all non-secret appearance preferences. */
  public BrowserSettings {
    Objects.requireNonNull(theme, "theme");
    Objects.requireNonNull(accentColor, "accentColor");
    Objects.requireNonNull(aiPanelPosition, "aiPanelPosition");
    Objects.requireNonNull(displayDensity, "displayDensity");
    if (!accentColor.matches("#[0-9A-Fa-f]{6}")) {
      throw new IllegalArgumentException("accentColor must be a six-digit hex color");
    }
    if (fontScalePercent < 80 || fontScalePercent > 200) {
      throw new IllegalArgumentException("fontScalePercent must be between 80 and 200");
    }
  }
}
