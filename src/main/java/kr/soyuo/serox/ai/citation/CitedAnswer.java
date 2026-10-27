package kr.soyuo.serox.ai.citation;

import java.util.List;
import java.util.Objects;

/**
 * Answer text with only valid markers kept, the citations they resolve to, and the source IDs the
 * model invented.
 */
public record CitedAnswer(String text, List<Citation> citations, List<String> invalidSourceIds) {
  public CitedAnswer {
    Objects.requireNonNull(text, "text");
    citations = List.copyOf(Objects.requireNonNull(citations, "citations"));
    invalidSourceIds = List.copyOf(Objects.requireNonNull(invalidSourceIds, "invalidSourceIds"));
  }

  /** True when the answer cites at least one real source and invented none. */
  public boolean fullyGrounded() {
    return !citations.isEmpty() && invalidSourceIds.isEmpty();
  }
}
