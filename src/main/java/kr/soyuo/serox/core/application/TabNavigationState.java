package kr.soyuo.serox.core.application;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import kr.soyuo.serox.browser.api.PageSnapshot;
import kr.soyuo.serox.core.model.NavigationStatus;
import kr.soyuo.serox.core.model.TabId;
import kr.soyuo.serox.core.model.TabSnapshot;

/** Mutable application-owned navigation state for a single tab. */
final class TabNavigationState {
  private final TabId id;
  private final List<URI> history = new ArrayList<>();
  private int historyIndex = -1;
  private String title = "New tab";
  private NavigationStatus status = NavigationStatus.IDLE;
  private CompletableFuture<PageSnapshot> pendingLoad;
  private long navigationVersion;

  TabNavigationState(TabId id) {
    this.id = id;
  }

  synchronized TabSnapshot snapshot() {
    return new TabSnapshot(
        id,
        currentUri(),
        title,
        status,
        historyIndex > 0,
        historyIndex >= 0 && historyIndex < history.size() - 1);
  }

  synchronized Optional<URI> currentUri() {
    return historyIndex < 0 ? Optional.empty() : Optional.of(history.get(historyIndex));
  }

  synchronized URI beginNavigation(URI uri, boolean addHistory) {
    navigationVersion++;
    if (pendingLoad != null) {
      pendingLoad.cancel(true);
      pendingLoad = null;
    }
    if (addHistory) {
      while (history.size() > historyIndex + 1) {
        history.remove(history.size() - 1);
      }
      history.add(uri);
      historyIndex = history.size() - 1;
    }
    status = NavigationStatus.LOADING;
    return uri;
  }

  synchronized long navigationVersion() {
    return navigationVersion;
  }

  synchronized void setPendingLoad(CompletableFuture<PageSnapshot> load, long version) {
    if (navigationVersion == version) {
      pendingLoad = load;
    } else {
      load.cancel(true);
    }
  }

  synchronized boolean complete(PageSnapshot page, long version) {
    if (navigationVersion != version) {
      return false;
    }
    title = page.title();
    status = NavigationStatus.SUCCEEDED;
    pendingLoad = null;
    return true;
  }

  synchronized boolean fail(long version) {
    if (navigationVersion != version) {
      return false;
    }
    status = NavigationStatus.FAILED;
    pendingLoad = null;
    return true;
  }

  synchronized boolean cancel() {
    if (status != NavigationStatus.LOADING) {
      return false;
    }
    navigationVersion++;
    if (pendingLoad != null) {
      pendingLoad.cancel(true);
      pendingLoad = null;
    }
    status = NavigationStatus.CANCELLED;
    return true;
  }

  synchronized Optional<URI> moveBack() {
    if (historyIndex <= 0) {
      return Optional.empty();
    }
    historyIndex--;
    return currentUri();
  }

  synchronized Optional<URI> moveForward() {
    if (historyIndex >= history.size() - 1) {
      return Optional.empty();
    }
    historyIndex++;
    return currentUri();
  }
}
