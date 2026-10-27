package kr.soyuo.serox.core.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.soyuo.serox.core.model.HistoryEntry;
import kr.soyuo.serox.storage.sqlite.SqliteStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LibraryServiceTest {
  @TempDir Path tempDir;

  @Test
  void bookmarksAreDeduplicatedListedAndRemoved() {
    try (var storage = new SqliteStorage(tempDir.resolve("serox.db"))) {
      var service =
          new LibraryService(
              Optional.of(storage.history()), Optional.of(storage.bookmarks()), Clock.systemUTC());
      URI uri = URI.create("https://example.test/a");

      assertTrue(service.addBookmark(uri, "A").toCompletableFuture().join());
      assertFalse(service.addBookmark(uri, "A again").toCompletableFuture().join());
      var saved = service.bookmarks().toCompletableFuture().join();
      assertEquals(List.of("A"), saved.stream().map(b -> b.title()).toList());

      service.removeBookmark(saved.getFirst().id()).toCompletableFuture().join();
      assertTrue(service.bookmarks().toCompletableFuture().join().isEmpty());
    }
  }

  @Test
  void historyComesFromStorageAndEmptyWithoutIt() {
    try (var storage = new SqliteStorage(tempDir.resolve("serox.db"))) {
      storage
          .history()
          .add(
              new HistoryEntry(
                  UUID.randomUUID(), URI.create("https://example.test/"), "Example", Instant.now()))
          .toCompletableFuture()
          .join();
      var service =
          new LibraryService(
              Optional.of(storage.history()), Optional.of(storage.bookmarks()), Clock.systemUTC());

      assertEquals(1, service.recentHistory().toCompletableFuture().join().size());
    }
    var unavailable = new LibraryService(Optional.empty(), Optional.empty(), Clock.systemUTC());
    assertFalse(unavailable.available());
    assertTrue(unavailable.recentHistory().toCompletableFuture().join().isEmpty());
    assertFalse(
        unavailable.addBookmark(URI.create("https://example.test/"), "x").toCompletableFuture().join());
  }
}
