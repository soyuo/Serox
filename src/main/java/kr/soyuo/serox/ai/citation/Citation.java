package kr.soyuo.serox.ai.citation;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

/** A validated link between a claim in an answer and one evidence document. */
public record Citation(
    String sourceId, URI uri, String title, String referencedText, Instant retrievedAt) {
  public Citation {
    Objects.requireNonNull(sourceId, "sourceId");
    Objects.requireNonNull(uri, "uri");
    Objects.requireNonNull(title, "title");
    Objects.requireNonNull(referencedText, "referencedText");
    Objects.requireNonNull(retrievedAt, "retrievedAt");
  }
}
