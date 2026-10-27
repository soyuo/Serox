package kr.soyuo.serox.browser.network;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import kr.soyuo.serox.browser.api.PageLoadRequest;

/** Sends bounded asynchronous HTTP requests using the JDK transport. */
public final class HttpRequestDispatcher {
  private final HttpClient client;
  private final Duration requestTimeout;
  private final int maxResponseBytes;

  public HttpRequestDispatcher(HttpClient client, Duration requestTimeout, int maxResponseBytes) {
    this.client = Objects.requireNonNull(client, "client");
    this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
    if (requestTimeout.isZero() || requestTimeout.isNegative()) {
      throw new IllegalArgumentException("requestTimeout must be positive");
    }
    if (maxResponseBytes < 1) {
      throw new IllegalArgumentException("maxResponseBytes must be positive");
    }
    this.maxResponseBytes = maxResponseBytes;
  }

  /** Creates a client with standard certificate validation and automatic redirects. */
  public static HttpRequestDispatcher createDefault(
      Duration connectTimeout, Duration requestTimeout, int maxResponseBytes) {
    HttpClient client =
        HttpClient.newBuilder()
            .connectTimeout(Objects.requireNonNull(connectTimeout, "connectTimeout"))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    return new HttpRequestDispatcher(client, requestTimeout, maxResponseBytes);
  }

  /** Dispatches one GET request; cancelling the returned future cancels the HTTP exchange. */
  public CompletionStage<HttpResponseData> dispatch(PageLoadRequest pageRequest) {
    Objects.requireNonNull(pageRequest, "pageRequest");
    HttpRequest request =
        HttpRequest.newBuilder(pageRequest.uri())
            .timeout(requestTimeout)
            .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
            .GET()
            .build();
    CompletableFuture<HttpResponse<byte[]>> exchange =
        client.sendAsync(request, ignored -> new LimitedBodySubscriber(maxResponseBytes));
    CompletableFuture<HttpResponseData> result = new CompletableFuture<>();
    exchange.whenComplete(
        (response, failure) -> {
          if (failure != null) {
            result.completeExceptionally(failure);
          } else {
            result.complete(
                new HttpResponseData(
                    response.uri(), response.statusCode(), response.headers(), response.body()));
          }
        });
    result.whenComplete(
        (response, failure) -> {
          if (result.isCancelled()) {
            exchange.cancel(true);
          }
        });
    return result;
  }

  private static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
    private final int maxBytes;
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final CompletableFuture<byte[]> body = new CompletableFuture<>();
    private Flow.Subscription subscription;

    private LimitedBodySubscriber(int maxBytes) {
      this.maxBytes = maxBytes;
    }

    @Override
    public CompletionStage<byte[]> getBody() {
      return body;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
      this.subscription = subscription;
      subscription.request(1);
    }

    @Override
    public void onNext(List<ByteBuffer> buffers) {
      for (ByteBuffer buffer : buffers) {
        if (bytes.size() + buffer.remaining() > maxBytes) {
          subscription.cancel();
          body.completeExceptionally(new ResponseTooLargeException(maxBytes));
          return;
        }
        byte[] chunk = new byte[buffer.remaining()];
        buffer.get(chunk);
        bytes.writeBytes(chunk);
      }
      subscription.request(1);
    }

    @Override
    public void onError(Throwable failure) {
      body.completeExceptionally(failure);
    }

    @Override
    public void onComplete() {
      body.complete(bytes.toByteArray());
    }
  }
}
