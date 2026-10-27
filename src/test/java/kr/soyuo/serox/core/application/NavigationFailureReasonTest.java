package kr.soyuo.serox.core.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import kr.soyuo.serox.browser.api.BrowserEngine;
import kr.soyuo.serox.core.event.InProcessBrowserEventBus;
import kr.soyuo.serox.core.event.NavigationFailed;
import org.junit.jupiter.api.Test;

/** Each way an address can fail selects the error page the design shows for it. */
class NavigationFailureReasonTest {
  private static List<NavigationFailed.Reason> navigateAndCollect(
      Throwable engineFailure, String address) {
    BrowserEngine engine = request -> CompletableFuture.failedFuture(engineFailure);
    var bus = new InProcessBrowserEventBus();
    List<NavigationFailed.Reason> reasons = new ArrayList<>();
    List<String> addresses = new ArrayList<>();
    bus.subscribe(
        NavigationFailed.class,
        event -> {
          reasons.add(event.reason());
          addresses.add(event.address());
        });
    var controller = new DefaultBrowserController(engine, bus, Runnable::run);
    var tab = controller.createTab().toCompletableFuture().join();
    assertThrows(
        CompletionException.class,
        () -> controller.navigate(tab.id(), address).toCompletableFuture().join());
    bus.close();
    return reasons;
  }

  @Test
  void unknownHostsTimeoutsAndOtherFailuresAreToldApart() {
    assertEquals(
        List.of(NavigationFailed.Reason.NOT_FOUND),
        navigateAndCollect(new UnknownHostException("nope.test"), "https://nope.test/"));
    assertEquals(
        List.of(NavigationFailed.Reason.TIMEOUT),
        navigateAndCollect(new HttpTimeoutException("slow"), "https://slow.test/"));
    assertEquals(
        List.of(NavigationFailed.Reason.UNREACHABLE),
        navigateAndCollect(new ConnectException("refused"), "https://down.test/"));
  }

  @Test
  void aWrappedCauseIsStillRecognised() {
    assertEquals(
        List.of(NavigationFailed.Reason.NOT_FOUND),
        navigateAndCollect(
            new CompletionException(new UnknownHostException("nope.test")), "https://nope.test/"));
  }

  @Test
  void anUnusableAddressIsReportedWithoutTouchingTheNetwork() {
    BrowserEngine engine =
        request -> {
          throw new AssertionError("the network must not be used for an invalid address");
        };
    var bus = new InProcessBrowserEventBus();
    List<NavigationFailed> seen = new ArrayList<>();
    bus.subscribe(NavigationFailed.class, seen::add);
    var controller = new DefaultBrowserController(engine, bus, Runnable::run);
    var tab = controller.createTab().toCompletableFuture().join();

    assertThrows(
        RuntimeException.class,
        () -> controller.navigate(tab.id(), "http://example.com:99999").toCompletableFuture().join());

    assertEquals(1, seen.size());
    assertEquals(NavigationFailed.Reason.INVALID_ADDRESS, seen.getFirst().reason());
    assertEquals("http://example.com:99999", seen.getFirst().address());
    bus.close();
  }
}
