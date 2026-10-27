package kr.soyuo.serox.storage.sqlite;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import kr.soyuo.serox.core.model.PermissionChoice;
import kr.soyuo.serox.core.model.SitePermission;
import kr.soyuo.serox.core.port.storage.SitePermissionRepository;

/** Keeps site permission choices in the shared settings table as {@code site.<host>.<NAME>}. */
public final class SqliteSitePermissionRepository implements SitePermissionRepository {
  private static final String PREFIX = "site.";

  private final SqliteDatabase database;

  public SqliteSitePermissionRepository(SqliteDatabase database) {
    this.database = Objects.requireNonNull(database, "database");
  }

  @Override
  public CompletionStage<Map<String, Map<SitePermission, PermissionChoice>>> loadAll() {
    return database.submit(
        connection -> {
          Map<String, Map<SitePermission, PermissionChoice>> all = new HashMap<>();
          try (PreparedStatement statement =
                  connection.prepareStatement(
                      "SELECT key, value FROM settings WHERE key LIKE 'site.%'");
              ResultSet result = statement.executeQuery()) {
            while (result.next()) {
              String key = result.getString("key");
              int dot = key.lastIndexOf('.');
              if (dot <= PREFIX.length()) {
                continue;
              }
              try {
                SitePermission permission = SitePermission.valueOf(key.substring(dot + 1));
                PermissionChoice choice = PermissionChoice.valueOf(result.getString("value"));
                all.computeIfAbsent(
                        key.substring(PREFIX.length(), dot),
                        host -> new EnumMap<>(SitePermission.class))
                    .put(permission, choice);
              } catch (IllegalArgumentException unknown) {
                // A row this version does not know is ignored.
              }
            }
          }
          return all;
        });
  }

  @Override
  public CompletionStage<Void> save(
      String host, SitePermission permission, PermissionChoice choice) {
    Objects.requireNonNull(host, "host");
    Objects.requireNonNull(permission, "permission");
    Objects.requireNonNull(choice, "choice");
    return database.submit(
        connection -> {
          try (PreparedStatement statement =
              connection.prepareStatement(
                  "INSERT INTO settings(key, value, updated_at) VALUES (?, ?, ?) "
                      + "ON CONFLICT(key) DO UPDATE SET "
                      + "value=excluded.value, updated_at=excluded.updated_at")) {
            statement.setString(1, PREFIX + host + "." + permission.name());
            statement.setString(2, choice.name());
            statement.setLong(3, System.currentTimeMillis());
            statement.executeUpdate();
          }
          return null;
        });
  }

  @Override
  public CompletionStage<Void> reset(String host) {
    Objects.requireNonNull(host, "host");
    return database.submit(
        connection -> {
          for (SitePermission permission : SitePermission.values()) {
            try (PreparedStatement statement =
                connection.prepareStatement("DELETE FROM settings WHERE key = ?")) {
              statement.setString(1, PREFIX + host + "." + permission.name());
              statement.executeUpdate();
            }
          }
          return null;
        });
  }
}
