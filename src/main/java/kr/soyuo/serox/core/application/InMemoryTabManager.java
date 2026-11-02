package kr.soyuo.serox.core.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import kr.soyuo.serox.core.event.ActiveTabChanged;
import kr.soyuo.serox.core.event.BrowserEventBus;
import kr.soyuo.serox.core.event.TabClosed;
import kr.soyuo.serox.core.event.TabStateChanged;
import kr.soyuo.serox.core.model.TabId;
import kr.soyuo.serox.core.model.TabSnapshot;

/** In-memory tab manager with stable ordering and one active tab. */
public final class InMemoryTabManager implements TabManager {
  private final LinkedHashMap<TabId, TabNavigationState> tabs = new LinkedHashMap<>();
  private final BrowserEventBus eventBus;
  private TabId activeTabId;

  public InMemoryTabManager(BrowserEventBus eventBus) {
    this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
  }

  @Override
  public synchronized CompletionStage<TabSnapshot> createTab() {
    TabId id = TabId.create();
    TabNavigationState state = new TabNavigationState(id);
    tabs.put(id, state);
    activeTabId = id;
    eventBus.publish(new TabStateChanged(Instant.now(), state.snapshot()));
    eventBus.publish(new ActiveTabChanged(Instant.now(), Optional.of(id)));
    return CompletableFuture.completedFuture(state.snapshot());
  }

  @Override
  public synchronized CompletionStage<Void> activateTab(TabId tabId) {
    requireTab(tabId);
    activeTabId = tabId;
    eventBus.publish(new ActiveTabChanged(Instant.now(), Optional.of(tabId)));
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public synchronized CompletionStage<Void> closeTab(TabId tabId) {
    requireTab(tabId);
    List<TabId> order = new ArrayList<>(tabs.keySet());
    int index = order.indexOf(tabId);
    tabs.remove(tabId).cancel();
    eventBus.publish(new TabClosed(Instant.now(), tabId));
    if (tabs.isEmpty()) {
      // A browser window always keeps one tab.
      return createTab().thenApply(snapshot -> null);
    }
    if (tabId.equals(activeTabId)) {
      // Go to the tab on the right, or the one on the left when the closed tab was last.
      order.remove(index);
      activeTabId = order.get(Math.min(index, order.size() - 1));
      eventBus.publish(new ActiveTabChanged(Instant.now(), Optional.of(activeTabId)));
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
