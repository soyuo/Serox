package kr.soyuo.serox.core.model;

/** Bounded evidence collection, the provider's safe-search mode, and which engine to ask. */
public record SearchPreferences(
    int evidencePages, String safeSearch, int timeoutSeconds, String engine) {
  /** Brave when a key is saved, DuckDuckGo otherwise. */
  public static final String AUTO = "auto";

  public static final String DUCKDUCKGO = "duckduckgo";
  public static final String BRAVE = "brave";

  public SearchPreferences {
    if (evidencePages < 1
        || evidencePages > 10
        || timeoutSeconds < 10
        || timeoutSeconds > 300
        || !java.util.Set.of("off", "moderate", "strict").contains(safeSearch)
        || !java.util.Set.of(AUTO, DUCKDUCKGO, BRAVE).contains(engine)) {
      throw new IllegalArgumentException("Invalid search preferences");
    }
  }

  /** Preferences that leave the engine on automatic. */
  public SearchPreferences(int evidencePages, String safeSearch, int timeoutSeconds) {
    this(evidencePages, safeSearch, timeoutSeconds, AUTO);
  }

  public static SearchPreferences defaults() {
    return new SearchPreferences(5, "moderate", 10);
  }

  public SearchPreferences withEvidencePages(int count) {
    return new SearchPreferences(count, safeSearch, timeoutSeconds, engine);
  }

  public SearchPreferences withSafeSearch(String mode) {
    return new SearchPreferences(evidencePages, mode, timeoutSeconds, engine);
  }

  public SearchPreferences withTimeoutSeconds(int seconds) {
    return new SearchPreferences(evidencePages, safeSearch, seconds, engine);
  }

  public SearchPreferences withEngine(String choice) {
    return new SearchPreferences(evidencePages, safeSearch, timeoutSeconds, choice);
  }
}
