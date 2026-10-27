package kr.soyuo.serox.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import kr.soyuo.serox.core.application.LibraryService;
import kr.soyuo.serox.core.model.BrowserSettings;
import kr.soyuo.serox.search.api.SearchResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Builds the main views at the minimum window size with long text and checks they still fit. */
class UiLayoutSmokeTest {
  private static final String LONG_TEXT = "아주 긴 문자열 ".repeat(80);
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

  /** Lays the view out at the minimum window size and returns its minimum width. */
  private static double minimumWidthAfterLayout(Region view) {
    javafx.scene.layout.BorderPane root = new javafx.scene.layout.BorderPane(view);
    root.getStyleClass().add("app-root");
    Scene scene = new Scene(root, UiTokens.WINDOW_MIN_WIDTH, UiTokens.WINDOW_MIN_HEIGHT);
    scene.getStylesheets().add(BrowserShellView.class.getResource("serox.css").toExternalForm());
    root.applyCss();
    root.layout();
    return view.minWidth(-1);
  }

  private static SearchResult longResult() {
    return new SearchResult(
        "r1", URI.create("https://example.test/" + "path/".repeat(60)), LONG_TEXT, LONG_TEXT, 1);
  }

  @Test
  void searchStatesFitTheMinimumWindow() throws Exception {
    assumeTrue(toolkitReady, "JavaFX toolkit is not available");
    double widest =
        onFx(
            () -> {
              double max = 0;
              for (Region view :
                  List.<Region>of(
                      SearchResultsView.loading(LONG_TEXT, () -> {}, r -> {}),
                      SearchResultsView.empty(LONG_TEXT, () -> {}, r -> {}),
                      SearchResultsView.cancelled(LONG_TEXT, () -> {}, r -> {}),
                      SearchResultsView.error(LONG_TEXT, LONG_TEXT, () -> {}, r -> {}),
                      SearchResultsView.results(LONG_TEXT, List.of(longResult()), r -> {}))) {
                double w = minimumWidthAfterLayout(view);
                max = Math.max(max, w);
              }
              return max;
            });
    assertTrue(
        widest <= UiTokens.WINDOW_MIN_WIDTH,
        "search views need " + widest + "px but the minimum window is narrower");
  }

  @Test
  void previewKeepsUsableWidthNextToListAndAiPanel() throws Exception {
    assumeTrue(toolkitReady, "JavaFX toolkit is not available");
    double previewWidth =
        onFx(
            () -> {
              var view = SearchResultsView.results("q", List.of(longResult()), r -> {});
              minimumWidthAfterLayout(view);
              return view.lookup(".search-preview").getLayoutBounds().getWidth();
            });
    assertTrue(previewWidth >= 150, "preview is only " + previewWidth + "px wide");
  }

  @Test
  void settingsAndLibraryFitTheMinimumWindow() throws Exception {
    assumeTrue(toolkitReady, "JavaFX toolkit is not available");
    double widest =
        onFx(
            () -> {
              var settings =
                  new SettingsView(BrowserSettings.defaults(), s -> {}, s -> {}, () -> {});
              var library =
                  new LibraryView(
                      new LibraryService(
                          Optional.empty(), Optional.empty(), java.time.Clock.systemUTC()),
                      LibraryView.Section.HISTORY,
                      uri -> {},
                      () -> {});
              return Math.max(minimumWidthAfterLayout(settings), minimumWidthAfterLayout(library));
            });
    assertTrue(widest <= UiTokens.WINDOW_MIN_WIDTH, "needs " + widest + "px");
  }
}
