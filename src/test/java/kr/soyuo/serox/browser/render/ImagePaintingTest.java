package kr.soyuo.serox.browser.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import kr.soyuo.serox.browser.css.CssStyleEngine;
import kr.soyuo.serox.browser.html.HtmlDocumentParser;
import kr.soyuo.serox.browser.layout.LayoutConstraints;
import kr.soyuo.serox.browser.layout.LayoutEngine;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ImagePaintingTest {
  private static boolean toolkitReady;

  @BeforeAll
  static void startToolkit() {
    try {
      var started = new CompletableFuture<Void>();
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

  @Test
  void paintsDecodedPixelsAndFallsBackWhenImageIsMissing() throws Exception {
    assumeTrue(toolkitReady);
    var done = new CompletableFuture<Void>();
    Platform.runLater(
        () -> {
          try {
            var document =
                new HtmlDocumentParser()
                    .parse(
                        URI.create("https://example.com/"),
                        "<body><img src='photo.png' alt='photo' width='24' height='24'></body>");
            var styles = new CssStyleEngine().compute(document);
            var layout =
                new LayoutEngine().layout(document, styles, new LayoutConstraints(100, 100, 0, 0));
            var display = new DisplayListBuilder().build(layout);
            var command =
                display.commands().stream()
                    .filter(PaintCommand.ImagePlaceholder.class::isInstance)
                    .map(PaintCommand.ImagePlaceholder.class::cast)
                    .findFirst()
                    .orElseThrow();
            assertEquals("photo.png", command.source());
            var image = new WritableImage(2, 2);
            for (int y = 0; y < 2; y++) {
              for (int x = 0; x < 2; x++) {
                image.getPixelWriter().setColor(x, y, Color.BLUE);
              }
            }
            var canvas = new Canvas(100, 100);
            var renderer = new JavaFxCanvasRenderer();
            int x = (int) (command.x() + command.width() / 2);
            int y = (int) (command.y() + command.height() / 2);
            renderer.render(display, canvas, Map.of("photo.png", image));
            assertEquals(Color.BLUE, canvas.snapshot(null, null).getPixelReader().getColor(x, y));
            renderer.render(display, canvas, Map.of());
            assertNotEquals(
                Color.BLUE, canvas.snapshot(null, null).getPixelReader().getColor(x, y));
            done.complete(null);
          } catch (Throwable failure) {
            done.completeExceptionally(failure);
          }
        });
    done.get(20, TimeUnit.SECONDS);
  }
}
