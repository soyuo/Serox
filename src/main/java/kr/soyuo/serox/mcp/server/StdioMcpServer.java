package kr.soyuo.serox.mcp.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;
import java.util.logging.Logger;
import kr.soyuo.serox.core.event.BrowserEventBus;
import kr.soyuo.serox.core.event.ConnectionStatusChanged;
import kr.soyuo.serox.core.event.ServiceId;
import kr.soyuo.serox.core.event.ServiceStatus;

/** Serves one MCP session over newline-delimited JSON-RPC on the given streams. */
public final class StdioMcpServer implements SeroxMcpServer {
  private static final Logger LOGGER = Logger.getLogger(StdioMcpServer.class.getName());

  private final McpEndpoint endpoint;
  private final InputStream input;
  private final PrintWriter output;
  private final Optional<BrowserEventBus> eventBus;
  private final CompletableFuture<Void> finished = new CompletableFuture<>();
  private volatile boolean running;

  /** Status changes are published to the bus when one is supplied. */
  public StdioMcpServer(
      McpEndpoint endpoint,
      InputStream input,
      OutputStream output,
      Optional<BrowserEventBus> eventBus) {
    this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
    this.input = Objects.requireNonNull(input, "input");
    this.output =
        new PrintWriter(
            new OutputStreamWriter(Objects.requireNonNull(output, "output"), StandardCharsets.UTF_8),
            false);
    this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
  }

  @Override
  public synchronized CompletionStage<Void> start() {
    if (running || finished.isDone()) {
      return finished;
    }
    running = true;
    publish(ServiceStatus.CONNECTED, "켜짐");
    Thread.ofVirtual().name("serox-mcp-reader").start(this::readLoop);
    return finished;
  }

  @Override
  public CompletionStage<Void> stop() {
    close(ServiceStatus.DISCONNECTED, "꺼짐");
    return finished;
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  private void readLoop() {
    try (BufferedReader lines =
        new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
      String line;
      while (running && (line = lines.readLine()) != null) {
        if (!line.isBlank()) {
          safeReceive(line);
        }
      }
      close(ServiceStatus.DISCONNECTED, "꺼짐");
    } catch (IOException exception) {
      LOGGER.log(Level.WARNING, "MCP input closed unexpectedly.");
      close(ServiceStatus.FAILED, "연결 실패");
    }
  }

  private void safeReceive(String line) {
    try {
      endpoint.receive(line, this::write);
    } catch (RuntimeException exception) {
      LOGGER.log(Level.WARNING, "MCP message handling failed.");
    }
  }

  private synchronized void write(String json) {
    output.print(json);
    output.print('\n');
    output.flush();
  }

  private void close(ServiceStatus status, String message) {
    boolean wasRunning;
    synchronized (this) {
      wasRunning = running;
      running = false;
    }
    if (wasRunning) {
      endpoint.cancelAll();
      publish(status, message);
      finished.complete(null);
    }
  }

  private void publish(ServiceStatus status, String message) {
    eventBus.ifPresent(
        bus -> {
          try {
            bus.publish(new ConnectionStatusChanged(Instant.now(), ServiceId.MCP, status, message));
          } catch (RuntimeException exception) {
            LOGGER.log(Level.FINE, "MCP status could not be published.");
          }
        });
  }
}
