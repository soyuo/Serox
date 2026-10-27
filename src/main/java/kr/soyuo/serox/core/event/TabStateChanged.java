package kr.soyuo.serox.core.event;

import java.time.Instant;
import java.util.Objects;
import kr.soyuo.serox.core.model.TabSnapshot;

/** New immutable navigation snapshot for one tab. */
public record TabStateChanged(Instant occurredAt, TabSnapshot snapshot)
    implements ApplicationEvent {
  public TabStateChanged {
    Objects.requireNonNull(occurredAt, "occurredAt");
    Objects.requireNonNull(snapshot, "snapshot");
  }
}
