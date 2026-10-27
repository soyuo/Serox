package kr.soyuo.serox.storage.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.sql.DriverManager;
import kr.soyuo.serox.core.model.AppLanguage;
import kr.soyuo.serox.core.model.BrowserSettings;
import kr.soyuo.serox.core.model.HistoryRetention;
import kr.soyuo.serox.core.model.SearchPreferences;
import kr.soyuo.serox.core.model.SummaryPreferences;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContentSettingsStorageTest {
  @TempDir Path directory;

  @Test
  void preferencesSurviveRestartAndOtherSettingsChanges() {
    Path file = directory.resolve("content.db");
    var chosen =
        BrowserSettings.defaults()
            .withSearchPreferences(new SearchPreferences(8, "strict", 120))
            .withSummaryPreferences(
                new SummaryPreferences("Korean", false, false, 20000, "짧은 문장으로 써 주세요."))
            .withLanguage(AppLanguage.ENGLISH)
            .withRetention(HistoryRetention.WEEK)
            .withDefaultShortcuts();
    try (var storage = new SqliteStorage(file)) {
      storage.settings().save(chosen).toCompletableFuture().join();
    }
    try (var storage = new SqliteStorage(file)) {
      assertEquals(chosen, storage.settings().load().toCompletableFuture().join());
    }
    var applied = BrowserSettings.defaults().withContentPreferencesOf(chosen);
    assertEquals(chosen.search(), applied.search());
    assertEquals(chosen.summary(), applied.summary());
  }

  @Test
  void oldProfilesKeepThePreviouslySelectedAnswerLanguage() throws Exception {
    Path file = directory.resolve("old.db");
    try (var storage = new SqliteStorage(file)) {
      storage
          .settings()
          .save(BrowserSettings.defaults().withLanguage(AppLanguage.ENGLISH))
          .toCompletableFuture()
          .join();
      try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file);
          var statement = connection.createStatement()) {
        statement.executeUpdate(
            "DELETE FROM settings WHERE key LIKE 'summary.%' OR key LIKE 'search.%'");
      }
      var loaded = storage.settings().load().toCompletableFuture().join();
      assertEquals("app", loaded.summary().language());
      assertEquals(AppLanguage.ENGLISH, loaded.language());
      assertEquals(SearchPreferences.defaults(), loaded.search());
    }
  }

  @Test
  void malformedContentSettingsDoNotResetAppearanceOrGeneralPreferences() throws Exception {
    Path file = directory.resolve("bad.db");
    try (var storage = new SqliteStorage(file)) {
      storage
          .settings()
          .save(BrowserSettings.defaults().withLanguage(AppLanguage.ENGLISH))
          .toCompletableFuture()
          .join();
      try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file);
          var statement = connection.createStatement()) {
        statement.executeUpdate("UPDATE settings SET value='999' WHERE key='search.evidencePages'");
        statement.executeUpdate(
            "UPDATE settings SET value='unsupported' WHERE key='summary.language'");
      }
      var loaded = storage.settings().load().toCompletableFuture().join();
      assertEquals(SearchPreferences.defaults(), loaded.search());
      assertEquals(SummaryPreferences.defaults(), loaded.summary());
      assertEquals(AppLanguage.ENGLISH, loaded.language());
    }
  }
}
