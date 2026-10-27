package kr.soyuo.serox.core.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;
import java.util.logging.Logger;
import kr.soyuo.serox.core.event.BrowserEventBus;
import kr.soyuo.serox.core.event.EventSubscription;
import kr.soyuo.serox.core.event.PageLoaded;
import kr.soyuo.serox.core.event.SearchStarted;
import kr.soyuo.serox.core.model.BrowserSettings;
import kr.soyuo.serox.core.model.HistoryEntry;
import kr.soyuo.serox.core.model.RecentSearch;
import kr.soyuo.serox.core.port.storage.HistoryRepository;
import kr.soyuo.serox.core.port.storage.RecentSearchRepository;

/** Records visits and searches from application events; storage failures never reach callers. */
public final class UsageRecorder implements AutoCloseable {
  static final Duration DUPLICATE_VISIT_WINDOW = Duration.ofSeconds(30);
  static final int MAX_HISTORY_ENTRIES = 5_000;
  private static final Logger LOGGER = Logger.getLogger(UsageRecorder.class.getName());

  private final HistoryRepository historyRepository;
  private final RecentSearchRepository recentSearchRepository;
  private final Clock clock;
  private final java.util.function.Supplier<BrowserSettings> settings;
  private final Map<java.net.URI, Instant> lastVisits = new HashMap<>();
  private final EventSubscription pageSubscription;
  private final EventSubscription searchSubscription;

  /** Records with the default privacy choices (30 days of visits, searches saved). */
  public UsageRecorder(
      BrowserEventBus eventBus,
      HistoryRepository historyRepository,
      RecentSearchRepository recentSearchRepository,
      Clock clock) {
    this(eventBus, historyRepository, recentSearchRepository, clock, BrowserSettings::defaults);
  }

  /** Subscribes to page and search events; {@code settings} is read on every event. */
  public UsageRecorder(
      BrowserEventBus eventBus,
      HistoryRepository historyRepository,
      RecentSearchRepository recentSearchRepository,
      Clock clock,
      java.util.function.Supplier<BrowserSettings> settings) {
    Objects.requireNonNull(eventBus, "eventBus");
    this.historyRepository = Objects.requireNonNull(historyRepository, "historyRepository");
    this.recentSearchRepository =
        Objects.requireNonNull(recentSearchRepository, "recentSearchRepository");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.settings = Objects.requireNonNull(settings, "settings");
    pageSubscription = eventBus.subscribe(PageLoaded.class, this::recordVisit);
    searchSubscription = eventBus.subscribe(SearchStarted.class, this::recordSearch);
  }

  /** Loads stored recent queries newest first, or an empty list when storage is unavailable. */
  public CompletionStage<List<String>> recentQueries(int limit) {
    return recentSearchRepository
        .recent(limit)
        .thenApply(searches -> searches.stream().map(RecentSearch::query).toList())
        .exceptionally(
            failure -> {
              LOGGER.log(Level.WARNING, "Recent searches could not be loaded.");
              return List.of();
            });
  }

  /** Stores a query so the home screen can offer it again. */
  public CompletionStage<Void> recordQuery(String query) {
    if (query == null || query.isBlank() || !settings.get().saveSearchHistory()) {
      return CompletableFuture.completedFuture(null);
    }
    return guarded(
        () -> recentSearchRepository.record(new RecentSearch(query.strip(), clock.instant())),
        "Recent search could not be saved.");
  }

  /** Removes one stored query; failures are logged and the query simply stays. */
  public CompletionStage<Void> deleteQuery(String query) {
    return guarded(() -> recentSearchRepository.delete(query), "Recent search could not be removed.");
  }

  /** Removes every stored query. */
  public CompletionStage<Void> clearQueries() {
    return guarded(recentSearchRepository::clear, "Recent searches could not be cleared.");
  }

  private void recordSearch(SearchStarted event) {
    recordQuery(event.query());
  }

  private void recordVisit(PageLoaded event) {
    java.net.URI uri = event.page().uri();
    Instant now = clock.instant();
    synchronized (lastVisits) {
      Instant previous = lastVisits.get(uri);
      if (previous != null && Duration.between(previous, now).abs().compareTo(DUPLICATE_VISIT_WINDOW) < 0) {
        return;
      }
      lastVisits.put(uri, now);
    }
    var entry = new HistoryEntry(UUID.randomUUID(), uri, event.page().title(), now);
    guarded(
        () ->
            historyRepository
                .add(entry)
                .thenCompose(
                    ignored ->
                        historyRepository.prune(
                            retentionCutoff(now), MAX_HISTORY_ENTRIES)),
        "Visit history could not be saved.");
  }

  /** Visits older than this are dropped; "forever" keeps age out of it (the count still caps). */
  private Instant retentionCutoff(Instant now) {
    return settings
        .get()
        .historyRetention()
        .keepFor()
        .map(now::minus)
        .orElse(Instant.EPOCH);
  }

  private CompletionStage<Void> guarded(
      java.util.function.Supplier<CompletionStage<Void>> work, String message) {
    try {
      return work.get()
          .exceptionally(
              failure -> {
                LOGGER.log(Level.WARNING, message);
                return null;
              });
    } catch (RuntimeException exception) {
      LOGGER.log(Level.WARNING, message);
      return CompletableFuture.completedFuture(null);
    }
  }

  @Override
  public void close() {
    pageSubscription.close();
    searchSubscription.close();
  }
}
