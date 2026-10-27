package kr.soyuo.serox.core.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Persistable browser session summary without document contents. */
public record SessionSnapshot(UUID id, List<TabSnapshot> tabs, Optional<TabId> activeTabId) {
  /** Validates the session and copies its tab list. */
  public SessionSnapshot {
    Objects.requireNonNull(id, "id");
    tabs = List.copyOf(tabs);
    activeTabId = Objects.requireNonNull(activeTabId, "activeTabId");
    if (activeTabId.isPresent()) {
      TabId activeId = activeTabId.orElseThrow();
      if (tabs.stream().noneMatch(tab -> tab.id().equals(activeId))) {
        throw new IllegalArgumentException("activeTabId must identify a tab in the session");
      }
    }
  }
}
