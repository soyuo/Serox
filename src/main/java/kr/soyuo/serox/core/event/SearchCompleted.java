package kr.soyuo.serox.core.event;

import java.time.Instant;
import java.util.Objects;
import kr.soyuo.serox.core.model.TabId;
import kr.soyuo.serox.search.api.SearchResponse;

/** Announces normalized search results for one browser tab. */
public record SearchCompleted(
    Instant occurredAt, TabId tabId, long requestId, String query, SearchResponse response)
    implements ApplicationEvent {
  public SearchCompleted {
    Objects.requireNonNull(occurredAt, "occurredAt");
    Objects.requireNonNull(tabId, "tabId");
    Objects.requireNonNull(query, "query");
    Objects.requireNonNull(response, "response");
  }
}
