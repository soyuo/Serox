package kr.soyuo.serox.search.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import kr.soyuo.serox.core.event.InProcessBrowserEventBus;
import kr.soyuo.serox.core.event.SearchCancelled;
import kr.soyuo.serox.core.event.SearchCompleted;
import kr.soyuo.serox.core.event.SearchFailed;
import kr.soyuo.serox.core.event.SearchStarted;
import kr.soyuo.serox.core.model.TabId;
import kr.soyuo.serox.search.api.SearchResponse;
import kr.soyuo.serox.search.api.SearchResult;
import org.junit.jupiter.api.Test;

class SearchServiceTest {
  @Test
  void publishesProviderIndependentResultsForTheRequestingTab() {
    var eventBus = new InProcessBrowserEventBus();
    SearchService service =
        new SearchService(
            Optional.of(
                request ->
                    CompletableFuture.completedFuture(
                        new SearchResponse(
                            "fake",
                            List.of(
                                new SearchResult(
                                    "https://example.test/",
                                    URI.create("https://example.test/"),
                                    "Example",
                                    "Snippet",
                                    1))))),
            eventBus);
    TabId tabId = TabId.create();
    var started = new AtomicReference<SearchStarted>();
    var completed = new AtomicReference<SearchCompleted>();
    eventBus.subscribe(SearchStarted.class, started::set);
    eventBus.subscribe(SearchCompleted.class, completed::set);

    var response = service.search(tabId, "  java browser  ").toCompletableFuture().join();

    assertEquals("java browser", started.get().query());
    assertEquals(tabId, started.get().tabId());
    assertEquals("fake", response.providerId());
    assertEquals(started.get().requestId(), completed.get().requestId());
    assertEquals("Example", completed.get().response().results().getFirst().title());
    service.close();
    eventBus.close();
  }

  @Test
  void missingProviderPublishesConfigurationErrorWithoutCallingTheNetwork() {
    var eventBus = new InProcessBrowserEventBus();
    var service = new SearchService(Optional.empty(), eventBus);
    var failureEvent = new AtomicReference<SearchFailed>();
    eventBus.subscribe(SearchFailed.class, failureEvent::set);

    assertThrows(
        CompletionException.class,
        () -> service.search(TabId.create(), "query").toCompletableFuture().join());

    assertTrue(failureEvent.get().safeMessage().contains("SEROX_BRAVE_SEARCH_API_KEY"));
    service.close();
    eventBus.close();
  }

  @Test
  void cancellationStopsTheProviderFutureAndPublishesCancellation() {
    var eventBus = new InProcessBrowserEventBus();
    var pending = new CompletableFuture<SearchResponse>();
    var service = new SearchService(Optional.of(request -> pending), eventBus);
    TabId tabId = TabId.create();
    var cancelled = new AtomicReference<SearchCancelled>();
    eventBus.subscribe(SearchCancelled.class, cancelled::set);
    var result = service.search(tabId, "cancel this").toCompletableFuture();

    assertTrue(service.cancel(tabId));

    assertTrue(result.isCancelled());
    assertTrue(pending.isCancelled());
    assertEquals("cancel this", cancelled.get().query());
    assertFalse(service.cancel(tabId));
    service.close();
    eventBus.close();
  }

  @Test
  void retryPublishesResultsAfterAnEarlierFailure() {
    var eventBus = new InProcessBrowserEventBus();
    var attempts = new AtomicInteger();
    var completed = new AtomicReference<SearchCompleted>();
    var failed = new AtomicReference<SearchFailed>();
    eventBus.subscribe(SearchCompleted.class, completed::set);
    eventBus.subscribe(SearchFailed.class, failed::set);
    var service =
        new SearchService(
            Optional.of(
                request ->
                    attempts.getAndIncrement() == 0
                        ? CompletableFuture.failedFuture(new IllegalStateException("offline"))
                        : CompletableFuture.completedFuture(
                            new SearchResponse(
                                "fake",
                                List.of(
                                    new SearchResult(
                                        "one",
                                        URI.create("https://example.test/"),
                                        "Example",
                                        "Snippet",
                                        1))))),
            eventBus);
    TabId tabId = TabId.create();

    var first = service.search(tabId, "retry query").toCompletableFuture();
    assertThrows(CompletionException.class, first::join);
    var retried = service.search(tabId, "retry query").toCompletableFuture().join();

    assertEquals("Search failed. Check your connection and try again.", failed.get().safeMessage());
    assertEquals("Example", retried.results().getFirst().title());
    assertEquals("retry query", completed.get().query());
    assertEquals(2, attempts.get());
    service.close();
    eventBus.close();
  }

  @Test
  void replacingSearchCancelsOldRequestAndSuppressesStaleCompletion() {
    var eventBus = new InProcessBrowserEventBus();
    var firstPending = new CompletableFuture<SearchResponse>();
    var completed = new java.util.ArrayList<SearchCompleted>();
    eventBus.subscribe(SearchCompleted.class, completed::add);
    var attempts = new AtomicInteger();
    var service =
        new SearchService(
            Optional.of(
                request ->
                    attempts.getAndIncrement() == 0
                        ? firstPending
                        : CompletableFuture.completedFuture(new SearchResponse("fake", List.of()))),
            eventBus);
    TabId tabId = TabId.create();
    var first = service.search(tabId, "old query").toCompletableFuture();

    service.search(tabId, "new query").toCompletableFuture().join();

    assertTrue(first.isCancelled());
    assertTrue(firstPending.isCancelled());
    assertEquals(1, completed.size());
    assertEquals("new query", completed.getFirst().query());
    service.close();
    eventBus.close();
  }
}
