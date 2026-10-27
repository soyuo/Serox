package kr.soyuo.serox.ai.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpHeaders;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import kr.soyuo.serox.ai.api.AiCompleted;
import kr.soyuo.serox.ai.api.AiEvent;
import kr.soyuo.serox.ai.api.AiFailed;
import kr.soyuo.serox.ai.api.AiProvider;
import kr.soyuo.serox.ai.api.AiTextDelta;
import kr.soyuo.serox.ai.context.ContextBuilder;
import kr.soyuo.serox.browser.html.HtmlDocumentParser;
import kr.soyuo.serox.browser.network.HttpResponseData;
import kr.soyuo.serox.browser.network.HttpResponseHandler;
import kr.soyuo.serox.core.event.AiAnswerChanged;
import kr.soyuo.serox.core.event.AiAnswerChanged.Phase;
import kr.soyuo.serox.core.event.InProcessBrowserEventBus;
import kr.soyuo.serox.core.event.SearchCompleted;
import kr.soyuo.serox.core.model.TabId;
import kr.soyuo.serox.search.api.SearchResponse;
import kr.soyuo.serox.search.api.SearchResult;
import kr.soyuo.serox.search.fetch.FetchLimits;
import kr.soyuo.serox.search.fetch.PageFetcher;
import org.junit.jupiter.api.Test;

class AiSearchServiceTest {
  private static final SearchResult RESULT =
      new SearchResult("r1", URI.create("https://example.test/a"), "Example", "snippet", 1);

  private final InProcessBrowserEventBus bus = new InProcessBrowserEventBus();
  private final List<AiAnswerChanged> aiEvents = new ArrayList<>();
  private final AtomicInteger searchEvents = new AtomicInteger();

  private PageFetcher fetcher(boolean reachable) {
    return new PageFetcher(
        request ->
            reachable
                ? CompletableFuture.completedFuture(
                    new HttpResponseData(
                        request.uri(),
                        200,
                        HttpHeaders.of(
                            Map.of("Content-Type", List.of("text/html; charset=utf-8")),
                            (a, b) -> true),
                        "<body><p>Serox is a browser.</p></body>".getBytes(StandardCharsets.UTF_8)))
                : CompletableFuture.failedFuture(new IllegalStateException("down")),
        new HttpResponseHandler(),
        new HtmlDocumentParser(),
        FetchLimits.defaults());
  }

  private AiSearchService service(Optional<AiProvider> provider, boolean reachable) {
    bus.subscribe(AiAnswerChanged.class, aiEvents::add);
    bus.subscribe(SearchCompleted.class, event -> searchEvents.incrementAndGet());
    return new AiSearchService(
        provider, fetcher(reachable), new ContextBuilder(), bus, Clock.systemUTC());
  }

  private void search() {
    bus.publish(
        new SearchCompleted(
            Instant.now(), TabId.create(), 1, "what is serox", new SearchResponse("test", List.of(RESULT))));
  }

  private static AiProvider emitting(AiEvent... events) {
    return request ->
        subscriber ->
            subscriber.onSubscribe(
                new Flow.Subscription() {
                  @Override
                  public void request(long n) {
                    for (AiEvent event : events) {
                      subscriber.onNext(event);
                    }
                    subscriber.onComplete();
                  }

                  @Override
                  public void cancel() {}
                });
  }

  @Test
  void streamsAnAnswerAndResolvesCitations() {
    try (var service =
        service(
            Optional.of(
                emitting(
                    new AiTextDelta("Serox is a browser [S1]."),
                    new AiTextDelta(" Invented [S7]."),
                    new AiCompleted(List.of()))),
            true)) {
      search();

      assertEquals(
          List.of(Phase.PREPARING, Phase.STREAMING, Phase.STREAMING, Phase.COMPLETED),
          aiEvents.stream().map(AiAnswerChanged::phase).toList());
      var answer = aiEvents.getLast().answer().orElseThrow();
      assertEquals(1, answer.citations().size());
      assertEquals(List.of("S7"), answer.invalidSourceIds());
    }
  }

  @Test
  void providerFailureLeavesSearchResultsUntouched() {
    try (var service = service(Optional.of(emitting(new AiFailed("unavailable", "AI down"))), true)) {
      search();

      assertEquals(Phase.FAILED, aiEvents.getLast().phase());
      assertEquals("AI down", aiEvents.getLast().message());
      assertEquals(1, searchEvents.get());
    }
  }

  @Test
  void unreadablePagesAndMissingKeysReportWithoutThrowing() {
    try (var service = service(Optional.of(emitting(new AiCompleted(List.of()))), false)) {
      search();
      assertEquals(Phase.FAILED, aiEvents.getLast().phase());
    }
    aiEvents.clear();
    try (var service = service(Optional.empty(), true)) {
      search();
      assertEquals(Phase.UNAVAILABLE, aiEvents.getLast().phase());
      assertTrue(aiEvents.getLast().message().contains("API 키"));
    }
  }
}
