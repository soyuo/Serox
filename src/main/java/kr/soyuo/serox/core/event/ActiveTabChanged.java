package kr.soyuo.serox.core.event;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import kr.soyuo.serox.core.model.TabId;

/** Indicates which tab is active after a tab selection or close operation. */
public record ActiveTabChanged(Instant occurredAt, Optional<TabId> tabId)
    implements ApplicationEvent {
  public ActiveTabChanged {
    Objects.requireNonNull(occurredAt, "occurredAt");
    Objects.requireNonNull(tabId, "tabId");
  }
}
