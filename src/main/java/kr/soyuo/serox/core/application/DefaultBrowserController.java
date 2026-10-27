package kr.soyuo.serox.core.application;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import kr.soyuo.serox.browser.api.BrowserEngine;
import kr.soyuo.serox.core.event.BrowserEventBus;
import kr.soyuo.serox.core.model.TabId;
import kr.soyuo.serox.core.model.TabSnapshot;

/** Default application facade composed from the tab and navigation managers. */
public final class DefaultBrowserController implements BrowserController {
  private final TabManager tabs;
  private final NavigationManager navigation;
  private final BrowserEventBus eventBus;

  /** Creates the default tab and navigation managers around the browser engine port. */
  public DefaultBrowserController(BrowserEngine browserEngine, BrowserEventBus eventBus) {
    this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
    InMemoryTabManager tabManager = new InMemoryTabManager(eventBus);
    tabs = tabManager;
    navigation =
        new DefaultNavigationManager(
            tabManager, browserEngine, new NavigationInputClassifier(), eventBus);
  }

  @Override
  public TabManager tabs() {
    return tabs;
  }

  @Override
  public NavigationManager navigation() {
    return navigation;
  }

  @Override
  public BrowserEventBus eventBus() {
    return eventBus;
  }

  @Override
  public CompletionStage<TabSnapshot> createTab() {
    return tabs.createTab();
  }

  @Override
  public CompletionStage<TabSnapshot> navigate(TabId tabId, String addressOrQuery) {
    return navigation.navigate(tabId, addressOrQuery);
  }

  @Override
  public CompletionStage<TabSnapshot> goBack(TabId tabId) {
    return navigation.goBack(tabId);
  }

  @Override
  public CompletionStage<TabSnapshot> goForward(TabId tabId) {
    return navigation.goForward(tabId);
  }

  @Override
  public CompletionStage<TabSnapshot> reload(TabId tabId) {
    return navigation.reload(tabId);
  }

  @Override
  public CompletionStage<Void> cancelNavigation(TabId tabId) {
    return navigation.cancelNavigation(tabId);
  }

  @Override
  public CompletionStage<Void> activateTab(TabId tabId) {
    return tabs.activateTab(tabId);
  }

  @Override
  public CompletionStage<Void> closeTab(TabId tabId) {
    return tabs.closeTab(tabId);
  }

  @Override
  public CompletionStage<List<TabSnapshot>> listTabs() {
    return tabs.listTabs();
  }
}
