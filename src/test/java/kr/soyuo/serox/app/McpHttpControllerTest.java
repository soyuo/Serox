package kr.soyuo.serox.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import kr.soyuo.serox.core.event.ConnectionStatusChanged;
import kr.soyuo.serox.core.event.InProcessBrowserEventBus;
import kr.soyuo.serox.core.event.ServiceStatus;
import kr.soyuo.serox.core.model.McpAccess;
import kr.soyuo.serox.core.model.McpSettings;
import kr.soyuo.serox.mcp.server.McpEndpoint;
import org.junit.jupiter.api.Test;

class McpHttpControllerTest {
  private static int freePort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
      return socket.getLocalPort();
    }
  }

  private static boolean listening(int port) {
    try (Socket socket = new Socket("127.0.0.1", port)) {
      return true;
    } catch (IOException refused) {
      return false;
    }
  }

  @Test
  void startsAndStopsTheServerOnTheChosenPort() throws Exception {
    var bus = new InProcessBrowserEventBus();
    List<ServiceStatus> states = new ArrayList<>();
    bus.subscribe(ConnectionStatusChanged.class, event -> states.add(event.status()));
    int port = freePort();
    try (var controller = new McpHttpController(() -> new McpEndpoint("test"), Map.of(), bus)) {
      var result = controller.start(new McpSettings(port, McpAccess.LOCAL_ONLY, false));

      assertTrue(result.running());
      assertTrue(controller.isRunning());
      assertEquals(port, controller.port());
      assertTrue(listening(port));

      controller.stop();

      assertFalse(controller.isRunning());
      assertEquals(0, controller.port());
      assertFalse(listening(port));
    }
    assertEquals(List.of(ServiceStatus.CONNECTED, ServiceStatus.DISCONNECTED), states);
  }

  @Test
  void aTakenPortIsReportedAndLeavesTheServerOff() throws Exception {
    var bus = new InProcessBrowserEventBus();
    try (ServerSocket taken = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
        var controller = new McpHttpController(() -> new McpEndpoint("test"), Map.of(), bus)) {
      var result =
          controller.start(new McpSettings(taken.getLocalPort(), McpAccess.LOCAL_ONLY, false));

      assertFalse(result.running());
      assertTrue(result.portInUse());
      assertTrue(result.message().contains(Integer.toString(taken.getLocalPort())));
      assertFalse(controller.isRunning());
    }
  }

  @Test
  void networkAccessNeedsTheTokenFromTheEnvironment() throws Exception {
    var bus = new InProcessBrowserEventBus();
    int port = freePort();
    try (var without = new McpHttpController(() -> new McpEndpoint("test"), Map.of(), bus)) {
      var refused = without.start(new McpSettings(port, McpAccess.NETWORK, false));

      assertFalse(refused.running());
      assertFalse(refused.portInUse());
      assertTrue(refused.message().contains("SEROX_MCP_TOKEN"));
    }
    try (var with =
        new McpHttpController(
            () -> new McpEndpoint("test"), Map.of("SEROX_MCP_TOKEN", "abc"), bus)) {
      assertTrue(with.start(new McpSettings(port, McpAccess.NETWORK, false)).running());
    }
  }

  @Test
  void startingAgainRestartsOnTheNewPort() throws Exception {
    var bus = new InProcessBrowserEventBus();
    int first = freePort();
    try (var controller = new McpHttpController(() -> new McpEndpoint("test"), Map.of(), bus)) {
      controller.start(new McpSettings(first, McpAccess.LOCAL_ONLY, false));
      int second = freePort();

      assertTrue(controller.start(new McpSettings(second, McpAccess.LOCAL_ONLY, false)).running());

      assertEquals(second, controller.port());
      assertFalse(listening(first));
    }
  }
}
