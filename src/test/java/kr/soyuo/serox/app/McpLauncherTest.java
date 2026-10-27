package kr.soyuo.serox.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.channels.Channels;
import java.nio.channels.Pipe;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import kr.soyuo.serox.core.event.ConnectionStatusChanged;
import kr.soyuo.serox.core.event.ServiceId;
import kr.soyuo.serox.core.event.ServiceStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class McpLauncherTest {
  @TempDir Path tempDir;

  @Test
  void approvedAgentReachesTheToolAndStatusReachesTheUiBus() throws Exception {
    Pipe toServer = Pipe.open();
    Pipe fromServer = Pipe.open();
    var client =
        new PrintWriter(
            new OutputStreamWriter(Channels.newOutputStream(toServer.sink()), StandardCharsets.UTF_8),
            true);
    var responses =
        new BufferedReader(
            new InputStreamReader(
                Channels.newInputStream(fromServer.source()), StandardCharsets.UTF_8));
    List<ServiceStatus> mcpStatuses = new ArrayList<>();

    try (var runtime =
        AppBootstrap.createRuntime(Map.of("SEROX_DATA_DIR", tempDir.toString()))) {
      runtime
          .eventBus()
          .subscribe(
              ConnectionStatusChanged.class,
              event -> {
                if (event.service() == ServiceId.MCP) {
                  mcpStatuses.add(event.status());
                }
              });
      var server =
          McpLauncher.create(
              Map.of("SEROX_MCP_APPROVED_TOOLS", "search_and_summarize"),
              Optional.of(runtime.eventBus()),
              Channels.newInputStream(toServer.source()),
              Channels.newOutputStream(fromServer.sink()));
      var finished = server.start().toCompletableFuture();

      client.println("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}");
      assertTrue(JsonParser.parseString(responses.readLine()).getAsJsonObject().has("result"));
      client.println("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");
      client.println(
          "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":"
              + "{\"name\":\"search_and_summarize\",\"arguments\":{\"query\":\"hello\"}}}");
      JsonObject call = JsonParser.parseString(responses.readLine()).getAsJsonObject();
      assertEquals(
          "search_unconfigured",
          call.getAsJsonObject("result")
              .getAsJsonObject("structuredContent")
              .get("errorCode")
              .getAsString());

      // The server going away must not disturb browsing.
      server.stop();
      finished.get(5, TimeUnit.SECONDS);
      assertFalse(server.isRunning());
      assertEquals(List.of(ServiceStatus.CONNECTED, ServiceStatus.DISCONNECTED), mcpStatuses);
      var tab = runtime.browserController().createTab().toCompletableFuture().join();
      assertEquals(1, runtime.browserController().listTabs().toCompletableFuture().join().size());
      assertTrue(tab.currentUri().isEmpty());
    }
  }

  @Test
  void anAgentWithoutApprovalIsRefused() throws Exception {
    Pipe toServer = Pipe.open();
    Pipe fromServer = Pipe.open();
    var client =
        new PrintWriter(
            new OutputStreamWriter(Channels.newOutputStream(toServer.sink()), StandardCharsets.UTF_8),
            true);
    var responses =
        new BufferedReader(
            new InputStreamReader(
                Channels.newInputStream(fromServer.source()), StandardCharsets.UTF_8));
    var server =
        McpLauncher.create(
            Map.of(),
            Optional.empty(),
            Channels.newInputStream(toServer.source()),
            Channels.newOutputStream(fromServer.sink()));
    server.start();

    client.println("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}");
    JsonObject response = JsonParser.parseString(responses.readLine()).getAsJsonObject();

    assertEquals(-32001, response.getAsJsonObject("error").get("code").getAsInt());
    server.stop();
  }
}
