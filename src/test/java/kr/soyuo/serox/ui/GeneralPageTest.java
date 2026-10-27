package kr.soyuo.serox.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import kr.soyuo.serox.core.model.AppLanguage;
import kr.soyuo.serox.core.model.BrowserSettings;
import kr.soyuo.serox.core.model.StartupMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GeneralPageTest {
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

  private static <T extends Node> T find(Node root, Class<T> type, String accessibleText) {
    if (type.isInstance(root) && accessibleText.equals(root.getAccessibleText())) {
      return type.cast(root);
    }
    if (root instanceof ScrollPane scroll && scroll.getContent() != null) {
      T inside = find(scroll.getContent(), type, accessibleText);
      if (inside != null) {
        return inside;
      }
    }
    if (root instanceof javafx.scene.Parent parent) {
      for (Node child : parent.getChildrenUnmodifiable()) {
        T found = find(child, type, accessibleText);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
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
  void theAddressRowShowsOnlyForASpecificPage() throws Exception {
    assumeTrue(toolkitReady);
    var actions = new FakeActions();
    onFx(
        () -> {
          var page = new GeneralPage(actions);
          TextField address = find(page, TextField.class, "시작 페이지 주소");
          assertFalse(address.getParent().isVisible());
          find(page, ToggleButton.class, "브라우저를 열면 특정 페이지").fire();
          assertTrue(address.getParent().isVisible());
          return null;
        });

    assertEquals(StartupMode.SPECIFIC_PAGE, actions.settings.startupMode());
  }

  @Test
  void aValidAddressIsSavedAndAnInvalidOneIsNot() throws Exception {
    assumeTrue(toolkitReady);
    var actions = new FakeActions();
    onFx(
        () -> {
          var page = new GeneralPage(actions);
          TextField address = find(page, TextField.class, "시작 페이지 주소");
          address.setText("not an address");
          address.fireEvent(new javafx.event.ActionEvent());
          assertEquals("", actions.settings.startPageUrl());
          address.setText("https://example.test/start");
          address.fireEvent(new javafx.event.ActionEvent());
          return null;
        });

    assertEquals("https://example.test/start", actions.settings.startPageUrl());
  }

  @Test
  void choosingALanguageSavesIt() throws Exception {
    assumeTrue(toolkitReady);
    var actions = new FakeActions();
    onFx(
        () -> {
          var page = new GeneralPage(actions);
          @SuppressWarnings("unchecked")
          ComboBox<AppLanguage> languages = find(page, ComboBox.class, "언어");
          assertEquals(AppLanguage.KOREAN, languages.getValue());
          languages.setValue(AppLanguage.ENGLISH);
          return null;
        });

    assertEquals(AppLanguage.ENGLISH, actions.settings.language());
  }
}
