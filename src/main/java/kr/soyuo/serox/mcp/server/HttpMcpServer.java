package kr.soyuo.serox.mcp.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import kr.soyuo.serox.core.event.BrowserEventBus;
import kr.soyuo.serox.core.event.ConnectionStatusChanged;
import kr.soyuo.serox.core.event.ServiceId;
import kr.soyuo.serox.core.event.ServiceStatus;

/**
 * Serves MCP over HTTP at {@code /mcp}: one JSON-RPC message per POST, one session per client.
 *
 * <p>A page in a browser can reach a local server, so loopback mode accepts only requests whose
 * {@code Host} names this machine and refuses any request that carries an {@code Origin} header,
 * which browsers add and agents do not. Network mode is open to other machines, so every request
 * must carry the bearer token.
 */
public final class HttpMcpServer implements SeroxMcpServer {
  /** The path agents connect to. */
  public static final String PATH = "/mcp";

  static final String SESSION_HEADER = "Mcp-Session-Id";
  private static final Logger LOGGER = Logger.getLogger(HttpMcpServer.class.getName());
  private static final int MAX_BODY_BYTES = 1 << 20;
  private static final int MAX_SESSIONS = 16;
  private static final long RESPONSE_TIMEOUT_SECONDS = 120;

  private final Supplier<McpEndpoint> endpoints;
  private final int requestedPort;
  private final boolean networkAccess;
  private final Optional<String> token;
  private final Optional<BrowserEventBus> eventBus;
  private final Map<String, McpEndpoint> sessions = new LinkedHashMap<>();
  private HttpServer server;
  private ExecutorService executor;

  /**
   * {@code endpoints} makes a fresh endpoint for each client session. A {@code port} of 0 picks a
   * free one. With {@code networkAccess} a non-blank {@code token} is required.
   */
  public HttpMcpServer(
      Supplier<McpEndpoint> endpoints,
      int port,
      boolean networkAccess,
      Optional<String> token,
      Optional<BrowserEventBus> eventBus) {
    this.endpoints = Objects.requireNonNull(endpoints, "endpoints");
    if (port < 0 || port > 65535) {
      throw new IllegalArgumentException("port must be between 0 and 65535");
    }
    this.requestedPort = port;
    this.networkAccess = networkAccess;
    this.token = Objects.requireNonNull(token, "token").filter(value -> !value.isBlank());
    this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
  }

  /** Why the server could not start. */
  public static final class StartException extends Exception {
    private static final long serialVersionUID = 1L;
    private final boolean portInUse;

    StartException(String message, boolean portInUse, Throwable cause) {
      super(message, cause);
      this.portInUse = portInUse;
    }

    /** Whether another program already uses the port. */
    public boolean portInUse() {
      return portInUse;
    }
  }

  /**
   * Starts listening. The stage fails with a {@link StartException} when the port is taken or
   * network access has no token; the failure is also published as a status.
   */
  @Override
  public synchronized CompletionStage<Void> start() {
    if (server != null) {
      return CompletableFuture.completedFuture(null);
    }
    if (networkAccess && token.isEmpty()) {
      return failed(
          new StartException("같은 네트워크에 열려면 SEROX_MCP_TOKEN을 설정해야 해요.", false, null),
          "토큰이 필요해요");
    }
    try {
      InetAddress address =
          networkAccess
              ? new InetSocketAddress(0).getAddress()
              : InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
      server = HttpServer.create(new InetSocketAddress(address, requestedPort), 0);
    } catch (java.net.BindException exception) {
      return failed(
          new StartException(requestedPort + "번 포트를 다른 프로그램이 쓰고 있어요.", true, exception),
          "시작하지 못했어요");
    } catch (IOException exception) {
      return failed(
          new StartException("서버를 시작하지 못했어요.", false, exception), "시작하지 못했어요");
    }
    executor = Executors.newVirtualThreadPerTaskExecutor();
    server.setExecutor(executor);
    server.createContext(PATH, this::handle);
    server.start();
    publish(ServiceStatus.CONNECTED, "실행 중");
    return CompletableFuture.completedFuture(null);
  }

  private CompletionStage<Void> failed(StartException exception, String status) {
    publish(ServiceStatus.FAILED, status);
    return CompletableFuture.failedFuture(exception);
  }

  /** The port in use; useful when 0 was requested. Zero while stopped. */
  public synchronized int port() {
    return server == null ? 0 : server.getAddress().getPort();
  }

