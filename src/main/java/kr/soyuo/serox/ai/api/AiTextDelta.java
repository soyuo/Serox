package kr.soyuo.serox.ai.api;

import java.util.Objects;

/** Incremental text chunk from a streamed AI answer. */
public record AiTextDelta(String text) implements AiEvent {
  public AiTextDelta {
    Objects.requireNonNull(text, "text");
  }
}
