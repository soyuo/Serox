package kr.soyuo.serox.ui;

import java.util.ArrayDeque;
import java.util.List;

/** Keeps the newest distinct search queries for the current application session. */
final class RecentSearchHistory {
  private static final int LIMIT = 5;
  private final ArrayDeque<String> queries = new ArrayDeque<>();

  void add(String query) {
    if (query == null || query.isBlank()) {
      return;
    }
    String normalized = query.strip();
    queries.remove(normalized);
    queries.addFirst(normalized);
    while (queries.size() > LIMIT) {
      queries.removeLast();
    }
  }

  List<String> queries() {
    return List.copyOf(queries);
  }
}
