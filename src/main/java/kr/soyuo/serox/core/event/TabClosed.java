package kr.soyuo.serox.core.event;

import java.time.Instant;
import java.util.Objects;
import kr.soyuo.serox.core.model.TabId;

/** Indicates that a tab has been removed. */
public record TabClosed(Instant occurredAt, TabId tabId) implements ApplicationEvent {
  public TabClosed {
    Objects.requireNonNull(occurredAt, "occurredAt");
    Objects.requireNonNull(tabId, "tabId");
  }
}
