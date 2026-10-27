package kr.soyuo.serox.core.model;

/** Bounded evidence collection and the provider's safe-search mode. */
public record SearchPreferences(int evidencePages, String safeSearch, int timeoutSeconds) {
  public SearchPreferences {
    if (evidencePages < 1
        || evidencePages > 10
        || timeoutSeconds < 10
        || timeoutSeconds > 300
        || !java.util.Set.of("off", "moderate", "strict").contains(safeSearch)) {
      throw new IllegalArgumentException("Invalid search preferences");
    }
  }

  public static SearchPreferences defaults() {
    return new SearchPreferences(5, "moderate", 10);
  }

  public SearchPreferences withEvidencePages(int count) {
    return new SearchPreferences(count, safeSearch, timeoutSeconds);
  }

  public SearchPreferences withSafeSearch(String mode) {
    return new SearchPreferences(evidencePages, mode, timeoutSeconds);
  }

  public SearchPreferences withTimeoutSeconds(int seconds) {
    return new SearchPreferences(evidencePages, safeSearch, seconds);
  }
}
