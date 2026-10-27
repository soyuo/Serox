package kr.soyuo.serox.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;
import kr.soyuo.serox.core.model.*;
import org.junit.jupiter.api.Test;

class AppearanceDraftTest {
  @Test
  void previewAndRestorePreserveOtherSettingsUntilExplicitApply() throws Exception {
    CompletableFuture<Void> started = new CompletableFuture<>();
    try {
      Platform.startup(() -> started.complete(null));
    } catch (IllegalStateException alreadyStarted) {
      started.complete(null);
    }
    started.get(10, TimeUnit.SECONDS);
    CompletableFuture<Void> checked = new CompletableFuture<>();
    Platform.runLater(
        () -> {
          try {
            BrowserSettings saved =
                BrowserSettings.defaults()
                    .withLanguage(AppLanguage.ENGLISH)
                    .withSearchPreferences(new SearchPreferences(7, "strict", 40))
                    .withSummaryPreferences(
                        SummaryPreferences.defaults().withInstructions("Keep concise."))
                    .withShortcut(ShortcutAction.NEW_TAB, KeyChord.UNASSIGNED);
            AtomicReference<BrowserSettings> preview = new AtomicReference<>();
            AtomicReference<BrowserSettings> applied = new AtomicReference<>();
            java.util.concurrent.atomic.AtomicBoolean cancelled =
                new java.util.concurrent.atomic.AtomicBoolean();
            AppearancePage page =
                new AppearancePage(saved, preview::set, applied::set, () -> cancelled.set(true));
            page.update(
                new BrowserSettings(
                    ThemeMode.DARK, "#112233", 150, AiPanelPosition.LEFT, DisplayDensity.COMPACT));
            assertNull(applied.get());
            assertEquals(saved.search(), preview.get().search());
            assertEquals(saved.summary(), preview.get().summary());
            assertEquals(saved.language(), preview.get().language());
            assertEquals(saved.shortcutOverrides(), preview.get().shortcutOverrides());
            button(page, "기본값 복원").fire();
            assertNull(applied.get());
            assertEquals(BrowserSettings.defaults().theme(), page.draft().theme());
            assertEquals(saved.search(), page.draft().search());
            button(page, "취소").fire();
            assertTrue(cancelled.get());
            assertNull(applied.get());
            button(page, "적용").fire();
            assertEquals(saved.summary(), applied.get().summary());
            checked.complete(null);
          } catch (Throwable failure) {
            checked.completeExceptionally(failure);
          }
        });
    checked.get(15, TimeUnit.SECONDS);
  }

  @Test
  void choosingThemeSavesItRightAway() throws Exception {
    CompletableFuture<Void> started = new CompletableFuture<>();
    try {
      Platform.startup(() -> started.complete(null));
    } catch (IllegalStateException alreadyStarted) {
      started.complete(null);
    }
    started.get(10, TimeUnit.SECONDS);
    CompletableFuture<Void> checked = new CompletableFuture<>();
    Platform.runLater(
        () -> {
          try {
            AtomicReference<BrowserSettings> applied = new AtomicReference<>();
            AppearancePage page =
                new AppearancePage(BrowserSettings.defaults(), draft -> {}, applied::set, () -> {});
            page.getContent().lookupAll(".segment-option").stream()
                .filter(node -> node instanceof ToggleButton option && "어둡게".equals(option.getText()))
                .map(ToggleButton.class::cast)
                .findFirst()
                .orElseThrow()
                .fire();
            assertEquals(ThemeMode.DARK, applied.get().theme());
            checked.complete(null);
          } catch (Throwable failure) {
            checked.completeExceptionally(failure);
          }
        });
    checked.get(15, TimeUnit.SECONDS);
  }

  private static Button button(AppearancePage page, String text) {
    return page.getContent().lookupAll(".button").stream()
        .filter(node -> node instanceof Button button && text.equals(button.getText()))
        .map(Button.class::cast)
        .findFirst()
        .orElseThrow();
  }
}
