package kr.soyuo.serox.ai.context;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import kr.soyuo.serox.ai.api.AiRequest;
import kr.soyuo.serox.ai.api.EvidenceDocument;
import kr.soyuo.serox.search.fetch.FetchedPage;

/** Builds a bounded, redacted AI request from fetched search evidence. */
public final class ContextBuilder {
  private static final int DEFAULT_MAX_TOTAL_CHARS = 24_000;
  private static final int MAX_QUESTION_CHARS = 500;

  private final int maxTotalChars;

  public ContextBuilder() {
    this(DEFAULT_MAX_TOTAL_CHARS);
  }

  /** Limits the combined evidence text of one request to {@code maxTotalChars}. */
  public ContextBuilder(int maxTotalChars) {
    if (maxTotalChars < 1) {
      throw new IllegalArgumentException("maxTotalChars must be positive");
    }
    this.maxTotalChars = maxTotalChars;
  }

  /**
   * Assigns source IDs S1, S2, ... in order, redacts sensitive data, drops pages without text and
   * trims the last documents until the total fits the limit.
   */
  public AiRequest build(String question, List<FetchedPage> pages, Instant retrievedAt) {
    Objects.requireNonNull(question, "question");
    Objects.requireNonNull(pages, "pages");
    Objects.requireNonNull(retrievedAt, "retrievedAt");
    String boundedQuestion = SensitiveDataFilter.redact(question.strip());
    if (boundedQuestion.length() > MAX_QUESTION_CHARS) {
      boundedQuestion = boundedQuestion.substring(0, MAX_QUESTION_CHARS);
    }
    List<EvidenceDocument> documents = new ArrayList<>();
    int remaining = maxTotalChars;
    int index = 0;
    for (FetchedPage page : pages) {
      if (remaining <= 0) {
        break;
      }
      String text = SensitiveDataFilter.redact(page.text());
      if (text.isBlank()) {
        continue;
      }
      if (text.length() > remaining) {
        text = text.substring(0, remaining);
      }
      remaining -= text.length();
      index++;
      documents.add(
          new EvidenceDocument(
              "S" + index,
              page.result().uri(),
              SensitiveDataFilter.redact(
                  page.title().isBlank() ? page.result().title() : page.title()),
              text,
              retrievedAt));
    }
    return new AiRequest(boundedQuestion, documents);
  }
}
