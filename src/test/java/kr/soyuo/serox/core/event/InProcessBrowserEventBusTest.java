package kr.soyuo.serox.core.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class InProcessBrowserEventBusTest {
  @Test
  void deliversMatchingEventsAndStopsAfterSubscriptionCloses() {
    InProcessBrowserEventBus eventBus = new InProcessBrowserEventBus();
    AtomicInteger deliveries = new AtomicInteger();
    EventSubscription subscription =
        eventBus.subscribe(ConnectionStatusChanged.class, event -> deliveries.incrementAndGet());

    eventBus.publish(
        new ConnectionStatusChanged(Instant.EPOCH, ServiceId.AI, ServiceStatus.CONNECTED, "연결됨"));
    subscription.close();
    eventBus.publish(
        new ConnectionStatusChanged(
            Instant.EPOCH, ServiceId.AI, ServiceStatus.DISCONNECTED, "연결 안 됨"));

    assertEquals(1, deliveries.get());
  }

  @Test
  void rejectsSubscriptionsAfterClose() {
    InProcessBrowserEventBus eventBus = new InProcessBrowserEventBus();
    eventBus.close();

    assertThrows(
        IllegalStateException.class,
        () -> eventBus.subscribe(ConnectionStatusChanged.class, event -> {}));
  }
}
