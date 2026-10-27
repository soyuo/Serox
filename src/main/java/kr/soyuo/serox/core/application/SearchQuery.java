package kr.soyuo.serox.core.application;

import java.util.Objects;

/** Text to be sent to a configured search provider. */
public record SearchQuery(String value) implements NavigationTarget {
  /** Validates the non-empty search query. */
  public SearchQuery {
    Objects.requireNonNull(value, "value");
    if (value.isBlank()) {
      throw new IllegalArgumentException("value must not be blank");
    }
  }
}
