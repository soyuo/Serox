package kr.soyuo.serox.mcp.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

/** JSON-RPC 2.0 message helpers. */
final class JsonRpc {
  static final int PARSE_ERROR = -32700;
  static final int INVALID_REQUEST = -32600;
  static final int METHOD_NOT_FOUND = -32601;
  static final int INVALID_PARAMS = -32602;
  static final int INTERNAL_ERROR = -32603;
  static final int NOT_INITIALIZED = -32002;

  /** Prevents instantiation of this stateless utility. */
  private JsonRpc() {}

  static JsonObject result(JsonElement id, JsonElement result) {
    JsonObject message = envelope(id);
    message.add("result", result);
    return message;
  }

  static JsonObject error(JsonElement id, int code, String text) {
    JsonObject error = new JsonObject();
    error.addProperty("code", code);
    error.addProperty("message", text);
    JsonObject message = envelope(id == null ? JsonNull.INSTANCE : id);
    message.add("error", error);
    return message;
  }

  private static JsonObject envelope(JsonElement id) {
    JsonObject message = new JsonObject();
    message.addProperty("jsonrpc", "2.0");
    message.add("id", id);
    return message;
  }
}
