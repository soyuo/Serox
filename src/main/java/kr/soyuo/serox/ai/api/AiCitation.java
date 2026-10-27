package kr.soyuo.serox.ai.api;

import java.util.Objects;

/** Citation reference to one source identifier in the submitted evidence. */
public record AiCitation(String sourceId, String quotedText) {
  /** Validates the source reference and quoted text. */
  public AiCitation {
    Objects.requireNonNull(sourceId, "sourceId");
    Objects.requireNonNull(quotedText, "quotedText");
    if (sourceId.isBlank()) {
      throw new IllegalArgumentException("sourceId must not be blank");
    }
  }
}
