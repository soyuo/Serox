package kr.soyuo.serox.core.model;

import java.util.Objects;

/** In-memory secret wrapper whose string representation is always redacted. */
public final class SecretValue {
  private final String value;

  /** Creates a non-empty secret wrapper. */
  public SecretValue(String value) {
    Objects.requireNonNull(value, "value");
    if (value.isBlank()) {
      throw new IllegalArgumentException("secret value must not be blank");
    }
    this.value = value;
  }

  /** Reveals the secret for the specific outbound provider request. */
  public String revealForRequest() {
    return value;
  }

  @Override
  public String toString() {
    return "[REDACTED]";
  }
}
