package kr.soyuo.serox.mcp.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.http.HttpHeaders;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import kr.soyuo.serox.ai.application.SearchSummaryService;
import kr.soyuo.serox.ai.context.ContextBuilder;
import kr.soyuo.serox.browser.html.HtmlDocumentParser;
import kr.soyuo.serox.browser.network.HttpResponseData;
import kr.soyuo.serox.browser.network.HttpResponseHandler;
import kr.soyuo.serox.mcp.server.McpEndpoint;
import kr.soyuo.serox.search.api.SearchProvider;
import kr.soyuo.serox.search.api.SearchResponse;
import kr.soyuo.serox.search.fetch.FetchLimits;
import kr.soyuo.serox.search.fetch.PageFetcher;
import org.junit.jupiter.api.Test;

class CancellationAndTimeoutTest {
  private static kr.soyuo.serox.mcp.security.AccessPolicy approvingPolicy() {
    return new kr.soyuo.serox.mcp.security.AccessPolicy(
        (client, tools) -> tools,
        java.util.Set.of(SearchAndSummarizeTool.NAME),
        2,
        100,
        Clock.systemUTC());
  }

  private final List<JsonObject> sent = new CopyOnWriteArrayList<>();
  private final McpEndpoint endpoint = new McpEndpoint("test");

  private PageFetcher fetcher() {
    return new PageFetcher(
        request ->
            CompletableFuture.completedFuture(
                new HttpResponseData(
                    request.uri(),
                    200,
                    HttpHeaders.of(
                        Map.of("Content-Type", List.of("text/html; charset=utf-8")), (a, b) -> true),
                    "<body>text</body>".getBytes(StandardCharsets.UTF_8))),
        new HttpResponseHandler(),
        new HtmlDocumentParser(),
        FetchLimits.defaults());
  }

  private void install(SearchProvider search, Duration timeout) {
    var service =
        new SearchSummaryService(
            Optional.of(search),
            Optional.of(request -> subscriber -> {}),
            fetcher(),
            new ContextBuilder(),
            Clock.systemUTC(),
            timeout);
    new ToolRegistry(approvingPolicy()).register(new SearchAndSummarizeTool(service)).installInto(endpoint);
    endpoint.receive("{\"jsonrpc\":\"2.0\",\"id\":0,\"method\":\"initialize\",\"params\":{}}", l -> {});
    endpoint.receive("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", l -> {});
  }

  private static final String CALL =
      "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":"
          + "{\"name\":\"search_and_summarize\",\"arguments\":{\"query\":\"slow\"}}}";

  private void send(String json) {
    endpoint.receive(json, line -> sent.add(JsonParser.parseString(line).getAsJsonObject()));
  }

  @Test
  void aRunThatNeverFinishesEndsWithATimeoutResult() throws Exception {
    install(request -> new CompletableFuture<>(), Duration.ofMillis(150));

    send(CALL);
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (sent.isEmpty() && System.nanoTime() < deadline) {
      Thread.sleep(20);
    }

    var result = sent.getFirst().getAsJsonObject("result");
    assertTrue(result.get("isError").getAsBoolean());
    assertEquals(
        "timeout", result.getAsJsonObject("structuredContent").get("errorCode").getAsString());
  }

  @Test
  void cancellingStopsTheSearchAndSendsNoResponse() throws Exception {
    List<CompletableFuture<SearchResponse>> searches = new ArrayList<>();
    install(
        request -> {
          var pending = new CompletableFuture<SearchResponse>();
          searches.add(pending);
          return pending;
        },
        Duration.ofSeconds(30));

    send(CALL);
    assertEquals(1, searches.size());
    send("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/cancelled\",\"params\":{\"requestId\":7}}");
    Thread.sleep(200);

    assertTrue(searches.getFirst().isCancelled());
    assertTrue(sent.isEmpty(), "a cancelled request must not be answered");
  }
}
