package kr.soyuo.serox.search.application;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;
import kr.soyuo.serox.core.event.BrowserEventBus;
import kr.soyuo.serox.core.event.SearchCancelled;
import kr.soyuo.serox.core.event.SearchCompleted;
import kr.soyuo.serox.core.event.SearchFailed;
import kr.soyuo.serox.core.event.SearchStarted;
import kr.soyuo.serox.core.model.TabId;
import kr.soyuo.serox.search.api.SearchProvider;
import kr.soyuo.serox.search.api.SearchRequest;
import kr.soyuo.serox.search.api.SearchResponse;
import kr.soyuo.serox.search.provider.SearchProviderException;

/** Coordinates cancellable, per-tab search requests and publishes UI-safe events. */
public final class SearchService implements AutoCloseable {
  private static final int RESULT_LIMIT = 10;
  private static final String UNCONFIGURED_MESSAGE =
      "Search is not configured. Set SEROX_BRAVE_SEARCH_API_KEY and restart Serox.";

  private volatile Optional<SearchProvider> provider;
  private volatile java.util.function.Supplier<String> safeSearch = () -> "moderate";
  private final BrowserEventBus eventBus;
  private final AtomicLong requestIds = new AtomicLong();
  private final Map<TabId, ActiveSearch> activeSearches = new HashMap<>();
  private boolean closed;

  /** Creates a search service with an optional provider and application event bus. */
  public SearchService(Optional<SearchProvider> provider, BrowserEventBus eventBus) {
    this.provider = Objects.requireNonNull(provider, "provider");
    this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
  }

  public void setSafeSearch(java.util.function.Supplier<String> mode) {
    safeSearch = Objects.requireNonNull(mode, "mode");
  }

  public boolean configured() {
    return provider.isPresent();
  }

  public void setProvider(SearchProvider value) {
    provider = Optional.of(Objects.requireNonNull(value, "value"));
  }

  /** Starts a search and returns its provider response stage. */
  public CompletionStage<SearchResponse> search(TabId tabId, String query) {
    Objects.requireNonNull(tabId, "tabId");
    Objects.requireNonNull(query, "query");
    String normalizedQuery = query.strip();
    long requestId = requestIds.incrementAndGet();
    ActiveSearch previous;
    synchronized (this) {
      ensureOpen();
      previous = activeSearches.remove(tabId);
    }
    if (previous != null) {
      previous.future().cancel(true);
    }
    eventBus.publish(new SearchStarted(Instant.now(), tabId, requestId, normalizedQuery));

    if (provider.isEmpty()) {
      SearchProviderException failure = new SearchProviderException(UNCONFIGURED_MESSAGE);
      publishFailure(tabId, requestId, normalizedQuery, failure);
      return CompletableFuture.failedFuture(failure);
    }

    SearchRequest request;
    try {
      request = new SearchRequest(normalizedQuery, RESULT_LIMIT, safeSearch.get());
    } catch (IllegalArgumentException exception) {
      publishFailure(tabId, requestId, normalizedQuery, exception);
      return CompletableFuture.failedFuture(exception);
    }

    CompletableFuture<SearchResponse> future;
    try {
      future =
          Objects.requireNonNull(provider.orElseThrow().search(request), "search result stage")
              .toCompletableFuture();
    } catch (RuntimeException exception) {
      publishFailure(tabId, requestId, normalizedQuery, exception);
      return CompletableFuture.failedFuture(exception);
    }
    synchronized (this) {
      ensureOpen();
      activeSearches.put(tabId, new ActiveSearch(requestId, normalizedQuery, future));
    }
    future.whenComplete(
        (response, failure) -> {
          synchronized (SearchService.this) {
            ActiveSearch active = activeSearches.get(tabId);
            if (active == null || active.requestId() != requestId) {
              return;
            }
            activeSearches.remove(tabId);
          }
          if (failure == null) {
            eventBus.publish(
                new SearchCompleted(Instant.now(), tabId, requestId, normalizedQuery, response));
          } else if (!future.isCancelled()) {
            publishFailure(tabId, requestId, normalizedQuery, unwrap(failure));
          }
        });
    return future;
  }

  /** Cancels the active search in one tab, if any. */
  public boolean cancel(TabId tabId) {
    Objects.requireNonNull(tabId, "tabId");
    ActiveSearch active;
    synchronized (this) {
      active = activeSearches.remove(tabId);
    }
    if (active == null) {
      return false;
    }
    active.future().cancel(true);
    eventBus.publish(new SearchCancelled(Instant.now(), tabId, active.requestId(), active.query()));
    return true;
  }

  @Override
  public void close() {
    Map<TabId, ActiveSearch> searches;
    synchronized (this) {
      if (closed) {
        return;
      }
      closed = true;
      searches = Map.copyOf(activeSearches);
      activeSearches.clear();
    }
    searches.values().forEach(search -> search.future().cancel(true));
  }

  private void publishFailure(TabId tabId, long requestId, String query, Throwable failure) {
    Throwable cause = unwrap(failure);
    String message =
        cause instanceof SearchProviderException
            ? cause.getMessage()
            : "Search failed. Check your connection and try again.";
    boolean offline = cause instanceof java.io.IOException;
    eventBus.publish(new SearchFailed(Instant.now(), tabId, requestId, query, message, offline));
  }

  private Throwable unwrap(Throwable failure) {
    return failure instanceof CompletionException && failure.getCause() != null
        ? failure.getCause()
        : failure;
  }

  private synchronized void ensureOpen() {
    if (closed) {
      throw new IllegalStateException("search service is closed");
    }
  }

  private record ActiveSearch(
      long requestId, String query, CompletableFuture<SearchResponse> future) {}
}
