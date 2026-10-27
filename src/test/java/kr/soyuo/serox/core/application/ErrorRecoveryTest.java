package kr.soyuo.serox.core.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import kr.soyuo.serox.browser.api.BrowserEngine;
import kr.soyuo.serox.browser.api.PageSnapshot;
import kr.soyuo.serox.core.event.ApplicationEvent;
import kr.soyuo.serox.core.event.InProcessBrowserEventBus;
import kr.soyuo.serox.core.model.NavigationStatus;
import org.junit.jupiter.api.Test;

/** A failure in one page load or listener never leaves a tab or the app unusable. */
class ErrorRecoveryTest {
  @Test
  void aTabRecoversWhenTheNextLoadSucceedsAfterAnEngineCrash() {
    AtomicBoolean broken = new AtomicBoolean(true);
    BrowserEngine engine =
        request -> {
          if (broken.get()) {
            throw new IllegalStateException("renderer crashed");
          }
          return CompletableFuture.completedFuture(
              new PageSnapshot(request.uri(), "Back", "ok", Instant.now()));
        };
    var bus = new InProcessBrowserEventBus();
    var controller = new DefaultBrowserController(engine, bus, Runnable::run);
    var tab = controller.createTab().toCompletableFuture().join();

    assertThrows(
        CompletionException.class,
        () -> controller.navigate(tab.id(), "https://a.test/").toCompletableFuture().join());
    assertEquals(
        NavigationStatus.FAILED,
        controller.listTabs().toCompletableFuture().join().getFirst().navigationStatus());

    broken.set(false);
    var page = controller.navigate(tab.id(), "https://a.test/").toCompletableFuture().join();
    assertEquals("Back", page.title());
    assertEquals(
        NavigationStatus.SUCCEEDED,
        controller.listTabs().toCompletableFuture().join().getFirst().navigationStatus());
    bus.close();
  }

  @Test
  void aFailingListenerDoesNotStopOtherListenersOrFuturePublishes() {
    var bus = new InProcessBrowserEventBus();
    List<ApplicationEvent> seen = new ArrayList<>();
    bus.subscribe(
        ApplicationEvent.class,
        event -> {
          throw new IllegalStateException("ui bug");
        });
    bus.subscribe(ApplicationEvent.class, seen::add);
    ApplicationEvent event = new kr.soyuo.serox.core.event.SettingsChanged(
            Instant.EPOCH,
        kr.soyuo.serox.core.model.BrowserSettings.defaults());

    bus.publish(event);
    bus.publish(event);

    assertEquals(2, seen.size());
    assertTrue(seen.stream().allMatch(event::equals));
    bus.close();
  }
}
