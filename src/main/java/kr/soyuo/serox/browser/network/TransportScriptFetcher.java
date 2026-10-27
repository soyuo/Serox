package kr.soyuo.serox.browser.network;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import kr.soyuo.serox.browser.api.PageLoadRequest;
import kr.soyuo.serox.browser.script.ScriptFetcher;

/** Loads external scripts through the same guarded transport as pages, with a size and time cap. */
public final class TransportScriptFetcher implements ScriptFetcher {
  private static final int MAX_BYTES = 1_000_000;
  private static final long TIMEOUT_SECONDS = 5;

  private final HttpTransport transport;

  public TransportScriptFetcher(HttpTransport transport) {
    this.transport = Objects.requireNonNull(transport, "transport");
  }

  @Override
  public Optional<String> fetch(URI uri) {
    try {
      HttpResponseData response =
          transport
              .dispatch(new PageLoadRequest(uri))
              .toCompletableFuture()
              .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
      if (response.statusCode() != 200 || response.body().length > MAX_BYTES) {
        return Optional.empty();
      }
      return Optional.of(new String(response.body(), StandardCharsets.UTF_8));
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      return Optional.empty();
    } catch (java.util.concurrent.ExecutionException
        | java.util.concurrent.TimeoutException
        | RuntimeException failure) {
      return Optional.empty();
    }
  }
}
