package kr.soyuo.serox.app;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executors;
import kr.soyuo.serox.browser.html.HtmlDocumentParser;
import kr.soyuo.serox.browser.network.HttpBrowserEngine;
import kr.soyuo.serox.browser.network.HttpRequestDispatcher;
import kr.soyuo.serox.browser.network.HttpResponseHandler;
import kr.soyuo.serox.core.application.AppConfigurationLoader;
import kr.soyuo.serox.core.application.BrowserController;
import kr.soyuo.serox.core.application.DefaultBrowserController;
import kr.soyuo.serox.core.event.InProcessBrowserEventBus;
import kr.soyuo.serox.search.api.SearchProvider;
import kr.soyuo.serox.search.application.SearchService;
import kr.soyuo.serox.search.provider.BraveSearchProvider;

/** Composition root for application-scoped adapters and configuration. */
public final class AppBootstrap {
  /** Prevents instantiation of the composition root. */
  private AppBootstrap() {}

  /** Configures application services from the current process environment. */
  public static SeroxRuntime createRuntime() {
    SeroxLogging.configure();
    var configuration = AppConfigurationLoader.fromEnvironment(System.getenv());
    return createRuntime(configuration);
  }

  static SeroxRuntime createRuntime(Map<String, String> environment) {
    SeroxLogging.configure();
    var configuration = AppConfigurationLoader.fromEnvironment(environment);
    return createRuntime(configuration);
  }

  private static SeroxRuntime createRuntime(
      kr.soyuo.serox.core.application.AppConfiguration configuration) {
    var eventBus = new InProcessBrowserEventBus();
    var engineExecutor = Executors.newVirtualThreadPerTaskExecutor();
    var engine =
        new HttpBrowserEngine(
            HttpRequestDispatcher.createDefault(
                Duration.ofSeconds(10), Duration.ofSeconds(30), 8 * 1024 * 1024),
            new HttpResponseHandler(),
            new HtmlDocumentParser());
    BrowserController browserController =
        new DefaultBrowserController(engine, eventBus, engineExecutor);
    var searchProvider =
        configuration
            .braveSearchApiKey()
            .map(key -> (SearchProvider) new BraveSearchProvider(key.revealForRequest()));
    var searchService = new SearchService(searchProvider, eventBus);
    return new SeroxRuntime(
        configuration, eventBus, browserController, engineExecutor, searchService);
  }
}
