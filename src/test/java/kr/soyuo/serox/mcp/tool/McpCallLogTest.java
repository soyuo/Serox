package kr.soyuo.serox.mcp.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class McpCallLogTest {
  @Test
  void keepsTheNewestCallsFirstAndBoundsTheList() {
    var log = new McpCallLog();
    for (int i = 0; i < McpCallLog.CAPACITY + 5; i++) {
      log.record("t", "q" + i, "c", Instant.EPOCH, true);
    }

    assertEquals(McpCallLog.CAPACITY, log.recent().size());
    assertEquals("q" + (McpCallLog.CAPACITY + 4), log.recent().getFirst().summary());
  }

  @Test
  void boundsAndCleansTheQueryText() {
    var log = new McpCallLog();
    log.record("t", "x".repeat(500), "c", Instant.EPOCH, false);

    assertEquals(80, log.recent().getFirst().summary().length());
  }
}
