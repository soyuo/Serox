package kr.soyuo.serox.ui;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Scene;
import kr.soyuo.serox.browser.api.PageSnapshot;
import kr.soyuo.serox.browser.html.HtmlDocumentParser;
import kr.soyuo.serox.core.application.DefaultBrowserController;
import kr.soyuo.serox.core.event.InProcessBrowserEventBus;
import kr.soyuo.serox.search.api.SearchResult;
import kr.soyuo.serox.search.application.SearchService;
import org.junit.jupiter.api.Test;

class OpenInNewTabTest {
  @Test
  void sourceOpenedInNewTabLoadsAndShowsThePage() throws Exception {
    CompletableFuture<Void> started = new CompletableFuture<>();
    try {
      Platform.startup(() -> started.complete(null));
    } catch (IllegalStateException alreadyStarted) {
      started.complete(null);
    }
    started.get(10, TimeUnit.SECONDS);
    var events = new InProcessBrowserEventBus();
    var engineThread = Executors.newSingleThreadExecutor();
    var browser =
        new DefaultBrowserController(
            request ->
                CompletableFuture.supplyAsync(
                    () ->
                        new PageSnapshot(
                            request.uri(),
                            "Page",
                            "text",
                            Instant.now(),
                            Optional.of(
                                new HtmlDocumentParser()
                                    .parse(request.uri(), "<p>hello</p>"))),
                    engineThread),
            events,
            engineThread);
    var search = new SearchService(Optional.empty(), events);
    CompletableFuture<BrowserShellView> made = new CompletableFuture<>();
    Platform.runLater(
        () -> {
          var shell = new BrowserShellView(browser, search);
          new Scene(shell, 1100, 760);
          browser.createTab();
          shell.openInNewTab(
              new SearchResult("S1", URI.create("https://example.test/"), "Example", "", 1));
          made.complete(shell);
        });
    BrowserShellView shell;
    try {
      shell = made.get(10, TimeUnit.SECONDS);
    } catch (Exception failure) {
      throw failure;
    }
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    Object center = null;
    while (System.nanoTime() < deadline) {
      CompletableFuture<Object> seen = new CompletableFuture<>();
      Platform.runLater(() -> seen.complete(shell.getCenter()));
      center = seen.get(5, TimeUnit.SECONDS);
      if (center instanceof kr.soyuo.serox.browser.render.CanvasPageView) {
        break;
      }
      Thread.sleep(50);
    }
    try {
      assertInstanceOf(kr.soyuo.serox.browser.render.CanvasPageView.class, center);
    } finally {
      Platform.runLater(shell::close);
      search.close();
      events.close();
      engineThread.shutdownNow();
    }
  }
}
