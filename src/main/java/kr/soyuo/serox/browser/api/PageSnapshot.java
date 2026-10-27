package kr.soyuo.serox.browser.api;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import kr.soyuo.serox.browser.dom.Document;

/** Immutable loaded page data shared by navigation and the presentation adapter. */
public record PageSnapshot(
    URI uri, String title, String visibleText, Instant loadedAt, Optional<Document> document) {
  /** Keeps compatibility for engines that only provide a page summary. */
  public PageSnapshot(URI uri, String title, String visibleText, Instant loadedAt) {
    this(uri, title, visibleText, loadedAt, Optional.empty());
  }

  /** Validates the public page summary fields. */
  public PageSnapshot {
    Objects.requireNonNull(uri, "uri");
    Objects.requireNonNull(title, "title");
    Objects.requireNonNull(visibleText, "visibleText");
    Objects.requireNonNull(loadedAt, "loadedAt");
    document = Objects.requireNonNull(document, "document");
  }
}