  @Override
  public CompletionStage<Void> stop() {
    HttpServer stopping;
    ExecutorService stoppingExecutor;
    java.util.List<McpEndpoint> open;
    synchronized (this) {
      stopping = server;
      stoppingExecutor = executor;
      server = null;
      executor = null;
    }
    synchronized (sessions) {
      open = java.util.List.copyOf(sessions.values());
      sessions.clear();
    }
    if (stopping != null) {
      stopping.stop(0);
      stoppingExecutor.shutdownNow();
      open.forEach(McpEndpoint::cancelAll);
      publish(ServiceStatus.DISCONNECTED, "꺼짐");
    }
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public synchronized boolean isRunning() {
    return server != null;
  }

  private void handle(HttpExchange exchange) throws IOException {
    try (exchange) {
      String method = exchange.getRequestMethod();
      if (!method.equals("POST") && !method.equals("DELETE")) {
        exchange.getResponseHeaders().set("Allow", "POST, DELETE");
        reply(exchange, 405, "");
        return;
      }
      int refused = refusal(exchange);
      if (refused != 0) {
        reply(exchange, refused, "");
        return;
      }
      if (method.equals("DELETE")) {
        endSession(exchange);
        return;
      }
      post(exchange);
    } catch (RuntimeException exception) {
      LOGGER.log(Level.WARNING, "MCP HTTP request failed.");
      reply(exchange, 500, "");
    }
  }

  /** The status to refuse the request with, or 0 when it may go on. */
  private int refusal(HttpExchange exchange) {
    var headers = exchange.getRequestHeaders();
    if (headers.getFirst("Origin") != null) {
      return 403;
    }
    if (!networkAccess && !isLoopbackHost(headers.getFirst("Host"))) {
      return 403;
    }
    if (networkAccess && !hasToken(headers.getFirst("Authorization"))) {
      return 401;
    }
    return 0;
  }

  private boolean hasToken(String authorization) {
    if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
      return false;
    }
    byte[] given = authorization.substring(7).strip().getBytes(StandardCharsets.UTF_8);
    return MessageDigest.isEqual(given, token.orElseThrow().getBytes(StandardCharsets.UTF_8));
  }

  static boolean isLoopbackHost(String hostHeader) {
    if (hostHeader == null) {
      return false;
    }
    String host =
        hostHeader.startsWith("[")
            ? hostHeader.substring(0, hostHeader.indexOf(']') + 1)
            : hostHeader.split(":", 2)[0];
    String lower = host.toLowerCase(Locale.ROOT);
    return lower.equals("localhost") || lower.equals("127.0.0.1") || lower.equals("[::1]");
  }

  private void post(HttpExchange exchange) throws IOException {
    String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
    if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("application/json")) {
      reply(exchange, 415, "");
      return;
    }
    String body = readBody(exchange.getRequestBody());
    if (body == null) {
      reply(exchange, 413, "");
      return;
    }
    JsonObject message;
    try {
      JsonElement parsed = JsonParser.parseString(body);
      message = parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
    } catch (RuntimeException invalid) {
      message = null;
    }
    boolean initialize =
        message != null
            && message.has("method")
            && message.get("method").isJsonPrimitive()
            && message.get("method").getAsString().equals("initialize");
    String sessionId = exchange.getRequestHeaders().getFirst(SESSION_HEADER);
    McpEndpoint endpoint;
    if (sessionId == null) {
      if (!initialize) {
        reply(exchange, 400, "");
        return;
      }
      sessionId = UUID.randomUUID().toString();
      endpoint = open(sessionId);
      exchange.getResponseHeaders().set(SESSION_HEADER, sessionId);
    } else {
      synchronized (sessions) {
        endpoint = sessions.get(sessionId);
      }
      if (endpoint == null) {
        reply(exchange, 404, "");
        return;
      }
    }
    if (message == null || !message.has("id")) {
      endpoint.receive(body, ignored -> {});
      reply(exchange, message == null ? 400 : 202, "");
      return;
    }
    CompletableFuture<String> answer = new CompletableFuture<>();
    endpoint.receive(body, answer::complete);
    try {
      String json = answer.get(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
      reply(exchange, 200, json);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      reply(exchange, 503, "");
    } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException late) {
      endpoint.cancelAll();
      reply(exchange, 504, "");
    }
  }

  private McpEndpoint open(String sessionId) {
    McpEndpoint created = endpoints.get();
    McpEndpoint evicted = null;
    synchronized (sessions) {
      if (sessions.size() >= MAX_SESSIONS) {
        String oldest = sessions.keySet().iterator().next();
        evicted = sessions.remove(oldest);
      }
      sessions.put(sessionId, created);
    }
    if (evicted != null) {
      evicted.cancelAll();
    }
    return created;
  }

  private void endSession(HttpExchange exchange) throws IOException {
    String sessionId = exchange.getRequestHeaders().getFirst(SESSION_HEADER);
    McpEndpoint ended;
    synchronized (sessions) {
      ended = sessionId == null ? null : sessions.remove(sessionId);
    }
    if (ended == null) {
      reply(exchange, 404, "");
      return;
    }
    ended.cancelAll();
    reply(exchange, 204, "");
  }

  /** The body as text, or null when it is longer than the limit. */
  private static String readBody(InputStream input) throws IOException {
    byte[] bytes = input.readNBytes(MAX_BODY_BYTES + 1);
    return bytes.length > MAX_BODY_BYTES ? null : new String(bytes, StandardCharsets.UTF_8);
  }

  private static void reply(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    if (bytes.length == 0) {
      exchange.sendResponseHeaders(status, -1);
      return;
    }
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
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
