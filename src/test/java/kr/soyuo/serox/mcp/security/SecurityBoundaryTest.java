package kr.soyuo.serox.mcp.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpHeaders;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import kr.soyuo.serox.ai.api.AiCompleted;
import kr.soyuo.serox.ai.api.AiTextDelta;
import kr.soyuo.serox.ai.application.SearchSummaryService;
import kr.soyuo.serox.ai.context.ContextBuilder;
import kr.soyuo.serox.browser.html.HtmlDocumentParser;
import kr.soyuo.serox.browser.network.HttpResponseData;
import kr.soyuo.serox.browser.network.HttpResponseHandler;
import kr.soyuo.serox.mcp.server.McpEndpoint;
import kr.soyuo.serox.mcp.tool.SearchAndSummarizeTool;
import kr.soyuo.serox.mcp.tool.ToolRegistry;
import kr.soyuo.serox.search.api.SearchResponse;
import kr.soyuo.serox.search.api.SearchResult;
import kr.soyuo.serox.search.fetch.FetchLimits;
import kr.soyuo.serox.search.fetch.PageFetcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** An approved agent still cannot control the browser, read files or receive secrets. */
class SecurityBoundaryTest {
  private final List<JsonObject> sent = new ArrayList<>();
  private final McpEndpoint endpoint = new McpEndpoint("test");

  @BeforeEach
  void connectApprovedClient() {
    AiProviderStub ai = new AiProviderStub("Mail me at owner@example.com [S1]. key sk-abcdefghijklmnop1234");
    var fetcher =
        new PageFetcher(
            request ->
                CompletableFuture.completedFuture(
                    new HttpResponseData(
                        request.uri(),
                        200,
                        HttpHeaders.of(
                            Map.of("Content-Type", List.of("text/html; charset=utf-8")),
                            (a, b) -> true),
                        "<body>text</body>".getBytes(StandardCharsets.UTF_8))),
            new HttpResponseHandler(),
            new HtmlDocumentParser(),
            FetchLimits.defaults());
    var service =
        new SearchSummaryService(
            Optional.of(
                request ->
                    CompletableFuture.completedFuture(
                        new SearchResponse(
                            "fake",
                            List.of(
                                new SearchResult(
                                    "r1", URI.create("https://example.test/a"), "Page", "s", 1))))),
            Optional.of(ai),
            fetcher,
            new ContextBuilder(),
            Clock.systemUTC());
    var policy =
        new AccessPolicy(
            (client, tools) -> tools, Set.of(SearchAndSummarizeTool.NAME), 2, 100, Clock.systemUTC());
    new ToolRegistry(policy).register(new SearchAndSummarizeTool(service)).installInto(endpoint);
    endpoint.receive("{\"jsonrpc\":\"2.0\",\"id\":0,\"method\":\"initialize\",\"params\":{}}", l -> {});
    endpoint.receive("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", l -> {});
  }

  private JsonObject ask(String json) {
    sent.clear();
    endpoint.receive(json, line -> sent.add(JsonParser.parseString(line).getAsJsonObject()));
    return sent.getFirst();
  }

  @Test
  void browserControlAndFileToolsDoNotExist() {
    for (String tool :
        List.of(
            "navigate", "open_tab", "close_tab", "click", "execute_script", "read_file",
            "write_file", "list_directory", "get_history", "get_cookies", "get_settings",
            "read_page", "screenshot", "run_command")) {
      var response =
          ask(
              "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\""
                  + tool
                  + "\",\"arguments\":{}}}");
      assertTrue(response.has("error"), tool);
      assertEquals(-32602, response.getAsJsonObject("error").get("code").getAsInt(), tool);
    }
    var listed = ask("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}");
    assertEquals(1, listed.getAsJsonObject("result").getAsJsonArray("tools").size());
  }

  @Test
  void otherMcpCapabilitiesAreNotServed() {
    for (String method :
        List.of(
            "resources/list", "resources/read", "resources/templates/list", "prompts/list",
            "prompts/get", "roots/list", "sampling/createMessage", "logging/setLevel",
            "completion/complete")) {
      var response = ask("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"" + method + "\"}");
      assertEquals(-32601, response.getAsJsonObject("error").get("code").getAsInt(), method);
    }
  }

  @Test
  void toolOutputNeverCarriesSecretsOrPersonalData() {
    var response =
        ask(
            "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":"
                + "{\"name\":\"search_and_summarize\",\"arguments\":{\"query\":\"anything\"}}}");

    String body = response.toString();
    assertFalse(body.contains("owner@example.com"));
    assertFalse(body.contains("sk-abcdefghijklmnop1234"));
    assertTrue(body.contains("[redacted]"));
  }

  @Test
  void topNIsListedAndKeptInRange() {
    var listed = ask("{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/list\"}");
    var properties =
        listed
            .getAsJsonObject("result")
            .getAsJsonArray("tools")
            .get(0)
            .getAsJsonObject()
            .getAsJsonObject("inputSchema")
            .getAsJsonObject("properties");
    assertTrue(properties.has("top_n"));

    var fine =
        ask(
            "{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"tools/call\",\"params\":"
                + "{\"name\":\"search_and_summarize\",\"arguments\":{\"query\":\"x\",\"top_n\":3}}}");
    assertFalse(fine.has("error"));

    var tooMany =
        ask(
            "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":"
                + "{\"name\":\"search_and_summarize\",\"arguments\":{\"query\":\"x\",\"top_n\":99}}}");
    assertTrue(tooMany.has("error"));
  }

  /** Emits a fixed answer. */
  private static final class AiProviderStub implements kr.soyuo.serox.ai.api.AiProvider {
    private final String answer;

    AiProviderStub(String answer) {
      this.answer = answer;
    }

    @Override
    public Flow.Publisher<kr.soyuo.serox.ai.api.AiEvent> analyze(
        kr.soyuo.serox.ai.api.AiRequest request) {
      return subscriber ->
          subscriber.onSubscribe(
              new Flow.Subscription() {
                @Override
                public void request(long n) {
                  subscriber.onNext(new AiTextDelta(answer));
                  subscriber.onNext(new AiCompleted(List.of()));
                }

                @Override
                public void cancel() {}
              });
    }
  }
}
