package kr.soyuo.serox.ai.application;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import kr.soyuo.serox.ai.api.AiCompleted;
import kr.soyuo.serox.ai.api.AiEvent;
import kr.soyuo.serox.ai.api.AiFailed;
import kr.soyuo.serox.ai.api.AiProvider;
import kr.soyuo.serox.ai.api.AiRequest;
import kr.soyuo.serox.ai.api.AiTextDelta;
import kr.soyuo.serox.ai.api.EvidenceDocument;
import kr.soyuo.serox.ai.citation.CitationManager;
import kr.soyuo.serox.ai.context.ContextBuilder;
import kr.soyuo.serox.core.event.AiAnswerChanged;
import kr.soyuo.serox.core.event.AiAnswerChanged.Phase;
import kr.soyuo.serox.core.event.BrowserEventBus;
import kr.soyuo.serox.core.event.EventSubscription;
import kr.soyuo.serox.core.event.SearchCancelled;
import kr.soyuo.serox.core.event.SearchCompleted;
import kr.soyuo.serox.core.event.SearchStarted;
import kr.soyuo.serox.core.model.TabId;
import kr.soyuo.serox.search.fetch.PageFetcher;

/** Turns completed searches into streamed, cited AI answers without touching search state. */
public final class AiSearchService implements AutoCloseable {
  static final String UNAVAILABLE_MESSAGE =
      "AI 요약을 사용하려면 선택한 제공자의 API 키를 설정하세요. AI 제공자 설정에서 확인할 수 있어요.";
  static final String NO_EVIDENCE_MESSAGE = "근거로 사용할 페이지를 읽지 못해 AI 요약을 만들 수 없어요.";
  private static final Logger LOGGER = Logger.getLogger(AiSearchService.class.getName());

  private volatile Optional<AiProvider> provider;
  private final PageFetcher pageFetcher;
  private final ContextBuilder contextBuilder;
  private final BrowserEventBus eventBus;
  private final Clock clock;
  private final Map<TabId, Active> active = new HashMap<>();
  private final List<EventSubscription> subscriptions;
  private volatile Supplier<String> answerLanguage = () -> "";
  private volatile Supplier<kr.soyuo.serox.search.fetch.FetchLimits> fetchLimits =
      kr.soyuo.serox.search.fetch.FetchLimits::defaults;
  private volatile Supplier<String> instructions = () -> "";
  private boolean closed;

  /** Subscribes to search events; the provider is absent when no API key is configured. */
  public AiSearchService(
      Optional<AiProvider> provider,
      PageFetcher pageFetcher,
      ContextBuilder contextBuilder,
      BrowserEventBus eventBus,
      Clock clock) {
    this.provider = Objects.requireNonNull(provider, "provider");
    this.pageFetcher = Objects.requireNonNull(pageFetcher, "pageFetcher");
    this.contextBuilder = Objects.requireNonNull(contextBuilder, "contextBuilder");
    this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
    this.clock = Objects.requireNonNull(clock, "clock");
    subscriptions =
        List.of(
            eventBus.subscribe(SearchCompleted.class, this::onSearchCompleted),
            eventBus.subscribe(SearchStarted.class, event -> stop(event.tabId(), false)),
            eventBus.subscribe(SearchCancelled.class, event -> stop(event.tabId(), false)));
  }

  /**
   * Chooses the language of later answers by its English name, read for each new answer; blank
   * means the language of the question.
   */
  public void setAnswerLanguage(Supplier<String> language) {
    this.answerLanguage = Objects.requireNonNull(language, "language");
  }

  public void setFetchLimits(Supplier<kr.soyuo.serox.search.fetch.FetchLimits> limits) {
    fetchLimits = Objects.requireNonNull(limits, "limits");
  }

  public void setInstructions(Supplier<String> value) {
    instructions = Objects.requireNonNull(value, "value");
  }

  /** Applies a connection choice to later searches and stops answers using the old credentials. */
  public synchronized void setProvider(Optional<AiProvider> provider) {
    this.provider = Objects.requireNonNull(provider, "provider");
    for (TabId tabId : List.copyOf(active.keySet())) {
      stop(tabId, true);
    }
  }

  /** Cancels the active AI answer in one tab and publishes the cancellation. */
  public boolean cancel(TabId tabId) {
    return stop(Objects.requireNonNull(tabId, "tabId"), true);
  }

