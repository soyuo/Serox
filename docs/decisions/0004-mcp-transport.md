# 0004 — MCP transport and implementation

- Status: accepted
- Date: 2026-10-27
- Related: STEP 12, SPEC 8

## Decision

Serox exposes its MCP server over **stdio** with newline-delimited JSON-RPC 2.0, protocol
version `2025-03-26`. The server is a small in-tree implementation using Gson; no MCP SDK
dependency is added.

It runs headless in its own process (`Serox --mcp`) which the agent launches. The desktop
window is not involved, so a failure of the server cannot affect browsing and vice versa.

## Why

- stdio needs no open network port, so there is nothing for other local programs or web
  pages to connect to.
- The surface we need is tiny (`initialize`, `ping`, `tools/list`, `tools/call`,
  cancellation). An SDK would add transitive dependencies for little benefit.
- The permission boundary (see `mcp.security`) is easier to audit when every method is
  visible in this repository.

## Consequences

- Only one agent talks to a server process; each launch is a separate, short-lived session.
- Streamable HTTP can be added later behind the same `McpEndpoint`.
