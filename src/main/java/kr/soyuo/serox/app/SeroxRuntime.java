package kr.soyuo.serox.app;

import java.util.Objects;
import kr.soyuo.serox.core.application.AppConfiguration;
import kr.soyuo.serox.core.event.BrowserEventBus;

/** Application-scoped dependencies created by the composition root. */
public record SeroxRuntime(AppConfiguration configuration, BrowserEventBus eventBus)
    implements AutoCloseable {
  public SeroxRuntime {
    Objects.requireNonNull(configuration, "configuration");
    Objects.requireNonNull(eventBus, "eventBus");
  }

  @Override
  public void close() {
    eventBus.close();
  }
}
