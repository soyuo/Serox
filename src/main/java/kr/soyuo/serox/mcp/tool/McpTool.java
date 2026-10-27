package kr.soyuo.serox.mcp.tool;

import com.google.gson.JsonObject;
import java.util.concurrent.CompletionStage;
import kr.soyuo.serox.mcp.server.McpCall;

/** One capability exposed to external agents. Arguments are validated before {@link #call}. */
public interface McpTool {
  String name();

  String description();

  /** JSON Schema (the subset understood by the validator) describing the arguments. */
  JsonObject inputSchema();

  CompletionStage<ToolResult> call(JsonObject arguments, McpCall call);
}
