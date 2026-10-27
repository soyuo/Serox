package kr.soyuo.serox.ai.application;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** A cited answer for one query, with the sources that support it. */
public record SearchSummary(String query, String answer, List<Source> sources) {
  /** One verified source behind a citation. */
  public record Source(
      String id, String title, URI url, String referencedText, Instant retrievedAt) {
    public Source {
      Objects.requireNonNull(id, "id");
      Objects.requireNonNull(title, "title");
      Objects.requireNonNull(url, "url");
      Objects.requireNonNull(referencedText, "referencedText");
      Objects.requireNonNull(retrievedAt, "retrievedAt");
    }
  }

  public SearchSummary {
    Objects.requireNonNull(query, "query");
    Objects.requireNonNull(answer, "answer");
    sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
  }
}
