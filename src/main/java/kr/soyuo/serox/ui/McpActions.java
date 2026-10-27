package kr.soyuo.serox.ui;

import java.time.Instant;
import java.util.List;
import kr.soyuo.serox.core.model.McpSettings;

/** What the MCP settings page can do with the HTTP server and its saved settings. */
public interface McpActions {
  /** The outcome of starting the server; {@code message} says why it did not start. */
  record Result(boolean running, boolean portInUse, String message) {}

  /** One tool call an agent made: the query text, who asked, when, and whether it worked. */
  record Call(String summary, String client, Instant at, boolean success) {}

  /** The latest calls, newest first; empty when none were made since Serox started. */
  default List<Call> recentCalls() {
    return List.of();
  }

  /** The saved settings. */
  McpSettings settings();

  /** Saves the settings; they take effect the next time the server starts. */
  void save(McpSettings settings);

  /** Whether the server is running now. */
  boolean running();

  /** Starts the server with these settings, restarting it when it already runs. */
  Result start(McpSettings settings);

  /** Stops the server. */
  void stop();

  /** For a shell without an MCP server: nothing runs and nothing is saved. */
  static McpActions none() {
    return new McpActions() {
      @Override
      public McpSettings settings() {
        return McpSettings.defaults();
      }

      @Override
      public void save(McpSettings settings) {}

      @Override
      public boolean running() {
        return false;
      }

      @Override
      public Result start(McpSettings settings) {
        return new Result(false, false, "이 환경에서는 MCP 서버를 켤 수 없어요.");
      }

      @Override
      public void stop() {}
    };
  }
}
