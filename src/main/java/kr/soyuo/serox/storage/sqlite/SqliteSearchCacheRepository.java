package kr.soyuo.serox.storage.sqlite;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import kr.soyuo.serox.core.model.SearchCacheEntry;
import kr.soyuo.serox.core.port.storage.SearchCacheRepository;

/** SQLite-backed expiring search response cache repository. */
public final class SqliteSearchCacheRepository implements SearchCacheRepository {
  private final SqliteDatabase database;

  public SqliteSearchCacheRepository(SqliteDatabase database) {
    this.database = Objects.requireNonNull(database, "database");
  }

  @Override
  public CompletionStage<Void> save(SearchCacheEntry entry) {
    Objects.requireNonNull(entry, "entry");
    return database.submit(
        connection -> {
          try (PreparedStatement statement =
              connection.prepareStatement(
                  "INSERT INTO search_cache(cache_key, response, created_at, expires_at) "
                      + "VALUES (?, ?, ?, ?) ON CONFLICT(cache_key) DO UPDATE SET "
                      + "response=excluded.response, created_at=excluded.created_at, "
                      + "expires_at=excluded.expires_at")) {
            statement.setString(1, entry.cacheKey());
            statement.setString(2, entry.response());
            statement.setLong(3, entry.createdAt().toEpochMilli());
            statement.setLong(4, entry.expiresAt().toEpochMilli());
            statement.executeUpdate();
          }
          return null;
        });
  }

  @Override
  public CompletionStage<Optional<String>> find(String cacheKey, Instant now) {
    Objects.requireNonNull(cacheKey, "cacheKey");
    Objects.requireNonNull(now, "now");
    return database.submit(
        connection -> {
          try (PreparedStatement statement =
              connection.prepareStatement(
                  "SELECT response FROM search_cache WHERE cache_key = ? AND expires_at > ?")) {
            statement.setString(1, cacheKey);
            statement.setLong(2, now.toEpochMilli());
            try (ResultSet result = statement.executeQuery()) {
              return result.next() ? Optional.of(result.getString("response")) : Optional.empty();
            }
          }
        });
  }

  @Override
  public CompletionStage<Integer> removeExpired(Instant now) {
    Objects.requireNonNull(now, "now");
    return database.submit(
        connection -> {
          try (PreparedStatement statement =
              connection.prepareStatement("DELETE FROM search_cache WHERE expires_at <= ?")) {
            statement.setLong(1, now.toEpochMilli());
            return statement.executeUpdate();
          }
        });
  }
}
