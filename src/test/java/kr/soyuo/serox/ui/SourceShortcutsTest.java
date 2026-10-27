package kr.soyuo.serox.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javafx.application.Platform;
import kr.soyuo.serox.search.api.SearchResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SourceShortcutsTest {
  private static boolean toolkitReady;

  @BeforeAll
  static void startToolkit() {
    try {
      CompletableFuture<Void> started = new CompletableFuture<>();
      try {
        Platform.startup(() -> started.complete(null));
      } catch (IllegalStateException alreadyRunning) {
        started.complete(null);
      }
      started.get(10, TimeUnit.SECONDS);
      toolkitReady = true;
    } catch (Exception | Error unavailable) {
      toolkitReady = false;
    }
  }

  private static <T> T onFx(Supplier<T> work) throws Exception {
    CompletableFuture<T> result = new CompletableFuture<>();
    Platform.runLater(
        () -> {
          try {
            result.complete(work.get());
          } catch (Throwable failure) {
            result.completeExceptionally(failure);
          }
        });
    return result.get(20, TimeUnit.SECONDS);
  }

  private static SearchResult result(int rank) {
    return new SearchResult(
        "r" + rank, URI.create("https://example.test/" + rank), "제목 " + rank, "요약", rank);
  }

  /** Puts the view in a scene so the scroll pane builds the rows it holds. */
  private static SearchResultsView shown(SearchResultsView view) {
    new javafx.scene.Scene(view, 800, 600);
    view.applyCss();
    view.layout();
    return view;
  }

  private static String openRow(SearchResultsView view) {
    var open = view.lookupAll(".search-result-open");
    assertEquals(1, open.size());
    return open.iterator().next().getAccessibleText();
  }

  @Test
  void nextAndPreviousSourceStopAtTheEnds() throws Exception {
    assumeTrue(toolkitReady);
    onFx(
        () -> {
          var view =
              shown(
                  SearchResultsView.results(
                      "q", List.of(result(1), result(2), result(3)), selected -> {}));
          assertTrue(openRow(view).startsWith("1번"));
          view.selectRelative(-1);
          assertTrue(openRow(view).startsWith("1번"));
          view.selectRelative(1);
          assertTrue(openRow(view).startsWith("2번"));
          view.selectRelative(1);
          view.selectRelative(1);
          assertTrue(openRow(view).startsWith("3번"));
          view.selectRelative(-1);
          assertTrue(openRow(view).startsWith("2번"));
          return null;
        });
  }

  @Test
  void viewsWithoutResultsIgnoreTheSourceAndPanelShortcuts() throws Exception {
    assumeTrue(toolkitReady);
    onFx(
        () -> {
          var view = SearchResultsView.empty("q", () -> {}, selected -> {});
          view.selectRelative(1);
          view.selectRelative(-1);
          view.togglePanel();
          return null;
        });
  }
}
