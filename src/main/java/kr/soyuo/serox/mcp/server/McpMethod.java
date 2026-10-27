package kr.soyuo.serox.mcp.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.concurrent.CompletionStage;

/** Handler for one JSON-RPC method; failures are mapped to protocol errors by the endpoint. */
@FunctionalInterface
public interface McpMethod {
  CompletionStage<JsonElement> call(JsonObject params, McpCall call);
}
