package kr.soyuo.serox.storage.sqlite;

import java.net.URI;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import kr.soyuo.serox.core.model.HistoryEntry;
import kr.soyuo.serox.core.port.storage.HistoryRepository;

/** SQLite-backed visit history repository. */
public final class SqliteHistoryRepository implements HistoryRepository {
  private final SqliteDatabase database;

  public SqliteHistoryRepository(SqliteDatabase database) {
    this.database = Objects.requireNonNull(database, "database");
  }

  @Override
  public CompletionStage<Void> add(HistoryEntry entry) {
    Objects.requireNonNull(entry, "entry");
    return database.submit(
        connection -> {
          try (PreparedStatement statement =
              connection.prepareStatement(
                  "INSERT INTO history(id, url, title, visited_at) VALUES (?, ?, ?, ?)")) {
            statement.setString(1, entry.id().toString());
            statement.setString(2, entry.uri().toString());
            statement.setString(3, entry.title());
            statement.setLong(4, entry.visitedAt().toEpochMilli());
            statement.executeUpdate();
          }
          return null;
        });
  }

  @Override
  public CompletionStage<List<HistoryEntry>> recent(int limit) {
    if (limit <= 0) {
      return java.util.concurrent.CompletableFuture.completedFuture(List.of());
    }
    return database.submit(
        connection -> {
          List<HistoryEntry> entries = new ArrayList<>();
          try (PreparedStatement statement =
              connection.prepareStatement(
                  "SELECT id, url, title, visited_at FROM history "
                      + "ORDER BY visited_at DESC, id DESC LIMIT ?")) {
            statement.setInt(1, Math.min(limit, 10_000));
            try (ResultSet result = statement.executeQuery()) {
              while (result.next()) {
                try {
                  entries.add(
                      new HistoryEntry(
                          UUID.fromString(result.getString("id")),
                          URI.create(result.getString("url")),
                          result.getString("title"),
                          Instant.ofEpochMilli(result.getLong("visited_at"))));
                } catch (IllegalArgumentException malformedRow) {
                  // A damaged row must not hide the rest of the history.
                }
              }
            }
          }
          return List.copyOf(entries);
        });
  }

  @Override
  public CompletionStage<Void> prune(Instant olderThan, int maxEntries) {
    Objects.requireNonNull(olderThan, "olderThan");
    if (maxEntries < 0) {
      throw new IllegalArgumentException("maxEntries must not be negative");
    }
    return database.submit(
        connection -> {
          try (PreparedStatement statement =
              connection.prepareStatement("DELETE FROM history WHERE visited_at < ?")) {
            statement.setLong(1, olderThan.toEpochMilli());
            statement.executeUpdate();
          }
          try (PreparedStatement statement =
              connection.prepareStatement(
                  "DELETE FROM history WHERE id NOT IN "
                      + "(SELECT id FROM history ORDER BY visited_at DESC, id DESC LIMIT ?)")) {
            statement.setInt(1, maxEntries);
            statement.executeUpdate();
          }
          return null;
        });
  }
}
