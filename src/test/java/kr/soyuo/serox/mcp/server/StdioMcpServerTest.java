package kr.soyuo.serox.mcp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.channels.Channels;
import java.nio.channels.Pipe;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import kr.soyuo.serox.core.event.ConnectionStatusChanged;
import kr.soyuo.serox.core.event.InProcessBrowserEventBus;
import kr.soyuo.serox.core.event.ServiceStatus;
import org.junit.jupiter.api.Test;

class StdioMcpServerTest {
  private final Pipe toServer = open();
  private final Pipe fromServer = open();
  private final java.io.OutputStream clientToServer = Channels.newOutputStream(toServer.sink());
  private final java.io.InputStream serverIn = Channels.newInputStream(toServer.source());
  private final java.io.OutputStream serverToClient = Channels.newOutputStream(fromServer.sink());
  private final PrintWriter client =
      new PrintWriter(new java.io.OutputStreamWriter(clientToServer, StandardCharsets.UTF_8), true);
  private final BufferedReader responses =
      new BufferedReader(
          new InputStreamReader(Channels.newInputStream(fromServer.source()), StandardCharsets.UTF_8));

  private static Pipe open() {
    try {
      return Pipe.open();
    } catch (java.io.IOException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private JsonObject exchange(String request) throws Exception {
    client.println(request);
    String line = responses.readLine();
    return JsonParser.parseString(line).getAsJsonObject();
  }

  @Test
  void completesTheInitializeHandshakeAndReportsStatus() throws Exception {
    List<ServiceStatus> statuses = new ArrayList<>();
    try (var bus = new InProcessBrowserEventBus()) {
      bus.subscribe(ConnectionStatusChanged.class, event -> statuses.add(event.status()));
      var server =
          new StdioMcpServer(new McpEndpoint("1.0"), serverIn, serverToClient, Optional.of(bus));
      var done = server.start().toCompletableFuture();
      assertTrue(server.isRunning());

      JsonObject init =
          exchange(
              "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}");
      assertEquals("2025-03-26", init.getAsJsonObject("result").get("protocolVersion").getAsString());
      assertEquals(
          "serox",
          init.getAsJsonObject("result").getAsJsonObject("serverInfo").get("name").getAsString());
      assertTrue(exchange("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"ping\"}").has("result"));

      client.close();
      clientToServer.close();
      done.get(5, TimeUnit.SECONDS);
      assertFalse(server.isRunning());
      assertEquals(List.of(ServiceStatus.CONNECTED, ServiceStatus.DISCONNECTED), statuses);
    }
  }

  @Test
  void rejectsUnknownMethodsBadJsonAndUninitializedUse() throws Exception {
    var endpoint = new McpEndpoint("1.0");
    endpoint.register("custom", (params, call) -> java.util.concurrent.CompletableFuture.completedFuture(new JsonObject()));
    var server = new StdioMcpServer(endpoint, serverIn, serverToClient, Optional.empty());
    server.start();

    assertEquals(
        -32601,
        exchange("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"nope\"}")
            .getAsJsonObject("error")
            .get("code")
            .getAsInt());
    assertEquals(
        -32002,
        exchange("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"custom\"}")
            .getAsJsonObject("error")
            .get("code")
            .getAsInt());
    assertEquals(-32700, exchange("{not json").getAsJsonObject("error").get("code").getAsInt());

    client.println("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");
    JsonObject ok = exchange("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"custom\"}");
    assertTrue(ok.has("result"));
    server.stop();
  }
}
