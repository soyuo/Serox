package kr.soyuo.serox.search.api;

import java.util.concurrent.CompletionStage;

/** Provider-independent search contract. */
public interface SearchProvider {
  CompletionStage<SearchResponse> search(SearchRequest request);
}
