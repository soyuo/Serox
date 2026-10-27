package kr.soyuo.serox.mcp.tool;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import kr.soyuo.serox.core.model.UntrustedText;

/** The last few tool calls an agent made, newest first. Held in memory only, never stored. */
public final class McpCallLog {
  /** How many calls are kept. */
  public static final int CAPACITY = 20;

  private static final int SUMMARY_LENGTH = 80;

  /** One finished call; {@code summary} is the cleaned query text, bounded in length. */
  public record Entry(String tool, String summary, String client, Instant at, boolean success) {}

  private final Deque<Entry> entries = new ArrayDeque<>();

  /** Adds a finished call; the summary is cleaned of markup and control characters. */
  public synchronized void record(
      String tool, String rawSummary, String client, Instant at, boolean success) {
    Objects.requireNonNull(tool, "tool");
    Objects.requireNonNull(at, "at");
    entries.addFirst(
        new Entry(
            tool,
            UntrustedText.fromMarkup(rawSummary == null ? "" : rawSummary, SUMMARY_LENGTH),
            UntrustedText.plain(client == null ? "" : client, 40),
            at,
            success));
    while (entries.size() > CAPACITY) {
      entries.removeLast();
    }
  }

  /** The kept calls, newest first. */
  public synchronized List<Entry> recent() {
    return List.copyOf(entries);
  }
}
