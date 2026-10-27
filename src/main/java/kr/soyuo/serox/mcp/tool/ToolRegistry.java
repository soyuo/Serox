package kr.soyuo.serox.mcp.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import kr.soyuo.serox.mcp.security.AccessPolicy;
import kr.soyuo.serox.mcp.security.InputValidator;
import kr.soyuo.serox.mcp.server.McpCall;
import kr.soyuo.serox.mcp.server.McpEndpoint;
import kr.soyuo.serox.mcp.server.McpProtocolException;

/** The complete set of tools an agent can call; anything not registered does not exist. */
public final class ToolRegistry {
  private final Map<String, McpTool> tools = new LinkedHashMap<>();
  private final AccessPolicy policy;

  public ToolRegistry(AccessPolicy policy) {
    this.policy = Objects.requireNonNull(policy, "policy");
  }

  /** Registers a tool; names must be unique. */
  public ToolRegistry register(McpTool tool) {
    Objects.requireNonNull(tool, "tool");
    if (tools.putIfAbsent(tool.name(), tool) != null) {
      throw new IllegalArgumentException("Duplicate tool: " + tool.name());
    }
    return this;
  }

  /** Names of the registered tools. */
  public java.util.Set<String> toolNames() {
    return java.util.Set.copyOf(tools.keySet());
  }

  /** Names of the registered tools in registration order. */
  public List<String> names() {
    return List.copyOf(tools.keySet());
  }

  /** Adds {@code tools/list} and {@code tools/call} to the endpoint. */
  public void installInto(McpEndpoint endpoint) {
    endpoint.setInitializeGuard(policy::authorizeSession);
    endpoint.register("tools/list", (params, call) -> CompletableFuture.completedFuture(list()));
    endpoint.register("tools/call", this::callTool);
  }

  private JsonElement list() {
    JsonArray array = new JsonArray();
    for (McpTool tool : tools.values()) {
      if (!policy.approvedTools().contains(tool.name())) {
        continue;
      }
      JsonObject entry = new JsonObject();
      entry.addProperty("name", tool.name());
      entry.addProperty("description", tool.description());
      entry.add("inputSchema", tool.inputSchema());
      array.add(entry);
    }
    JsonObject result = new JsonObject();
    result.add("tools", array);
    return result;
  }

  private CompletionStage<JsonElement> callTool(JsonObject params, McpCall call) {
    String name =
        params.has("name") && params.get("name").isJsonPrimitive()
            ? params.get("name").getAsString()
            : "";
    McpTool tool = tools.get(name);
    if (tool == null) {
      throw McpProtocolException.invalidParams("Unknown tool.");
    }
    JsonObject arguments =
        params.has("arguments") && params.get("arguments").isJsonObject()
            ? params.getAsJsonObject("arguments")
            : new JsonObject();
    List<String> problems = InputValidator.validate(tool.inputSchema(), arguments);
    if (!problems.isEmpty()) {
      throw McpProtocolException.invalidParams(String.join(" ", problems));
    }
    AccessPolicy.Permit permit = policy.begin(name);
    try {
      return tool.call(arguments, call)
          .whenComplete((result, failure) -> permit.close())
          .thenApply(ToolResult::toJson);
    } catch (RuntimeException exception) {
      permit.close();
      throw exception;
    }
  }
}
