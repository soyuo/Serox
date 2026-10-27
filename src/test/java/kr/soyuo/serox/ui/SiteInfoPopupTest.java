package kr.soyuo.serox.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import kr.soyuo.serox.core.application.SitePermissionService;
import kr.soyuo.serox.core.model.PermissionChoice;
import kr.soyuo.serox.core.model.SiteInfo;
import kr.soyuo.serox.core.model.SitePermission;
import org.junit.jupiter.api.Test;

class SiteInfoPopupTest {
  private static void onFx(Runnable work) throws Exception {
    CompletableFuture<Void> started = new CompletableFuture<>();
    try {
      Platform.startup(() -> started.complete(null));
    } catch (IllegalStateException alreadyRunning) {
      started.complete(null);
    } catch (UnsupportedOperationException noDisplay) {
      assumeTrue(false);
    }
    started.get(10, TimeUnit.SECONDS);
    CompletableFuture<Void> done = new CompletableFuture<>();
    Platform.runLater(
        () -> {
          try {
            work.run();
            done.complete(null);
          } catch (Throwable failure) {
            done.completeExceptionally(failure);
          }
        });
    done.get(15, TimeUnit.SECONDS);
  }

  private static VBox root(SiteInfoPopup popup) throws ReflectiveOperationException {
    var field = SiteInfoPopup.class.getDeclaredField("root");
    field.setAccessible(true);
    return (VBox) field.get(popup);
  }

  private static Node row(VBox root, String accessibleText) {
    return root.getChildren().stream()
        .filter(node -> accessibleText.equals(node.getAccessibleText()))
        .findFirst()
        .orElseThrow();
  }

  private static void click(Node node) {
    node.getOnMouseClicked()
        .handle(
            new MouseEvent(
                MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, null, 1, false, false, false, false, true,
                false, false, true, false, false, null));
  }

  @Test
  void blockingJavascriptForTheSiteIsSavedAndReloadsThePage() throws Exception {
    var service = new SitePermissionService(Optional.empty());
    var reloads = new AtomicInteger();
    onFx(
        () -> {
          try {
            var info = SiteInfo.of(Optional.of(URI.create("https://example.test/")));
            var popup = new SiteInfoPopup(info, service, reloads::incrementAndGet, false);
            var root = root(popup);
            click(row(root, "사이트 설정"));
            click(row(root, "JavaScript"));
            var radios =
                root.lookupAll(".radio-button").stream().map(RadioButton.class::cast).toList();
            assertEquals(2, radios.size());
            assertTrue(radios.get(0).isSelected());
            radios.get(1).fire();

            assertEquals(
                PermissionChoice.BLOCK, service.choice("example.test", SitePermission.JAVASCRIPT));
            assertEquals(1, reloads.get());
          } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
          }
        });
  }

  @Test
  void featuresSeroxLacksAreShownAsAlwaysBlockedAndCannotBeChanged() throws Exception {
    var service = new SitePermissionService(Optional.empty());
    onFx(
        () -> {
          try {
            var info = SiteInfo.of(Optional.of(URI.create("https://example.test/")));
            var popup = new SiteInfoPopup(info, service, () -> {}, false);
            var root = root(popup);
            click(row(root, "사이트 설정"));
            click(row(root, "카메라"));
            var radios =
                root.lookupAll(".radio-button").stream().map(RadioButton.class::cast).toList();

            assertTrue(radios.stream().allMatch(RadioButton::isDisabled));
            assertTrue(radios.get(1).isSelected());
            assertTrue(
                root.lookupAll(".label").stream()
                    .anyMatch(
                        node ->
                            node instanceof Label label
                                && label.getText().contains("항상 차단")));
          } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
          }
        });
  }

  @Test
  void aSeroxScreenHasNoSiteSettings() throws Exception {
    var service = new SitePermissionService(Optional.empty());
    onFx(
        () -> {
          try {
            var popup =
                new SiteInfoPopup(SiteInfo.of(Optional.empty()), service, () -> {}, false);
            var root = root(popup);

            assertFalse(
                root.getChildren().stream().anyMatch(node -> "사이트 설정".equals(node.getAccessibleText())));
          } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
          }
        });
  }
}
