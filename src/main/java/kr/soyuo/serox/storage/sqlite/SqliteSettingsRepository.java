package kr.soyuo.serox.storage.sqlite;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import kr.soyuo.serox.core.model.AiPanelPosition;
import kr.soyuo.serox.core.model.BrowserSettings;
import kr.soyuo.serox.core.model.DisplayDensity;
import kr.soyuo.serox.core.model.ThemeMode;
import kr.soyuo.serox.core.port.storage.SettingsRepository;

/** SQLite-backed non-secret browser preferences repository. */
public final class SqliteSettingsRepository implements SettingsRepository {
  private static final BrowserSettings DEFAULTS =
      new BrowserSettings(
          ThemeMode.SYSTEM, "#2563EB", 100, AiPanelPosition.RIGHT, DisplayDensity.COMFORTABLE);

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
          return new BrowserSettings(
              enumValue(values, "theme", DEFAULTS.theme(), ThemeMode.class),
              values.getOrDefault("accentColor", DEFAULTS.accentColor()),
              integerValue(values, "fontScalePercent", DEFAULTS.fontScalePercent()),
              enumValue(
                  values, "aiPanelPosition", DEFAULTS.aiPanelPosition(), AiPanelPosition.class),
              enumValue(values, "displayDensity", DEFAULTS.displayDensity(), DisplayDensity.class));
        });
  }

  @Override
  public CompletionStage<Void> save(BrowserSettings settings) {
    Objects.requireNonNull(settings, "settings");
    Map<String, String> values =
        Map.of(
            "theme", settings.theme().name(),
            "accentColor", settings.accentColor(),
            "fontScalePercent", Integer.toString(settings.fontScalePercent()),
            "aiPanelPosition", settings.aiPanelPosition().name(),
            "displayDensity", settings.displayDensity().name());
    return database.submit(
        connection -> {
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
          return null;
        });
  }

  private static int integerValue(Map<String, String> values, String key, int fallback) {
    String value = values.get(key);
    return value == null ? fallback : Integer.parseInt(value);
  }

  private static <T extends Enum<T>> T enumValue(
      Map<String, String> values, String key, T fallback, Class<T> type) {
    String value = values.get(key);
    return value == null ? fallback : Enum.valueOf(type, value);
  }
}
