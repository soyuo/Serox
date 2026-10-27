package kr.soyuo.serox.search.api;

import java.util.Objects;

/** Search query and bounded result limit sent to a provider. */
public record SearchRequest(String query, int resultLimit) {
  /** Validates the query and enforces the provider result limit. */
  public SearchRequest {
    Objects.requireNonNull(query, "query");
    if (query.isBlank()) {
      throw new IllegalArgumentException("query must not be blank");
    }
    if (resultLimit < 1 || resultLimit > 50) {
      throw new IllegalArgumentException("resultLimit must be between 1 and 50");
    }
    if (query.length() > 600) {
      throw new IllegalArgumentException("query must not exceed 600 characters");
    }
    if (query.strip().split("\\s+").length > 75) {
      throw new IllegalArgumentException("query must not exceed 75 words");
    }
  }
}
