package kr.soyuo.serox.ai.provider;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import kr.soyuo.serox.ai.api.AiCompleted;
import kr.soyuo.serox.ai.api.AiFailed;
import kr.soyuo.serox.ai.api.AiTextDelta;
import org.junit.jupiter.api.Test;

class ClaudeCodeProviderTest {
  @Test
  void readsTextDeltasAndTheFinalResult() {
    assertEquals(
        List.of(new AiTextDelta("hi")),
        ClaudeCodeProvider.events(
            "{\"type\":\"stream_event\",\"event\":{\"type\":\"content_block_delta\","
                + "\"delta\":{\"type\":\"text_delta\",\"text\":\"hi\"}}}"));
    assertEquals(
        List.of(new AiCompleted(List.of())),
        ClaudeCodeProvider.events(
            "{\"type\":\"result\",\"subtype\":\"success\",\"is_error\":false,\"result\":\"hi\"}"));
    assertTrue(ClaudeCodeProvider.events("{\"type\":\"system\"}").isEmpty());
    assertTrue(ClaudeCodeProvider.events("not json").isEmpty());
  }

  @Test
  void mapsFailedResults() {
    var login =
        ClaudeCodeProvider.events(
            "{\"type\":\"result\",\"subtype\":\"success\",\"is_error\":true,"
                + "\"result\":\"Not logged in · Please run /login\"}");
    assertEquals("unauthorized", ((AiFailed) login.getFirst()).code());
    var limit =
        ClaudeCodeProvider.events(
            "{\"type\":\"result\",\"subtype\":\"success\",\"is_error\":true,"
                + "\"result\":\"Usage limit reached\"}");
    assertEquals("rate_limited", ((AiFailed) limit.getFirst()).code());
  }

  @Test
  void turnsOffToolsSettingsAndHistory() {
    var arguments = ClaudeCodeProvider.arguments("sonnet", Path.of("system.txt"));
    assertTrue(arguments.contains("--no-session-persistence"));
    assertEquals("", arguments.get(arguments.indexOf("--tools") + 1));
    assertEquals("", arguments.get(arguments.indexOf("--setting-sources") + 1));
    assertEquals("sonnet", arguments.get(arguments.indexOf("--model") + 1));
    assertThrows(IllegalArgumentException.class, () -> new ClaudeCodeProvider("bad model"));
  }
}
