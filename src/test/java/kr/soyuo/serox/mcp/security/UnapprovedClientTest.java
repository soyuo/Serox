package kr.soyuo.serox.mcp.security;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import kr.soyuo.serox.mcp.server.McpEndpoint;
import kr.soyuo.serox.mcp.tool.McpTool;
import kr.soyuo.serox.mcp.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

/** A client the user did not approve cannot run a tool, whatever it sends. */
class UnapprovedClientTest {
  @Test
  void toolCallsAreDeniedWhenTheUserApprovedNothing() {
    McpEndpoint endpoint = new McpEndpoint("test");
    var policy =
        new AccessPolicy((client, tools) -> Set.of(), Set.of("search_and_summarize"), 2, 100, Clock.systemUTC());
    McpTool tool = new McpTool() {
      @Override
      public String name() {
        return "search_and_summarize";
      }

      @Override
      public String description() {
        return "test";
      }

      @Override
      public JsonObject inputSchema() {
        return new JsonObject();
      }

      @Override
      public java.util.concurrent.CompletionStage<kr.soyuo.serox.mcp.tool.ToolResult> call(
          JsonObject arguments, kr.soyuo.serox.mcp.server.McpCall call) {
        throw new AssertionError("an unapproved client must never reach the tool");
      }
    };
    new ToolRegistry(policy).register(tool).installInto(endpoint);
    List<JsonObject> sent = new ArrayList<>();
    endpoint.receive("{\"jsonrpc\":\"2.0\",\"id\":0,\"method\":\"initialize\",\"params\":{}}", l -> {});
    endpoint.receive("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", l -> {});
    endpoint.receive(
        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"search_and_summarize\",\"arguments\":{\"query\":\"x\"}}}",
        line -> sent.add(JsonParser.parseString(line).getAsJsonObject()));

    assertTrue(sent.getFirst().has("error"));
    int code = sent.getFirst().getAsJsonObject("error").get("code").getAsInt();
    assertTrue(code == AccessPolicy.DENIED || code == -32602, "code " + code);
  }
}