  private synchronized void onSearchCompleted(SearchCompleted event) {
    if (event.response().results().isEmpty()) {
      return;
    }
    Optional<AiProvider> selected = provider;
    if (selected.isEmpty()) {
      publish(event, Phase.UNAVAILABLE, "", Optional.empty(), List.of(), UNAVAILABLE_MESSAGE);
      return;
    }
    Active run = new Active(event.requestId(), selected.orElseThrow());
    synchronized (this) {
      if (closed) {
        return;
      }
      stopLocked(event.tabId(), false);
      active.put(event.tabId(), run);
    }
    publish(event, Phase.PREPARING, "", Optional.empty(), List.of(), "");
    var fetching =
        pageFetcher.fetch(event.response().results(), fetchLimits.get()).toCompletableFuture();
    run.fetch = fetching;
    fetching.whenComplete(
        (pages, failure) -> {
          if (failure != null || !isCurrent(event.tabId(), run)) {
            return;
          }
          try {
            AiRequest request =
                contextBuilder
                    .build(event.query(), pages, clock.instant())
                    .withAnswerLanguage(answerLanguage.get())
                    .withInstructions(instructions.get());
            if (request.evidence().isEmpty()) {
              fail(event, run, NO_EVIDENCE_MESSAGE);
            } else {
              stream(event, run, request);
            }
          } catch (RuntimeException exception) {
            LOGGER.log(Level.WARNING, "AI answer could not be prepared.");
            fail(event, run, "AI 요약을 준비하지 못했어요.");
          }
        });
  }

  private void stream(SearchCompleted event, Active run, AiRequest request) {
    List<EvidenceDocument> evidence = request.evidence();
    StringBuilder text = new StringBuilder();
    run.provider
        .analyze(request)
        .subscribe(
            new Flow.Subscriber<AiEvent>() {
              @Override
              public void onSubscribe(Flow.Subscription subscription) {
                run.subscription = subscription;
                if (!isCurrent(event.tabId(), run)) {
                  subscription.cancel();
                  return;
                }
                subscription.request(Long.MAX_VALUE);
              }

              @Override
              public void onNext(AiEvent item) {
                if (!isCurrent(event.tabId(), run)) {
                  return;
                }
                switch (item) {
                  case AiTextDelta delta -> {
                    text.append(delta.text());
                    publish(
                        event, Phase.STREAMING, text.toString(), Optional.empty(), evidence, "");
                  }
                  case AiCompleted ignored -> {
                    var answer = CitationManager.resolve(text.toString(), evidence);
                    finish(event, run);
                    publish(
                        event, Phase.COMPLETED, answer.text(), Optional.of(answer), evidence, "");
                  }
                  case AiFailed failed -> fail(event, run, failed.safeMessage());
                }
              }

              @Override
              public void onError(Throwable throwable) {
                fail(event, run, "AI 요약을 불러오지 못했어요.");
              }

              @Override
              public void onComplete() {}
            });
  }

  private void fail(SearchCompleted event, Active run, String message) {
    if (isCurrent(event.tabId(), run)) {
      finish(event, run);
      publish(event, Phase.FAILED, "", Optional.empty(), List.of(), message);
    }
  }

  private synchronized void finish(SearchCompleted event, Active run) {
    if (active.get(event.tabId()) == run) {
      active.remove(event.tabId());
    }
  }

  private synchronized boolean isCurrent(TabId tabId, Active run) {
    return !closed && active.get(tabId) == run;
  }

  private boolean stop(TabId tabId, boolean announce) {
    Active run;
    synchronized (this) {
      run = stopLocked(tabId, announce);
    }
    if (run != null && announce) {
      eventBus.publish(
          new AiAnswerChanged(
              clock.instant(),
              tabId,
              run.requestId,
              Phase.CANCELLED,
              "",
              Optional.empty(),
              List.of(),
              ""));
    }
    return run != null;
  }

  private Active stopLocked(TabId tabId, boolean announce) {
    Active run = active.remove(tabId);
    if (run != null) {
      if (run.fetch != null) {
        run.fetch.cancel(true);
      }
      if (run.subscription != null) {
        run.subscription.cancel();
      }
    }
    return run;
  }

  private void publish(
      SearchCompleted event,
      Phase phase,
      String text,
      Optional<kr.soyuo.serox.ai.citation.CitedAnswer> answer,
      List<EvidenceDocument> evidence,
      String message) {
    eventBus.publish(
        new AiAnswerChanged(
            clock.instant(),
            event.tabId(),
            event.requestId(),
            phase,
            text,
            answer,
            evidence,
            message));
  }

  @Override
  public void close() {
    List<Active> runs;
    synchronized (this) {
      if (closed) {
        return;
      }
      closed = true;
      runs = List.copyOf(active.values());
      active.clear();
    }
    subscriptions.forEach(EventSubscription::close);
    for (Active run : runs) {
      if (run.fetch != null) {
        run.fetch.cancel(true);
      }
      if (run.subscription != null) {
        run.subscription.cancel();
      }
    }
  }

  private static final class Active {
    final long requestId;
    final AiProvider provider;
    volatile CompletableFuture<?> fetch;
    volatile Flow.Subscription subscription;

    Active(long requestId, AiProvider provider) {
      this.requestId = requestId;
      this.provider = provider;
    }
  }
}
