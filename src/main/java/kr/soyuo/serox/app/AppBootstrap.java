package kr.soyuo.serox.app;

import java.util.Map;
import kr.soyuo.serox.core.application.AppConfigurationLoader;
import kr.soyuo.serox.core.event.InProcessBrowserEventBus;

/** Composition root for application-scoped adapters and configuration. */
public final class AppBootstrap {
  /** Prevents instantiation of the composition root. */
  private AppBootstrap() {}

  /** Configures application services from the current process environment. */
  public static SeroxRuntime createRuntime() {
    SeroxLogging.configure();
    var configuration = AppConfigurationLoader.fromEnvironment(System.getenv());
    return new SeroxRuntime(configuration, new InProcessBrowserEventBus());
  }

  static SeroxRuntime createRuntime(Map<String, String> environment) {
    SeroxLogging.configure();
    var configuration = AppConfigurationLoader.fromEnvironment(environment);
    return new SeroxRuntime(configuration, new InProcessBrowserEventBus());
  }
}
