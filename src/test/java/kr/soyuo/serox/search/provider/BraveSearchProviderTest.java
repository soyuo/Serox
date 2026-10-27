package kr.soyuo.serox.search.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import kr.soyuo.serox.search.api.SearchRequest;
import org.junit.jupiter.api.Test;

class BraveSearchProviderTest {
  @Test
  void sendsCredentialInHeaderAndNormalizesAndDeduplicatesWebResults() throws Exception {
    String body =
        "{\"web\":{\"results\":["
            + "{\"title\":\"First\",\"url\":\"https://example.test/page/\",\"description\":\"Snippet\"},"
            + "{\"title\":\"Duplicate\",\"url\":\"https://EXAMPLE.test/page#section\",\"description\":\"Duplicate\"},"
            + "{\"title\":\"Unsafe\",\"url\":\"javascript:alert(1)\"},"
            + "{\"title\":\"Second\",\"url\":\"https://other.test/\",\"description\":null}]}}";
    try (var server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
      var requestLine = new java.util.concurrent.atomic.AtomicReference<String>();
      var authHeader = new java.util.concurrent.atomic.AtomicReference<String>();
      CompletableFuture<Void> served =
          CompletableFuture.runAsync(() -> serve(server, body, requestLine, authHeader));
      URI endpoint = URI.create("http://127.0.0.1:" + server.getLocalPort() + "/res/v1/web/search");
      var provider =
          new BraveSearchProvider(
              HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
              endpoint,
              "secret-test-key",
              Duration.ofSeconds(3));

      var response =
          provider.search(new SearchRequest("java threads", 10)).toCompletableFuture().join();
      served.join();

      assertEquals("brave", response.providerId());
      assertEquals(2, response.results().size());
      assertEquals("First", response.results().getFirst().title());
      assertEquals("Snippet", response.results().getFirst().snippet());
      assertEquals(1, response.results().getFirst().rank());
      assertEquals("secret-test-key", authHeader.get());
      assertFalse(requestLine.get().contains("secret-test-key"));
      assertEquals(true, requestLine.get().contains("q=java+threads"));
      assertEquals(true, requestLine.get().contains("country=KR"));
      assertEquals(true, requestLine.get().contains("search_lang=ko"));
    }
  }

  @Test
  void mapsAuthenticationAndRateLimitResponsesToSafeMessages() throws Exception {
    assertProviderFailure(401, "API key");
    assertProviderFailure(429, "rate limit");
  }

  @Test
  void rejectsQueriesOutsideTheProviderContract() {
    assertThrows(IllegalArgumentException.class, () -> new SearchRequest(" ", 10));
    assertThrows(IllegalArgumentException.class, () -> new SearchRequest("x".repeat(601), 10));
    assertThrows(
        IllegalArgumentException.class,
        () -> new SearchRequest(String.join(" ", java.util.Collections.nCopies(76, "word")), 10));
  }

  private void assertProviderFailure(int status, String messagePart) throws Exception {
    try (var server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
      CompletableFuture<Void> served =
          CompletableFuture.runAsync(() -> serveStatus(server, status));
      var provider =
          new BraveSearchProvider(
              HttpClient.newHttpClient(),
              URI.create("http://127.0.0.1:" + server.getLocalPort() + "/search"),
              "secret-test-key",
              Duration.ofSeconds(3));

      CompletionException failure =
          assertThrows(
              CompletionException.class,
              () -> provider.search(new SearchRequest("query", 5)).toCompletableFuture().join());
      served.join();
      assertEquals(true, failure.getCause() instanceof SearchProviderException);
      assertEquals(
          true, failure.getCause().getMessage().toLowerCase().contains(messagePart.toLowerCase()));
    }
  }

  private void serve(
      ServerSocket server,
      String body,
      java.util.concurrent.atomic.AtomicReference<String> requestLine,
      java.util.concurrent.atomic.AtomicReference<String> authHeader) {
    try (var socket = server.accept()) {
      var reader = readHeaders(socket);
      requestLine.set(reader[0]);
      authHeader.set(reader[1]);
      writeResponse(socket, 200, body);
    } catch (java.io.IOException exception) {
      throw new java.io.UncheckedIOException(exception);
    }
  }

  private void serveStatus(ServerSocket server, int status) {
    try (var socket = server.accept()) {
      readHeaders(socket);
      writeResponse(socket, status, "{\"token\":\"secret-test-key\"}");
    } catch (java.io.IOException exception) {
      throw new java.io.UncheckedIOException(exception);
    }
  }

  private String[] readHeaders(java.net.Socket socket) throws java.io.IOException {
    var reader =
        new BufferedReader(
            new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
    String requestLine = reader.readLine();
    String apiKey = null;
    String line;
    while ((line = reader.readLine()) != null && !line.isEmpty()) {
      if (line.regionMatches(true, 0, "X-Subscription-Token:", 0, 21)) {
        apiKey = line.substring(line.indexOf(':') + 1).trim();
      }
    }
    return new String[] {requestLine, apiKey};
  }

  private void writeResponse(java.net.Socket socket, int status, String body)
      throws java.io.IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    String reason = status == 200 ? "OK" : status == 401 ? "Unauthorized" : "Too Many Requests";
    socket
        .getOutputStream()
        .write(
            ("HTTP/1.1 "
                    + status
                    + " "
                    + reason
                    + "\r\nContent-Type: application/json\r\nContent-Length: "
                    + bytes.length
                    + "\r\nConnection: close\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII));
    socket.getOutputStream().write(bytes);
  }
}
