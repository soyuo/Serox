package kr.soyuo.serox.browser.network;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import kr.soyuo.serox.browser.api.BrowserEngine;
import kr.soyuo.serox.browser.api.PageLoadRequest;
import kr.soyuo.serox.browser.api.PageSnapshot;
import kr.soyuo.serox.browser.dom.Document;
import kr.soyuo.serox.browser.html.HtmlDocumentParser;
import kr.soyuo.serox.core.error.ApplicationError;
import kr.soyuo.serox.core.error.ErrorCode;
import kr.soyuo.serox.core.model.UntrustedText;

/** Connects HTTP transport, response decoding, and HTML parsing as a browser engine. */
public final class HttpBrowserEngine implements BrowserEngine {
  private final HttpTransport transport;
  private final HttpResponseHandler responseHandler;
  private final HtmlDocumentParser documentParser;

  /** Creates a browser engine from injectable transport and parser boundaries. */
  public HttpBrowserEngine(
      HttpTransport transport,
      HttpResponseHandler responseHandler,
      HtmlDocumentParser documentParser) {
    this.transport = Objects.requireNonNull(transport, "transport");
    this.responseHandler = Objects.requireNonNull(responseHandler, "responseHandler");
    this.documentParser = Objects.requireNonNull(documentParser, "documentParser");
  }

  @Override
  public CompletionStage<PageSnapshot> load(PageLoadRequest request) {
    CompletableFuture<HttpResponseData> exchange =
        transport.dispatch(request).toCompletableFuture();
    CompletableFuture<PageSnapshot> result = new CompletableFuture<>();
    result.whenComplete(
        (snapshot, failure) -> {
          if (result.isCancelled()) {
            exchange.cancel(true);
          }
        });
    exchange.whenComplete(
        (response, failure) -> {
          if (failure != null) {
            result.completeExceptionally(mapFailure(unwrap(failure)));
            return;
          }
          try {
            result.complete(toPageSnapshot(responseHandler.handle(response)));
          } catch (RuntimeException exception) {
            result.completeExceptionally(mapFailure(exception));
          }
        });
    return result;
  }

  private PageSnapshot toPageSnapshot(DecodedHtmlResponse response) {
    Document document = documentParser.parse(response.uri(), response.html());
    String title =
        document.getElementsByTagName("title").stream()
            .findFirst()
            .map(node -> UntrustedText.plain(node.textContent(), 200))
            .orElse("");
    String visibleText = document.body().map(node -> node.textContent().strip()).orElse("");
    return new PageSnapshot(
        response.uri(), title, visibleText, Instant.now(), java.util.Optional.of(document));
  }

  private RuntimeException mapFailure(Throwable failure) {
    if (failure instanceof ResponseTooLargeException) {
      return new BrowserResponseException(
          new ApplicationError(
              ErrorCode.INVALID_RESPONSE,
              "The page is larger than the supported response limit.",
              false));
    }
    if (failure instanceof RuntimeException runtimeException) {
      return runtimeException;
    }
    return new CompletionException(failure);
  }

  private Throwable unwrap(Throwable failure) {
    return failure instanceof CompletionException && failure.getCause() != null
        ? failure.getCause()
        : failure;
  }
}
