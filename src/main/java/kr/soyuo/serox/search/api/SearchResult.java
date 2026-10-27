package kr.soyuo.serox.search.api;

import java.net.URI;
import java.util.Objects;

/** Normalized result metadata returned by a search provider. */
public record SearchResult(String id, URI uri, String title, String snippet, int rank) {
  /** Validates provider result metadata and its positive rank. */
  public SearchResult {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(uri, "uri");
    Objects.requireNonNull(title, "title");
    Objects.requireNonNull(snippet, "snippet");
    if (rank < 1) {
      throw new IllegalArgumentException("rank must be positive");
    }
    if (id.isBlank()) {
      throw new IllegalArgumentException("id must not be blank");
    }
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
