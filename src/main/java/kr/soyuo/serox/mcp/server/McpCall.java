package kr.soyuo.serox.mcp.server;

import java.util.concurrent.CompletableFuture;

/** Per-request context that lets a handler observe cancellation requested by the client. */
public final class McpCall {
  private final CompletableFuture<Void> cancelled = new CompletableFuture<>();

  /** Completes when the client cancels this request. */
  public CompletableFuture<Void> cancellation() {
    return cancelled;
  }

  boolean cancel() {
    return cancelled.complete(null);
  }
}
