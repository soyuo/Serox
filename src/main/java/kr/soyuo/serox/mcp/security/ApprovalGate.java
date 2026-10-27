package kr.soyuo.serox.mcp.security;

import java.util.Set;

/** Decides which of the offered tools a connecting client may use. */
@FunctionalInterface
public interface ApprovalGate {
  /** Returns the subset of {@code offeredTools} the user has approved for this client. */
  Set<String> approve(String clientName, Set<String> offeredTools);
}
