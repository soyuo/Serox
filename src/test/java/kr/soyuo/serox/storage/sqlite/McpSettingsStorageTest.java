package kr.soyuo.serox.storage.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import kr.soyuo.serox.core.model.BrowserSettings;
import kr.soyuo.serox.core.model.McpAccess;
import kr.soyuo.serox.core.model.McpSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class McpSettingsStorageTest {
  @TempDir Path tempDir;

  @Test
  void defaultsAreOffForThisComputerOnly() {
    var defaults = McpSettings.defaults();

    assertEquals(3845, defaults.port());
    assertEquals(McpAccess.LOCAL_ONLY, defaults.access());
    assertFalse(defaults.autoStart());
    try (var storage = new SqliteStorage(tempDir.resolve("fresh.db"))) {
      assertEquals(defaults, storage.mcpSettings().load().toCompletableFuture().join());
    }
  }

  @Test
  void settingsSurviveReopeningAndDoNotTouchTheBrowserSettings() {
    var chosen = new McpSettings(4010, McpAccess.NETWORK, true);
    var browser = BrowserSettings.defaults().withRetention(kr.soyuo.serox.core.model.HistoryRetention.WEEK);
    try (var storage = new SqliteStorage(tempDir.resolve("mcp.db"))) {
      storage.settings().save(browser).toCompletableFuture().join();
      storage.mcpSettings().save(chosen).toCompletableFuture().join();
    }
    try (var reopened = new SqliteStorage(tempDir.resolve("mcp.db"))) {
      assertEquals(chosen, reopened.mcpSettings().load().toCompletableFuture().join());
      assertEquals(browser, reopened.settings().load().toCompletableFuture().join());
    }
  }

  @Test
  void aStoredPortOutOfRangeFallsBackToTheDefault() throws Exception {
    try (var storage = new SqliteStorage(tempDir.resolve("bad.db"))) {
      storage.mcpSettings().save(new McpSettings(4010, McpAccess.NETWORK, true))
          .toCompletableFuture().join();
      try (var connection =
              java.sql.DriverManager.getConnection("jdbc:sqlite:" + tempDir.resolve("bad.db"));
          var statement = connection.createStatement()) {
        statement.executeUpdate("UPDATE settings SET value='80' WHERE key='mcp.port'");
        statement.executeUpdate("UPDATE settings SET value='EVERYONE' WHERE key='mcp.access'");
      }
      var loaded = storage.mcpSettings().load().toCompletableFuture().join();

      assertEquals(McpSettings.DEFAULT_PORT, loaded.port());
      assertEquals(McpAccess.LOCAL_ONLY, loaded.access());
      assertTrue(loaded.autoStart());
    }
  }

  @Test
  void portsBelow1024OrAbove65535AreRefused() {
    assertThrows(IllegalArgumentException.class, () -> new McpSettings(80, McpAccess.LOCAL_ONLY, false));
    assertThrows(IllegalArgumentException.class, () -> McpSettings.defaults().withPort(70000));
    assertTrue(McpSettings.validPort(1024));
    assertTrue(McpSettings.validPort(65535));
    assertFalse(McpSettings.validPort(0));
    assertEquals(
        new McpSettings(5000, McpAccess.NETWORK, true),
        McpSettings.defaults().withPort(5000).withAccess(McpAccess.NETWORK).withAutoStart(true));
  }
}
