package kr.soyuo.serox.ai.application;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import kr.soyuo.serox.ai.api.AiCompleted;
import kr.soyuo.serox.ai.api.AiEvent;
import kr.soyuo.serox.ai.api.AiFailed;
import kr.soyuo.serox.ai.api.AiProvider;
import kr.soyuo.serox.ai.api.AiTextDelta;
import kr.soyuo.serox.ai.api.EvidenceDocument;
import kr.soyuo.serox.ai.citation.CitationManager;
import kr.soyuo.serox.ai.context.ContextBuilder;
import kr.soyuo.serox.search.api.SearchProvider;
import kr.soyuo.serox.search.api.SearchRequest;
import kr.soyuo.serox.search.fetch.PageFetcher;

/** Runs search, evidence fetching and AI summarisation as one call, without any UI. */
public final class SearchSummaryService {
  public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);
  public static final int MAX_RESULT_LIMIT = 10;
  private static final int RESULT_LIMIT = 5;

  private final Optional<SearchProvider> searchProvider;
  private final Optional<AiProvider> aiProvider;
  private final PageFetcher pageFetcher;
  private final ContextBuilder contextBuilder;
  private final Clock clock;
  private final Duration timeout;

  public SearchSummaryService(
      Optional<SearchProvider> searchProvider,
      Optional<AiProvider> aiProvider,
      PageFetcher pageFetcher,
      ContextBuilder contextBuilder,
      Clock clock) {
    this(searchProvider, aiProvider, pageFetcher, contextBuilder, clock, DEFAULT_TIMEOUT);
  }

  /** The whole run, including search, page reads and the AI answer, ends after {@code timeout}. */
  public SearchSummaryService(
      Optional<SearchProvider> searchProvider,
      Optional<AiProvider> aiProvider,
      PageFetcher pageFetcher,
      ContextBuilder contextBuilder,
      Clock clock,
      Duration timeout) {
    this.searchProvider = Objects.requireNonNull(searchProvider, "searchProvider");
    this.aiProvider = Objects.requireNonNull(aiProvider, "aiProvider");
    this.pageFetcher = Objects.requireNonNull(pageFetcher, "pageFetcher");
    this.contextBuilder = Objects.requireNonNull(contextBuilder, "contextBuilder");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.timeout = Objects.requireNonNull(timeout, "timeout");
  }

  /**
   * Searches, reads the top pages and returns a cited answer. Cancelling the returned future stops
   * the search, page reads and the AI stream; a run that exceeds the timeout fails with code
   * {@code timeout}.
   */
  public CompletableFuture<SearchSummary> summarize(String query) {
    return summarize(query, RESULT_LIMIT);
  }

  /** Like {@link #summarize(String)}, reading the top {@code topN} results (1 to 10). */
  public CompletableFuture<SearchSummary> summarize(String query, int topN) {
    if (topN < 1 || topN > MAX_RESULT_LIMIT) {
      return failed("invalid_query", "top_n must be between 1 and " + MAX_RESULT_LIMIT + ".");
    }
    if (searchProvider.isEmpty()) {
      return failed("search_unconfigured", "Search is not configured.");
    }
    if (aiProvider.isEmpty()) {
      return failed("ai_unconfigured", "AI is not configured.");
    }
    SearchRequest request;
    try {
      request = new SearchRequest(query.strip(), topN);
    } catch (IllegalArgumentException exception) {
      return failed("invalid_query", "The query is not valid.");
    }
    Run run = new Run();
    CompletableFuture<SearchSummary> result = new CompletableFuture<>();
    result.whenComplete((summary, failure) -> run.stop());
    CompletableFuture.delayedExecutor(timeout.toMillis(), TimeUnit.MILLISECONDS)
        .execute(
            () ->
                result.completeExceptionally(
                    new SummaryException("timeout", "The search took too long and was stopped.")));
    run.track(searchProvider.orElseThrow().search(request).toCompletableFuture())
        .thenCompose(response -> run.track(pageFetcher.fetch(response.results()).toCompletableFuture()))
        .whenComplete(
            (pages, failure) -> {
              if (failure != null) {
                result.completeExceptionally(mapFailure(failure));
                return;
              }
              try {
                var aiRequest = contextBuilder.build(query, pages, clock.instant());
                if (aiRequest.evidence().isEmpty()) {
                  result.completeExceptionally(
                      new SummaryException("no_evidence", "No readable sources were found."));
                  return;
                }
                collect(
                    query,
                    run,
                    aiProvider.orElseThrow().analyze(aiRequest),
                    aiRequest.evidence(),
                    result);
              } catch (RuntimeException exception) {
                result.completeExceptionally(mapFailure(exception));
              }
            });
    return result;
  }

  private static Throwable mapFailure(Throwable failure) {
    Throwable cause =
        failure instanceof CompletionException && failure.getCause() != null
            ? failure.getCause()
            : failure;
    if (cause instanceof SummaryException || cause instanceof CancellationException) {
      return cause;
    }
    return new SummaryException("search_failed", "The search could not be completed.");
  }

  private void collect(
      String query,
      Run run,
      Flow.Publisher<AiEvent> publisher,
      List<EvidenceDocument> evidence,
      CompletableFuture<SearchSummary> result) {
    StringBuilder text = new StringBuilder();
    publisher.subscribe(
        new Flow.Subscriber<AiEvent>() {
          @Override
          public void onSubscribe(Flow.Subscription subscription) {
            run.subscription.set(subscription);
            if (result.isDone()) {
              subscription.cancel();
              return;
            }
            subscription.request(Long.MAX_VALUE);
          }

          @Override
          public void onNext(AiEvent item) {
            switch (item) {
              case AiTextDelta delta -> text.append(delta.text());
              case AiCompleted ignored -> {
                var answer = CitationManager.resolve(text.toString(), evidence);
                var sources =
                    answer.citations().stream()
                        .map(
                            citation ->
                                new SearchSummary.Source(
                                    citation.sourceId(),
                                    citation.title(),
                                    citation.uri(),
                                    citation.referencedText(),
                                    citation.retrievedAt()))
                        .toList();
                result.complete(new SearchSummary(query, answer.text(), sources));
              }
              case AiFailed failure ->
                  result.completeExceptionally(
                      new SummaryException("ai_failed", failure.safeMessage()));
            }
          }

          @Override
          public void onError(Throwable throwable) {
            result.completeExceptionally(
                new SummaryException("ai_failed", "The AI service failed."));
          }

          @Override
          public void onComplete() {
            result.completeExceptionally(
                new SummaryException("ai_failed", "The AI response ended unexpectedly."));
          }
        });
  }

  private static CompletableFuture<SearchSummary> failed(String code, String message) {
    return CompletableFuture.failedFuture(new SummaryException(code, message));
  }

  /** Handles to the work in flight so that cancellation and timeouts can stop it. */
  private static final class Run {
    private final AtomicReference<CompletableFuture<?>> current = new AtomicReference<>();
    private final AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();

    <T> CompletableFuture<T> track(CompletableFuture<T> future) {
      current.set(future);
      return future;
    }

    void stop() {
      CompletableFuture<?> pending = current.get();
      if (pending != null) {
        pending.cancel(true);
      }
      Flow.Subscription active = subscription.get();
      if (active != null) {
        active.cancel();
      }
    }
  }
}
