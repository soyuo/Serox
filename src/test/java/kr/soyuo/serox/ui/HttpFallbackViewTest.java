package kr.soyuo.serox.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import javafx.scene.control.Button;
import org.junit.jupiter.api.Test;

class HttpFallbackViewTest {
  @Test
  void httpIsUsedOnlyWhenTheUserChoosesIt() throws Exception {
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
            var http = new AtomicInteger();
            var cancel = new AtomicInteger();
            var view = new HttpFallbackView("example.com", http::incrementAndGet, cancel::incrementAndGet);
            assertEquals(0, http.get());
            button(view, "취소").fire();
            assertEquals(1, cancel.get());
            assertEquals(0, http.get());
            button(view, "http로 연결").fire();
            assertEquals(1, http.get());
            assertTrue(view.lookupAll(".label").stream().anyMatch(n -> n instanceof javafx.scene.control.Label l && l.getText().equals("example.com")));
            assertFalse(view.getAccessibleText().isBlank());
            checked.complete(null);
          } catch (Throwable failure) {
            checked.completeExceptionally(failure);
          }
        });
    checked.get(15, TimeUnit.SECONDS);
  }

  private static Button button(HttpFallbackView view, String text) {
    return view.lookupAll(".button").stream()
        .filter(node -> node instanceof Button b && text.equals(b.getText()))
        .map(Button.class::cast)
        .findFirst()
        .orElseThrow();
  }
}
