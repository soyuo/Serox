package kr.soyuo.serox.mcp.security;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Approval given by the user when they configure the agent: the tools listed in
 * {@code SEROX_MCP_APPROVED_TOOLS} (comma separated) are allowed. With no list nothing is allowed.
 */
public final class EnvironmentApprovalGate implements ApprovalGate {
  public static final String VARIABLE = "SEROX_MCP_APPROVED_TOOLS";

  private final Set<String> approved;

  public EnvironmentApprovalGate(Map<String, String> environment) {
    String value = environment.get(VARIABLE);
    approved =
        value == null
            ? Set.of()
            : Arrays.stream(value.split(","))
                .map(String::strip)
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
  }

  @Override
  public Set<String> approve(String clientName, Set<String> offeredTools) {
    return offeredTools.stream()
        .filter(approved::contains)
        .collect(Collectors.toUnmodifiableSet());
  }
}
