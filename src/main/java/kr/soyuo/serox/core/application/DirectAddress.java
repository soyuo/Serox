package kr.soyuo.serox.core.application;

import java.net.URI;
import java.util.Objects;

/** An absolute HTTP or HTTPS destination. */
public record DirectAddress(URI uri) implements NavigationTarget {
  public DirectAddress {
    Objects.requireNonNull(uri, "uri");
  }
}
