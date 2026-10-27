package kr.soyuo.serox.ai.citation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.soyuo.serox.ai.api.EvidenceDocument;

/** Resolves [S1]-style markers in an answer against the evidence that was actually supplied. */
public final class CitationManager {
  private static final Pattern MARKER = Pattern.compile("\\[(S\\d{1,3})]");
  private static final int MAX_REFERENCE_CHARS = 240;

  /** Prevents instantiation of this stateless utility. */
  private CitationManager() {}

  /**
   * Keeps markers that name a supplied document with an http(s) address, removes the rest from the
   * text, and records the sentence around the first use of each valid source as its referenced
   * text. A URL appearing in the answer is never treated as a citation by itself.
   */
  public static CitedAnswer resolve(String answer, List<EvidenceDocument> evidence) {
    Objects.requireNonNull(answer, "answer");
    Map<String, EvidenceDocument> byId = new LinkedHashMap<>();
    for (EvidenceDocument document : evidence) {
      if (isWebAddress(document)) {
        byId.putIfAbsent(document.sourceId(), document);
      }
    }
    Map<String, Citation> citations = new LinkedHashMap<>();
    Set<String> invalid = new LinkedHashSet<>();
    StringBuilder cleaned = new StringBuilder();
    Matcher matcher = MARKER.matcher(answer);
    int last = 0;
    while (matcher.find()) {
      String id = matcher.group(1);
      cleaned.append(answer, last, matcher.start());
      last = matcher.end();
      EvidenceDocument document = byId.get(id);
      if (document == null) {
        invalid.add(id);
        continue;
      }
      cleaned.append(matcher.group());
      citations.computeIfAbsent(
          id,
          key ->
              new Citation(
                  key,
                  document.uri(),
                  document.title(),
                  sentenceBefore(answer, matcher.start()),
                  document.retrievedAt()));
    }
    cleaned.append(answer, last, answer.length());
    return new CitedAnswer(
        cleaned.toString().replaceAll("[ \\t]{2,}", " ").strip(),
        new ArrayList<>(citations.values()),
        List.copyOf(invalid));
  }

  private static boolean isWebAddress(EvidenceDocument document) {
    String scheme = document.uri().getScheme();
    return document.uri().getHost() != null
        && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
  }

  private static String sentenceBefore(String answer, int markerStart) {
    int start = 0;
    for (int index = markerStart - 1; index >= 0; index--) {
      char c = answer.charAt(index);
      if (c == '.' || c == '!' || c == '?' || c == '\n') {
        start = index + 1;
        break;
      }
    }
    String sentence = MARKER.matcher(answer.substring(start, markerStart)).replaceAll("").strip();
    if (sentence.length() > MAX_REFERENCE_CHARS) {
      sentence = sentence.substring(sentence.length() - MAX_REFERENCE_CHARS);
    }
    return sentence;
  }
}
