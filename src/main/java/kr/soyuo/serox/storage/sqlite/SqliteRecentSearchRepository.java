package kr.soyuo.serox.storage.sqlite;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import kr.soyuo.serox.core.model.RecentSearch;
import kr.soyuo.serox.core.port.storage.RecentSearchRepository;

/** SQLite-backed, bounded recent-query repository. */
public final class SqliteRecentSearchRepository implements RecentSearchRepository {
  private static final int MAX_RECENT_SEARCHES = 5;
  private final SqliteDatabase database;

  public SqliteRecentSearchRepository(SqliteDatabase database) {
    this.database = Objects.requireNonNull(database, "database");
  }

  @Override
  public CompletionStage<Void> record(RecentSearch recentSearch) {
    Objects.requireNonNull(recentSearch, "recentSearch");
    String query = recentSearch.query().strip();
    if (query.isBlank()) {
      return java.util.concurrent.CompletableFuture.completedFuture(null);
    }
    return database.submit(
        connection -> {
          try (PreparedStatement statement =
              connection.prepareStatement(
                  "INSERT INTO recent_searches(query, used_at) VALUES (?, ?) "
                      + "ON CONFLICT(query) DO UPDATE SET used_at=excluded.used_at")) {
            statement.setString(1, query);
            statement.setLong(2, recentSearch.usedAt().toEpochMilli());
            statement.executeUpdate();
          }
          try (PreparedStatement statement =
              connection.prepareStatement(
                  "DELETE FROM recent_searches WHERE query NOT IN "
                      + "(SELECT query FROM recent_searches ORDER BY used_at DESC, query LIMIT ?)")) {
            statement.setInt(1, MAX_RECENT_SEARCHES);
            statement.executeUpdate();
          }
          return null;
        });
  }

  @Override
  public CompletionStage<List<RecentSearch>> recent(int limit) {
    if (limit <= 0) {
      return java.util.concurrent.CompletableFuture.completedFuture(List.of());
    }
    return database.submit(
        connection -> {
          List<RecentSearch> recent = new ArrayList<>();
          try (PreparedStatement statement =
              connection.prepareStatement(
                  "SELECT query, used_at FROM recent_searches "
                      + "ORDER BY used_at DESC, query LIMIT ?")) {
            statement.setInt(1, Math.min(limit, MAX_RECENT_SEARCHES));
            try (ResultSet result = statement.executeQuery()) {
              while (result.next()) {
                recent.add(
                    new RecentSearch(
                        result.getString("query"),
                        Instant.ofEpochMilli(result.getLong("used_at"))));
              }
            }
          }
          return List.copyOf(recent);
        });
  }

  @Override
  public CompletionStage<Void> delete(String query) {
    Objects.requireNonNull(query, "query");
    return database.submit(
        connection -> {
          try (PreparedStatement statement =
              connection.prepareStatement("DELETE FROM recent_searches WHERE query = ?")) {
            statement.setString(1, query.strip());
            statement.executeUpdate();
          }
          return null;
        });
  }

  @Override
  public CompletionStage<Void> clear() {
    return database.submit(
        connection -> {
          try (PreparedStatement statement =
              connection.prepareStatement("DELETE FROM recent_searches")) {
            statement.executeUpdate();
          }
          return null;
        });
  }
}
