package kr.soyuo.serox.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.util.List;
import kr.soyuo.serox.ai.api.EvidenceDocument;
import kr.soyuo.serox.search.api.SearchResult;
import org.junit.jupiter.api.Test;

class UnreadSourcesTest {
  private static SearchResult result(int n) {
    return new SearchResult("S" + n, URI.create("https://e" + n + ".test/"), "t" + n, "", n);
  }

  @Test
  void listsTriedResultsWithoutText() {
    List<SearchResult> found = List.of(result(1), result(2), result(3), result(4));
    EvidenceDocument read =
        new EvidenceDocument("S1", URI.create("https://e1.test"), "t1", "body", java.time.Instant.EPOCH);
    assertEquals(
        List.of(result(2), result(3)), SearchResultsView.unreadSources(found, List.of(read), 3));
  }
}
