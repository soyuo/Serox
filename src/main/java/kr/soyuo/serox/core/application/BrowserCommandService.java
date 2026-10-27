package kr.soyuo.serox.core.application;

import java.util.List;
import java.util.concurrent.CompletionStage;
import kr.soyuo.serox.core.model.TabId;
import kr.soyuo.serox.core.model.TabSnapshot;

/** Public asynchronous commands exposed to the presentation layer. */
public interface BrowserCommandService {
  CompletionStage<TabSnapshot> createTab();

  CompletionStage<TabSnapshot> navigate(TabId tabId, String addressOrQuery);

  CompletionStage<TabSnapshot> goBack(TabId tabId);

  CompletionStage<TabSnapshot> goForward(TabId tabId);

  CompletionStage<TabSnapshot> reload(TabId tabId);

  CompletionStage<Void> cancelNavigation(TabId tabId);

  CompletionStage<Void> activateTab(TabId tabId);

  CompletionStage<Void> closeTab(TabId tabId);

  CompletionStage<List<TabSnapshot>> listTabs();
}
