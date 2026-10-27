package kr.soyuo.serox.app;

import java.util.Objects;
import kr.soyuo.serox.core.application.AppConfiguration;
import kr.soyuo.serox.core.application.BrowserController;
import kr.soyuo.serox.core.event.BrowserEventBus;

/** Application-scoped dependencies created by the composition root. */
public record SeroxRuntime(
    AppConfiguration configuration,
    BrowserEventBus eventBus,
    BrowserController browserController,
    java.util.concurrent.ExecutorService engineExecutor)
    implements AutoCloseable {
  public SeroxRuntime {
    Objects.requireNonNull(configuration, "configuration");
    Objects.requireNonNull(eventBus, "eventBus");
    Objects.requireNonNull(browserController, "browserController");
    Objects.requireNonNull(engineExecutor, "engineExecutor");
  }

  @Override
  public void close() {
    engineExecutor.close();
    eventBus.close();
  }
}
