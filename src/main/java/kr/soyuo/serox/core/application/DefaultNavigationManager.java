package kr.soyuo.serox.core.application;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import kr.soyuo.serox.browser.api.BrowserEngine;
import kr.soyuo.serox.browser.api.PageLoadRequest;
import kr.soyuo.serox.browser.api.PageSnapshot;
import kr.soyuo.serox.core.error.ApplicationError;
import kr.soyuo.serox.core.error.ErrorCode;
import kr.soyuo.serox.core.model.TabId;
import kr.soyuo.serox.core.model.TabSnapshot;

/** Per-tab navigation actions backed by the browser engine port. */
public final class DefaultNavigationManager implements NavigationManager {
  private final InMemoryTabManager tabs;
  private final BrowserEngine browserEngine;
  private final NavigationInputClassifier classifier;

  public DefaultNavigationManager(
      InMemoryTabManager tabs, BrowserEngine browserEngine, NavigationInputClassifier classifier) {
    this.tabs = tabs;
    this.browserEngine = browserEngine;
    this.classifier = classifier;
  }

  @Override
  public CompletionStage<TabSnapshot> navigate(TabId tabId, String addressOrQuery) {
    try {
      NavigationTarget target = classifier.classify(addressOrQuery);
      if (target instanceof SearchQuery) {
        return CompletableFuture.failedFuture(
            new NavigationInputException(
                new ApplicationError(
                    ErrorCode.SEARCH_NOT_CONFIGURED, "Search is not configured yet.", false)));
      }
      return load(tabId, ((DirectAddress) target).uri(), true);
    } catch (RuntimeException exception) {
      return CompletableFuture.failedFuture(exception);
    }
  }

  @Override
  public CompletionStage<TabSnapshot> goBack(TabId tabId) {
    try {
      var state = tabs.requireTab(tabId);
      URI uri = state.moveBack().orElseThrow(() -> new IllegalStateException("No previous page"));
      return load(tabId, uri, false);
    } catch (RuntimeException exception) {
      return CompletableFuture.failedFuture(exception);
    }
  }

  @Override
  public CompletionStage<TabSnapshot> goForward(TabId tabId) {
    try {
      var state = tabs.requireTab(tabId);
      URI uri = state.moveForward().orElseThrow(() -> new IllegalStateException("No next page"));
      return load(tabId, uri, false);
    } catch (RuntimeException exception) {
      return CompletableFuture.failedFuture(exception);
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
      return CompletableFuture.failedFuture(exception);
    }
  }

  @Override
  public CompletionStage<Void> cancelNavigation(TabId tabId) {
    try {
      tabs.requireTab(tabId).cancel();
      return CompletableFuture.completedFuture(null);
    } catch (RuntimeException exception) {
      return CompletableFuture.failedFuture(exception);
    }
  }

  private CompletionStage<TabSnapshot> load(TabId tabId, URI uri, boolean addHistory) {
    TabNavigationState state = tabs.requireTab(tabId);
    state.beginNavigation(uri, addHistory);
    long version = state.navigationVersion();
    CompletionStage<PageSnapshot> stage;
    try {
      stage = browserEngine.load(new PageLoadRequest(uri));
    } catch (RuntimeException exception) {
      state.fail(version);
      return CompletableFuture.failedFuture(exception);
    }
    CompletableFuture<PageSnapshot> future = stage.toCompletableFuture();
    state.setPendingLoad(future, version);
    return future.handle(
        (page, failure) -> {
          if (failure != null) {
            state.fail(version);
            throw new CompletionException(failure);
          }
          state.complete(page, version);
          return state.snapshot();
        });
  }
}
