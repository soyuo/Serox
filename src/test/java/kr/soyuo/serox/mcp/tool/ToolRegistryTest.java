package kr.soyuo.serox.mcp.tool;

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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import kr.soyuo.serox.ai.api.AiCompleted;
import kr.soyuo.serox.ai.api.AiProvider;
import kr.soyuo.serox.ai.api.AiTextDelta;
import kr.soyuo.serox.ai.application.SearchSummaryService;
import kr.soyuo.serox.ai.context.ContextBuilder;
import kr.soyuo.serox.browser.html.HtmlDocumentParser;
import kr.soyuo.serox.browser.network.HttpResponseData;
import kr.soyuo.serox.browser.network.HttpResponseHandler;
import kr.soyuo.serox.mcp.server.McpEndpoint;
import kr.soyuo.serox.search.api.SearchResponse;
import kr.soyuo.serox.search.api.SearchResult;
import kr.soyuo.serox.search.fetch.FetchLimits;
import kr.soyuo.serox.search.fetch.PageFetcher;
import org.junit.jupiter.api.Test;

class ToolRegistryTest {
  private static kr.soyuo.serox.mcp.security.AccessPolicy approvingPolicy() {
    return new kr.soyuo.serox.mcp.security.AccessPolicy(
        (client, tools) -> tools,
        java.util.Set.of(SearchAndSummarizeTool.NAME),
        2,
        100,
        Clock.systemUTC());
  }

  private final List<JsonObject> sent = new ArrayList<>();
  private final McpEndpoint endpoint = new McpEndpoint("test");

  private SearchSummaryService summaryService() {
    AiProvider ai =
        request ->
            subscriber ->
                subscriber.onSubscribe(
                    new Flow.Subscription() {
                      @Override
                      public void request(long n) {
                        subscriber.onNext(new AiTextDelta("Serox is a browser [S1]."));
                        subscriber.onNext(new AiCompleted(List.of()));
                      }

                      @Override
                      public void cancel() {}
                    });
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
                        "<body><p>Serox is a browser.</p></body>".getBytes(StandardCharsets.UTF_8))),
            new HttpResponseHandler(),
            new HtmlDocumentParser(),
            FetchLimits.defaults());
    return new SearchSummaryService(
        Optional.of(
            request ->
                CompletableFuture.completedFuture(
                    new SearchResponse(
                        "fake",
                        List.of(
                            new SearchResult(
                                "r1", URI.create("https://example.test/a"), "Serox", "s", 1))))),
        Optional.of(ai),
        fetcher,
        new ContextBuilder(),
        Clock.systemUTC());
  }

  private JsonObject ask(String json) {
    sent.clear();
    endpoint.receive(json, line -> sent.add(JsonParser.parseString(line).getAsJsonObject()));
    return sent.getFirst();
  }

  private void ready() {
    new ToolRegistry(approvingPolicy())
        .register(new SearchAndSummarizeTool(summaryService()))
        .installInto(endpoint);
    endpoint.receive("{\"jsonrpc\":\"2.0\",\"id\":0,\"method\":\"initialize\",\"params\":{}}", line -> {});
    endpoint.receive("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", line -> {});
  }

  @Test
  void listsAndCallsTheSearchTool() {
    ready();

    var tools = ask("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}");
    var first = tools.getAsJsonObject("result").getAsJsonArray("tools").get(0).getAsJsonObject();
    assertEquals("search_and_summarize", first.get("name").getAsString());

    var result =
        ask(
            "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":"
                + "{\"name\":\"search_and_summarize\",\"arguments\":{\"query\":\"what is serox\"}}}")
            .getAsJsonObject("result");
    assertFalse(result.get("isError").getAsBoolean());
    var structured = result.getAsJsonObject("structuredContent");
    assertEquals("Serox is a browser [S1].", structured.get("answer").getAsString());
    var source = structured.getAsJsonArray("sources").get(0).getAsJsonObject();
    assertEquals("https://example.test/a", source.get("url").getAsString());
  }

  @Test
  void rejectsInvalidArgumentsAndUnknownTools() {
    ready();
    String prefix = "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":";

    for (String params :
        List.of(
            "{\"name\":\"search_and_summarize\",\"arguments\":{}}",
            "{\"name\":\"search_and_summarize\",\"arguments\":{\"query\":\"x\",\"path\":\"/etc\"}}",
            "{\"name\":\"search_and_summarize\",\"arguments\":{\"query\":123}}",
            "{\"name\":\"search_and_summarize\",\"arguments\":{\"query\":\"" + "a".repeat(501) + "\"}}",
            "{\"name\":\"read_file\",\"arguments\":{\"path\":\"/etc/passwd\"}}",
            "{\"name\":\"navigate\",\"arguments\":{\"url\":\"https://example.test\"}}")) {
      var response = ask(prefix + params + "}");
      assertTrue(response.has("error"), params);
      assertEquals(-32602, response.getAsJsonObject("error").get("code").getAsInt(), params);
    }
  }

  @Test
  void aSwitchedOffToolIsHiddenAndCannotBeCalled() {
    var enabled = new java.util.concurrent.atomic.AtomicBoolean(false);
    new ToolRegistry(approvingPolicy())
        .register(new SearchAndSummarizeTool(summaryService()))
        .setEnabled(name -> enabled.get())
        .installInto(endpoint);
    endpoint.receive("{\"jsonrpc\":\"2.0\",\"id\":0,\"method\":\"initialize\",\"params\":{}}", line -> {});
    endpoint.receive("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", line -> {});
    String call =
        "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":"
            + "{\"name\":\"search_and_summarize\",\"arguments\":{\"query\":\"x\"}}}";

    var listed = ask("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}");
    assertEquals(0, listed.getAsJsonObject("result").getAsJsonArray("tools").size());
    assertTrue(ask(call).has("error"));

    enabled.set(true);
    assertFalse(ask(call).has("error"));
  }

  @Test
  void finishedCallsAreRecordedWithTheCleanedQuery() {
    var log = new McpCallLog();
    new ToolRegistry(approvingPolicy())
        .register(new SearchAndSummarizeTool(summaryService()))
        .setCallLog(log)
        .installInto(endpoint);
    endpoint.receive(
        "{\"jsonrpc\":\"2.0\",\"id\":0,\"method\":\"initialize\",\"params\":"
            + "{\"clientInfo\":{\"name\":\"agent-a\"}}}",
        line -> {});
    endpoint.receive("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", line -> {});

    ask(
        "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":"
            + "{\"name\":\"search_and_summarize\",\"arguments\":{\"query\":\"<b>java</b> thread\"}}}");

    var entry = log.recent().getFirst();
    assertEquals("java thread", entry.summary());
    assertEquals("agent-a", entry.client());
    assertTrue(entry.success());
  }
}
