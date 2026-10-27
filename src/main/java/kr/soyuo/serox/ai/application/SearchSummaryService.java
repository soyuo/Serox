package kr.soyuo.serox.ai.application;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
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
  private static final int RESULT_LIMIT = 5;

  private final Optional<SearchProvider> searchProvider;
  private final Optional<AiProvider> aiProvider;
  private final PageFetcher pageFetcher;
  private final ContextBuilder contextBuilder;
  private final Clock clock;

  public SearchSummaryService(
      Optional<SearchProvider> searchProvider,
      Optional<AiProvider> aiProvider,
      PageFetcher pageFetcher,
      ContextBuilder contextBuilder,
      Clock clock) {
    this.searchProvider = Objects.requireNonNull(searchProvider, "searchProvider");
    this.aiProvider = Objects.requireNonNull(aiProvider, "aiProvider");
    this.pageFetcher = Objects.requireNonNull(pageFetcher, "pageFetcher");
    this.contextBuilder = Objects.requireNonNull(contextBuilder, "contextBuilder");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /** Searches, reads the top pages and returns a cited answer. */
  public CompletionStage<SearchSummary> summarize(String query) {
    if (searchProvider.isEmpty()) {
      return failed("search_unconfigured", "Search is not configured.");
    }
    if (aiProvider.isEmpty()) {
      return failed("ai_unconfigured", "AI is not configured.");
    }
    SearchRequest request;
    try {
      request = new SearchRequest(query.strip(), RESULT_LIMIT);
    } catch (IllegalArgumentException exception) {
      return failed("invalid_query", "The query is not valid.");
    }
    return searchProvider
        .orElseThrow()
        .search(request)
        .thenCompose(response -> pageFetcher.fetch(response.results()))
        .thenCompose(
            pages -> {
              var aiRequest = contextBuilder.build(query, pages, clock.instant());
              if (aiRequest.evidence().isEmpty()) {
                return failed("no_evidence", "No readable sources were found.");
              }
              return collect(query, aiProvider.orElseThrow().analyze(aiRequest), aiRequest.evidence());
            });
  }

  private CompletionStage<SearchSummary> collect(
      String query, Flow.Publisher<AiEvent> publisher, List<EvidenceDocument> evidence) {
    CompletableFuture<SearchSummary> result = new CompletableFuture<>();
    StringBuilder text = new StringBuilder();
    publisher.subscribe(
        new Flow.Subscriber<AiEvent>() {
          @Override
          public void onSubscribe(Flow.Subscription subscription) {
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
    return result;
  }

  private static <T> CompletionStage<T> failed(String code, String message) {
    return CompletableFuture.failedFuture(new SummaryException(code, message));
  }
}
