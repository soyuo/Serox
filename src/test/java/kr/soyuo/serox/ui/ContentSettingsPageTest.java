package kr.soyuo.serox.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.BorderPane;
import kr.soyuo.serox.core.model.BrowserSettings;
import org.junit.jupiter.api.Test;

class ContentSettingsPageTest {
  @Test
  void controlsApplyPreferencesAndWrapWithoutStartingRequests() throws Exception {
    CompletableFuture<Void> started = new CompletableFuture<>();
    try {
      Platform.startup(() -> started.complete(null));
    } catch (IllegalStateException alreadyStarted) {
      started.complete(null);
    }
    started.get(10, TimeUnit.SECONDS);
    CompletableFuture<Void> checked = new CompletableFuture<>();
    Platform.runLater(
        () -> {
          try {
            MemorySettings settings = new MemorySettings();
            SearchPage search = new SearchPage(settings, SearchActions.none());
            BorderPane root = new BorderPane(search);
            root.getStyleClass().add("app-root");
            AppearanceStyler.apply(root, BrowserSettings.defaults());
            Scene scene = new Scene(root, 1180, 803);
            scene
                .getStylesheets()
                .add(BrowserShellView.class.getResource("serox.css").toExternalForm());
            root.applyCss();
            root.layout();
            button(search, "+").fire();
            assertEquals(6, settings.value.search().evidencePages());
            search.lookupAll(".toggle-button").stream()
                .filter(n -> n instanceof ToggleButton b && b.getText().equals("엄격"))
                .map(ToggleButton.class::cast)
                .findFirst()
                .orElseThrow()
                .fire();
            assertEquals("strict", settings.value.search().safeSearch());
            SummaryPage summary = new SummaryPage(settings);
            root.setCenter(summary);
            root.applyCss();
            root.layout();
            TextArea instructions = (TextArea) summary.lookup(".text-area");
            instructions.setText("Keep concise.");
            assertNotEquals("Keep concise.", settings.value.summary().instructions());
            button(summary, "지침 저장").fire();
            assertEquals("Keep concise.", settings.value.summary().instructions());
            instructions.appendText("x".repeat(2001));
            assertEquals("Keep concise.", instructions.getText());
            for (ScrollPane page : List.of(search, summary)) {
              root.setCenter(page);
              root.resize(500, 600);
              root.applyCss();
              root.layout();
              assertTrue(page.minWidth(-1) <= 500);
              assertTrue(page.getContent().getLayoutBounds().getWidth() <= 500);
            }
            AiPanelView panel = new AiPanelView(source -> {});
            panel.setPresentation(false, false);
            panel.show(
                new kr.soyuo.serox.core.event.AiAnswerChanged(
                    java.time.Instant.now(),
                    kr.soyuo.serox.core.model.TabId.create(),
                    1,
                    kr.soyuo.serox.core.event.AiAnswerChanged.Phase.COMPLETED,
                    "",
                    java.util.Optional.of(
                        new kr.soyuo.serox.ai.citation.CitedAnswer(
                            "- Preserved answer", List.of(), List.of())),
                    List.of(),
                    ""));
            root.setCenter(panel);
            root.applyCss();
            root.layout();
            assertTrue(
                panel.lookupAll(".ai-panel-flow").stream()
                    .filter(javafx.scene.text.TextFlow.class::isInstance)
                    .map(javafx.scene.text.TextFlow.class::cast)
                    .flatMap(flow -> flow.getChildren().stream())
                    .anyMatch(
                        node ->
                            node instanceof javafx.scene.text.Text text
                                && text.getText().contains("Preserved answer")));
            checked.complete(null);
          } catch (Throwable failure) {
            checked.completeExceptionally(failure);
          }
        });
    checked.get(15, TimeUnit.SECONDS);
  }

  private static Button button(ScrollPane page, String text) {
    return page.getContent().lookupAll(".button").stream()
        .filter(n -> n instanceof Button b && b.getText().equals(text))
        .map(Button.class::cast)
        .findFirst()
        .orElseThrow();
  }

  private static final class MemorySettings implements PrivacyActions {
    BrowserSettings value = BrowserSettings.defaults();

    public boolean available() {
      return true;
    }

    public CompletionStage<List<String>> recentQueries() {
      return CompletableFuture.completedFuture(List.of());
    }

    public CompletionStage<Void> deleteQuery(String query) {
      return clearQueries();
    }

    public CompletionStage<Void> clearQueries() {
      return CompletableFuture.completedFuture(null);
    }

    public CompletionStage<Void> clearVisits() {
      return clearQueries();
    }

    public BrowserSettings settings() {
      return value;
    }

    public CompletionStage<Void> save(BrowserSettings value) {
      this.value = value;
      return clearQueries();
    }
  }
}
