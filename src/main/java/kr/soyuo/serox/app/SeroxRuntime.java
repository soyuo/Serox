package kr.soyuo.serox.app;

import java.util.Objects;
import java.util.Optional;
import kr.soyuo.serox.core.application.AppConfiguration;
import kr.soyuo.serox.core.application.BrowserController;
import kr.soyuo.serox.core.application.UsageRecorder;
import kr.soyuo.serox.core.event.BrowserEventBus;
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
    Optional<UsageRecorder> usageRecorder)
    implements AutoCloseable {
  public SeroxRuntime {
    Objects.requireNonNull(configuration, "configuration");
    Objects.requireNonNull(eventBus, "eventBus");
    Objects.requireNonNull(browserController, "browserController");
    Objects.requireNonNull(engineExecutor, "engineExecutor");
    Objects.requireNonNull(searchService, "searchService");
    Objects.requireNonNull(storage, "storage");
    Objects.requireNonNull(usageRecorder, "usageRecorder");
  }

  @Override
  public void close() {
    usageRecorder.ifPresent(UsageRecorder::close);
    searchService.close();
    engineExecutor.close();
    eventBus.close();
    storage.ifPresent(SqliteStorage::close);
  }
}
