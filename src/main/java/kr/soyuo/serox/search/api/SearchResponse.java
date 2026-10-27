package kr.soyuo.serox.search.api;

import java.util.List;
import java.util.Objects;

/** Immutable normalized response from one configured search provider. */
public record SearchResponse(String providerId, List<SearchResult> results) {
  public SearchResponse {
    Objects.requireNonNull(providerId, "providerId");
    results = List.copyOf(Objects.requireNonNull(results, "results"));
  }
}
