package kr.soyuo.serox.core.event;

import java.time.Instant;
import java.util.Objects;
import kr.soyuo.serox.core.model.TabId;

/** Announces a safe, user-visible search failure for one browser tab. */
public record SearchFailed(
    Instant occurredAt, TabId tabId, long requestId, String query, String safeMessage)
    implements ApplicationEvent {
  public SearchFailed {
    Objects.requireNonNull(occurredAt, "occurredAt");
    Objects.requireNonNull(tabId, "tabId");
    Objects.requireNonNull(query, "query");
    Objects.requireNonNull(safeMessage, "safeMessage");
  }
}
