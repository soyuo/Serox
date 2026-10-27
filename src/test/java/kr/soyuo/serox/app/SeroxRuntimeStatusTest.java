package kr.soyuo.serox.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import kr.soyuo.serox.core.event.ConnectionStatusChanged;
import kr.soyuo.serox.core.event.ServiceId;
import kr.soyuo.serox.core.event.ServiceStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SeroxRuntimeStatusTest {
  @TempDir Path tempDir;

  private Map<ServiceId, ServiceStatus> announce(Map<String, String> environment) {
    Map<ServiceId, ServiceStatus> seen = new EnumMap<>(ServiceId.class);
    try (var runtime = AppBootstrap.createRuntime(environment)) {
      runtime
          .eventBus()
          .subscribe(ConnectionStatusChanged.class, event -> seen.put(event.service(), event.status()));
      runtime.announceServiceStatus();
    }
    return seen;
  }

  @Test
  void statusesFollowTheConfiguredKeysAndStorage() {
    var configured =
        announce(
            Map.of(
                "SEROX_DATA_DIR", tempDir.resolve("a").toString(),
                "SEROX_BRAVE_SEARCH_API_KEY", "brave-key",
                "SEROX_ANTHROPIC_API_KEY", "claude-key"));
    assertEquals(ServiceStatus.CONNECTED, configured.get(ServiceId.SEARCH));
    assertEquals(ServiceStatus.CONNECTED, configured.get(ServiceId.AI));
    assertEquals(ServiceStatus.CONNECTED, configured.get(ServiceId.STORAGE));
    assertEquals(ServiceStatus.DISCONNECTED, configured.get(ServiceId.MCP));

    var bare = announce(Map.of("SEROX_DATA_DIR", tempDir.resolve("b").toString()));
    // Search has a no-account fallback, so it is usable without a key.
    assertEquals(ServiceStatus.CONNECTED, bare.get(ServiceId.SEARCH));
    assertEquals(ServiceStatus.DISCONNECTED, bare.get(ServiceId.AI));
  }
}
