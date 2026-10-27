package kr.soyuo.serox.mcp.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Objects;
import java.util.Optional;

/** Outcome of a tool call in the shape the MCP {@code tools/call} result expects. */
public record ToolResult(String text, Optional<JsonObject> structured, boolean isError) {
  public ToolResult {
    Objects.requireNonNull(text, "text");
    Objects.requireNonNull(structured, "structured");
  }

  public static ToolResult success(String text, JsonObject structured) {
    return new ToolResult(text, Optional.of(structured), false);
  }

  /** A failure the model can read and react to; it is not a protocol error. */
  public static ToolResult failure(String text) {
    return new ToolResult(text, Optional.empty(), true);
  }

  /** Converts the result to the JSON returned to the client. */
  public JsonObject toJson() {
    JsonObject content = new JsonObject();
    content.addProperty("type", "text");
    content.addProperty("text", text);
    JsonArray contents = new JsonArray();
    contents.add(content);
    JsonObject json = new JsonObject();
    json.add("content", contents);
    structured.ifPresent(value -> json.add("structuredContent", value));
    json.addProperty("isError", isError);
    return json;
  }
}
