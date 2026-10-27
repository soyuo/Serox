package kr.soyuo.serox.storage.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import kr.soyuo.serox.core.model.HistoryEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteRecoveryTest {
  @TempDir Path tempDir;

  @Test
  void corruptDatabaseIsSetAsideAndReplacedWithAFreshOne() throws Exception {
    Path file = tempDir.resolve("serox.db");
    Files.writeString(file, "this is not a sqlite database, just text".repeat(50));

    try (var storage = new SqliteStorage(file)) {
      assertEquals(0, storage.history().recent(10).toCompletableFuture().join().size());
      storage.history().add(entry("https://example.test/")).toCompletableFuture().join();
      assertEquals(1, storage.history().recent(10).toCompletableFuture().join().size());
    }

    try (var siblings = Files.list(tempDir)) {
      assertTrue(siblings.anyMatch(path -> path.getFileName().toString().contains(".corrupt-")));
    }
  }

  @Test
  void newerSchemaIsKeptIntactAndReportedAsUnavailable() throws Exception {
    Path file = tempDir.resolve("serox.db");
    try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
        Statement statement = connection.createStatement()) {
      statement.execute("CREATE TABLE future (id INTEGER)");
      statement.execute("PRAGMA user_version = 99");
    }

    assertThrows(StorageException.class, () -> new SqliteStorage(file));
    try (var siblings = Files.list(tempDir)) {
      assertTrue(siblings.noneMatch(path -> path.getFileName().toString().contains(".corrupt-")));
    }
  }

  @Test
  void lockedDatabaseFailsTheOperationWithoutHangingOrThrowingToCallers() throws Exception {
    Path file = tempDir.resolve("serox.db");
    try (var database = new SqliteDatabase(file, 100);
        Connection blocker = DriverManager.getConnection("jdbc:sqlite:" + file);
        Statement statement = blocker.createStatement()) {
      var history = new SqliteHistoryRepository(database);
      statement.execute("BEGIN IMMEDIATE");

      var failed = history.add(entry("https://example.test/")).toCompletableFuture();
      var exception = assertThrows(CompletionException.class, failed::join);
      assertTrue(exception.getCause() instanceof StorageException);

      statement.execute("ROLLBACK");
      history.add(entry("https://example.test/")).toCompletableFuture().join();
      assertEquals(1, history.recent(10).toCompletableFuture().join().size());
    }
  }

  @Test
  void invalidStoredSettingsFallBackToDefaults() throws Exception {
    Path file = tempDir.resolve("serox.db");
    try (var storage = new SqliteStorage(file)) {
      storage.settings().load().toCompletableFuture().join();
    }
    try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
        Statement statement = connection.createStatement()) {
      statement.execute(
          "INSERT INTO settings(key, value, updated_at) VALUES "
              + "('theme', 'PURPLE', 0), ('fontScalePercent', 'huge', 0), "
              + "('accentColor', 'red', 0)");
    }

    try (var storage = new SqliteStorage(file)) {
      var loaded = storage.settings().load().toCompletableFuture().join();
      try (var pristine = new SqliteStorage(tempDir.resolve("pristine.db"))) {
        assertEquals(pristine.settings().load().toCompletableFuture().join(), loaded);
      }
    }
  }

  private static HistoryEntry entry(String uri) {
    return new HistoryEntry(UUID.randomUUID(), URI.create(uri), "t", Instant.now());
  }
}
