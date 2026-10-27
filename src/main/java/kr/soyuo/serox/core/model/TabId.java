package kr.soyuo.serox.core.model;

import java.util.Objects;
import java.util.UUID;

/** Stable identity for one browser tab. */
public record TabId(UUID value) {
  public TabId {
    Objects.requireNonNull(value, "value");
  }

  public static TabId create() {
    return new TabId(UUID.randomUUID());
  }
}
