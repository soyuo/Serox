package kr.soyuo.serox.search.fetch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpHeaders;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import kr.soyuo.serox.browser.html.HtmlDocumentParser;
import kr.soyuo.serox.browser.network.HttpResponseData;
import kr.soyuo.serox.browser.network.HttpResponseHandler;
import kr.soyuo.serox.search.api.SearchResult;
import org.junit.jupiter.api.Test;

class PageFetcherTest {
  private static SearchResult result(int rank, String path) {
    return new SearchResult(
        "S" + rank, URI.create("https://example.test/" + path), "t" + rank, "s", rank);
  }

  private static HttpResponseData html(URI uri, String body) {
    return new HttpResponseData(
        uri,
        200,
        HttpHeaders.of(Map.of("Content-Type", List.of("text/html; charset=utf-8")), (a, b) -> true),
        body.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void extractsVisibleTextAndSkipsScripts() {
    var fetcher =
        new PageFetcher(
            request ->
                CompletableFuture.completedFuture(
                    html(
                        request.uri(),
                        "<html><head><title>Hello</title></head><body><p>First</p>"
                            + "<script>secret()</script><div>Second  line</div></body></html>")),
            new HttpResponseHandler(),
            new HtmlDocumentParser(),
            FetchLimits.defaults());

    var pages = fetcher.fetch(List.of(result(1, "a"))).toCompletableFuture().join();

    assertEquals(1, pages.size());
    assertEquals("Hello", pages.getFirst().title());
    assertEquals("First Second line", pages.getFirst().text());
  }

  @Test
  void limitsPageCountAndTextLengthAndSkipsFailures() {
    AtomicInteger requests = new AtomicInteger();
    var limits = new FetchLimits(2, Duration.ofSeconds(1), Duration.ofSeconds(2), 10);
    var fetcher =
        new PageFetcher(
            request -> {
              requests.incrementAndGet();
              if (request.uri().getPath().endsWith("bad")) {
                return CompletableFuture.failedFuture(new IllegalStateException("down"));
              }
              return CompletableFuture.completedFuture(
                  html(request.uri(), "<body>" + "word ".repeat(100) + "</body>"));
            },
            new HttpResponseHandler(),
            new HtmlDocumentParser(),
            limits);

    var pages =
        fetcher
            .fetch(List.of(result(1, "bad"), result(2, "ok"), result(3, "never")))
            .toCompletableFuture()
            .join();

    assertEquals(2, requests.get());
    assertEquals(1, pages.size());
    assertTrue(pages.getFirst().text().length() <= 10);
  }

  @Test
  void slowPagesAreDroppedWhenTheTimeoutExpires() {
    var limits = new FetchLimits(2, Duration.ofMillis(100), Duration.ofMillis(500), 100);
    var fetcher =
        new PageFetcher(
            request -> new CompletableFuture<>(),
            new HttpResponseHandler(),
            new HtmlDocumentParser(),
            limits);

    var pages = fetcher.fetch(List.of(result(1, "slow"))).toCompletableFuture().join();

    assertFalse(pages.iterator().hasNext());
  }

  @Test
  void cancellationAndDeadlinesCancelTheUnderlyingTransport() throws Exception {
    for (boolean cancel : new boolean[] {true, false}) {
      CompletableFuture<HttpResponseData> transport = new CompletableFuture<>();
      CompletableFuture<Void> stopped = new CompletableFuture<>();
      transport.whenComplete(
          (response, failure) -> {
            if (transport.isCancelled()) stopped.complete(null);
          });
      var fetcher =
          new PageFetcher(
              request -> transport,
              new HttpResponseHandler(),
              new HtmlDocumentParser(),
              new FetchLimits(1, Duration.ofMillis(100), Duration.ofMillis(200), 100));
      var pages = fetcher.fetch(List.of(result(1, "slow"))).toCompletableFuture();
      if (cancel) pages.cancel(true);
      stopped.get(2, java.util.concurrent.TimeUnit.SECONDS);
      assertTrue(transport.isCancelled());
    }
  }

  @Test
  void aPreferenceSnapshotControlsPageAndTextLimits() {
    AtomicInteger requests = new AtomicInteger();
    var fetcher =
        new PageFetcher(
            request -> {
              requests.incrementAndGet();
              return CompletableFuture.completedFuture(
                  html(request.uri(), "<body>" + "content ".repeat(2000) + "</body>"));
            },
            new HttpResponseHandler(),
            new HtmlDocumentParser(),
            FetchLimits.defaults());
    var pages =
        fetcher
            .fetch(
                List.of(result(1, "one"), result(2, "two")),
                new FetchLimits(1, Duration.ofSeconds(10), Duration.ofSeconds(10), 3000))
            .toCompletableFuture()
            .join();
    assertEquals(1, requests.get());
    assertEquals(3000, pages.getFirst().text().length());
  }
}
