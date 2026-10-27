package kr.soyuo.serox.app;

import java.util.Objects;
import kr.soyuo.serox.core.application.AppConfiguration;
import kr.soyuo.serox.core.application.BrowserController;
import kr.soyuo.serox.core.event.BrowserEventBus;
import kr.soyuo.serox.search.application.SearchService;

/** Application-scoped dependencies created by the composition root. */
public record SeroxRuntime(
    AppConfiguration configuration,
    BrowserEventBus eventBus,
    BrowserController browserController,
    java.util.concurrent.ExecutorService engineExecutor,
    SearchService searchService)
    implements AutoCloseable {
  public SeroxRuntime {
    Objects.requireNonNull(configuration, "configuration");
    Objects.requireNonNull(eventBus, "eventBus");
    Objects.requireNonNull(browserController, "browserController");
    Objects.requireNonNull(engineExecutor, "engineExecutor");
    Objects.requireNonNull(searchService, "searchService");
  }

  @Override
  public void close() {
    searchService.close();
    engineExecutor.close();
    eventBus.close();
  }
}
