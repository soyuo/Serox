package kr.soyuo.serox.core.model;

import java.time.Instant;
import java.util.Objects;

/** A non-private search query and its most recent use time. */
public record RecentSearch(String query, Instant usedAt) {
  public RecentSearch {
    Objects.requireNonNull(query, "query");
    Objects.requireNonNull(usedAt, "usedAt");
    if (query.isBlank()) {
      throw new IllegalArgumentException("query must not be blank");
    }
  }
}
