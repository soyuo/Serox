package kr.soyuo.serox.mcp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import kr.soyuo.serox.core.event.ConnectionStatusChanged;
import kr.soyuo.serox.core.event.InProcessBrowserEventBus;
import kr.soyuo.serox.core.event.ServiceStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class HttpMcpServerTest {
  private static final String INITIALIZE =
      "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}";

  private final HttpClient client = HttpClient.newHttpClient();
  private HttpMcpServer server;

  @AfterEach
  void stopServer() {
    if (server != null) {
      server.stop();
    }
  }

  private static McpEndpoint endpoint() {
    McpEndpoint endpoint = new McpEndpoint("test");
    endpoint.register(
        "echo",
        (params, call) -> {
          JsonObject result = new JsonObject();
          result.add("echo", params);
          return CompletableFuture.completedFuture(result);
        });
    return endpoint;
  }

  private HttpMcpServer start(boolean network, Optional<String> token) throws Exception {
    server = new HttpMcpServer(HttpMcpServerTest::endpoint, 0, network, token, Optional.empty());
    server.start().toCompletableFuture().get();
    return server;
  }

  private URI uri() {
    return URI.create("http://127.0.0.1:" + server.port() + HttpMcpServer.PATH);
  }

  private HttpRequest.Builder post(String body) {
    return HttpRequest.newBuilder(uri())
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body));
  }

  private HttpResponse<String> send(HttpRequest request) throws Exception {
    return client.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private String openSession() throws Exception {
    HttpResponse<String> response = send(post(INITIALIZE).build());
    assertEquals(200, response.statusCode());
    String session = response.headers().firstValue(HttpMcpServer.SESSION_HEADER).orElseThrow();
    HttpResponse<String> initialized =
        send(
            post("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}")
                .header(HttpMcpServer.SESSION_HEADER, session)
                .build());
    assertEquals(202, initialized.statusCode());
    return session;
  }

  @Test
  void aClientInitializesAndCallsAMethodOverHttp() throws Exception {
    start(false, Optional.empty());
    HttpResponse<String> init = send(post(INITIALIZE).build());
    JsonObject result = JsonParser.parseString(init.body()).getAsJsonObject().getAsJsonObject("result");
    assertEquals("serox", result.getAsJsonObject("serverInfo").get("name").getAsString());

    String session = openSession();
    HttpResponse<String> echoed =
        send(
            post("{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"echo\",\"params\":{\"a\":1}}")
                .header(HttpMcpServer.SESSION_HEADER, session)
                .build());

    assertEquals(200, echoed.statusCode());
    assertTrue(echoed.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
    JsonObject body = JsonParser.parseString(echoed.body()).getAsJsonObject();
    assertEquals(7, body.get("id").getAsInt());
    assertEquals(1, body.getAsJsonObject("result").getAsJsonObject("echo").get("a").getAsInt());
  }

  @Test
  void sessionsAreSeparateAndMustExist() throws Exception {
    start(false, Optional.empty());

    assertEquals(
        400,
        send(post("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"echo\"}").build()).statusCode());
    assertEquals(
        404,
        send(
                post("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"echo\"}")
                    .header(HttpMcpServer.SESSION_HEADER, "nope")
                    .build())
            .statusCode());

    String first = openSession();
    HttpRequest end =
        HttpRequest.newBuilder(uri())
            .header(HttpMcpServer.SESSION_HEADER, first)
            .DELETE()
            .build();
    assertEquals(204, send(end).statusCode());
    assertEquals(
        404,
        send(
                post("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"ping\"}")
                    .header(HttpMcpServer.SESSION_HEADER, first)
                    .build())
            .statusCode());
  }

  @Test
  void aSessionIsRefusedMethodsBeforeItInitializes() throws Exception {
    start(false, Optional.empty());
    HttpResponse<String> response = send(post(INITIALIZE).build());
    String session = response.headers().firstValue(HttpMcpServer.SESSION_HEADER).orElseThrow();

    HttpResponse<String> early =
        send(
            post("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"echo\",\"params\":{}}")
                .header(HttpMcpServer.SESSION_HEADER, session)
                .build());

    assertTrue(early.body().contains("not initialized"));
  }

  @Test
  void aBrowserRequestWithAnOriginIsRefused() throws Exception {
    start(false, Optional.empty());

    HttpResponse<String> response =
        send(post(INITIALIZE).header("Origin", "https://evil.example").build());

    assertEquals(403, response.statusCode());
  }

  @Test
  void aHostThatIsNotThisMachineIsRefusedInLoopbackMode() throws Exception {
    start(false, Optional.empty());

    assertEquals(403, rawStatus("evil.example:" + server.port()));
    assertEquals(200, rawStatus("localhost:" + server.port()));
    assertEquals(200, rawStatus("127.0.0.1:" + server.port()));
    assertTrue(HttpMcpServer.isLoopbackHost("[::1]:3845"));
    assertFalse(HttpMcpServer.isLoopbackHost("[::1"));
    assertFalse(HttpMcpServer.isLoopbackHost(null));
    assertFalse(HttpMcpServer.isLoopbackHost("localhost.evil.example"));
  }

  /** Sends a request with a chosen Host header, which the HTTP client does not allow. */
  private int rawStatus(String host) throws IOException {
    try (Socket socket = new Socket("127.0.0.1", server.port())) {
      byte[] body = INITIALIZE.getBytes(StandardCharsets.UTF_8);
      String head =
          "POST /mcp HTTP/1.1\r\nHost: "
              + host
              + "\r\nContent-Type: application/json\r\nConnection: close\r\nContent-Length: "
              + body.length
              + "\r\n\r\n";
      socket.getOutputStream().write(head.getBytes(StandardCharsets.UTF_8));
      socket.getOutputStream().write(body);
      socket.getOutputStream().flush();
      String reply = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      return Integer.parseInt(reply.split(" ", 3)[1]);
    }
  }

  @Test
  void oddRequestsAreRefusedWithTheRightStatus() throws Exception {
    start(false, Optional.empty());

    assertEquals(
        405, send(HttpRequest.newBuilder(uri()).GET().build()).statusCode());
    assertEquals(
        415,
        send(
                HttpRequest.newBuilder(uri())
                    .header("Content-Type", "text/plain")
                    .POST(HttpRequest.BodyPublishers.ofString(INITIALIZE))
                    .build())
            .statusCode());
    assertEquals(413, send(post("x".repeat((1 << 20) + 1)).build()).statusCode());
  }

  @Test
  void networkModeNeedsTheBearerToken() throws Exception {
    start(true, Optional.of("s3cret-token"));

    assertEquals(401, send(post(INITIALIZE).build()).statusCode());
    assertEquals(
        401, send(post(INITIALIZE).header("Authorization", "Bearer wrong").build()).statusCode());
    assertEquals(
        200,
        send(post(INITIALIZE).header("Authorization", "Bearer s3cret-token").build()).statusCode());
  }

  @Test
  void networkModeWithoutATokenDoesNotStart() {
    var bus = new InProcessBrowserEventBus();
    List<ConnectionStatusChanged> events = new ArrayList<>();
    bus.subscribe(ConnectionStatusChanged.class, events::add);
    server =
        new HttpMcpServer(
            HttpMcpServerTest::endpoint, 0, true, Optional.of("  "), Optional.of(bus));

    var failure =
        assertThrows(
            ExecutionException.class, () -> server.start().toCompletableFuture().get());

    assertFalse(server.isRunning());
    assertFalse(((HttpMcpServer.StartException) failure.getCause()).portInUse());
    assertEquals(ServiceStatus.FAILED, events.getLast().status());
  }

  @Test
  void aPortAnotherProgramUsesIsReportedAsInUse() throws Exception {
    try (ServerSocket taken = new ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
      server =
          new HttpMcpServer(
              HttpMcpServerTest::endpoint,
              taken.getLocalPort(),
              false,
              Optional.empty(),
              Optional.empty());

      var failure =
          assertThrows(
              ExecutionException.class, () -> server.start().toCompletableFuture().get());

      assertTrue(((HttpMcpServer.StartException) failure.getCause()).portInUse());
      assertTrue(failure.getCause().getMessage().contains(Integer.toString(taken.getLocalPort())));
      assertFalse(server.isRunning());
    }
  }

  @Test
  void statusFollowsStartAndStopAndAStoppedServerRefusesConnections() throws Exception {
    var bus = new InProcessBrowserEventBus();
    List<ServiceStatus> states = new ArrayList<>();
    bus.subscribe(ConnectionStatusChanged.class, event -> states.add(event.status()));
    server =
        new HttpMcpServer(
            HttpMcpServerTest::endpoint, 0, false, Optional.empty(), Optional.of(bus));
    server.start().toCompletableFuture().get();
    int port = server.port();
    assertTrue(server.isRunning());

    server.stop();

    assertEquals(List.of(ServiceStatus.CONNECTED, ServiceStatus.DISCONNECTED), states);
    assertFalse(server.isRunning());
    assertThrows(IOException.class, () -> new Socket("127.0.0.1", port).close());
  }
}
