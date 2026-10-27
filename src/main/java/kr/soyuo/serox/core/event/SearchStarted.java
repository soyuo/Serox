package kr.soyuo.serox.core.event;

import java.time.Instant;
import java.util.Objects;
import kr.soyuo.serox.core.model.TabId;

/** Announces a search request for one browser tab. */
public record SearchStarted(Instant occurredAt, TabId tabId, long requestId, String query)
    implements ApplicationEvent {
  public SearchStarted {
    Objects.requireNonNull(occurredAt, "occurredAt");
    Objects.requireNonNull(tabId, "tabId");
    Objects.requireNonNull(query, "query");
  }
}
