package kr.soyuo.serox.mcp.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import kr.soyuo.serox.mcp.server.McpProtocolException;
import org.junit.jupiter.api.Test;

class AccessPolicyTest {
  private static final Set<String> OFFERED = Set.of("search_and_summarize");

  private static JsonObject client(String name) {
    JsonObject info = new JsonObject();
    info.addProperty("name", name);
    JsonObject params = new JsonObject();
    params.add("clientInfo", info);
    return params;
  }

  private static AccessPolicy policy(ApprovalGate gate, Clock clock) {
    return new AccessPolicy(gate, OFFERED, 1, 3, clock);
  }

  @Test
  void nothingIsAllowedWithoutUserApproval() {
    var policy = policy(new EnvironmentApprovalGate(Map.of()), Clock.systemUTC());

    var denied = assertThrows(McpProtocolException.class, () -> policy.authorizeSession(client("agent")));
    assertTrue(denied.getMessage().startsWith("Access denied"));
    assertTrue(policy.approvedTools().isEmpty());
    assertThrows(McpProtocolException.class, () -> policy.begin("search_and_summarize"));
  }

  @Test
  void onlyToolsListedByTheUserAreApproved() {
    var gate =
        new EnvironmentApprovalGate(
            Map.of(EnvironmentApprovalGate.VARIABLE, " search_and_summarize , read_file "));
    var policy = policy(gate, Clock.systemUTC());

    policy.authorizeSession(client("agent"));

    assertEquals(Set.of("search_and_summarize"), policy.approvedTools());
    policy.begin("search_and_summarize").close();
    assertThrows(McpProtocolException.class, () -> policy.begin("read_file"));
  }

  @Test
  void concurrentCallsAndCallRateAreBounded() {
    var clock = new MovableClock();
    var policy = policy((name, tools) -> tools, clock);
    policy.authorizeSession(client("agent"));

    var first = policy.begin("search_and_summarize");
    var busy = assertThrows(McpProtocolException.class, () -> policy.begin("search_and_summarize"));
    assertEquals(AccessPolicy.RATE_LIMITED, errorCode(busy));
    first.close();

    policy.begin("search_and_summarize").close();
    policy.begin("search_and_summarize").close();
    assertThrows(McpProtocolException.class, () -> policy.begin("search_and_summarize"));

    clock.advance(Duration.ofMinutes(2));
    policy.begin("search_and_summarize").close();
  }

  private static int errorCode(McpProtocolException exception) {
    // The code is verified through the public constant it must equal.
    return exception.getMessage().startsWith("Too many") ? AccessPolicy.RATE_LIMITED : -1;
  }

  private static final class MovableClock extends Clock {
    private Instant now = Instant.parse("2026-10-27T06:00:00Z");

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public java.time.ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
