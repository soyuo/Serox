package kr.soyuo.serox.core.model;

/** User presentation and style preferences; grounding and the global evidence cap stay enforced. */
public record SummaryPreferences(
    String language,
    boolean keyPoints,
    boolean inlineCitations,
    int sourceCharacters,
    String instructions) {
  public static final String DEFAULT_INSTRUCTIONS = "검색어와 본문을 읽고 핵심만 정리해 주세요. 본문에 없는 내용은 쓰지 마세요.";

  public SummaryPreferences {
    java.util.Objects.requireNonNull(instructions, "instructions");
    if (!java.util.Set.of("question", "Korean", "app").contains(language)) {
      throw new IllegalArgumentException("Invalid summary language");
    }
    if (!java.util.Set.of(3000, 8000, 20000).contains(sourceCharacters)
        || instructions.length() > 2000) {
      throw new IllegalArgumentException("Invalid summary preferences");
    }
  }

  public static SummaryPreferences defaults() {
    return new SummaryPreferences("question", true, true, 8000, DEFAULT_INSTRUCTIONS);
  }

  public SummaryPreferences withLanguage(String value) {
    return new SummaryPreferences(
        value, keyPoints, inlineCitations, sourceCharacters, instructions);
  }

  public SummaryPreferences withKeyPoints(boolean value) {
    return new SummaryPreferences(language, value, inlineCitations, sourceCharacters, instructions);
  }

  public SummaryPreferences withInlineCitations(boolean value) {
    return new SummaryPreferences(language, keyPoints, value, sourceCharacters, instructions);
  }

  public SummaryPreferences withSourceCharacters(int value) {
    return new SummaryPreferences(language, keyPoints, inlineCitations, value, instructions);
  }

  public SummaryPreferences withInstructions(String value) {
    return new SummaryPreferences(language, keyPoints, inlineCitations, sourceCharacters, value);
  }
}
