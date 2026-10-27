package kr.soyuo.serox.ai.api;

import java.util.List;
import java.util.Objects;

/** AI prompt context containing only selected, bounded evidence documents. */
public record AiRequest(String question, List<EvidenceDocument> evidence) {
  /** Validates the question and copies the evidence list. */
  public AiRequest {
    Objects.requireNonNull(question, "question");
    if (question.isBlank()) {
      throw new IllegalArgumentException("question must not be blank");
    }
    evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
  }
}
