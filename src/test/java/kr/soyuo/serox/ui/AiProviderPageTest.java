package kr.soyuo.serox.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.BorderPane;
import javax.imageio.ImageIO;
import kr.soyuo.serox.ai.application.AiConnectionService;
import kr.soyuo.serox.ai.application.AiSearchService;
import kr.soyuo.serox.ai.context.ContextBuilder;
import kr.soyuo.serox.browser.html.HtmlDocumentParser;
import kr.soyuo.serox.browser.network.HttpResponseHandler;
import kr.soyuo.serox.core.application.AppConfiguration;
import kr.soyuo.serox.core.event.InProcessBrowserEventBus;
import kr.soyuo.serox.search.fetch.FetchLimits;
import kr.soyuo.serox.search.fetch.PageFetcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AiProviderPageTest {
  @TempDir Path directory;

  @Test
  void providerCardsAndBrowserConnectionFitLargeAndSmallWindows() throws Exception {
    CompletableFuture<Void> started = new CompletableFuture<>();
    try {
      Platform.startup(() -> started.complete(null));
    } catch (IllegalStateException alreadyStarted) {
      started.complete(null);
    }
    started.get(10, TimeUnit.SECONDS);
    var events = new InProcessBrowserEventBus();
    var fetcher =
        new PageFetcher(
            request ->
                CompletableFuture.failedFuture(new AssertionError("No page requests expected")),
            new HttpResponseHandler(),
            new HtmlDocumentParser(),
            FetchLimits.defaults());
    try (var ai =
            new AiSearchService(
                Optional.empty(), fetcher, new ContextBuilder(), events, Clock.systemUTC());
        var connections =
            new AiConnectionService(
                new AppConfiguration(Optional.empty(), Optional.empty()), directory, ai, events)) {
      CompletableFuture<Void> checked = new CompletableFuture<>();
      Platform.runLater(
          () -> {
            try {
              AiProviderPage page =
                  new AiProviderPage(
                      connections,
                      uri -> {
                        throw new AssertionError("A render must not start login");
                      });
              BorderPane root = new BorderPane(page);
              root.getStyleClass().add("app-root");
              AppearanceStyler.apply(root, kr.soyuo.serox.core.model.BrowserSettings.defaults());
              Scene scene = new Scene(root, 1180, 803);
              javafx.stage.Stage stage = new javafx.stage.Stage();
              Platform.setImplicitExit(false);
              stage.setX(-10000);
              stage.setY(-10000);
              stage.setScene(scene);
              stage.show();
              scene
                  .getStylesheets()
                  .add(BrowserShellView.class.getResource("serox.css").toExternalForm());
              root.applyCss();
              root.layout();
              assertTrue(page.lookupAll(".provider-card").size() == 5);
              Path screenshots = Path.of("target", "review");
              Files.createDirectories(screenshots);
              ImageIO.write(
                  SwingFXUtils.fromFXImage(root.snapshot(null, null), null),
                  "png",
                  screenshots.resolve("ai-providers-claude.png").toFile());
              page.lookupAll(".provider-card").stream()
                  .filter(
                      node ->
                          node instanceof ToggleButton toggle
                              && toggle.getAccessibleText().equals("Codex 제공자 선택"))
                  .map(ToggleButton.class::cast)
                  .findFirst()
                  .orElseThrow()
                  .fire();
              root.applyCss();
              root.layout();
              assertTrue(
                  page.lookupAll(".button").stream()
                      .anyMatch(
                          node ->
                              node instanceof Button button
                                  && button.getText().equals("Continue with ChatGPT")));
              new javafx.animation.AnimationTimer() {
                private int frames;

                @Override
                public void handle(long now) {
                  if (++frames < 3) return;
                  stop();
                  root.snapshot(
                      result -> {
                        try {
                          ImageIO.write(
                              SwingFXUtils.fromFXImage(result.getImage(), null),
                              "png",
                              screenshots.resolve("ai-providers-chatgpt.png").toFile());
                          root.resize(500, 600);
                          root.layout();
                          assertTrue(
                              page.minWidth(-1) <= 500,
                              "Provider page must fit next to the settings sidebar");
                          assertTrue(
                              page.getContent().getLayoutBounds().getWidth() <= 500,
                              "Provider content must wrap at small widths");
                          checked.complete(null);
                        } catch (Throwable failure) {
                          checked.completeExceptionally(failure);
                        } finally {
                          stage.close();
                        }
                        return null;
                      },
                      null,
                      null);
                }
              }.start();
            } catch (Throwable failure) {
              checked.completeExceptionally(failure);
            }
          });
      checked.get(20, TimeUnit.SECONDS);
    }
    events.close();
  }
}
