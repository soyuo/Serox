package kr.soyuo.serox.core.application;

import java.util.concurrent.CompletionStage;
import kr.soyuo.serox.core.model.TabId;
import kr.soyuo.serox.core.model.TabSnapshot;

/** Owns address classification and per-tab navigation actions and history. */
public interface NavigationManager {
  CompletionStage<TabSnapshot> navigate(TabId tabId, String addressOrQuery);

  CompletionStage<TabSnapshot> goBack(TabId tabId);

  CompletionStage<TabSnapshot> goForward(TabId tabId);

  CompletionStage<TabSnapshot> reload(TabId tabId);

  CompletionStage<Void> cancelNavigation(TabId tabId);
}
