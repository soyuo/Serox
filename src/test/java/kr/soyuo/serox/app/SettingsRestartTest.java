package kr.soyuo.serox.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.Map;
import kr.soyuo.serox.core.model.AiPanelPosition;
import kr.soyuo.serox.core.model.BrowserSettings;
import kr.soyuo.serox.core.model.DisplayDensity;
import kr.soyuo.serox.core.model.ThemeMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettingsRestartTest {
  @TempDir Path dataDirectory;

  private SeroxRuntime start() {
    return AppBootstrap.createRuntime(Map.of("SEROX_DATA_DIR", dataDirectory.toString()));
  }

  @Test
  void themeSurvivesRestart() throws Exception {
    BrowserSettings dark =
        new BrowserSettings(
            ThemeMode.DARK,
            "#2563EB",
            100,
            AiPanelPosition.RIGHT,
            DisplayDensity.COMFORTABLE);
    try (SeroxRuntime first = start()) {
      first.settingsService().save(dark).toCompletableFuture().get();
    }

    try (SeroxRuntime second = start()) {
      assertEquals(ThemeMode.DARK, second.settingsService().current().theme());
    }
  }
}
