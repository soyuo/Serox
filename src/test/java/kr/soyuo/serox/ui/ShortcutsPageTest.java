package kr.soyuo.serox.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import kr.soyuo.serox.core.model.BrowserSettings;
import kr.soyuo.serox.core.model.KeyChord;
import kr.soyuo.serox.core.model.ShortcutAction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ShortcutsPageTest {
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

  private static Button button(Node root, String accessibleOrText) {
    if (root instanceof Button button
        && (accessibleOrText.equals(button.getAccessibleText())
            || accessibleOrText.equals(button.getText()))) {
      return button;
    }
    if (root instanceof ScrollPane scroll && scroll.getContent() != null) {
      Button inside = button(scroll.getContent(), accessibleOrText);
      if (inside != null) {
        return inside;
      }
    }
    if (root instanceof javafx.scene.Parent parent) {
      for (Node child : parent.getChildrenUnmodifiable()) {
        Button found = button(child, accessibleOrText);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }

  private static boolean hasText(Node root, String text) {
    if (root instanceof Label label && label.getText() != null && label.getText().contains(text)) {
      return true;
    }
    if (root instanceof ScrollPane scroll && scroll.getContent() != null) {
      return hasText(scroll.getContent(), text);
    }
    if (root instanceof javafx.scene.Parent parent) {
      for (Node child : parent.getChildrenUnmodifiable()) {
        if (hasText(child, text)) {
          return true;
        }
      }
    }
    return false;
  }

  private static void press(Node page, KeyCode code, boolean shift, boolean ctrl, boolean alt) {
    Event.fireEvent(
        page, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, shift, ctrl, alt, false));
  }

  private static KeyChord chord(String text) {
    return KeyChord.parse(text).orElseThrow();
  }

  /** Keeps the saved settings in memory, as the real service does. */
  private static final class FakeActions implements PrivacyActions {
    BrowserSettings settings = BrowserSettings.defaults();

    @Override
    public boolean available() {
      return true;
    }

    @Override
    public CompletionStage<List<String>> recentQueries() {
      return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletionStage<Void> deleteQuery(String query) {
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> clearQueries() {
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> clearVisits() {
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public BrowserSettings settings() {
      return settings;
    }

    @Override
    public CompletionStage<Void> save(BrowserSettings next) {
      settings = next;
      return CompletableFuture.completedFuture(null);
    }
  }

  @Test
  void aFreeKeyIsSavedForTheWaitingAction() throws Exception {
    assumeTrue(toolkitReady);
    var actions = new FakeActions();
    onFx(
        () -> {
          var page = new ShortcutsPage(actions);
          button(page, "새 탭 단축키 변경").fire();
          assertTrue(hasText(page, "새 단축키를 눌러 주세요"));
          press(page, KeyCode.N, false, true, false);
          assertFalse(hasText(page, "새 단축키를 눌러 주세요"));
          return null;
        });

    assertEquals(chord("Ctrl+N"), actions.settings.shortcut(ShortcutAction.NEW_TAB));
  }

  @Test
  void keysThatCannotBeShortcutsAreRefusedAndTheRowKeepsWaiting() throws Exception {
    assumeTrue(toolkitReady);
    var actions = new FakeActions();
    onFx(
        () -> {
          var page = new ShortcutsPage(actions);
          button(page, "새 탭 단축키 변경").fire();
          press(page, KeyCode.CONTROL, false, true, false);
          press(page, KeyCode.N, false, false, false);
          press(page, KeyCode.C, false, true, false);
          assertTrue(hasText(page, "새 단축키를 눌러 주세요"));
          assertTrue(hasText(page, "Ctrl이나 Alt와 함께"));
          press(page, KeyCode.ESCAPE, false, false, false);
          assertFalse(hasText(page, "새 단축키를 눌러 주세요"));
          return null;
        });

    assertEquals(ShortcutAction.NEW_TAB.defaultChord(), actions.settings.shortcut(ShortcutAction.NEW_TAB));
  }

  @Test
  void aKeyAnotherActionUsesAsksFirstAndThenTakesItAway() throws Exception {
    assumeTrue(toolkitReady);
    var actions = new FakeActions();
    onFx(
        () -> {
          var page = new ShortcutsPage(actions);
          button(page, "이전 출처 열기 단축키 변경").fire();
          press(page, KeyCode.DOWN, false, false, true);
          assertTrue(hasText(page, "Alt+↓ 은 ‘다음 출처 열기’와 겹쳐요."));
          assertEquals(
              ShortcutAction.PREVIOUS_SOURCE.defaultChord(),
              actions.settings.shortcut(ShortcutAction.PREVIOUS_SOURCE));
          button(page, "그래도 바꾸기").fire();
          return null;
        });

    assertEquals(chord("Alt+Down"), actions.settings.shortcut(ShortcutAction.PREVIOUS_SOURCE));
    assertFalse(actions.settings.shortcut(ShortcutAction.NEXT_SOURCE).assigned());
  }

  @Test
  void cancellingAConflictChangesNothing() throws Exception {
    assumeTrue(toolkitReady);
    var actions = new FakeActions();
    onFx(
        () -> {
          var page = new ShortcutsPage(actions);
          button(page, "이전 출처 열기 단축키 변경").fire();
          press(page, KeyCode.DOWN, false, false, true);
          button(page, "취소").fire();
          assertNull(button(page, "그래도 바꾸기"));
          assertNotNull(button(page, "이전 출처 열기 단축키 변경"));
          return null;
        });

    assertEquals(BrowserSettings.defaults(), actions.settings);
  }

  @Test
  void resetBringsEveryShortcutBack() throws Exception {
    assumeTrue(toolkitReady);
    var actions = new FakeActions();
    actions.settings = actions.settings.withShortcut(ShortcutAction.NEW_TAB, chord("Ctrl+N"));
    onFx(
        () -> {
          button(new ShortcutsPage(actions), "기본값으로 되돌리기").fire();
          return null;
        });

    assertEquals(BrowserSettings.defaults(), actions.settings);
  }
}
