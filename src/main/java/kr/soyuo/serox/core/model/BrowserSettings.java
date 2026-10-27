package kr.soyuo.serox.core.model;

import java.util.Objects;

/** Typed, non-secret appearance preferences; credentials use a separate secure store. */
public record BrowserSettings(
    ThemeMode theme,
    String accentColor,
    int fontScalePercent,
    AiPanelPosition aiPanelPosition,
    DisplayDensity displayDensity,
    HistoryRetention historyRetention,
    boolean saveSearchHistory) {
  /** Appearance preferences with the default privacy choices. */
  public BrowserSettings(
      ThemeMode theme,
      String accentColor,
      int fontScalePercent,
      AiPanelPosition aiPanelPosition,
      DisplayDensity displayDensity) {
    this(
        theme,
        accentColor,
        fontScalePercent,
        aiPanelPosition,
        displayDensity,
        HistoryRetention.MONTH,
        true);
  }

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
    Objects.requireNonNull(historyRetention, "historyRetention");
    if (!accentColor.matches("#[0-9A-Fa-f]{6}")) {
      throw new IllegalArgumentException("accentColor must be a six-digit hex color");
    }
    if (fontScalePercent < 80 || fontScalePercent > 200) {
      throw new IllegalArgumentException("fontScalePercent must be between 80 and 200");
    }
  }

  /** These appearance choices with the privacy choices of {@code other}. */
  public BrowserSettings withPrivacyOf(BrowserSettings other) {
    return new BrowserSettings(
        theme,
        accentColor,
        fontScalePercent,
        aiPanelPosition,
        displayDensity,
        other.historyRetention(),
        other.saveSearchHistory());
  }

  /** These settings with a different history retention. */
  public BrowserSettings withRetention(HistoryRetention retention) {
    return new BrowserSettings(
        theme,
        accentColor,
        fontScalePercent,
        aiPanelPosition,
        displayDensity,
        retention,
        saveSearchHistory);
  }

  /** These settings with search history saving switched on or off. */
  public BrowserSettings withSaveSearchHistory(boolean save) {
    return new BrowserSettings(
        theme,
        accentColor,
        fontScalePercent,
        aiPanelPosition,
        displayDensity,
        historyRetention,
        save);
  }
}
