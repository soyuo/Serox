package kr.soyuo.serox.ai.api;

import java.util.Objects;

/** Sanitized provider failure category; must never contain credentials. */
public record AiFailed(String code, String safeMessage) implements AiEvent {
  public AiFailed {
    Objects.requireNonNull(code, "code");
    Objects.requireNonNull(safeMessage, "safeMessage");
  }
}
