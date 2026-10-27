package kr.soyuo.serox.ai.api;

import java.util.List;
import java.util.Objects;

/** Final citation metadata for a completed streamed answer. */
public record AiCompleted(List<AiCitation> citations) implements AiEvent {
  public AiCompleted {
    citations = List.copyOf(Objects.requireNonNull(citations, "citations"));
  }
}
