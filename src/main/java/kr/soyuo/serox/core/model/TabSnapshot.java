package kr.soyuo.serox.core.model;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

/** Immutable presentation-safe summary of a tab's current state. */
public record TabSnapshot(
    TabId id,
    Optional<URI> currentUri,
    String title,
    NavigationStatus navigationStatus,
    boolean canGoBack,
    boolean canGoForward) {
  /** Validates the immutable tab snapshot. */
  public TabSnapshot {
    Objects.requireNonNull(id, "id");
    currentUri = Objects.requireNonNull(currentUri, "currentUri");
    title = Objects.requireNonNull(title, "title");
    Objects.requireNonNull(navigationStatus, "navigationStatus");
  }
}
