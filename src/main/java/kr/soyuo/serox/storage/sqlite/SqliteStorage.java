package kr.soyuo.serox.storage.sqlite;

import java.nio.file.Path;
import java.util.Objects;
import kr.soyuo.serox.core.port.storage.BookmarkRepository;
import kr.soyuo.serox.core.port.storage.HistoryRepository;
import kr.soyuo.serox.core.port.storage.RecentSearchRepository;
import kr.soyuo.serox.core.port.storage.SearchCacheRepository;
import kr.soyuo.serox.core.port.storage.SessionRepository;
import kr.soyuo.serox.core.port.storage.SettingsRepository;

/** Composition root for SQLite storage adapters. */
public final class SqliteStorage implements AutoCloseable {
  private final SqliteDatabase database;
  private final HistoryRepository historyRepository;
  private final BookmarkRepository bookmarkRepository;
  private final SessionRepository sessionRepository;
  private final SettingsRepository settingsRepository;
  private final SearchCacheRepository searchCacheRepository;
  private final RecentSearchRepository recentSearchRepository;

  public SqliteStorage(Path databaseFile) {
    database = new SqliteDatabase(Objects.requireNonNull(databaseFile, "databaseFile"));
    historyRepository = new SqliteHistoryRepository(database);
    bookmarkRepository = new SqliteBookmarkRepository(database);
    sessionRepository = new SqliteSessionRepository(database);
    settingsRepository = new SqliteSettingsRepository(database);
    searchCacheRepository = new SqliteSearchCacheRepository(database);
    recentSearchRepository = new SqliteRecentSearchRepository(database);
  }

  public HistoryRepository history() {
    return historyRepository;
  }

  public BookmarkRepository bookmarks() {
    return bookmarkRepository;
  }

  public SessionRepository sessions() {
    return sessionRepository;
  }

  public SettingsRepository settings() {
    return settingsRepository;
  }

  public SearchCacheRepository searchCache() {
    return searchCacheRepository;
  }

  public RecentSearchRepository recentSearches() {
    return recentSearchRepository;
  }

  @Override
  public void close() {
    database.close();
  }
}
