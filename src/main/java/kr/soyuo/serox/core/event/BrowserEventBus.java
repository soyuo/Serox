package kr.soyuo.serox.core.event;

import java.util.function.Consumer;

/** Typed in-process event boundary between application services and adapters. */
public interface BrowserEventBus extends AutoCloseable {
  <E extends ApplicationEvent> EventSubscription subscribe(
      Class<E> eventType, Consumer<? super E> listener);

  void publish(ApplicationEvent event);

  @Override
  void close();
}
