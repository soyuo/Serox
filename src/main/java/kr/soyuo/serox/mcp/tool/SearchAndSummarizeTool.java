package kr.soyuo.serox.mcp.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import kr.soyuo.serox.ai.application.SearchSummary;
import kr.soyuo.serox.ai.application.SearchSummaryService;
import kr.soyuo.serox.ai.application.SummaryException;
import kr.soyuo.serox.mcp.server.McpCall;

/** Searches the web and returns a cited summary; the only tool exposed by default. */
public final class SearchAndSummarizeTool implements McpTool {
  public static final String NAME = "search_and_summarize";
  public static final int MAX_QUERY_CHARS = 500;

  private final SearchSummaryService service;

  public SearchAndSummarizeTool(SearchSummaryService service) {
    this.service = Objects.requireNonNull(service, "service");
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public String description() {
    return "Search the web for a question and return a short answer with cited sources.";
  }

  @Override
  public JsonObject inputSchema() {
    JsonObject query = new JsonObject();
    query.addProperty("type", "string");
    query.addProperty("minLength", 1);
    query.addProperty("maxLength", MAX_QUERY_CHARS);
    query.addProperty("description", "The question or search terms.");
    JsonObject properties = new JsonObject();
    properties.add("query", query);
    JsonArray required = new JsonArray();
    required.add("query");
    JsonObject schema = new JsonObject();
    schema.addProperty("type", "object");
    schema.add("properties", properties);
    schema.add("required", required);
    schema.addProperty("additionalProperties", false);
    return schema;
  }

  @Override
  public CompletionStage<ToolResult> call(JsonObject arguments, McpCall call) {
    String query = arguments.get("query").getAsString().strip();
    var run = service.summarize(query);
    call.cancellation().thenRun(() -> run.cancel(true));
    return run.handle(
        (summary, failure) -> {
          if (failure == null) {
            return toResult(summary);
          }
          Throwable cause =
              failure instanceof CompletionException && failure.getCause() != null
                  ? failure.getCause()
                  : failure;
          if (cause instanceof SummaryException summaryFailure) {
            return ToolResult.failure(summaryFailure.code(), summaryFailure.getMessage());
          }
          return ToolResult.failure("search_failed", "The search could not be completed.");
        });
  }

  private static ToolResult toResult(SearchSummary summary) {
    JsonArray sources = new JsonArray();
    for (SearchSummary.Source source : summary.sources()) {
      JsonObject entry = new JsonObject();
      entry.addProperty("id", source.id());
      entry.addProperty("title", source.title());
      entry.addProperty("url", source.url().toString());
      entry.addProperty("referencedText", source.referencedText());
      entry.addProperty("retrievedAt", source.retrievedAt().toString());
      sources.add(entry);
    }
    JsonObject structured = new JsonObject();
    structured.addProperty("query", summary.query());
    structured.addProperty("answer", summary.answer());
    structured.add("sources", sources);
    structured.addProperty("grounded", !summary.sources().isEmpty());
    return ToolResult.success(summary.answer(), structured);
  }
}
