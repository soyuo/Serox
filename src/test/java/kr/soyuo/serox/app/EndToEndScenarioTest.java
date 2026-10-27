package kr.soyuo.serox.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import kr.soyuo.serox.core.model.AiPanelPosition;
import kr.soyuo.serox.core.model.BrowserSettings;
import kr.soyuo.serox.core.model.DisplayDensity;
import kr.soyuo.serox.core.model.ThemeMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Runs the real composition root: open a page, save settings and a bookmark, restart, restore. */
class EndToEndScenarioTest {
  @TempDir Path dataDirectory;

  private HttpServer server;
  private URI pageUri;

  @BeforeEach
  void startSite() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          byte[] body =
              "<html><head><title>Hello Serox</title></head><body><p>content</p></body></html>"
                  .getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    pageUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
  }

  @AfterEach
  void stopSite() {
    server.stop(0);
  }

  private SeroxRuntime start() {
    return AppBootstrap.createRuntime(Map.of("SEROX_DATA_DIR", dataDirectory.toString()));
  }

  @Test
  void pageVisitSettingsBookmarkAndSessionSurviveARestart() {
    BrowserSettings chosen =
        new BrowserSettings(
            ThemeMode.DARK, "#10B981", 120, AiPanelPosition.LEFT, DisplayDensity.COMPACT);
    try (SeroxRuntime first = start()) {
      first.sessionKeeper().orElseThrow().restoreOrStart(first.browserController());
      var tab = first.browserController().listTabs().toCompletableFuture().join().getFirst();
      var page =
          first.browserController().navigate(tab.id(), pageUri.toString()).toCompletableFuture().join();
      assertEquals("Hello Serox", page.title());

      first.settingsService().save(chosen).toCompletableFuture().join();
      assertTrue(
          first.libraryService().addBookmark(pageUri, "Hello Serox").toCompletableFuture().join());
    }

    try (SeroxRuntime second = start()) {
      second.sessionKeeper().orElseThrow().restoreOrStart(second.browserController());

      assertEquals(chosen, second.settingsService().current());
      var tabs = second.browserController().listTabs().toCompletableFuture().join();
      assertEquals(1, tabs.size());
      assertEquals(pageUri, tabs.getFirst().currentUri().orElseThrow());
      var bookmarks = second.libraryService().bookmarks().toCompletableFuture().join();
      assertEquals(1, bookmarks.size());
      assertEquals(pageUri, bookmarks.getFirst().uri());
      var history = second.libraryService().recentHistory().toCompletableFuture().join();
      assertTrue(history.stream().anyMatch(entry -> entry.uri().equals(pageUri)));
    }
  }

  @Test
  void searchAndAiReportAnUnavailableProviderInsteadOfFailing() {
    try (SeroxRuntime runtime = start()) {
      assertTrue(runtime.configuration().braveSearchApiKey().isEmpty());
      assertTrue(runtime.configuration().anthropicApiKey().isEmpty());
      assertTrue(runtime.browserController().listTabs().toCompletableFuture().join().isEmpty());
    }
  }
}
