package kr.soyuo.serox.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.image.BufferedImage;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.Test;

class ThemedIconTest {
  @Test
  void darkGreyStrokesTurnLightWhileColorsAndAlphaStay() {
    var image = new BufferedImage(3, 1, BufferedImage.TYPE_INT_ARGB);
    image.setRGB(0, 0, 0xFF5B6577);
    image.setRGB(1, 0, 0xFF0F7B5F);
    image.setRGB(2, 0, 0x00151A23);

    SvgIconView.lightenDarkPixels(image);

    assertEquals(0xFFDDE3EE, image.getRGB(0, 0));
    assertEquals(0xFF0F7B5F, image.getRGB(1, 0));
    assertEquals(0x00, image.getRGB(2, 0) >>> 24);
  }

  @Test
  void anIconSwitchesPictureWhenTheAppRootBecomesDark() throws Exception {
    CompletableFuture<Void> started = new CompletableFuture<>();
    try {
      Platform.startup(() -> started.complete(null));
    } catch (IllegalStateException alreadyRunning) {
      started.complete(null);
    } catch (UnsupportedOperationException noDisplay) {
      assumeTrue(false);
    }
    started.get(10, TimeUnit.SECONDS);
    CompletableFuture<Void> checked = new CompletableFuture<>();
    Platform.runLater(
        () -> {
          try {
            var icon = SvgIconView.icon(UiTokens.ICON_TAB_CLOSE, 12);
            var root = new VBox(icon);
            root.getStyleClass().add("app-root");
            new Scene(root, 100, 100);
            var lightImage = icon.getImage();

            root.getStyleClass().add("theme-dark");
            var darkImage = icon.getImage();
            assertNotSame(lightImage, darkImage);

            root.getStyleClass().remove("theme-dark");
            assertSame(lightImage, icon.getImage());
            checked.complete(null);
          } catch (Throwable failure) {
            checked.completeExceptionally(failure);
          }
        });
    checked.get(15, TimeUnit.SECONDS);
  }
}
