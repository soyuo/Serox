package kr.soyuo.serox.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import kr.soyuo.serox.ui.McpConnectionGuide.App;
import kr.soyuo.serox.ui.McpConnectionGuide.Transport;
import org.junit.jupiter.api.Test;

class McpConnectionGuideTest {
  @Test
  void httpSnippetsMatchTheDesign() {
    assertEquals(
        "claude mcp add --transport http serox http://localhost:3845/mcp",
        McpConnectionGuide.snippet(Transport.HTTP, App.CLAUDE_CODE, 3845).text());
    assertEquals(
        "[mcp_servers.serox]\nurl = \"http://localhost:3845/mcp\"",
        McpConnectionGuide.snippet(Transport.HTTP, App.CODEX, 3845).text());
    assertEquals(
        "~/.gemini/settings.json에 추가",
        McpConnectionGuide.snippet(Transport.HTTP, App.GEMINI_CLI, 3845).where());
    assertEquals(
        "{\n  \"mcpServers\": {\n    \"serox\": {\n      \"httpUrl\": \"http://localhost:3845/mcp\"\n    }\n  }\n}",
        McpConnectionGuide.snippet(Transport.HTTP, App.GEMINI_CLI, 3845).text());
    assertEquals(
        "{\n  \"mcpServers\": {\n    \"serox\": {\n      \"url\": \"http://localhost:3845/mcp\"\n    }\n  }\n}",
        McpConnectionGuide.snippet(Transport.HTTP, App.OTHER, 3845).text());
  }

  @Test
  void theSnippetFollowsThePort() {
    for (App app : App.values()) {
      assertTrue(McpConnectionGuide.snippet(Transport.HTTP, app, 4010).text().contains(":4010/mcp"));
      assertFalse(McpConnectionGuide.snippet(Transport.HTTP, app, 4010).text().contains("3845"));
    }
    assertTrue(McpConnectionGuide.caption(Transport.HTTP, 4010).endsWith("http://localhost:4010/mcp"));
  }

  @Test
  void everyJsonSnippetIsValidJson() {
    for (Transport transport : Transport.values()) {
      for (App app : new App[] {App.GEMINI_CLI, App.OTHER}) {
        var json = McpConnectionGuide.snippet(transport, app, 3845).text();
        var serox =
            JsonParser.parseString(json)
                .getAsJsonObject()
                .getAsJsonObject("mcpServers")
                .getAsJsonObject("serox");
        assertFalse(serox.entrySet().isEmpty(), transport + " " + app);
      }
    }
  }

  @Test
  void stdioSnippetsCarryTheToolApprovalOrTheSessionWouldBeRefused() {
    for (App app : App.values()) {
      var text = McpConnectionGuide.snippet(Transport.STDIO, app, 3845).text();

      assertTrue(text.contains("SEROX_MCP_APPROVED_TOOLS"), app.name());
      assertTrue(text.contains("--mcp"), app.name());
    }
    assertEquals("", McpConnectionGuide.requirement(Transport.STDIO, false));
  }

  @Test
  void httpNotesTellWhatTheServerSideNeeds() {
    var local = McpConnectionGuide.requirement(Transport.HTTP, false);
    var network = McpConnectionGuide.requirement(Transport.HTTP, true);

    assertTrue(local.contains("SEROX_MCP_APPROVED_TOOLS"));
    assertFalse(local.contains("SEROX_MCP_TOKEN"));
    assertTrue(network.contains("SEROX_MCP_TOKEN"));
    assertTrue(network.contains("Bearer"));
  }
}
