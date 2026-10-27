package kr.soyuo.serox.core.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import kr.soyuo.serox.browser.api.PageSnapshot;
import kr.soyuo.serox.core.event.InProcessBrowserEventBus;
import kr.soyuo.serox.core.event.PageLoaded;
import kr.soyuo.serox.core.event.SearchStarted;
import kr.soyuo.serox.core.model.HistoryEntry;
import kr.soyuo.serox.core.model.TabId;
import kr.soyuo.serox.storage.sqlite.SqliteStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UsageRecorderTest {
  private static final Instant NOW = Instant.parse("2026-10-27T06:00:00Z");
  @TempDir Path tempDir;

  @Test
  void recordsVisitsWithoutDuplicatingRapidReloads() {
    try (var storage = new SqliteStorage(tempDir.resolve("serox.db"));
        var bus = new InProcessBrowserEventBus()) {
      var clock = new MutableClock(NOW);
      try (var recorder =
          new UsageRecorder(bus, storage.history(), storage.recentSearches(), clock)) {
        TabId tab = TabId.create();
        URI uri = URI.create("https://example.test/");
        bus.publish(new PageLoaded(NOW, tab, new PageSnapshot(uri, "Example", "", NOW)));
        clock.advance(Duration.ofSeconds(5));
        bus.publish(new PageLoaded(NOW, tab, new PageSnapshot(uri, "Example", "", NOW)));
        clock.advance(Duration.ofMinutes(5));
        bus.publish(new PageLoaded(NOW, tab, new PageSnapshot(uri, "Example", "", NOW)));

        assertEquals(2, storage.history().recent(10).toCompletableFuture().join().size());
      }
    }
  }

  @Test
  void keepsNewestDistinctSearchesAndSurvivesReopen() {
    Path file = tempDir.resolve("serox.db");
    try (var storage = new SqliteStorage(file);
        var bus = new InProcessBrowserEventBus()) {
      var clock = new MutableClock(NOW);
      try (var recorder =
          new UsageRecorder(bus, storage.history(), storage.recentSearches(), clock)) {
        TabId tab = TabId.create();
        for (String query : List.of("a", "b", "c", "d", "e", "f", "a")) {
          clock.advance(Duration.ofSeconds(1));
          bus.publish(new SearchStarted(NOW, tab, 1, query));
          recorder.recordQuery("   ").toCompletableFuture().join();
        }
        storage.recentSearches().recent(5).toCompletableFuture().join();
      }
    }
    try (var storage = new SqliteStorage(file);
        var bus = new InProcessBrowserEventBus();
        var recorder =
            new UsageRecorder(
                bus, storage.history(), storage.recentSearches(), Clock.systemUTC())) {
      assertEquals(
          List.of("a", "f", "e", "d", "c"),
          recorder.recentQueries(5).toCompletableFuture().join());
    }
  }

  @Test
  void pruneRemovesExpiredAndSurplusHistory() {
    try (var storage = new SqliteStorage(tempDir.resolve("serox.db"))) {
      for (int day = 0; day < 6; day++) {
        storage
            .history()
            .add(
                new HistoryEntry(
                    UUID.randomUUID(),
                    URI.create("https://example.test/" + day),
                    "p" + day,
                    NOW.minus(Duration.ofDays(day))))
            .toCompletableFuture()
            .join();
      }
      storage.history().prune(NOW.minus(Duration.ofDays(4)), 3).toCompletableFuture().join();

      var remaining = storage.history().recent(10).toCompletableFuture().join();
      assertEquals(
          List.of("p0", "p1", "p2"), remaining.stream().map(HistoryEntry::title).toList());
    }
  }

  private static final class MutableClock extends Clock {
    private Instant now;

    MutableClock(Instant now) {
      this.now = now;
    }

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
