package kr.soyuo.serox.search.fetch;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import kr.soyuo.serox.browser.api.PageLoadRequest;
import kr.soyuo.serox.browser.html.HtmlDocumentParser;
import kr.soyuo.serox.browser.network.HttpResponseHandler;
import kr.soyuo.serox.browser.network.HttpTransport;
import kr.soyuo.serox.search.api.SearchResult;
import kr.soyuo.serox.search.extract.ContentExtractor;

/** Fetches the top search results in parallel under page-count, size and time limits. */
public final class PageFetcher {
  private static final Logger LOGGER = Logger.getLogger(PageFetcher.class.getName());

  private final HttpTransport transport;
  private final HttpResponseHandler responseHandler;
  private final HtmlDocumentParser parser;
  private final ContentExtractor extractor;
  private final FetchLimits limits;

  /** The transport enforces the response byte limit; this class enforces count and time. */
  public PageFetcher(
      HttpTransport transport,
      HttpResponseHandler responseHandler,
      HtmlDocumentParser parser,
      FetchLimits limits) {
    this.transport = Objects.requireNonNull(transport, "transport");
    this.responseHandler = Objects.requireNonNull(responseHandler, "responseHandler");
    this.parser = Objects.requireNonNull(parser, "parser");
    this.limits = Objects.requireNonNull(limits, "limits");
    this.extractor = new ContentExtractor(limits.maxTextCharsPerPage());
  }

  /**
   * Fetches at most {@code maxPages} results; pages that fail or time out are skipped. The result
   * is in the input order and never fails because of an individual page.
   */
  public CompletionStage<List<FetchedPage>> fetch(List<SearchResult> results) {
    List<SearchResult> selected = results.stream().limit(limits.maxPages()).toList();
    List<CompletableFuture<Optional<FetchedPage>>> pending = new ArrayList<>();
    for (SearchResult result : selected) {
      pending.add(fetchOne(result));
    }
    CompletableFuture<Void> all =
        CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new));
    return all.orTimeout(limits.totalTimeout().toMillis(), TimeUnit.MILLISECONDS)
        .handle(
            (ignored, failure) -> {
              if (failure != null) {
                pending.forEach(future -> future.cancel(true));
              }
              return pending.stream()
                  .filter(future -> future.isDone() && !future.isCompletedExceptionally())
                  .map(CompletableFuture::join)
                  .flatMap(Optional::stream)
                  .toList();
            });
  }

  private CompletableFuture<Optional<FetchedPage>> fetchOne(SearchResult result) {
    try {
      return transport
          .dispatch(new PageLoadRequest(result.uri()))
          .toCompletableFuture()
          .orTimeout(limits.perPageTimeout().toMillis(), TimeUnit.MILLISECONDS)
          .thenApply(
              response -> {
                var decoded = responseHandler.handle(response);
                var document = parser.parse(decoded.uri(), decoded.html());
                var content = extractor.extract(document);
                return Optional.of(
                    new FetchedPage(result, decoded.uri(), content.title(), content.text()));
              })
          .exceptionally(
              failure -> {
                LOGGER.log(Level.FINE, "Skipping a result page that could not be read.");
                return Optional.empty();
              });
    } catch (RuntimeException exception) {
      return CompletableFuture.completedFuture(Optional.empty());
    }
  }
}
