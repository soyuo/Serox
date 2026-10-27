package kr.soyuo.serox.app;

import java.util.Objects;
import java.util.Optional;
import kr.soyuo.serox.ai.application.AiConnectionService;
import kr.soyuo.serox.ai.application.AiSearchService;
import kr.soyuo.serox.core.application.AppConfiguration;
import kr.soyuo.serox.core.application.BrowserController;
import kr.soyuo.serox.core.application.LibraryService;
import kr.soyuo.serox.core.application.SessionKeeper;
import kr.soyuo.serox.core.application.SettingsService;
import kr.soyuo.serox.core.application.UsageRecorder;
import kr.soyuo.serox.core.event.BrowserEventBus;
import kr.soyuo.serox.core.event.ConnectionStatusChanged;
import kr.soyuo.serox.core.event.ServiceId;
import kr.soyuo.serox.core.event.ServiceStatus;
import kr.soyuo.serox.search.application.SearchService;
import kr.soyuo.serox.storage.sqlite.SqliteStorage;

/** Application-scoped dependencies created by the composition root. */
public record SeroxRuntime(
    AppConfiguration configuration,
    BrowserEventBus eventBus,
    BrowserController browserController,
    java.util.concurrent.ExecutorService engineExecutor,
    SearchService searchService,
    Optional<SqliteStorage> storage,
    Optional<UsageRecorder> usageRecorder,
    Optional<SessionKeeper> sessionKeeper,
    AiSearchService aiSearchService,
    SettingsService settingsService,
    LibraryService libraryService,
    AiConnectionService aiConnections)
    implements AutoCloseable {
  public SeroxRuntime {
    Objects.requireNonNull(configuration, "configuration");
    Objects.requireNonNull(eventBus, "eventBus");
    Objects.requireNonNull(browserController, "browserController");
    Objects.requireNonNull(engineExecutor, "engineExecutor");
    Objects.requireNonNull(searchService, "searchService");
    Objects.requireNonNull(storage, "storage");
    Objects.requireNonNull(usageRecorder, "usageRecorder");
    Objects.requireNonNull(sessionKeeper, "sessionKeeper");
    Objects.requireNonNull(aiSearchService, "aiSearchService");
    Objects.requireNonNull(settingsService, "settingsService");
    Objects.requireNonNull(libraryService, "libraryService");
  }

  /** Reports the real readiness of each service so the UI badges match the configuration. */
  public void announceServiceStatus() {
    java.time.Instant now = java.time.Instant.now();
    eventBus.publish(
        status(
            now, ServiceId.SEARCH, configuration.hasBraveSearchApiKey(), "켜짐 (Brave)", "API 키 필요"));
    aiConnections.announce();
    eventBus.publish(
        new ConnectionStatusChanged(
            now, ServiceId.MCP, ServiceStatus.DISCONNECTED, "꺼짐 (--mcp로 실행하면 켜짐)"));
    eventBus.publish(status(now, ServiceId.STORAGE, storage.isPresent(), "저장소 사용 가능", "저장소 사용 불가"));
  }

  private static ConnectionStatusChanged status(
      java.time.Instant now, ServiceId service, boolean on, String onText, String offText) {
    return new ConnectionStatusChanged(
        now,
        service,
        on ? ServiceStatus.CONNECTED : ServiceStatus.DISCONNECTED,
        on ? onText : offText);
  }

  @Override
  public void close() {
    aiSearchService.close();
    aiConnections.close();
    sessionKeeper.ifPresent(SessionKeeper::close);
    usageRecorder.ifPresent(UsageRecorder::close);
    searchService.close();
    engineExecutor.close();
    eventBus.close();
    storage.ifPresent(SqliteStorage::close);
  }
}
