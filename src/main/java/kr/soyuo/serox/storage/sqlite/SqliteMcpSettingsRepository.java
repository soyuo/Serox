package kr.soyuo.serox.storage.sqlite;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import kr.soyuo.serox.core.model.McpAccess;
import kr.soyuo.serox.core.model.McpSettings;
import kr.soyuo.serox.core.port.storage.McpSettingsRepository;

/** Keeps the MCP server settings in the shared settings table under {@code mcp.*} keys. */
public final class SqliteMcpSettingsRepository implements McpSettingsRepository {
  private static final McpSettings DEFAULTS = McpSettings.defaults();

  private final SqliteDatabase database;

  public SqliteMcpSettingsRepository(SqliteDatabase database) {
    this.database = Objects.requireNonNull(database, "database");
  }

  @Override
  public CompletionStage<McpSettings> load() {
    return database.submit(
        connection -> {
          Map<String, String> values = new HashMap<>();
          try (PreparedStatement statement =
              connection.prepareStatement("SELECT key, value FROM settings WHERE key LIKE 'mcp.%'");
              ResultSet result = statement.executeQuery()) {
            while (result.next()) {
              values.put(result.getString("key"), result.getString("value"));
            }
          }
          return fromValues(values);
        });
  }

  private static McpSettings fromValues(Map<String, String> values) {
    int port = DEFAULTS.port();
    try {
      String stored = values.get("mcp.port");
      if (stored != null) {
        port = Integer.parseInt(stored);
      }
    } catch (NumberFormatException exception) {
      port = DEFAULTS.port();
    }
    if (!McpSettings.validPort(port)) {
      port = DEFAULTS.port();
    }
    McpAccess access = DEFAULTS.access();
    try {
      String stored = values.get("mcp.access");
      if (stored != null) {
        access = McpAccess.valueOf(stored);
      }
    } catch (IllegalArgumentException exception) {
      access = DEFAULTS.access();
    }
    boolean autoStart = "true".equals(values.get("mcp.autoStart"));
    return new McpSettings(port, access, autoStart);
  }

  @Override
  public CompletionStage<Void> save(McpSettings settings) {
    Objects.requireNonNull(settings, "settings");
    Map<String, String> values =
        Map.of(
            "mcp.port", Integer.toString(settings.port()),
            "mcp.access", settings.access().name(),
            "mcp.autoStart", Boolean.toString(settings.autoStart()));
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
}
