package kr.soyuo.serox.storage.sqlite;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import kr.soyuo.serox.core.model.AiPanelPosition;
import kr.soyuo.serox.core.model.AppLanguage;
import kr.soyuo.serox.core.model.BrowserSettings;
import kr.soyuo.serox.core.model.DisplayDensity;
import kr.soyuo.serox.core.model.HistoryRetention;
import kr.soyuo.serox.core.model.KeyChord;
import kr.soyuo.serox.core.model.ShortcutAction;
import kr.soyuo.serox.core.model.StartupMode;
import kr.soyuo.serox.core.model.ThemeMode;
import kr.soyuo.serox.core.port.storage.SettingsRepository;

/** SQLite-backed non-secret browser preferences repository. */
public final class SqliteSettingsRepository implements SettingsRepository {
  private static final BrowserSettings DEFAULTS = BrowserSettings.defaults();
  private static final String SHORTCUT_PREFIX = "shortcut.";

  private final SqliteDatabase database;

  public SqliteSettingsRepository(SqliteDatabase database) {
    this.database = Objects.requireNonNull(database, "database");
  }

  @Override
  public CompletionStage<BrowserSettings> load() {
    return database.submit(
        connection -> {
          Map<String, String> values = new HashMap<>();
          try (PreparedStatement statement =
              connection.prepareStatement("SELECT key, value FROM settings")) {
            try (ResultSet result = statement.executeQuery()) {
              while (result.next()) {
                values.put(result.getString("key"), result.getString("value"));
              }
            }
          }
          return validOrDefault(
              () ->
                  new BrowserSettings(
                      enumValue(values, "theme", DEFAULTS.theme(), ThemeMode.class),
                      values.getOrDefault("accentColor", DEFAULTS.accentColor()),
                      integerValue(values, "fontScalePercent", DEFAULTS.fontScalePercent()),
                      enumValue(
                          values,
                          "aiPanelPosition",
                          DEFAULTS.aiPanelPosition(),
                          AiPanelPosition.class),
                      enumValue(
                          values,
                          "displayDensity",
                          DEFAULTS.displayDensity(),
                          DisplayDensity.class),
                      enumValue(
                          values,
                          "historyRetention",
                          DEFAULTS.historyRetention(),
                          HistoryRetention.class),
                      !"false".equals(values.get("saveSearchHistory")),
                      enumValue(values, "startupMode", DEFAULTS.startupMode(), StartupMode.class),
                      values.getOrDefault("startPageUrl", DEFAULTS.startPageUrl()),
                      enumValue(values, "language", DEFAULTS.language(), AppLanguage.class),
                      shortcutValues(values),
                      searchValues(values),
                      summaryValues(values)));
        });
  }

  /** The stored shortcut choices; entries that are no longer valid fall back to the default. */
  private static Map<ShortcutAction, KeyChord> shortcutValues(Map<String, String> values) {
    Map<ShortcutAction, KeyChord> overrides = new EnumMap<>(ShortcutAction.class);
    for (ShortcutAction action : ShortcutAction.values()) {
      String stored = values.get(SHORTCUT_PREFIX + action.name());
      if (stored != null) {
        KeyChord.parse(stored).ifPresent(chord -> overrides.put(action, chord));
      }
    }
    return overrides;
  }

