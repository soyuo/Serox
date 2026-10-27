package kr.soyuo.serox.mcp.security;

import com.google.gson.JsonObject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Set;
import kr.soyuo.serox.mcp.server.McpProtocolException;

/**
 * Grants a session the minimum it needs: only user-approved tools, a bounded number of concurrent
 * calls, and a bounded call rate. Nothing is allowed before the session has been approved.
 */
public final class AccessPolicy {
  public static final int DENIED = -32001;
  public static final int RATE_LIMITED = -32003;

  private final ApprovalGate gate;
  private final Set<String> offeredTools;
  private final int maxConcurrent;
  private final int maxCallsPerMinute;
  private final Clock clock;
  private final Deque<Instant> recentCalls = new ArrayDeque<>();
  private Set<String> approved = Set.of();
  private int running;

  public AccessPolicy(
      ApprovalGate gate,
      Set<String> offeredTools,
      int maxConcurrent,
      int maxCallsPerMinute,
      Clock clock) {
    this.gate = Objects.requireNonNull(gate, "gate");
    this.offeredTools = Set.copyOf(offeredTools);
    this.maxConcurrent = maxConcurrent;
    this.maxCallsPerMinute = maxCallsPerMinute;
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /** Asks the user's approval for the connecting client; refuses the session when nothing is allowed. */
  public synchronized void authorizeSession(JsonObject initializeParams) {
    String clientName = "unknown";
    if (initializeParams.has("clientInfo") && initializeParams.get("clientInfo").isJsonObject()) {
      var info = initializeParams.getAsJsonObject("clientInfo");
      if (info.has("name") && info.get("name").isJsonPrimitive()) {
        clientName = info.get("name").getAsString();
      }
    }
    approved = Set.copyOf(gate.approve(clientName, offeredTools));
    if (approved.isEmpty()) {
      throw new McpProtocolException(
          DENIED, "Access denied: the user has not approved any Serox tools for this client.");
    }
  }

  /** Tools the current session may see and call. */
  public synchronized Set<String> approvedTools() {
    return approved;
  }

  /** Reserves capacity for one call; close the permit when the call ends. */
  public synchronized Permit begin(String tool) {
    if (!approved.contains(tool)) {
      throw new McpProtocolException(DENIED, "Tool not approved.");
    }
    Instant now = clock.instant();
    Instant windowStart = now.minus(Duration.ofMinutes(1));
    while (!recentCalls.isEmpty() && recentCalls.peekFirst().isBefore(windowStart)) {
      recentCalls.removeFirst();
    }
    if (running >= maxConcurrent || recentCalls.size() >= maxCallsPerMinute) {
      throw new McpProtocolException(RATE_LIMITED, "Too many requests. Try again shortly.");
    }
    recentCalls.addLast(now);
    running++;
    return new Permit();
  }

  /** Handle returned by {@link #begin}; releases the concurrency slot when closed. */
  public final class Permit implements AutoCloseable {
    private boolean closed;

    @Override
    public void close() {
      synchronized (AccessPolicy.this) {
        if (!closed) {
          closed = true;
          running--;
        }
      }
    }
  }
}
