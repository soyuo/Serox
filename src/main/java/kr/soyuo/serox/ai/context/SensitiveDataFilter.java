package kr.soyuo.serox.ai.context;

import java.util.List;
import java.util.regex.Pattern;

/** Removes credentials and personal identifiers from text before it can leave the machine. */
public final class SensitiveDataFilter {
  static final String REDACTED = "[redacted]";

  private static final List<Pattern> PATTERNS =
      List.of(
          Pattern.compile("(?i)\\bbearer\\s+[A-Za-z0-9._~+/=-]{12,}"),
          Pattern.compile("\\b(?:sk|pk|rk)-[A-Za-z0-9_-]{16,}"),
          Pattern.compile("\\b(?:ghp|gho|ghs|github_pat)_[A-Za-z0-9_]{16,}"),
          Pattern.compile("\\bAKIA[0-9A-Z]{16}\\b"),
          Pattern.compile("\\beyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}"),
          Pattern.compile(
              "(?i)\\b(?:api[_-]?key|secret|token|password|passwd|pwd)\\b\\s*[:=]\\s*\\S+"),
          Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"),
          Pattern.compile("\\b(?:\\d[ -]?){13,19}\\b"),
          Pattern.compile("(?<!\\d)\\+?\\d{1,3}[ .-]?\\(?\\d{2,4}\\)?[ .-]?\\d{3,4}[ .-]?\\d{4}(?!\\d)"));

  /** Prevents instantiation of this stateless utility. */
  private SensitiveDataFilter() {}

  /** Replaces every recognized secret or personal identifier with a neutral marker. */
  public static String redact(String text) {
    String result = text;
    for (Pattern pattern : PATTERNS) {
      result = pattern.matcher(result).replaceAll(REDACTED);
    }
    return result;
  }
}
