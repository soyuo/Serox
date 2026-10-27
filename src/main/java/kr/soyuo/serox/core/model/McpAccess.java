package kr.soyuo.serox.core.model;

/** Who may reach the MCP server over HTTP. */
public enum McpAccess {
  /** Only programs on this computer. */
  LOCAL_ONLY,
  /** Other computers on the network too; every request then needs the bearer token. */
  NETWORK
}
