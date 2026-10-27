package kr.soyuo.serox.mcp.server;

import java.util.concurrent.CompletionStage;

/** Lifecycle contract for the separately permissioned MCP server. */
public interface SeroxMcpServer {
  CompletionStage<Void> start();

  CompletionStage<Void> stop();

  boolean isRunning();
}
