package kr.soyuo.serox.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import kr.soyuo.serox.core.model.McpAccess;
import kr.soyuo.serox.core.model.McpSettings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class McpPageTest {
  private static boolean toolkitReady;

  @BeforeAll
  static void startToolkit() {
    try {
      CompletableFuture<Void> started = new CompletableFuture<>();
      try {
        Platform.startup(() -> started.complete(null));
      } catch (IllegalStateException alreadyRunning) {
        started.complete(null);
      }
      started.get(10, TimeUnit.SECONDS);
      toolkitReady = true;
    } catch (Exception | Error unavailable) {
      toolkitReady = false;
    }
  }

  private static <T> T onFx(Supplier<T> work) throws Exception {
    CompletableFuture<T> result = new CompletableFuture<>();
    Platform.runLater(
        () -> {
          try {
            result.complete(work.get());
          } catch (Throwable failure) {
            result.completeExceptionally(failure);
          }
        });
    return result.get(20, TimeUnit.SECONDS);
  }

  private static void walk(Node root, java.util.function.Consumer<Node> visit) {
    visit.accept(root);
    if (root instanceof ScrollPane scroll && scroll.getContent() != null) {
      walk(scroll.getContent(), visit);
    } else if (root instanceof Parent parent) {
      for (Node child : parent.getChildrenUnmodifiable()) {
        walk(child, visit);
      }
    }
  }

  private static <T extends Node> T find(Node root, Class<T> type, String accessibleText) {
    List<T> found = new ArrayList<>();
    walk(
        root,
        node -> {
          if (type.isInstance(node) && accessibleText.equals(node.getAccessibleText())) {
            found.add(type.cast(node));
          }
        });
    return found.isEmpty() ? null : found.getFirst();
  }

  private static Label labelWithClass(Node root, String styleClass) {
    List<Label> found = new ArrayList<>();
    walk(
        root,
        node -> {
          if (node instanceof Label label && label.getStyleClass().contains(styleClass)) {
            found.add(label);
          }
        });
    return found.isEmpty() ? null : found.getFirst();
  }

  private static Button button(Node root, String text) {
    List<Button> found = new ArrayList<>();
    walk(
        root,
        node -> {
          if (node instanceof Button button && text.equals(button.getText())) {
            found.add(button);
          }
        });
    return found.isEmpty() ? null : found.getFirst();
  }

  /** A server that starts or fails as told, and remembers what it was asked. */
  private static final class FakeServer implements McpActions {
    McpSettings settings = McpSettings.defaults();
    boolean running;
    final List<McpSettings> starts = new ArrayList<>();
    final List<McpSettings> saves = new ArrayList<>();
    int takenPort = -1;
    int stops;

    @Override
    public McpSettings settings() {
      return settings;
    }

    @Override
    public void save(McpSettings next) {
      settings = next;
      saves.add(next);
    }

    @Override
    public boolean running() {
      return running;
    }

    @Override
    public Result start(McpSettings next) {
      starts.add(next);
      if (next.port() == takenPort) {
        running = false;
        return new Result(false, true, next.port() + "번 포트를 다른 프로그램이 쓰고 있어요.");
      }
      running = true;
      return new Result(true, false, "");
    }

    @Override
    public void stop() {
      running = false;
      stops++;
    }
  }

  private static String chip(Node page) {
    return labelWithClass(page, "status-chip").getText();
  }

  @Test
  void startsOffShowingTheSettingsAndTheGuide() throws Exception {
    assumeTrue(toolkitReady);
    var server = new FakeServer();
    onFx(
        () -> {
          var page = new McpPage(server, text -> {});
          assertEquals("꺼짐", chip(page));
          assertEquals("3845", find(page, TextField.class, "포트").getText());
          assertEquals(
              "claude mcp add --transport http serox http://localhost:3845/mcp",
              labelWithClass(page, "code-block").getText());
          assertTrue(find(page, ToggleButton.class, "이 컴퓨터만").isSelected());
          return null;
        });
  }

  @Test
  void theSwitchStartsAndStopsTheServer() throws Exception {
    assumeTrue(toolkitReady);
    var server = new FakeServer();
    onFx(
        () -> {
          var page = new McpPage(server, text -> {});
          var power = find(page, ToggleButton.class, "MCP 서버 켜기");
          power.fire();
          assertEquals("실행 중", chip(page));
          power.fire();
          assertEquals("꺼짐", chip(page));
          return null;
        });
    assertEquals(1, server.starts.size());
    assertEquals(1, server.stops);
  }

  @Test
  void aTakenPortTurnsTheSwitchBackOffAndExplainsOnThePort() throws Exception {
    assumeTrue(toolkitReady);
    var server = new FakeServer();
    server.takenPort = 3845;
    onFx(
        () -> {
          var page = new McpPage(server, text -> {});
          var power = find(page, ToggleButton.class, "MCP 서버 켜기");
          power.fire();

          assertFalse(power.isSelected());
          assertEquals("시작하지 못했어요", chip(page));
          assertTrue(find(page, TextField.class, "포트").getStyleClass().contains("field-error"));
          var error = labelWithClass(page, "field-error-text");
          assertTrue(error.isVisible());
          assertTrue(error.getText().contains("3845번 포트를 다른 프로그램이 쓰고 있어요."));
          assertTrue(error.getText().contains("다른 번호를 입력해 주세요."));

          var port = find(page, TextField.class, "포트");
          port.setText("3900");
          port.fireEvent(new javafx.event.ActionEvent());

          assertFalse(find(page, TextField.class, "포트").getStyleClass().contains("field-error"));
          assertEquals("꺼짐", chip(page));
          return null;
        });

    assertEquals(3900, server.settings.port());
  }

  @Test
  void aSuccessfulStartShowsRunningAndARunningServerRestartsOnChanges() throws Exception {
    assumeTrue(toolkitReady);
    var server = new FakeServer();
    onFx(
        () -> {
          var page = new McpPage(server, text -> {});
          var power = find(page, ToggleButton.class, "MCP 서버 켜기");
          power.fire();
          assertEquals("실행 중", chip(page));
          assertTrue(power.isSelected());

          find(page, ToggleButton.class, "같은 네트워크").fire();
          var port = find(page, TextField.class, "포트");
          port.setText("4010");
          port.fireEvent(new javafx.event.ActionEvent());

          assertEquals("실행 중", chip(page));
          power.fire();
          assertEquals("꺼짐", chip(page));
          return null;
        });

    assertEquals(3, server.starts.size());
    assertEquals(McpAccess.NETWORK, server.starts.get(1).access());
    assertEquals(4010, server.starts.get(2).port());
    assertEquals(1, server.stops);
  }

  @Test
  void aPortOutsideTheRangeIsRefusedWithoutSaving() throws Exception {
    assumeTrue(toolkitReady);
    var server = new FakeServer();
    onFx(
        () -> {
          var page = new McpPage(server, text -> {});
          var port = find(page, TextField.class, "포트");
          for (String bad : List.of("80", "70000", "abc", "")) {
            port.setText(bad);
            port.fireEvent(new javafx.event.ActionEvent());
            var error = labelWithClass(page, "field-error-text");
            assertTrue(error.isVisible(), bad);
            assertTrue(error.getText().contains("1024~65535"), bad);
          }
          return null;
        });

    assertTrue(server.saves.isEmpty());
    assertEquals(3845, server.settings.port());
  }

  @Test
  void autoStartIsSaved() throws Exception {
    assumeTrue(toolkitReady);
    var server = new FakeServer();
    onFx(
        () -> {
          var page = new McpPage(server, text -> {});
          find(page, ToggleButton.class, "Serox를 켤 때 자동 실행").fire();
          return null;
        });

    assertTrue(server.settings.autoStart());
  }

  @Test
  void theGuideFollowsTheChosenConnectionAndAppAndCopiesTheText() throws Exception {
    assumeTrue(toolkitReady);
    var server = new FakeServer();
    List<String> copied = new ArrayList<>();
    onFx(
        () -> {
          var page = new McpPage(server, copied::add);
          assertTrue(labelWithClass(page, "info-strong").getText().contains("한 번 직접"));

          find(page, ToggleButton.class, "Codex").fire();
          assertEquals(
              "[mcp_servers.serox]\nurl = \"http://localhost:3845/mcp\"",
              labelWithClass(page, "code-block").getText());

          find(page, ToggleButton.class, "표준 입출력 (stdio)").fire();
          assertTrue(labelWithClass(page, "code-block").getText().contains("--mcp"));
          assertTrue(labelWithClass(page, "code-block").getText().contains("SEROX_MCP_APPROVED_TOOLS"));

          Button copy = button(page, "복사");
          copy.fire();
          assertEquals("복사했어요", copy.getText());
          return null;
        });

    assertEquals(1, copied.size());
    assertTrue(copied.getFirst().contains("[mcp_servers.serox]"));
  }

  @Test
  void networkAccessAddsTheTokenNoteToTheHttpGuide() throws Exception {
    assumeTrue(toolkitReady);
    var server = new FakeServer();
    onFx(
        () -> {
          var page = new McpPage(server, text -> {});
          find(page, ToggleButton.class, "같은 네트워크").fire();

          List<String> texts = new ArrayList<>();
          walk(
              page,
              node -> {
                if (node instanceof Label label && label.getText() != null) {
                  texts.add(label.getText());
                }
              });
          assertTrue(texts.stream().anyMatch(text -> text.contains("SEROX_MCP_TOKEN")));
          return null;
        });
  }
}
