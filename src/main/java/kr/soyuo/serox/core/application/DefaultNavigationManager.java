package kr.soyuo.serox.core.application;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import kr.soyuo.serox.browser.api.BrowserEngine;
import kr.soyuo.serox.browser.api.PageLoadRequest;
import kr.soyuo.serox.browser.api.PageSnapshot;
import kr.soyuo.serox.core.error.ApplicationError;
import kr.soyuo.serox.core.error.ErrorCode;
import kr.soyuo.serox.core.event.BrowserEventBus;
import kr.soyuo.serox.core.event.OperationFailed;
import kr.soyuo.serox.core.event.TabStateChanged;
import kr.soyuo.serox.core.model.TabId;
import kr.soyuo.serox.core.model.TabSnapshot;

/** Per-tab navigation actions backed by the browser engine port. */
public final class DefaultNavigationManager implements NavigationManager {
  private final InMemoryTabManager tabs;
  private final BrowserEngine browserEngine;
  private final NavigationInputClassifier classifier;
  private final BrowserEventBus eventBus;
  private final Executor engineExecutor;

  /** Creates a navigation manager that reports state through the application event bus. */
  public DefaultNavigationManager(
      InMemoryTabManager tabs,
      BrowserEngine browserEngine,
      NavigationInputClassifier classifier,
      BrowserEventBus eventBus,
      Executor engineExecutor) {
    this.tabs = Objects.requireNonNull(tabs, "tabs");
    this.browserEngine = Objects.requireNonNull(browserEngine, "browserEngine");
    this.classifier = Objects.requireNonNull(classifier, "classifier");
    this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
    this.engineExecutor = Objects.requireNonNull(engineExecutor, "engineExecutor");
  }

  @Override
  public CompletionStage<TabSnapshot> navigate(TabId tabId, String addressOrQuery) {
    try {
      NavigationTarget target = classifier.classify(addressOrQuery);
      if (target instanceof SearchQuery) {
        return fail(
            new NavigationInputException(
                new ApplicationError(
                    ErrorCode.SEARCH_NOT_CONFIGURED, "Search is not configured yet.", false)));
      }
      return load(tabId, ((DirectAddress) target).uri(), true);
    } catch (RuntimeException exception) {
      return fail(exception);
    }
  }

  @Override
  public CompletionStage<TabSnapshot> goBack(TabId tabId) {
    try {
      var state = tabs.requireTab(tabId);
      URI uri = state.moveBack().orElseThrow(() -> new IllegalStateException("No previous page"));
      return load(tabId, uri, false);
    } catch (RuntimeException exception) {
      return fail(exception);
    }
  }

  @Override
  public CompletionStage<TabSnapshot> goForward(TabId tabId) {
    try {
      var state = tabs.requireTab(tabId);
      URI uri = state.moveForward().orElseThrow(() -> new IllegalStateException("No next page"));
      return load(tabId, uri, false);
    } catch (RuntimeException exception) {
      return fail(exception);
    }
  }

  @Override
  public CompletionStage<TabSnapshot> reload(TabId tabId) {
    try {
      URI uri =
          tabs.requireTab(tabId)
              .currentUri()
              .orElseThrow(() -> new IllegalStateException("No page to reload"));
      return load(tabId, uri, false);
    } catch (RuntimeException exception) {
      return fail(exception);
    }
  }

  @Override
  public CompletionStage<Void> cancelNavigation(TabId tabId) {
    try {
      TabNavigationState state = tabs.requireTab(tabId);
      if (state.cancel()) {
        publishState(state);
      }
      return CompletableFuture.completedFuture(null);
    } catch (RuntimeException exception) {
      return CompletableFuture.failedFuture(exception);
    }
  }

  private CompletionStage<TabSnapshot> load(TabId tabId, URI uri, boolean addHistory) {
    TabNavigationState state = tabs.requireTab(tabId);
    state.beginNavigation(uri, addHistory);
    long version = state.navigationVersion();
    publishState(state);

    CompletableFuture<PageSnapshot> future = new CompletableFuture<>();
    state.setPendingLoad(future, version);
    try {
      engineExecutor.execute(
          () -> {
            if (future.isCancelled()) {
              return;
            }
            CompletionStage<PageSnapshot> stage;
            CompletableFuture<PageSnapshot> engineFuture;
            try {
              stage =
                  Objects.requireNonNull(
                      browserEngine.load(new PageLoadRequest(uri)), "browserEngine.load result");
              engineFuture = stage.toCompletableFuture();
            } catch (RuntimeException exception) {
              future.completeExceptionally(exception);
              return;
            }
            future.whenComplete(
                (page, failure) -> {
                  if (future.isCancelled()) {
                    engineFuture.cancel(true);
                  }
                });
            engineFuture.whenComplete(
                (page, failure) -> {
                  if (failure == null) {
                    future.complete(page);
                  } else {
                    future.completeExceptionally(failure);
                  }
                });
          });
    } catch (RejectedExecutionException exception) {
      future.completeExceptionally(exception);
    }
    return future.handle(
        (page, failure) -> {
          if (failure != null) {
            if (state.fail(version)) {
              publishState(state);
              publishFailure(failure);
            }
            throw new CompletionException(failure);
          }
          if (state.complete(page, version)) {
            publishState(state);
          }
          return state.snapshot();
        });
  }

  private CompletionStage<TabSnapshot> fail(RuntimeException exception) {
    if (exception instanceof NavigationInputException inputException) {
      eventBus.publish(new OperationFailed(Instant.now(), inputException.error()));
    }
    return CompletableFuture.failedFuture(exception);
  }

  private void publishFailure(Throwable failure) {
    Throwable cause = failure instanceof CompletionException ? failure.getCause() : failure;
    ApplicationError error =
        cause instanceof NavigationInputException inputException
            ? inputException.error()
            : new ApplicationError(
                ErrorCode.NETWORK_FAILURE, "The page could not be loaded.", true);
    eventBus.publish(new OperationFailed(Instant.now(), error));
  }

  private void publishState(TabNavigationState state) {
    eventBus.publish(new TabStateChanged(Instant.now(), state.snapshot()));
  }
}
