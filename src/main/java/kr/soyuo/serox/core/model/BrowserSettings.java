package kr.soyuo.serox.core.model;

import java.net.URI;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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
    AppLanguage language,
    Map<ShortcutAction, KeyChord> shortcutOverrides,
    SearchPreferences search,
    SummaryPreferences summary) {
  private static final int MAX_START_PAGE_LENGTH = 2048;

  /** Older callers and profiles use the default search and summary preferences. */
  public BrowserSettings(
      ThemeMode theme,
      String accentColor,
      int fontScalePercent,
      AiPanelPosition aiPanelPosition,
      DisplayDensity displayDensity,
      HistoryRetention historyRetention,
      boolean saveSearchHistory,
      StartupMode startupMode,
      String startPageUrl,
      AppLanguage language,
      Map<ShortcutAction, KeyChord> shortcutOverrides) {
    this(
        theme,
        accentColor,
        fontScalePercent,
        aiPanelPosition,
        displayDensity,
        historyRetention,
        saveSearchHistory,
        startupMode,
        startPageUrl,
        language,
        shortcutOverrides,
        SearchPreferences.defaults(),
        SummaryPreferences.defaults());
  }

  /** Appearance and privacy preferences with the default general choices and shortcuts. */
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
        AppLanguage.KOREAN,
        Map.of());
  }

  /** Appearance preferences with the default privacy, general and shortcut choices. */
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
    Objects.requireNonNull(shortcutOverrides, "shortcutOverrides");
    Objects.requireNonNull(search, "search");
    Objects.requireNonNull(summary, "summary");
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
    Map<ShortcutAction, KeyChord> kept = new EnumMap<>(ShortcutAction.class);
    shortcutOverrides.forEach(
        (action, chord) -> {
          if (!chord.equals(action.defaultChord())) {
            kept.put(action, chord);
          }
        });
    shortcutOverrides = Map.copyOf(kept);
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

  /** The key combination of an action: the user's choice, or the default. */
  public KeyChord shortcut(ShortcutAction action) {
    return shortcutOverrides.getOrDefault(action, action.defaultChord());
  }

  /** The other action that already uses {@code chord}, if any. */
  public Optional<ShortcutAction> actionUsing(KeyChord chord, ShortcutAction except) {
    if (!chord.assigned()) {
      return Optional.empty();
    }
    for (ShortcutAction action : ShortcutAction.values()) {
      if (action != except && shortcut(action).equals(chord)) {
        return Optional.of(action);
      }
    }
    return Optional.empty();
  }

  /** These settings with a new key for an action; the key is not checked for conflicts. */
  public BrowserSettings withShortcut(ShortcutAction action, KeyChord chord) {
    Map<ShortcutAction, KeyChord> next = new EnumMap<>(ShortcutAction.class);
    next.putAll(shortcutOverrides);
    next.put(action, chord);
    return withShortcutOverrides(next);
  }

  /**
   * These settings with a new key for an action, taking it from any other action that has it; that
   * action is left without a key.
   */
  public BrowserSettings withShortcutTaken(ShortcutAction action, KeyChord chord) {
    BrowserSettings result = this;
    Optional<ShortcutAction> other = actionUsing(chord, action);
    if (other.isPresent()) {
      result = result.withShortcut(other.orElseThrow(), KeyChord.UNASSIGNED);
    }
    return result.withShortcut(action, chord);
  }

  /** These settings with every shortcut back at its default. */
  public BrowserSettings withDefaultShortcuts() {
    return withShortcutOverrides(Map.of());
  }

  /** These settings with the shortcuts of {@code other}. */
  public BrowserSettings withShortcutsOf(BrowserSettings other) {
    return withShortcutOverrides(other.shortcutOverrides());
  }

  private BrowserSettings withShortcutOverrides(Map<ShortcutAction, KeyChord> overrides) {
    return new BrowserSettings(
        theme,
        accentColor,
        fontScalePercent,
        aiPanelPosition,
        displayDensity,
        historyRetention,
        saveSearchHistory,
        startupMode,
        startPageUrl,
        language,
        overrides,
        search,
        summary);
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
        language,
        shortcutOverrides,
        search,
        summary);
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
        language,
        shortcutOverrides,
        search,
        summary);
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
        language,
        shortcutOverrides,
        search,
        summary);
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
        appLanguage,
        shortcutOverrides,
        search,
        summary);
  }

  public BrowserSettings withSearchPreferences(SearchPreferences value) {
    return new BrowserSettings(
        theme,
        accentColor,
        fontScalePercent,
        aiPanelPosition,
        displayDensity,
        historyRetention,
        saveSearchHistory,
        startupMode,
        startPageUrl,
        language,
        shortcutOverrides,
        value,
        summary);
  }

  public BrowserSettings withSummaryPreferences(SummaryPreferences value) {
    return new BrowserSettings(
        theme,
        accentColor,
        fontScalePercent,
        aiPanelPosition,
        displayDensity,
        historyRetention,
        saveSearchHistory,
        startupMode,
        startPageUrl,
        language,
        shortcutOverrides,
        search,
        value);
  }

  /** Keeps current content choices when an appearance draft is applied. */
  public BrowserSettings withContentPreferencesOf(BrowserSettings other) {
    return withSearchPreferences(other.search()).withSummaryPreferences(other.summary());
  }
}
