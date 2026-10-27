package kr.soyuo.serox.core.error;

import java.util.Objects;

/** Sanitized failure data safe to pass across module and UI boundaries. */
public record ApplicationError(ErrorCode code, String safeMessage, boolean retryable) {
  public ApplicationError {
    Objects.requireNonNull(code, "code");
    Objects.requireNonNull(safeMessage, "safeMessage");
  }
}
