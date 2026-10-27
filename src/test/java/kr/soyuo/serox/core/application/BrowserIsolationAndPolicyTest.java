package kr.soyuo.serox.core.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import kr.soyuo.serox.browser.api.BrowserEngine;
import kr.soyuo.serox.browser.api.PageSnapshot;
import kr.soyuo.serox.browser.network.HttpRequestDispatcher;
import kr.soyuo.serox.core.event.InProcessBrowserEventBus;
import kr.soyuo.serox.core.model.NavigationStatus;
import org.junit.jupiter.api.Test;

class BrowserIsolationAndPolicyTest {
  @Test
  void failedPageInOneTabDoesNotInterruptAnotherTab() {
    BrowserEngine engine =
        request ->
            request.uri().getHost().equals("broken.test")
                ? CompletableFuture.failedFuture(new IllegalStateException("offline"))
                : CompletableFuture.completedFuture(
                    new PageSnapshot(request.uri(), "Healthy", "healthy", Instant.now()));
    var eventBus = new InProcessBrowserEventBus();
    var controller = new DefaultBrowserController(engine, eventBus, Runnable::run);
    var brokenTab = controller.createTab().toCompletableFuture().join();
    var healthyTab = controller.createTab().toCompletableFuture().join();

    assertThrows(
        CompletionException.class,
        () ->
            controller
                .navigate(brokenTab.id(), "https://broken.test/")
                .toCompletableFuture()
                .join());
    var healthyPage =
        controller.navigate(healthyTab.id(), "https://healthy.test/").toCompletableFuture().join();
    var tabs = controller.listTabs().toCompletableFuture().join();

    assertEquals("Healthy", healthyPage.title());
    assertEquals(NavigationStatus.FAILED, tabs.getFirst().navigationStatus());
    assertEquals(NavigationStatus.SUCCEEDED, tabs.get(1).navigationStatus());
    eventBus.close();
  }

  @Test
  void addressClassifierAllowsOnlyHttpAndHttpsSchemes() {
    var classifier = new NavigationInputClassifier();

    assertEquals(
        "http", ((DirectAddress) classifier.classify("http://example.test")).uri().getScheme());
    assertEquals(
        "https", ((DirectAddress) classifier.classify("https://example.test")).uri().getScheme());
    assertThrows(NavigationInputException.class, () -> classifier.classify("file:///etc/passwd"));
    assertThrows(NavigationInputException.class, () -> classifier.classify("javascript:alert(1)"));
  }

  @Test
  void defaultDispatcherFollowsHttpRedirectsAndPreservesFinalUri() throws Exception {
    try (var server = new ServerSocket(0, 2, InetAddress.getByName("127.0.0.1"))) {
      var served = CompletableFuture.runAsync(() -> serveRedirect(server));
      URI start = URI.create("http://127.0.0.1:" + server.getLocalPort() + "/start");
      var dispatcher =
          HttpRequestDispatcher.createDefault(
              Duration.ofSeconds(2), Duration.ofSeconds(3), 16 * 1024);

      var response =
          dispatcher
              .dispatch(new kr.soyuo.serox.browser.api.PageLoadRequest(start))
              .toCompletableFuture()
              .join();
      served.join();

      assertEquals(200, response.statusCode());
      assertEquals("/final", response.uri().getPath());
      assertTrue(new String(response.body(), StandardCharsets.UTF_8).contains("redirected"));
    }
  }

  private void serveRedirect(ServerSocket server) {
    try (var redirect = server.accept()) {
      readRequest(redirect);
      redirect
          .getOutputStream()
          .write(
              ("HTTP/1.1 302 Found\r\nLocation: /final\r\nContent-Length: 0\r\n"
                      + "Connection: close\r\n\r\n")
                  .getBytes(StandardCharsets.US_ASCII));
    } catch (java.io.IOException exception) {
      throw new java.io.UncheckedIOException(exception);
    }
    try (var destination = server.accept()) {
      readRequest(destination);
      byte[] body = "<html><body>redirected</body></html>".getBytes(StandardCharsets.UTF_8);
      destination
          .getOutputStream()
          .write(
              ("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: "
                      + body.length
                      + "\r\nConnection: close\r\n\r\n")
                  .getBytes(StandardCharsets.US_ASCII));
      destination.getOutputStream().write(body);
    } catch (java.io.IOException exception) {
      throw new java.io.UncheckedIOException(exception);
    }
  }

  private void readRequest(java.net.Socket socket) throws java.io.IOException {
    var reader =
        new BufferedReader(
            new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
    String line;
    while ((line = reader.readLine()) != null && !line.isEmpty()) {}
  }
}
