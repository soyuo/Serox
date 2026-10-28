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
import kr.soyuo.serox.browser.script.ScriptRunner;
import kr.soyuo.serox.core.error.ApplicationError;
import kr.soyuo.serox.core.error.ErrorCode;
import kr.soyuo.serox.core.model.UntrustedText;

/** Connects HTTP transport, response decoding, and HTML parsing as a browser engine. */
public final class HttpBrowserEngine implements BrowserEngine {
  private final HttpTransport transport;
  private final HttpResponseHandler responseHandler;
  private final HtmlDocumentParser documentParser;
  private final ScriptRunner scriptRunner;
  private final StylesheetInliner stylesheets;

  /** Creates an engine that runs no page scripts. */
  public HttpBrowserEngine(
      HttpTransport transport,
      HttpResponseHandler responseHandler,
      HtmlDocumentParser documentParser) {
    this(transport, responseHandler, documentParser, ScriptRunner.NONE);
  }

  /** Creates a browser engine from injectable transport, parser and script boundaries. */
  public HttpBrowserEngine(
      HttpTransport transport,
      HttpResponseHandler responseHandler,
      HtmlDocumentParser documentParser,
      ScriptRunner scriptRunner) {
    this(
        transport,
        responseHandler,
        documentParser,
        scriptRunner,
        new StylesheetInliner(kr.soyuo.serox.browser.script.ScriptFetcher.NONE));
  }

  /** Creates a browser engine that also loads the page's outside style sheets. */
  public HttpBrowserEngine(
      HttpTransport transport,
      HttpResponseHandler responseHandler,
      HtmlDocumentParser documentParser,
      ScriptRunner scriptRunner,
      StylesheetInliner stylesheets) {
    this.transport = Objects.requireNonNull(transport, "transport");
    this.responseHandler = Objects.requireNonNull(responseHandler, "responseHandler");
    this.documentParser = Objects.requireNonNull(documentParser, "documentParser");
    this.scriptRunner = Objects.requireNonNull(scriptRunner, "scriptRunner");
    this.stylesheets = Objects.requireNonNull(stylesheets, "stylesheets");
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
    try {
      stylesheets.inline(document);
      scriptRunner.run(document);
    } catch (RuntimeException subresourceFailure) {
      // A style sheet or script problem must never stop the page from showing.
    }
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
