package kr.soyuo.serox.core.event;

import java.time.Instant;

/** Timestamped state change delivered through the application event bus. */
public interface ApplicationEvent {
  Instant occurredAt();
}