  @Override
  public CompletionStage<Void> save(BrowserSettings settings) {
    Objects.requireNonNull(settings, "settings");
    Map<String, String> values = new HashMap<>();
    values.put("theme", settings.theme().name());
    values.put("accentColor", settings.accentColor());
    values.put("fontScalePercent", Integer.toString(settings.fontScalePercent()));
    values.put("aiPanelPosition", settings.aiPanelPosition().name());
    values.put("displayDensity", settings.displayDensity().name());
    values.put("historyRetention", settings.historyRetention().name());
    values.put("saveSearchHistory", Boolean.toString(settings.saveSearchHistory()));
    values.put("startupMode", settings.startupMode().name());
    values.put("startPageUrl", settings.startPageUrl());
    values.put("language", settings.language().name());
    values.put("search.evidencePages", Integer.toString(settings.search().evidencePages()));
    values.put("search.safeSearch", settings.search().safeSearch());
    values.put("search.timeoutSeconds", Integer.toString(settings.search().timeoutSeconds()));
    values.put("search.engine", settings.search().engine());
    values.put("summary.language", settings.summary().language());
    values.put("summary.keyPoints", Boolean.toString(settings.summary().keyPoints()));
    values.put("summary.inlineCitations", Boolean.toString(settings.summary().inlineCitations()));
    values.put("summary.sourceCharacters", Integer.toString(settings.summary().sourceCharacters()));
    values.put("summary.instructions", settings.summary().instructions());
    settings
        .shortcutOverrides()
        .forEach((action, chord) -> values.put(SHORTCUT_PREFIX + action.name(), chord.toString()));
    return database.submit(
        connection -> {
          connection.setAutoCommit(false);
          try {
            // Only choices that differ from the default are kept, so earlier ones must go first.
            try (PreparedStatement clear =
                connection.prepareStatement("DELETE FROM settings WHERE key LIKE 'shortcut.%'")) {
              clear.executeUpdate();
            }
            try (PreparedStatement statement =
                connection.prepareStatement(
                    "INSERT INTO settings(key, value, updated_at) VALUES (?, ?, ?) "
                        + "ON CONFLICT(key) DO UPDATE SET "
                        + "value=excluded.value, updated_at=excluded.updated_at")) {
              for (Map.Entry<String, String> entry : values.entrySet()) {
                statement.setString(1, entry.getKey());
                statement.setString(2, entry.getValue());
                statement.setLong(3, System.currentTimeMillis());
                statement.addBatch();
              }
              statement.executeBatch();
            }
            connection.commit();
          } catch (Exception exception) {
            connection.rollback();
            throw exception;
          } finally {
            connection.setAutoCommit(true);
          }
          return null;
        });
  }

  private static BrowserSettings validOrDefault(
      java.util.function.Supplier<BrowserSettings> loader) {
    try {
      return loader.get();
    } catch (IllegalArgumentException exception) {
      return DEFAULTS;
    }
  }

  private static int integerValue(Map<String, String> values, String key, int fallback) {
    String value = values.get(key);
    try {
      return value == null ? fallback : Integer.parseInt(value);
    } catch (NumberFormatException exception) {
      return fallback;
    }
  }

  private static kr.soyuo.serox.core.model.SearchPreferences searchValues(
      Map<String, String> values) {
    try {
      return new kr.soyuo.serox.core.model.SearchPreferences(
          integerValue(values, "search.evidencePages", 5),
          values.getOrDefault("search.safeSearch", "moderate"),
          integerValue(values, "search.timeoutSeconds", 10),
          values.getOrDefault("search.engine", "auto"));
    } catch (IllegalArgumentException invalid) {
      return kr.soyuo.serox.core.model.SearchPreferences.defaults();
    }
  }

  private static kr.soyuo.serox.core.model.SummaryPreferences summaryValues(
      Map<String, String> values) {
    try {
      return new kr.soyuo.serox.core.model.SummaryPreferences(
          values.getOrDefault(
              "summary.language", values.containsKey("language") ? "app" : "question"),
          !"false".equals(values.get("summary.keyPoints")),
          !"false".equals(values.get("summary.inlineCitations")),
          integerValue(values, "summary.sourceCharacters", 8000),
          values.getOrDefault(
              "summary.instructions",
              kr.soyuo.serox.core.model.SummaryPreferences.DEFAULT_INSTRUCTIONS));
    } catch (IllegalArgumentException invalid) {
      return kr.soyuo.serox.core.model.SummaryPreferences.defaults();
    }
  }

  private static <T extends Enum<T>> T enumValue(
      Map<String, String> values, String key, T fallback, Class<T> type) {
    String value = values.get(key);
    try {
      return value == null ? fallback : Enum.valueOf(type, value);
    } catch (IllegalArgumentException exception) {
      return fallback;
    }
  }
}
