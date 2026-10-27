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
import kr.soyuo.serox.core.model.Bookmark;
import kr.soyuo.serox.core.port.storage.BookmarkRepository;

/** SQLite-backed bookmark repository. */
public final class SqliteBookmarkRepository implements BookmarkRepository {
  private final SqliteDatabase database;

  public SqliteBookmarkRepository(SqliteDatabase database) {
    this.database = Objects.requireNonNull(database, "database");
  }

  @Override
  public CompletionStage<Void> save(Bookmark bookmark) {
    Objects.requireNonNull(bookmark, "bookmark");
    return database.submit(
        connection -> {
          try (PreparedStatement statement =
              connection.prepareStatement(
                  "INSERT INTO bookmarks(id, url, title, folder_id, created_at) "
                      + "VALUES (?, ?, ?, NULL, ?) "
                      + "ON CONFLICT(id) DO UPDATE SET url=excluded.url, title=excluded.title")) {
            statement.setString(1, bookmark.id().toString());
            statement.setString(2, bookmark.uri().toString());
            statement.setString(3, bookmark.title());
            statement.setLong(4, bookmark.createdAt().toEpochMilli());
            statement.executeUpdate();
          }
          return null;
        });
  }

  @Override
  public CompletionStage<Void> remove(UUID bookmarkId) {
    Objects.requireNonNull(bookmarkId, "bookmarkId");
    return database.submit(
        connection -> {
          try (PreparedStatement statement =
              connection.prepareStatement("DELETE FROM bookmarks WHERE id = ?")) {
            statement.setString(1, bookmarkId.toString());
            statement.executeUpdate();
          }
          return null;
        });
  }

  @Override
  public CompletionStage<List<Bookmark>> all() {
    return database.submit(
        connection -> {
          List<Bookmark> bookmarks = new ArrayList<>();
          try (PreparedStatement statement =
              connection.prepareStatement(
                  "SELECT id, url, title, created_at FROM bookmarks ORDER BY created_at DESC")) {
            try (ResultSet result = statement.executeQuery()) {
              while (result.next()) {
                bookmarks.add(
                    new Bookmark(
                        UUID.fromString(result.getString("id")),
                        URI.create(result.getString("url")),
                        result.getString("title"),
                        Instant.ofEpochMilli(result.getLong("created_at"))));
              }
            }
          }
          return List.copyOf(bookmarks);
        });
  }
}
