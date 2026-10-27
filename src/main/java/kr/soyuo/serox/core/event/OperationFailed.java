package kr.soyuo.serox.core.event;

import java.time.Instant;
import java.util.Objects;
import kr.soyuo.serox.core.error.ApplicationError;

/** Sanitized operation error for application consumers. */
public record OperationFailed(Instant occurredAt, ApplicationError error)
    implements ApplicationEvent {
  public OperationFailed {
    Objects.requireNonNull(occurredAt, "occurredAt");
    Objects.requireNonNull(error, "error");
  }
}
