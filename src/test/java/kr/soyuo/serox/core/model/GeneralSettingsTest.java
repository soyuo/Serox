package kr.soyuo.serox.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GeneralSettingsTest {
  @Test
  void defaultsOpenTheHomeScreenInKorean() {
    var defaults = BrowserSettings.defaults();

    assertEquals(StartupMode.HOME, defaults.startupMode());
    assertEquals("", defaults.startPageUrl());
    assertEquals(AppLanguage.KOREAN, defaults.language());
  }

  @Test
  void generalChoicesSurviveOtherChanges() {
    var settings =
        BrowserSettings.defaults()
            .withStartupMode(StartupMode.SPECIFIC_PAGE)
            .withStartPageUrl("  https://example.test/start  ")
            .withLanguage(AppLanguage.ENGLISH);

    var retained = settings.withRetention(HistoryRetention.WEEK).withSaveSearchHistory(false);

    assertEquals(StartupMode.SPECIFIC_PAGE, retained.startupMode());
    assertEquals("https://example.test/start", retained.startPageUrl());
    assertEquals(AppLanguage.ENGLISH, retained.language());
    assertEquals(HistoryRetention.WEEK, retained.historyRetention());
  }

  @Test
  void withGeneralOfCopiesOnlyTheGeneralChoices() {
    var general =
        BrowserSettings.defaults().withStartupMode(StartupMode.RESTORE_TABS).withLanguage(AppLanguage.JAPANESE);
    var appearance = new BrowserSettings(
        ThemeMode.DARK, "#112233", 115, AiPanelPosition.LEFT, DisplayDensity.COMPACT);

    var merged = appearance.withGeneralOf(general);

    assertEquals(ThemeMode.DARK, merged.theme());
    assertEquals(StartupMode.RESTORE_TABS, merged.startupMode());
    assertEquals(AppLanguage.JAPANESE, merged.language());
  }

  @Test
  void startPageMustBeAWebAddress() {
    var defaults = BrowserSettings.defaults();

    assertTrue(BrowserSettings.isWebAddress("http://example.test/"));
    assertFalse(BrowserSettings.isWebAddress("example.test"));
    assertFalse(BrowserSettings.isWebAddress("file:///c:/secret.txt"));
    assertFalse(BrowserSettings.isWebAddress("javascript:alert(1)"));
    assertFalse(BrowserSettings.isWebAddress("https://" + "a".repeat(2100)));
    assertThrows(IllegalArgumentException.class, () -> defaults.withStartPageUrl("ftp://x.test/"));
    assertEquals("", defaults.withStartPageUrl("   ").startPageUrl());
  }
}
