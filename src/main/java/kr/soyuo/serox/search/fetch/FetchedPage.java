package kr.soyuo.serox.search.fetch;

import java.net.URI;
import java.util.Objects;
import kr.soyuo.serox.search.api.SearchResult;

/** Text captured from the page behind one search result. */
public record FetchedPage(SearchResult result, URI finalUri, String title, String text) {
  public FetchedPage {
    Objects.requireNonNull(result, "result");
    Objects.requireNonNull(finalUri, "finalUri");
    Objects.requireNonNull(title, "title");
    Objects.requireNonNull(text, "text");
  }
}
