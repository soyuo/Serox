package kr.soyuo.serox.core.event;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Thread-safe event dispatcher that isolates subscriber failures. */
public final class InProcessBrowserEventBus implements BrowserEventBus {
  private static final Logger LOGGER = Logger.getLogger(InProcessBrowserEventBus.class.getName());

  private final CopyOnWriteArrayList<Handler> handlers = new CopyOnWriteArrayList<>();
  private final AtomicBoolean closed = new AtomicBoolean();

  @Override
  public synchronized <E extends ApplicationEvent> EventSubscription subscribe(
      Class<E> eventType, Consumer<? super E> listener) {
    Objects.requireNonNull(eventType, "eventType");
    Objects.requireNonNull(listener, "listener");
    if (closed.get()) {
      throw new IllegalStateException("event bus is closed");
    }

    Handler handler = new TypedHandler<>(eventType, listener);
    handlers.add(handler);
    return () -> handlers.remove(handler);
  }

  @Override
  public void publish(ApplicationEvent event) {
    Objects.requireNonNull(event, "event");
    if (closed.get()) {
      throw new IllegalStateException("event bus is closed");
    }

    for (Handler handler : handlers) {
      if (handler.accepts(event)) {
        try {
          handler.deliver(event);
        } catch (RuntimeException exception) {
          LOGGER.log(
              Level.WARNING,
              "Application event listener failed for {0}",
              event.getClass().getSimpleName());
        }
      }
    }
  }

  @Override
  public synchronized void close() {
    if (closed.compareAndSet(false, true)) {
      handlers.clear();
    }
  }

  private interface Handler {
    boolean accepts(ApplicationEvent event);

    void deliver(ApplicationEvent event);
  }

  private record TypedHandler<E extends ApplicationEvent>(
      Class<E> eventType, Consumer<? super E> listener) implements Handler {
    @Override
    public boolean accepts(ApplicationEvent event) {
      return eventType.isInstance(event);
    }

    @Override
    public void deliver(ApplicationEvent event) {
      listener.accept(eventType.cast(event));
    }
  }
}
