package kr.soyuo.serox.ui;

import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** The setup text an agent app needs to reach the MCP server, as the settings page offers it. */
final class McpConnectionGuide {
  /** How the agent reaches Serox. */
  enum Transport {
    STDIO("표준 입출력 (stdio)"),
    HTTP("HTTP 주소");

    private final String label;

    Transport(String label) {
      this.label = label;
    }

    String label() {
      return label;
    }
  }

  /** The app being connected. */
  enum App {
    CLAUDE_CODE("Claude Code"),
    CODEX("Codex"),
    GEMINI_CLI("Gemini CLI"),
    OTHER("그 밖의 앱 (JSON)");

    private final String label;

    App(String label) {
      this.label = label;
    }

    String label() {
      return label;
    }
  }

  /** Where to put {@code text}, and the text itself. */
  record Snippet(String where, String text) {}

  private static final String APPROVAL = "SEROX_MCP_APPROVED_TOOLS=search_and_summarize";
  private static final String COMMAND = "\"command\": \"java\"";
  private static final String ARGS = "\"args\": [\"-jar\", \"serox.jar\", \"--mcp\"]";
  private static final String ENV =
      "\"env\": { \"SEROX_MCP_APPROVED_TOOLS\": \"search_and_summarize\" }";

  private McpConnectionGuide() {}

  /** The address agents connect to over HTTP. */
  static String address(int port) {
    return String.format(Locale.ROOT, "http://localhost:%d/mcp", port);
  }

  /** One line under the connection choice. */
  static String caption(Transport transport, int port) {
    return switch (transport) {
      case STDIO -> "에이전트가 Serox를 직접 실행해서 연결해요. Serox를 미리 켜 둘 필요가 없어요.";
      case HTTP -> "Serox가 켜져 있을 때 주소로 연결해요. 주소: " + address(port);
    };
  }

  /**
   * What the agent still needs besides the snippet. A stdio session is refused until the user
   * approves the tool, so that snippet carries the approval; an HTTP server reads it from its own
   * environment, so the note says so. Empty when nothing more is needed.
   */
  static String requirement(Transport transport, boolean networkAccess) {
    if (transport == Transport.STDIO) {
      return "";
    }
    String approval = "Serox를 켜는 환경에 " + APPROVAL + "가 있어야 에이전트가 도구를 쓸 수 있어요.";
    return networkAccess
        ? approval
            + " 같은 네트워크에서는 요청마다 Authorization: Bearer 토큰 헤더가 필요하고,"
            + " 토큰은 Serox를 켜는 환경의 SEROX_MCP_TOKEN과 같아야 해요."
        : approval;
  }

  static Snippet snippet(Transport transport, App app, int port) {
    return transport == Transport.HTTP ? http(app, port) : stdio(app);
  }

  private static Snippet http(App app, int port) {
    String url = address(port);
    return switch (app) {
      case CLAUDE_CODE ->
          new Snippet("터미널에서 실행", "claude mcp add --transport http serox " + url);
      case CODEX ->
          new Snippet("~/.codex/config.toml에 추가", "[mcp_servers.serox]\nurl = \"" + url + "\"");
      case GEMINI_CLI ->
          new Snippet("~/.gemini/settings.json에 추가", json("\"httpUrl\": \"" + url + "\""));
      case OTHER -> new Snippet("앱의 MCP 설정 파일에 추가", json("\"url\": \"" + url + "\""));
    };
  }

  private static Snippet stdio(App app) {
    return switch (app) {
      case CLAUDE_CODE ->
          new Snippet(
              "터미널에서 실행",
              "claude mcp add serox -e " + APPROVAL + " -- java -jar serox.jar --mcp");
      case CODEX ->
          new Snippet(
              "~/.codex/config.toml에 추가",
              "[mcp_servers.serox]\n"
                  + "command = \"java\"\n"
                  + "args = [\"-jar\", \"serox.jar\", \"--mcp\"]\n"
                  + "env = { SEROX_MCP_APPROVED_TOOLS = \"search_and_summarize\" }");
      case GEMINI_CLI -> new Snippet("~/.gemini/settings.json에 추가", json(COMMAND, ARGS, ENV));
      case OTHER -> new Snippet("앱의 MCP 설정 파일에 추가", json(COMMAND, ARGS, ENV));
    };
  }

  /** A {@code mcpServers} block whose {@code serox} entry holds the given property lines. */
  private static String json(String... properties) {
    String body =
        Stream.of(properties).map(line -> "      " + line).collect(Collectors.joining(",\n"));
    return "{\n  \"mcpServers\": {\n    \"serox\": {\n" + body + "\n    }\n  }\n}";
  }
}
