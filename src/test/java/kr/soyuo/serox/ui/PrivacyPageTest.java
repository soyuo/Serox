package kr.soyuo.serox.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PrivacyPageTest {
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

  private static Button buttonLabelled(Node root, String accessibleText) {
    if (root instanceof Button button && accessibleText.equals(button.getAccessibleText())) {
      return button;
    }
    if (root instanceof javafx.scene.control.ScrollPane scroll && scroll.getContent() != null) {
      Button inside = buttonLabelled(scroll.getContent(), accessibleText);
      if (inside != null) {
        return inside;
      }
    }
    if (root instanceof javafx.scene.Parent parent) {
      for (Node child : parent.getChildrenUnmodifiable()) {
        Button found = buttonLabelled(child, accessibleText);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }

  /** Records what the page asked for. */
  private static final class FakeActions implements PrivacyActions {
    final List<String> calls = new ArrayList<>();
    final List<String> queries = new ArrayList<>(List.of("가상 스레드", "maven"));

    @Override
    public boolean available() {
      return true;
    }

    @Override
    public CompletionStage<List<String>> recentQueries() {
      return CompletableFuture.completedFuture(List.copyOf(queries));
    }

    @Override
    public CompletionStage<Void> deleteQuery(String query) {
      calls.add("delete:" + query);
      queries.remove(query);
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> clearQueries() {
      calls.add("clearQueries");
      queries.clear();
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> clearVisits() {
      calls.add("clearVisits");
      return CompletableFuture.completedFuture(null);
    }

    kr.soyuo.serox.core.model.BrowserSettings saved =
        kr.soyuo.serox.core.model.BrowserSettings.defaults();

    @Override
    public kr.soyuo.serox.core.model.BrowserSettings settings() {
      return saved;
    }

    @Override
    public CompletionStage<Void> save(kr.soyuo.serox.core.model.BrowserSettings settings) {
      calls.add("save:" + settings.historyRetention() + ":" + settings.saveSearchHistory());
      saved = settings;
      return CompletableFuture.completedFuture(null);
    }
  }

  @Test
  void clearingOnlyHappensAfterTheUserConfirms() throws Exception {
    assumeTrue(toolkitReady, "JavaFX toolkit is not available");
    FakeActions actions = new FakeActions();
    List<String> asked = new ArrayList<>();
    boolean[] answer = {false};
    Region page =
        onFx(
            () ->
                new PrivacyPage(
                    actions,
                    question -> {
                      asked.add(question);
                      return answer[0];
                    }));
    onFx(
        () -> {
          buttonLabelled(page, "검색 기록 모두 지우기").fire();
          return null;
        });

    assertEquals(List.of("검색 기록을 모두 지울까요?"), asked);
    assertTrue(actions.calls.isEmpty(), "declining must not delete anything");

    answer[0] = true;
    onFx(
        () -> {
          buttonLabelled(page, "검색 기록 모두 지우기").fire();
          buttonLabelled(page, "방문 기록 모두 지우기").fire();
          return null;
        });
    assertEquals(List.of("clearQueries", "clearVisits"), actions.calls);
  }

  @Test
  void aSingleQueryCanBeDeletedWithoutAskingAndTheListShrinks() throws Exception {
    assumeTrue(toolkitReady, "JavaFX toolkit is not available");
    FakeActions actions = new FakeActions();
    Region page = onFx(() -> new PrivacyPage(actions, question -> true));
    Thread.sleep(300);
    onFx(
        () -> {
          buttonLabelled(page, "‘maven’ 검색 기록 삭제").fire();
          return null;
        });

    assertEquals(List.of("delete:maven"), actions.calls);
    assertEquals(List.of("가상 스레드"), actions.queries);
  }

  @Test
  void theSwitchAndTheRetentionChoiceAreSaved() throws Exception {
    assumeTrue(toolkitReady, "JavaFX toolkit is not available");
    FakeActions actions = new FakeActions();
    Region page = onFx(() -> new PrivacyPage(actions, question -> true));
    onFx(
        () -> {
          buttonLabelledToggle(page, "검색 기록 저장").fire();
          buttonLabelledToggle(page, "방문 기록 보관 7일").fire();
          return null;
        });

    assertEquals(
        List.of("save:MONTH:false", "save:WEEK:false"), actions.calls);
  }

  private static javafx.scene.control.ToggleButton buttonLabelledToggle(
      Node root, String accessibleText) {
    if (root instanceof javafx.scene.control.ToggleButton toggle
        && accessibleText.equals(toggle.getAccessibleText())) {
      return toggle;
    }
    if (root instanceof javafx.scene.control.ScrollPane scroll && scroll.getContent() != null) {
      var inside = buttonLabelledToggle(scroll.getContent(), accessibleText);
      if (inside != null) {
        return inside;
      }
    }
    if (root instanceof javafx.scene.Parent parent) {
      for (Node child : parent.getChildrenUnmodifiable()) {
        var found = buttonLabelledToggle(child, accessibleText);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }
}
