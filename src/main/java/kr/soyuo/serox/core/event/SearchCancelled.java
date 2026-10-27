package kr.soyuo.serox.core.event;

import java.time.Instant;
import java.util.Objects;
import kr.soyuo.serox.core.model.TabId;

/** Announces cancellation of a pending search for one browser tab. */
public record SearchCancelled(Instant occurredAt, TabId tabId, long requestId, String query)
    implements ApplicationEvent {
  public SearchCancelled {
    Objects.requireNonNull(occurredAt, "occurredAt");
    Objects.requireNonNull(tabId, "tabId");
    Objects.requireNonNull(query, "query");
  }
}
