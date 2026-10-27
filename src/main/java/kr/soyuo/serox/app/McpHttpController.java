package kr.soyuo.serox.app;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import kr.soyuo.serox.core.event.BrowserEventBus;
import kr.soyuo.serox.core.model.McpAccess;
import kr.soyuo.serox.core.model.McpSettings;
import kr.soyuo.serox.mcp.server.HttpMcpServer;
import kr.soyuo.serox.mcp.server.McpEndpoint;

/**
 * Starts and stops the MCP HTTP server as the settings say. The bearer token for network access
 * comes only from {@code SEROX_MCP_TOKEN} and is never stored or logged.
 */
public final class McpHttpController implements AutoCloseable {
  /** The outcome of a start: running, or why not, in words the settings page can show. */
  public record Result(boolean running, boolean portInUse, String message) {
    static Result started() {
      return new Result(true, false, "");
    }
  }

  private final Supplier<McpEndpoint> endpoints;
  private final Map<String, String> environment;
  private final BrowserEventBus eventBus;
  private HttpMcpServer server;

  public McpHttpController(
      Supplier<McpEndpoint> endpoints, Map<String, String> environment, BrowserEventBus eventBus) {
    this.endpoints = Objects.requireNonNull(endpoints, "endpoints");
    this.environment = Map.copyOf(Objects.requireNonNull(environment, "environment"));
    this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
  }

  /** Starts the server with these settings, restarting it when it already runs. */
  public synchronized Result start(McpSettings settings) {
    Objects.requireNonNull(settings, "settings");
    stop();
    HttpMcpServer next =
        new HttpMcpServer(
            endpoints,
            settings.port(),
            settings.access() == McpAccess.NETWORK,
            Optional.ofNullable(environment.get("SEROX_MCP_TOKEN")),
            Optional.of(eventBus));
    try {
      next.start().toCompletableFuture().get();
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      return new Result(false, false, "시작이 중단됐어요.");
    } catch (ExecutionException failure) {
      if (failure.getCause() instanceof HttpMcpServer.StartException start) {
        return new Result(false, start.portInUse(), start.getMessage());
      }
      return new Result(false, false, "서버를 시작하지 못했어요.");
    }
    server = next;
    return Result.started();
  }

  /** Stops the server if it runs. */
  public synchronized void stop() {
    if (server != null) {
      server.stop();
      server = null;
    }
  }

  public synchronized boolean isRunning() {
    return server != null && server.isRunning();
  }

  /** The port in use, or 0 when stopped. */
  public synchronized int port() {
    return server == null ? 0 : server.port();
  }

  @Override
  public void close() {
    stop();
  }
}
