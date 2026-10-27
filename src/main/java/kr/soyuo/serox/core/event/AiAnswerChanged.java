package kr.soyuo.serox.core.event;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import kr.soyuo.serox.ai.api.EvidenceDocument;
import kr.soyuo.serox.ai.citation.CitedAnswer;
import kr.soyuo.serox.core.model.TabId;

/**
 * Progress of the AI answer for one tab's search. It is independent of search events, so an AI
 * failure never affects the general results.
 */
public record AiAnswerChanged(
    Instant occurredAt,
    TabId tabId,
    long searchRequestId,
    Phase phase,
    String text,
    Optional<CitedAnswer> answer,
    List<EvidenceDocument> evidence,
    String message)
    implements ApplicationEvent {
  /** Lifecycle of one AI answer. */
  public enum Phase {
    UNAVAILABLE,
    PREPARING,
    STREAMING,
    COMPLETED,
    FAILED,
    CANCELLED
  }

  public AiAnswerChanged {
    Objects.requireNonNull(occurredAt, "occurredAt");
    Objects.requireNonNull(tabId, "tabId");
    Objects.requireNonNull(phase, "phase");
    Objects.requireNonNull(text, "text");
    Objects.requireNonNull(answer, "answer");
    evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
    Objects.requireNonNull(message, "message");
  }
}
