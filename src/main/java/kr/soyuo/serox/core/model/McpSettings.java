package kr.soyuo.serox.core.model;

import java.util.Objects;

/**
 * How the MCP HTTP server runs: its port, who may connect, whether it starts with Serox, and
 * whether the search tool is offered to agents.
 */
public record McpSettings(
    int port, McpAccess access, boolean autoStart, boolean searchToolEnabled) {
  /** The port the design shows. */
  public static final int DEFAULT_PORT = 3845;

  /** Ports below this need special rights on many systems. */
  public static final int MIN_PORT = 1024;

  public static final int MAX_PORT = 65535;

  /** Validates the port and the access choice. */
  public McpSettings {
    Objects.requireNonNull(access, "access");
    if (!validPort(port)) {
      throw new IllegalArgumentException("port must be between 1024 and 65535");
    }
  }

  /** Settings with the search tool on, which is how it starts. */
  public McpSettings(int port, McpAccess access, boolean autoStart) {
    this(port, access, autoStart, true);
  }

  /** Off at start and for this computer only, which is the safe way to begin. */
  public static McpSettings defaults() {
    return new McpSettings(DEFAULT_PORT, McpAccess.LOCAL_ONLY, false);
  }

  /** Whether a port number can be used by the server. */
  public static boolean validPort(int port) {
    return port >= MIN_PORT && port <= MAX_PORT;
  }

  /** These settings with another port. */
  public McpSettings withPort(int newPort) {
    return new McpSettings(newPort, access, autoStart, searchToolEnabled);
  }

  /** These settings with another access scope. */
  public McpSettings withAccess(McpAccess newAccess) {
    return new McpSettings(port, newAccess, autoStart, searchToolEnabled);
  }

  /** These settings with automatic start switched on or off. */
  public McpSettings withAutoStart(boolean start) {
    return new McpSettings(port, access, start, searchToolEnabled);
  }

  /** These settings with the search tool offered or hidden. */
  public McpSettings withSearchToolEnabled(boolean enabled) {
    return new McpSettings(port, access, autoStart, enabled);
  }
}
