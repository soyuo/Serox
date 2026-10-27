package kr.soyuo.serox.core.event;

import java.time.Instant;
import java.util.Objects;
import kr.soyuo.serox.core.model.TabId;

/**
 * Announces a safe, user-visible search failure for one browser tab. {@code offline} is true when
 * the request never reached the provider because the network was unavailable.
 */
public record SearchFailed(
    Instant occurredAt,
    TabId tabId,
    long requestId,
    String query,
    String safeMessage,
    boolean offline)
    implements ApplicationEvent {
  /** A failure that is not a connectivity problem. */
  public SearchFailed(
      Instant occurredAt, TabId tabId, long requestId, String query, String safeMessage) {
    this(occurredAt, tabId, requestId, query, safeMessage, false);
  }

  public SearchFailed {
    Objects.requireNonNull(occurredAt, "occurredAt");
    Objects.requireNonNull(tabId, "tabId");
    Objects.requireNonNull(query, "query");
    Objects.requireNonNull(safeMessage, "safeMessage");
  }
}
