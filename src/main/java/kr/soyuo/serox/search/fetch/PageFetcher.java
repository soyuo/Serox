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
  private static final java.util.concurrent.ScheduledThreadPoolExecutor DEADLINES = deadlines();

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
    CompletableFuture<List<FetchedPage>> result = new CompletableFuture<>();
    var deadline =
        DEADLINES.schedule(
            () -> result.complete(completed(pending)),
            limits.totalTimeout().toMillis(),
            TimeUnit.MILLISECONDS);
    all.whenComplete((ignored, failure) -> result.complete(completed(pending)));
    result.whenComplete(
        (pages, failure) -> {
          deadline.cancel(false);
          pending.forEach(
              future -> {
                if (!future.isDone()) future.cancel(true);
              });
        });
    return result;
  }

  /** One immutable preference snapshot applies to the whole evidence-gathering run. */
  public CompletionStage<List<FetchedPage>> fetch(
      List<SearchResult> results, FetchLimits selected) {
    return new PageFetcher(transport, responseHandler, parser, selected).fetch(results);
  }

  private CompletableFuture<Optional<FetchedPage>> fetchOne(SearchResult result) {
    try {
      var request = transport.dispatch(new PageLoadRequest(result.uri())).toCompletableFuture();
      CompletableFuture<Optional<FetchedPage>> page = new CompletableFuture<>();
      var deadline =
          DEADLINES.schedule(
              () -> page.complete(Optional.empty()),
              limits.perPageTimeout().toMillis(),
              TimeUnit.MILLISECONDS);
      page.whenComplete(
          (value, failure) -> {
            deadline.cancel(false);
            if (!request.isDone()) request.cancel(true);
          });
      request.whenComplete(
          (response, failure) -> {
            if (page.isDone()) return;
            try {
              if (failure != null) {
                page.complete(Optional.empty());
                return;
              }
              var decoded = responseHandler.handle(response);
              var document = parser.parse(decoded.uri(), decoded.html());
              var content = extractor.extract(document);
              page.complete(
                  Optional.of(
                      new FetchedPage(result, decoded.uri(), content.title(), content.text())));
            } catch (RuntimeException unreadable) {
              LOGGER.log(Level.FINE, "Skipping a result page that could not be read.");
              page.complete(Optional.empty());
            }
          });
      return page;
    } catch (RuntimeException exception) {
      return CompletableFuture.completedFuture(Optional.empty());
    }
  }

  private static List<FetchedPage> completed(
      List<CompletableFuture<Optional<FetchedPage>>> pending) {
    List<FetchedPage> pages = new ArrayList<>();
    for (var future : pending) {
      try {
        future.getNow(Optional.empty()).ifPresent(pages::add);
      } catch (java.util.concurrent.CompletionException
          | java.util.concurrent.CancellationException ignored) {
        // A timed-out or cancelled source contributes no evidence.
      }
    }
    return List.copyOf(pages);
  }

  private static java.util.concurrent.ScheduledThreadPoolExecutor deadlines() {
    var executor =
        new java.util.concurrent.ScheduledThreadPoolExecutor(
            1,
            runnable -> {
              Thread thread = new Thread(runnable, "evidence-deadlines");
              thread.setDaemon(true);
              return thread;
            });
    executor.setRemoveOnCancelPolicy(true);
    return executor;
  }
}
