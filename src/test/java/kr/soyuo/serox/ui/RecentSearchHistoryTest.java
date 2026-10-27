package kr.soyuo.serox.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class RecentSearchHistoryTest {
  @Test
  void keepsFiveUniqueQueriesWithTheNewestFirst() {
    var history = new RecentSearchHistory();
    history.add("one");
    history.add("two");
    history.add("three");
    history.add("four");
    history.add("five");
    history.add("six");
    history.add("  three  ");

    assertEquals(List.of("three", "six", "five", "four", "two"), history.queries());
  }

  @Test
  void ignoresBlankQueries() {
    var history = new RecentSearchHistory();

    history.add(" ");
    history.add(null);

    assertEquals(List.of(), history.queries());
  }
}
