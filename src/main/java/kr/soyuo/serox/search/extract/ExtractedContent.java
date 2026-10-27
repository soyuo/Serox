package kr.soyuo.serox.search.extract;

import java.util.Objects;

/** Readable title and body text pulled from one parsed page. */
public record ExtractedContent(String title, String text) {
  public ExtractedContent {
    Objects.requireNonNull(title, "title");
    Objects.requireNonNull(text, "text");
  }
}
