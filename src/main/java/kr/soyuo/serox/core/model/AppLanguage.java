package kr.soyuo.serox.core.model;

/** The language used for menus and AI summaries. */
public enum AppLanguage {
  KOREAN("한국어"),
  ENGLISH("English"),
  JAPANESE("日本語");

  private final String label;

  AppLanguage(String label) {
    this.label = label;
  }

  /** The name of the language written in that language. */
  public String label() {
    return label;
  }
}
