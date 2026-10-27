package kr.soyuo.serox.core.event;

import java.time.Instant;
import java.util.Objects;
import kr.soyuo.serox.browser.api.PageSnapshot;
import kr.soyuo.serox.core.model.TabId;

/** Published after a tab has successfully parsed a page. */
public record PageLoaded(Instant occurredAt, TabId tabId, PageSnapshot page)
    implements ApplicationEvent {
  public PageLoaded {
    Objects.requireNonNull(occurredAt, "occurredAt");
    Objects.requireNonNull(tabId, "tabId");
    Objects.requireNonNull(page, "page");
  }
}
