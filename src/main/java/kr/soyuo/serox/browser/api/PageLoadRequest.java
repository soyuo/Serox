package kr.soyuo.serox.browser.api;

import java.net.URI;
import java.util.Objects;

/** Request to load one already-classified web address. */
public record PageLoadRequest(URI uri) {
  /** Requires an absolute HTTP or HTTPS address. */
  public PageLoadRequest {
    Objects.requireNonNull(uri, "uri");
    String scheme = uri.getScheme();
    if (!uri.isAbsolute()
        || scheme == null
        || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
        || uri.getHost() == null
        || uri.getHost().isBlank()
        || uri.getUserInfo() != null) {
      throw new IllegalArgumentException("uri must be an absolute HTTP or HTTPS address");
    }
  }
}
