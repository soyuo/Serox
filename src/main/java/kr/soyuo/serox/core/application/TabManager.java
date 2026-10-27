package kr.soyuo.serox.core.application;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import kr.soyuo.serox.core.model.TabId;
import kr.soyuo.serox.core.model.TabSnapshot;

/** Owns tab identity, ordering, activation, and tab snapshots. */
public interface TabManager {
  CompletionStage<TabSnapshot> createTab();

  CompletionStage<Void> activateTab(TabId tabId);

  CompletionStage<Void> closeTab(TabId tabId);

  CompletionStage<List<TabSnapshot>> listTabs();

  Optional<TabId> activeTabId();
}
