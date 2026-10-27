package kr.soyuo.serox.core.model;

import java.net.URI;
import java.util.Objects;

/** Typed, non-secret appearance preferences; credentials use a separate secure store. */
public record BrowserSettings(
    ThemeMode theme,
    String accentColor,
    int fontScalePercent,
    AiPanelPosition aiPanelPosition,
    DisplayDensity displayDensity,
    HistoryRetention historyRetention,
    boolean saveSearchHistory,
    StartupMode startupMode,
    String startPageUrl,
    AppLanguage language) {
  private static final int MAX_START_PAGE_LENGTH = 2048;

  /** Appearance and privacy preferences with the default general choices. */
  public BrowserSettings(
      ThemeMode theme,
      String accentColor,
      int fontScalePercent,
      AiPanelPosition aiPanelPosition,
      DisplayDensity displayDensity,
      HistoryRetention historyRetention,
      boolean saveSearchHistory) {
    this(
        theme,
        accentColor,
        fontScalePercent,
        aiPanelPosition,
        displayDensity,
        historyRetention,
        saveSearchHistory,
        StartupMode.HOME,
        "",
        AppLanguage.KOREAN);
  }

  /** Appearance preferences with the default privacy and general choices. */
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

  /** Validates all non-secret preferences. */
  public BrowserSettings {
    Objects.requireNonNull(theme, "theme");
    Objects.requireNonNull(accentColor, "accentColor");
    Objects.requireNonNull(aiPanelPosition, "aiPanelPosition");
    Objects.requireNonNull(displayDensity, "displayDensity");
    Objects.requireNonNull(historyRetention, "historyRetention");
    Objects.requireNonNull(startupMode, "startupMode");
    Objects.requireNonNull(startPageUrl, "startPageUrl");
    Objects.requireNonNull(language, "language");
    if (!accentColor.matches("#[0-9A-Fa-f]{6}")) {
      throw new IllegalArgumentException("accentColor must be a six-digit hex color");
    }
    if (fontScalePercent < 80 || fontScalePercent > 200) {
      throw new IllegalArgumentException("fontScalePercent must be between 80 and 200");
    }
    startPageUrl = startPageUrl.strip();
    if (!startPageUrl.isEmpty() && !isWebAddress(startPageUrl)) {
      throw new IllegalArgumentException("startPageUrl must be an http or https address");
    }
  }

  /** Whether the text is an http or https address with a host, as a start page must be. */
  public static boolean isWebAddress(String text) {
    if (text.length() > MAX_START_PAGE_LENGTH) {
      return false;
    }
    try {
      URI uri = URI.create(text);
      String scheme = uri.getScheme();
      return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
          && uri.getHost() != null;
    } catch (IllegalArgumentException exception) {
      return false;
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
        other.saveSearchHistory(),
        startupMode,
        startPageUrl,
        language);
  }

  /** These settings with the general choices of {@code other}. */
  public BrowserSettings withGeneralOf(BrowserSettings other) {
    return withGeneral(other.startupMode(), other.startPageUrl(), other.language());
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
        saveSearchHistory,
        startupMode,
        startPageUrl,
        language);
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
        save,
        startupMode,
        startPageUrl,
        language);
  }

  /** These settings with a different startup choice. */
  public BrowserSettings withStartupMode(StartupMode mode) {
    return withGeneral(mode, startPageUrl, language);
  }

  /** These settings with a different start page address; blank clears it. */
  public BrowserSettings withStartPageUrl(String url) {
    return withGeneral(startupMode, url, language);
  }

  /** These settings with a different language. */
  public BrowserSettings withLanguage(AppLanguage appLanguage) {
    return withGeneral(startupMode, startPageUrl, appLanguage);
  }

  private BrowserSettings withGeneral(StartupMode mode, String url, AppLanguage appLanguage) {
    return new BrowserSettings(
        theme,
        accentColor,
        fontScalePercent,
        aiPanelPosition,
        displayDensity,
        historyRetention,
        saveSearchHistory,
        mode,
        url,
        appLanguage);
  }
}
