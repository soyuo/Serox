package kr.soyuo.serox.browser.api;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

/** Immutable page summary; DOM and rendering internals do not cross this boundary. */
public record PageSnapshot(URI uri, String title, String visibleText, Instant loadedAt) {
  /** Validates the public page summary fields. */
  public PageSnapshot {
    Objects.requireNonNull(uri, "uri");
    Objects.requireNonNull(title, "title");
    Objects.requireNonNull(visibleText, "visibleText");
    Objects.requireNonNull(loadedAt, "loadedAt");
  }
}
