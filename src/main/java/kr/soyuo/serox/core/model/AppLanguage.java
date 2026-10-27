package kr.soyuo.serox.core.model;

/** The language used for menus and AI summaries. */
public enum AppLanguage {
  KOREAN("한국어", "Korean"),
  ENGLISH("English", "English"),
  JAPANESE("日本語", "Japanese");

  private final String label;
  private final String englishName;

  AppLanguage(String label, String englishName) {
    this.label = label;
    this.englishName = englishName;
  }

  /** The name of the language in English, as written into an AI prompt. */
  public String englishName() {
    return englishName;
  }

  /** The name of the language written in that language. */
  public String label() {
    return label;
  }
}
