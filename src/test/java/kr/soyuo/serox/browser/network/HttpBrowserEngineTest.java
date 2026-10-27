package kr.soyuo.serox.browser.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpHeaders;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import kr.soyuo.serox.browser.api.PageLoadRequest;
import kr.soyuo.serox.browser.html.HtmlDocumentParser;
import org.junit.jupiter.api.Test;

class HttpBrowserEngineTest {
  private static final URI REQUEST_URI = URI.create("https://example.test/start");

  @Test
  void fakeTransportFlowsThroughResponseDecodingAndDomIntoPageSnapshot() {
    URI finalUri = URI.create("https://example.test/final");
    HttpBrowserEngine engine =
        engine(
            CompletableFuture.completedFuture(
                response(
                    finalUri,
                    200,
                    "text/html; charset=UTF-8",
                    "<html><head><title>Example</title></head>"
                        + "<body><p>Hello &amp; world</p></body></html>")));

    var page = engine.load(new PageLoadRequest(REQUEST_URI)).toCompletableFuture().join();

    assertEquals(finalUri, page.uri());
    assertEquals("Example", page.title());
    assertEquals("Hello & world", page.visibleText());
  }

  @Test
  void failuresRemainAsynchronousAndCancellationReachesTransport() {
    CompletableFuture<HttpResponseData> failed =
        CompletableFuture.failedFuture(new IllegalStateException("offline"));
    HttpBrowserEngine failingEngine = engine(failed);
    assertThrows(
        CompletionException.class,
        () -> failingEngine.load(new PageLoadRequest(REQUEST_URI)).toCompletableFuture().join());

    CompletableFuture<HttpResponseData> pending = new CompletableFuture<>();
    HttpBrowserEngine pendingEngine = engine(pending);
    var result = pendingEngine.load(new PageLoadRequest(REQUEST_URI)).toCompletableFuture();
    assertTrue(result.cancel(true));
    assertTrue(pending.isCancelled());
  }

  @Test
  void responseBodyLimitIsEnforcedBeforeExcessBytesAreAccepted() {
    HttpRequestDispatcher.LimitedBodySubscriber subscriber =
        new HttpRequestDispatcher.LimitedBodySubscriber(3);
    var subscription = new RecordingSubscription();
    subscriber.onSubscribe(subscription);
    subscriber.onNext(List.of(java.nio.ByteBuffer.wrap(new byte[] {1, 2, 3, 4})));

    assertTrue(subscription.cancelled);
    assertThrows(
        CompletionException.class, () -> subscriber.getBody().toCompletableFuture().join());
  }

  private HttpBrowserEngine engine(CompletableFuture<HttpResponseData> response) {
    HttpTransport transport = request -> response;
    return new HttpBrowserEngine(transport, new HttpResponseHandler(), new HtmlDocumentParser());
  }

  private HttpResponseData response(URI uri, int status, String contentType, String html) {
    HttpHeaders headers =
        HttpHeaders.of(Map.of("Content-Type", List.of(contentType)), (name, value) -> true);
    return new HttpResponseData(uri, status, headers, html.getBytes(StandardCharsets.UTF_8));
  }

  private static final class RecordingSubscription
      implements java.util.concurrent.Flow.Subscription {
    private boolean cancelled;

    @Override
    public void request(long count) {}

    @Override
    public void cancel() {
      cancelled = true;
    }
  }
}
