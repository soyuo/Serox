package kr.soyuo.serox.core.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import kr.soyuo.serox.browser.api.PageSnapshot;
import kr.soyuo.serox.core.model.TabId;
import org.junit.jupiter.api.Test;

/** Long sessions keep per-tab state bounded and release in-flight work. */
class TabResourceTest {
  @Test
  void historyStaysBoundedAndKeepsTheNewestEntries() {
    var state = new TabNavigationState(TabId.create());
    for (int i = 0; i < TabNavigationState.MAX_HISTORY * 3; i++) {
      state.beginNavigation(URI.create("https://a.test/" + i), true);
    }

    assertEquals(
        URI.create("https://a.test/" + (TabNavigationState.MAX_HISTORY * 3 - 1)),
        state.currentUri().orElseThrow());
    assertTrue(state.snapshot().canGoBack());
    int steps = 0;
    while (state.snapshot().canGoBack() && steps < 1000) {
      state.moveBack();
      steps++;
    }
    assertEquals(TabNavigationState.MAX_HISTORY - 1, steps);
  }

  @Test
  void startingAnotherNavigationCancelsTheInFlightLoad() {
    var state = new TabNavigationState(TabId.create());
    state.beginNavigation(URI.create("https://a.test/"), true);
    long version = state.navigationVersion();
    var load = new CompletableFuture<PageSnapshot>();
    state.setPendingLoad(load, version);

    state.beginNavigation(URI.create("https://b.test/"), true);

    assertTrue(load.isCancelled());
    assertFalse(
        state.complete(
            new PageSnapshot(URI.create("https://a.test/"), "stale", "", Instant.EPOCH), version));
  }
}
