package kr.soyuo.serox.ai.api;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

/** Bounded, user-approved source text supplied to an AI provider. */
public record EvidenceDocument(
    String sourceId, URI uri, String title, String text, Instant retrievedAt) {
  /** Validates the source metadata and captured text. */
  public EvidenceDocument {
    Objects.requireNonNull(sourceId, "sourceId");
    Objects.requireNonNull(uri, "uri");
    Objects.requireNonNull(title, "title");
    Objects.requireNonNull(text, "text");
    Objects.requireNonNull(retrievedAt, "retrievedAt");
  }
}
