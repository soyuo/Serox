package kr.soyuo.serox.ai.api;

import java.util.List;
import java.util.Objects;

/**
 * AI prompt context containing only selected, bounded evidence documents. {@code answerLanguage}
 * names the language to answer in, such as "English"; blank means the language of the question.
 */
public record AiRequest(
    String question, List<EvidenceDocument> evidence, String answerLanguage) {
  /** A request answered in the language of the question. */
  public AiRequest(String question, List<EvidenceDocument> evidence) {
    this(question, evidence, "");
  }

  /** Validates the question and copies the evidence list. */
  public AiRequest {
    Objects.requireNonNull(question, "question");
    if (question.isBlank()) {
      throw new IllegalArgumentException("question must not be blank");
    }
    evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
    answerLanguage = Objects.requireNonNull(answerLanguage, "answerLanguage").strip();
  }

  /** The same request, answered in the named language. */
  public AiRequest withAnswerLanguage(String language) {
    return new AiRequest(question, evidence, language);
  }
}
