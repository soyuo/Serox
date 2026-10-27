package kr.soyuo.serox.core.model;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** User-saved page reference. */
public record Bookmark(UUID id, URI uri, String title, Instant createdAt) {
  /** Validates the saved page reference. */
  public Bookmark {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(uri, "uri");
    Objects.requireNonNull(title, "title");
    Objects.requireNonNull(createdAt, "createdAt");
  }
}
