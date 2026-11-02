package kr.soyuo.serox.core.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import kr.soyuo.serox.core.event.InProcessBrowserEventBus;
import kr.soyuo.serox.core.model.TabId;
import org.junit.jupiter.api.Test;

class InMemoryTabManagerTest {
  private final InMemoryTabManager tabs = new InMemoryTabManager(new InProcessBrowserEventBus());

  private TabId open() {
    return tabs.createTab().toCompletableFuture().join().id();
  }

  @Test
  void closingTheActiveTabGoesToItsRightNeighbour() {
    TabId first = open();
    TabId middle = open();
    TabId last = open();
    tabs.activateTab(middle);
    tabs.closeTab(middle);
    assertEquals(last, tabs.activeTabId().orElseThrow());
    tabs.closeTab(last);
    assertEquals(first, tabs.activeTabId().orElseThrow());
  }

  @Test
  void closingTheLastTabOpensANewOne() {
    TabId only = open();
    tabs.closeTab(only);
    assertEquals(1, tabs.listTabs().toCompletableFuture().join().size());
    assertNotEquals(only, tabs.activeTabId().orElseThrow());
  }
}
