package kr.soyuo.serox.core.model;

import java.time.Duration;
import java.util.Optional;

/** How long visited pages are remembered; "forever" is still limited by the entry count. */
public enum HistoryRetention {
  WEEK(Duration.ofDays(7)),
  MONTH(Duration.ofDays(30)),
  FOREVER(null);

  private final Duration keepFor;

  HistoryRetention(Duration keepFor) {
    this.keepFor = keepFor;
  }

  /** The maximum age of a stored visit, or empty when age never expires a visit. */
  public Optional<Duration> keepFor() {
    return Optional.ofNullable(keepFor);
  }
}
