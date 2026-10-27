package kr.soyuo.serox.core.event;

import java.time.Instant;
import java.util.Objects;

/** Service state and sanitized optional user-facing detail. */
public record ConnectionStatusChanged(
    Instant occurredAt, ServiceId service, ServiceStatus status, String safeMessage)
    implements ApplicationEvent {
  /** Validates the event payload. */
  public ConnectionStatusChanged {
    Objects.requireNonNull(occurredAt, "occurredAt");
    Objects.requireNonNull(service, "service");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(safeMessage, "safeMessage");
  }
}
