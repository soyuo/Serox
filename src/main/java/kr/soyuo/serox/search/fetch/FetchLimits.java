package kr.soyuo.serox.search.fetch;

import java.time.Duration;
import java.util.Objects;

/** Upper bounds applied to one evidence-gathering run. */
public record FetchLimits(
    int maxPages, Duration perPageTimeout, Duration totalTimeout, int maxTextCharsPerPage) {
  public FetchLimits {
    Objects.requireNonNull(perPageTimeout, "perPageTimeout");
    Objects.requireNonNull(totalTimeout, "totalTimeout");
    if (maxPages < 1 || maxTextCharsPerPage < 1) {
      throw new IllegalArgumentException("page and text limits must be positive");
    }
    if (perPageTimeout.isZero()
        || perPageTimeout.isNegative()
        || totalTimeout.isZero()
        || totalTimeout.isNegative()) {
      throw new IllegalArgumentException("timeouts must be positive");
    }
  }

  /** Defaults: five pages, 8 seconds each, 15 seconds overall, 6,000 characters per page. */
  public static FetchLimits defaults() {
    return new FetchLimits(5, Duration.ofSeconds(8), Duration.ofSeconds(15), 6_000);
  }
}
