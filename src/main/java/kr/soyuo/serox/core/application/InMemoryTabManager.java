package kr.soyuo.serox.core.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import kr.soyuo.serox.core.model.TabId;
import kr.soyuo.serox.core.model.TabSnapshot;

/** In-memory tab manager with stable ordering and one active tab. */
public final class InMemoryTabManager implements TabManager {
  private final LinkedHashMap<TabId, TabNavigationState> tabs = new LinkedHashMap<>();
  private TabId activeTabId;

  @Override
  public synchronized CompletionStage<TabSnapshot> createTab() {
    TabId id = TabId.create();
    TabNavigationState state = new TabNavigationState(id);
    tabs.put(id, state);
    activeTabId = id;
    return CompletableFuture.completedFuture(state.snapshot());
  }

  @Override
  public synchronized CompletionStage<Void> activateTab(TabId tabId) {
    requireTab(tabId);
    activeTabId = tabId;
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public synchronized CompletionStage<Void> closeTab(TabId tabId) {
    requireTab(tabId);
    tabs.remove(tabId).cancel();
    if (tabId.equals(activeTabId)) {
      activeTabId = new ArrayList<>(tabs.keySet()).stream().findFirst().orElse(null);
    }
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public synchronized CompletionStage<List<TabSnapshot>> listTabs() {
    return CompletableFuture.completedFuture(
        tabs.values().stream().map(TabNavigationState::snapshot).toList());
  }

  @Override
  public synchronized Optional<TabId> activeTabId() {
    return Optional.ofNullable(activeTabId);
  }

  synchronized TabNavigationState requireTab(TabId tabId) {
    TabNavigationState state = tabs.get(tabId);
    if (state == null) {
      throw new IllegalArgumentException("Unknown tab: " + tabId);
    }
    return state;
  }
}
