package kr.soyuo.serox.core.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import kr.soyuo.serox.core.event.InProcessBrowserEventBus;
import kr.soyuo.serox.core.event.SettingsChanged;
import kr.soyuo.serox.core.model.AiPanelPosition;
import kr.soyuo.serox.core.model.BrowserSettings;
import kr.soyuo.serox.core.model.DisplayDensity;
import kr.soyuo.serox.core.model.ThemeMode;
import kr.soyuo.serox.storage.sqlite.SqliteStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettingsServiceTest {
  @TempDir Path tempDir;

  @Test
  void savedSettingsAreAnnouncedAndSurviveARestart() {
    Path file = tempDir.resolve("serox.db");
    var changed =
        new BrowserSettings(
            ThemeMode.DARK, "#7C3AED", 120, AiPanelPosition.LEFT, DisplayDensity.COMPACT);
    List<BrowserSettings> announced = new ArrayList<>();
    try (var storage = new SqliteStorage(file);
        var bus = new InProcessBrowserEventBus()) {
      bus.subscribe(SettingsChanged.class, event -> announced.add(event.settings()));
      var service = new SettingsService(Optional.of(storage.settings()), bus);
      assertEquals(BrowserSettings.defaults(), service.load());
      service.save(changed).toCompletableFuture().join();
      assertEquals(List.of(changed), announced);
    }
    try (var storage = new SqliteStorage(file);
        var bus = new InProcessBrowserEventBus()) {
      assertEquals(changed, new SettingsService(Optional.of(storage.settings()), bus).load());
    }
  }

  @Test
  void worksForTheCurrentRunWithoutStorage() {
    try (var bus = new InProcessBrowserEventBus()) {
      var service = new SettingsService(Optional.empty(), bus);
      var changed = new BrowserSettings(
          ThemeMode.LIGHT, "#2563EB", 90, AiPanelPosition.RIGHT, DisplayDensity.COMFORTABLE);
      service.save(changed).toCompletableFuture().join();
      assertEquals(changed, service.current());
    }
  }
}
