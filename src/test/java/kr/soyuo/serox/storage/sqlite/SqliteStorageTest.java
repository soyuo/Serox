package kr.soyuo.serox.storage.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.soyuo.serox.core.model.AiPanelPosition;
import kr.soyuo.serox.core.model.Bookmark;
import kr.soyuo.serox.core.model.BrowserSettings;
import kr.soyuo.serox.core.model.DisplayDensity;
import kr.soyuo.serox.core.model.HistoryEntry;
import kr.soyuo.serox.core.model.NavigationStatus;
import kr.soyuo.serox.core.model.RecentSearch;
import kr.soyuo.serox.core.model.SearchCacheEntry;
import kr.soyuo.serox.core.model.SessionSnapshot;
import kr.soyuo.serox.core.model.TabId;
import kr.soyuo.serox.core.model.TabSnapshot;
import kr.soyuo.serox.core.model.ThemeMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteStorageTest {
  @TempDir Path tempDir;

  @Test
  void migratesAndPersistsRepositoriesAcrossReopen() {
    Path databaseFile = tempDir.resolve("profile/serox.db");
    UUID bookmarkId = UUID.randomUUID();
    TabId tabId = TabId.create();
    Instant timestamp = Instant.parse("2026-10-27T04:00:00Z");
    var settings =
        new BrowserSettings(
            ThemeMode.DARK, "#1266CC", 115, AiPanelPosition.LEFT, DisplayDensity.COMPACT);
    var history =
        new HistoryEntry(
            UUID.randomUUID(), URI.create("https://example.test/article"), "Example", timestamp);
    var session =
        new SessionSnapshot(
            UUID.randomUUID(),
            List.of(
                new TabSnapshot(
                    tabId,
                    Optional.of(URI.create("https://example.test/article")),
                    "Example",
                    NavigationStatus.IDLE,
                    false,
                    false)),
            Optional.of(tabId));

    try (var storage = new SqliteStorage(databaseFile)) {
      storage.settings().save(settings).toCompletableFuture().join();
      storage.history().add(history).toCompletableFuture().join();
      storage
          .bookmarks()
          .save(new Bookmark(bookmarkId, history.uri(), history.title(), timestamp))
          .toCompletableFuture()
          .join();
      storage.sessions().save(session).toCompletableFuture().join();
      storage
          .recentSearches()
          .record(new RecentSearch("sqlite storage", timestamp))
          .toCompletableFuture()
          .join();
      storage
          .searchCache()
          .save(
              new SearchCacheEntry(
                  "query-hash", "{\"results\":[]}", timestamp, timestamp.plusSeconds(60)))
          .toCompletableFuture()
          .join();
    }

    try (var reopened = new SqliteStorage(databaseFile)) {
      assertEquals(settings, reopened.settings().load().toCompletableFuture().join());
      assertEquals(List.of(history), reopened.history().recent(10).toCompletableFuture().join());
      assertEquals(
          bookmarkId, reopened.bookmarks().all().toCompletableFuture().join().getFirst().id());
      var restored = reopened.sessions().latest().toCompletableFuture().join().orElseThrow();
      assertEquals(session.id(), restored.id());
      assertEquals(tabId, restored.tabs().getFirst().id());
      assertEquals(Optional.of(tabId), restored.activeTabId());
      assertEquals(
          List.of(new RecentSearch("sqlite storage", timestamp)),
          reopened.recentSearches().recent(5).toCompletableFuture().join());
      assertEquals(
          Optional.of("{\"results\":[]}"),
          reopened
              .searchCache()
              .find("query-hash", timestamp.plusSeconds(30))
              .toCompletableFuture()
              .join());
      assertTrue(
          reopened
              .searchCache()
              .find("query-hash", timestamp.plusSeconds(60))
              .toCompletableFuture()
              .join()
              .isEmpty());
      assertEquals(
          1,
          reopened
              .searchCache()
              .removeExpired(timestamp.plusSeconds(60))
              .toCompletableFuture()
              .join());
      assertFalse(
          reopened
              .searchCache()
              .find("query-hash", timestamp)
              .toCompletableFuture()
              .join()
              .isPresent());
    }
  }

  @Test
  void settingsRepositorySuppliesSafeDefaultsForANewProfile() {
    try (var storage = new SqliteStorage(tempDir.resolve("empty.db"))) {
      assertEquals(
          new BrowserSettings(
              ThemeMode.SYSTEM, "#2563EB", 100, AiPanelPosition.RIGHT, DisplayDensity.COMFORTABLE),
          storage.settings().load().toCompletableFuture().join());
    }
  }

  @Test
  void clearAndDeleteRemoveOnlyWhatWasAskedFor() {
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    try (var storage = new SqliteStorage(tempDir.resolve("clear.db"))) {
      var searches = storage.recentSearches();
      searches.record(new RecentSearch("one", now)).toCompletableFuture().join();
      searches.record(new RecentSearch("two", now.plusSeconds(1))).toCompletableFuture().join();
      storage
          .history()
          .add(new HistoryEntry(UUID.randomUUID(), URI.create("https://a.test/"), "A", now))
          .toCompletableFuture()
          .join();

      searches.delete("one").toCompletableFuture().join();
      assertEquals(
          List.of("two"),
          searches.recent(5).toCompletableFuture().join().stream().map(RecentSearch::query).toList());

      searches.clear().toCompletableFuture().join();
      assertTrue(searches.recent(5).toCompletableFuture().join().isEmpty());
      assertEquals(1, storage.history().recent(10).toCompletableFuture().join().size());

      storage.history().clear().toCompletableFuture().join();
      assertTrue(storage.history().recent(10).toCompletableFuture().join().isEmpty());
    }
  }
}
