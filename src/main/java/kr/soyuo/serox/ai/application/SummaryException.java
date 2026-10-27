package kr.soyuo.serox.ai.application;

/** Failure of a search-and-summarize run with a message that is safe to show to callers. */
public final class SummaryException extends RuntimeException {
  private final String code;

  public SummaryException(String code, String safeMessage) {
    super(safeMessage);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
