package kr.soyuo.serox.browser.network;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import kr.soyuo.serox.browser.api.PageLoadRequest;

/**
 * Transport for unattended fetches: every address, including each redirect target, must pass the
 * {@link PublicAddressPolicy}. The delegate must not follow redirects itself.
 */
public final class GuardedTransport implements HttpTransport {
  static final int MAX_REDIRECTS = 5;

  private final HttpTransport delegate;
  private final PublicAddressPolicy policy;

  public GuardedTransport(HttpTransport delegate, PublicAddressPolicy policy) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
    this.policy = Objects.requireNonNull(policy, "policy");
  }

  @Override
  public CompletionStage<HttpResponseData> dispatch(PageLoadRequest request) {
    CompletableFuture<HttpResponseData> result = new CompletableFuture<>();
    Thread.ofVirtual().start(() -> follow(request.uri(), 0, result));
    return result;
  }

  private void follow(URI uri, int hops, CompletableFuture<HttpResponseData> result) {
    if (result.isDone()) {
      return;
    }
    if (!policy.isPublic(uri)) {
      result.completeExceptionally(new SecurityException("Address is not on the public web."));
      return;
    }
    CompletableFuture<HttpResponseData> exchange =
        delegate.dispatch(new PageLoadRequest(uri)).toCompletableFuture();
    result.whenComplete(
        (response, failure) -> {
          if (result.isCancelled()) {
            exchange.cancel(true);
          }
        });
    exchange.whenComplete(
        (response, failure) -> {
          if (failure != null) {
            result.completeExceptionally(failure);
            return;
          }
          Optional<URI> target = redirectTarget(uri, response);
          if (target.isEmpty()) {
            result.complete(response);
          } else if (hops >= MAX_REDIRECTS) {
            result.completeExceptionally(new SecurityException("Too many redirects."));
          } else if (uri.getScheme().equalsIgnoreCase("https")
              && !target.orElseThrow().getScheme().equalsIgnoreCase("https")) {
            result.completeExceptionally(new SecurityException("Redirect downgrades to HTTP."));
          } else {
            Thread.ofVirtual().start(() -> follow(target.orElseThrow(), hops + 1, result));
          }
        });
  }

  private static Optional<URI> redirectTarget(URI from, HttpResponseData response) {
    int status = response.statusCode();
    if (status != 301 && status != 302 && status != 303 && status != 307 && status != 308) {
      return Optional.empty();
    }
    return response
        .headers()
        .firstValue("Location")
        .flatMap(
            location -> {
              try {
                return Optional.of(from.resolve(location));
              } catch (IllegalArgumentException exception) {
                return Optional.empty();
              }
            });
  }
}
