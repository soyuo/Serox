package kr.soyuo.serox.mcp.server;

/** Failure with a JSON-RPC error code and a message that is safe to show to the client. */
public final class McpProtocolException extends RuntimeException {
  private final int code;

  public McpProtocolException(int code, String safeMessage) {
    super(safeMessage);
    this.code = code;
  }

  /** Invalid parameters supplied by the client. */
  public static McpProtocolException invalidParams(String safeMessage) {
    return new McpProtocolException(JsonRpc.INVALID_PARAMS, safeMessage);
  }

  int code() {
    return code;
  }
}
