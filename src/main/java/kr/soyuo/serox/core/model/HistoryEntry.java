package kr.soyuo.serox.core.model;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Persistable visit metadata; page content and credentials are excluded. */
public record HistoryEntry(UUID id, URI uri, String title, Instant visitedAt) {
  /** Validates the visit metadata. */
  public HistoryEntry {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(uri, "uri");
    Objects.requireNonNull(title, "title");
    Objects.requireNonNull(visitedAt, "visitedAt");
  }
}
