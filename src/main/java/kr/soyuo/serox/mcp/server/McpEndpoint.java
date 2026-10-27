package kr.soyuo.serox.mcp.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Transport-independent MCP session: parses messages, enforces lifecycle and dispatches. */
public final class McpEndpoint {
  public static final String PROTOCOL_VERSION = "2025-03-26";
  private static final Logger LOGGER = Logger.getLogger(McpEndpoint.class.getName());

  private final Map<String, McpMethod> methods = new ConcurrentHashMap<>();
  private final Map<String, McpCall> inFlight = new ConcurrentHashMap<>();
  private final String serverVersion;
  private volatile boolean initialized;
  private volatile Consumer<JsonObject> initializeGuard = params -> {};

  public McpEndpoint(String serverVersion) {
    this.serverVersion = Objects.requireNonNull(serverVersion, "serverVersion");
    register("ping", (params, call) -> CompletableFuture.completedFuture(new JsonObject()));
    register(
        "initialize",
        (params, call) -> {
          initializeGuard.accept(params);
          return CompletableFuture.completedFuture(initialize());
        });
  }

  /** Runs before a session is accepted; throwing a protocol exception refuses the session. */
  public void setInitializeGuard(Consumer<JsonObject> guard) {
    this.initializeGuard = Objects.requireNonNull(guard, "guard");
  }

  /** Adds a method that is available once the client has initialized the session. */
  public void register(String method, McpMethod handler) {
    methods.put(
        Objects.requireNonNull(method, "method"), Objects.requireNonNull(handler, "handler"));
  }

  /** Handles one incoming line; responses are passed to {@code send} (possibly later). */
  public void receive(String line, Consumer<String> send) {
    JsonObject message;
    try {
      JsonElement parsed = JsonParser.parseString(line);
      if (!parsed.isJsonObject()) {
        send.accept(
            JsonRpc.error(null, JsonRpc.INVALID_REQUEST, "Batches are not supported.").toString());
        return;
      }
      message = parsed.getAsJsonObject();
    } catch (RuntimeException exception) {
      send.accept(JsonRpc.error(null, JsonRpc.PARSE_ERROR, "Invalid JSON.").toString());
      return;
    }
    JsonElement id = message.get("id");
    String method =
        message.has("method") && message.get("method").isJsonPrimitive()
            ? message.get("method").getAsString()
            : null;
    if (method == null) {
      if (id != null && message.has("method")) {
        send.accept(JsonRpc.error(id, JsonRpc.INVALID_REQUEST, "Invalid method.").toString());
      }
      return;
    }
    JsonObject params =
        message.has("params") && message.get("params").isJsonObject()
            ? message.getAsJsonObject("params")
            : new JsonObject();
    if (id == null) {
      notification(method, params);
      return;
    }
    request(id, method, params, send);
  }

  /** Cancels every request still running, for example when the transport closes. */
  public void cancelAll() {
    inFlight.values().forEach(McpCall::cancel);
  }

  private void notification(String method, JsonObject params) {
    switch (method) {
      case "notifications/initialized" -> initialized = true;
      case "notifications/cancelled" -> {
        JsonElement requestId = params.get("requestId");
        if (requestId != null) {
          McpCall call = inFlight.get(requestId.toString());
          if (call != null) {
            call.cancel();
          }
        }
      }
      default -> {}
    }
  }

  private void request(JsonElement id, String method, JsonObject params, Consumer<String> send) {
    McpMethod handler = methods.get(method);
    if (handler == null) {
      send.accept(JsonRpc.error(id, JsonRpc.METHOD_NOT_FOUND, "Method not found.").toString());
      return;
    }
    if (!initialized && !method.equals("initialize") && !method.equals("ping")) {
      send.accept(JsonRpc.error(id, JsonRpc.NOT_INITIALIZED, "Server not initialized.").toString());
      return;
    }
    String key = id.toString();
    McpCall call = new McpCall();
    inFlight.put(key, call);
    try {
      handler
          .call(params, call)
          .whenComplete(
              (result, failure) -> {
                inFlight.remove(key);
                if (call.cancellation().isDone()) {
                  return;
                }
                if (failure == null) {
                  send.accept(JsonRpc.result(id, result).toString());
                } else {
                  send.accept(failureMessage(id, failure).toString());
                }
              });
    } catch (RuntimeException exception) {
      inFlight.remove(key);
      send.accept(failureMessage(id, exception).toString());
    }
  }

  private JsonObject failureMessage(JsonElement id, Throwable failure) {
    Throwable cause =
        failure instanceof CompletionException && failure.getCause() != null
            ? failure.getCause()
            : failure;
    if (cause instanceof McpProtocolException protocol) {
      return JsonRpc.error(id, protocol.code(), protocol.getMessage());
    }
    LOGGER.log(Level.WARNING, "MCP request failed: {0}", cause.getClass().getSimpleName());
    return JsonRpc.error(id, JsonRpc.INTERNAL_ERROR, "Internal error.");
  }

  private JsonObject initialize() {
    JsonObject result = new JsonObject();
    result.addProperty("protocolVersion", PROTOCOL_VERSION);
    JsonObject capabilities = new JsonObject();
    capabilities.add("tools", new JsonObject());
    result.add("capabilities", capabilities);
    JsonObject info = new JsonObject();
    info.addProperty("name", "serox");
    info.addProperty("version", serverVersion);
    result.add("serverInfo", info);
    return result;
  }
}
