package kr.soyuo.serox.core.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpHeaders;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import kr.soyuo.serox.browser.html.HtmlDocumentParser;
import kr.soyuo.serox.browser.network.HttpBrowserEngine;
import kr.soyuo.serox.browser.network.HttpResponseData;
import kr.soyuo.serox.browser.network.HttpResponseHandler;
import kr.soyuo.serox.browser.network.HttpTransport;
import kr.soyuo.serox.core.event.InProcessBrowserEventBus;
import kr.soyuo.serox.core.event.PageLoaded;
import org.junit.jupiter.api.Test;

class BrowserNavigationIntegrationTest {
  @Test
  void directPageNavigationParsesAndPublishesDocumentWithoutSearchOrAiProviders() {
    URI finalUri = URI.create("https://example.test/page");
    String html = "<html><head><title>Offline-free</title></head><body><p>Ready</p></body></html>";
    HttpTransport transport =
        request ->
            CompletableFuture.completedFuture(
                new HttpResponseData(
                    finalUri,
                    200,
                    HttpHeaders.of(
                        Map.of("Content-Type", List.of("text/html; charset=UTF-8")),
                        (name, value) -> true),
                    html.getBytes(StandardCharsets.UTF_8)));
    var eventBus = new InProcessBrowserEventBus();
    var engine =
        new HttpBrowserEngine(transport, new HttpResponseHandler(), new HtmlDocumentParser());
    var controller = new DefaultBrowserController(engine, eventBus, Runnable::run);
    var pages = new java.util.ArrayList<PageLoaded>();
    eventBus.subscribe(PageLoaded.class, pages::add);

    var tab = controller.createTab().toCompletableFuture().join();
    var loaded = controller.navigate(tab.id(), finalUri.toString()).toCompletableFuture().join();

    assertEquals(finalUri, loaded.currentUri().orElseThrow());
    assertEquals("Offline-free", loaded.title());
    assertEquals(1, pages.size());
    assertTrue(pages.getFirst().page().document().isPresent());
    assertEquals(
        "Ready",
        pages.getFirst().page().document().orElseThrow().body().orElseThrow().textContent());
    eventBus.close();
  }

  @Test
  void aSearchQueryDoesNotRequireOrInvokeAnExternalSearchProvider() {
    var eventBus = new InProcessBrowserEventBus();
    var engine =
        new HttpBrowserEngine(
            request -> CompletableFuture.failedFuture(new AssertionError("network must not run")),
            new HttpResponseHandler(),
            new HtmlDocumentParser());
    var controller = new DefaultBrowserController(engine, eventBus, Runnable::run);
    var tab = controller.createTab().toCompletableFuture().join();

    CompletionException failure =
        assertThrows(
            CompletionException.class,
            () -> controller.navigate(tab.id(), "a search query").toCompletableFuture().join());
    assertTrue(failure.getCause() instanceof NavigationInputException);
    eventBus.close();
  }
}
