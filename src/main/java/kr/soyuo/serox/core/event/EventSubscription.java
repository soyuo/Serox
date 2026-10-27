package kr.soyuo.serox.core.event;

/** Handle used to stop receiving events. */
@FunctionalInterface
public interface EventSubscription extends AutoCloseable {
  @Override
  void close();
}
