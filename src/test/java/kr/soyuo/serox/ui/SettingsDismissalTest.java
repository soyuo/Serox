package kr.soyuo.serox.ui;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import kr.soyuo.serox.core.application.DefaultBrowserController;
import kr.soyuo.serox.core.application.SettingsService;
import kr.soyuo.serox.core.event.InProcessBrowserEventBus;
import kr.soyuo.serox.search.application.SearchService;
import org.junit.jupiter.api.Test;

class SettingsDismissalTest {
  @Test
  void settingsIconTogglesAndEscapeRestoresTheBrowserView() throws Exception {
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
          var events = new InProcessBrowserEventBus();
          var browser =
              new DefaultBrowserController(
                  request -> CompletableFuture.failedFuture(new AssertionError("Unexpected load")),
                  events,
                  Runnable::run);
          var search = new SearchService(Optional.empty(), events);
          var shell = new BrowserShellView(browser, search);
          var settings = new SettingsService(Optional.empty(), events);
          settings.load();
          shell.setSettingsService(settings);
          try {
            new Scene(shell, 1100, 760);
            shell.applyCss();
            shell.layout();
            Button icon =
                shell.lookupAll(".toolbar-button").stream()
                    .filter(node -> "설정".equals(node.getAccessibleText()))
                    .map(Button.class::cast)
                    .findFirst()
                    .orElseThrow();

            icon.fire();
            assertInstanceOf(SettingsView.class, shell.getCenter());
            icon.fire();
            assertInstanceOf(HomeView.class, shell.getCenter());

            icon.fire();
            assertInstanceOf(SettingsView.class, shell.getCenter());
            shell.fireEvent(
                new KeyEvent(
                    KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false));
            assertInstanceOf(HomeView.class, shell.getCenter());
            checked.complete(null);
          } catch (Throwable failure) {
            checked.completeExceptionally(failure);
          } finally {
            shell.close();
            search.close();
            events.close();
          }
        });
    checked.get(15, TimeUnit.SECONDS);
  }
}
