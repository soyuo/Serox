package kr.soyuo.serox.app;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import kr.soyuo.serox.ai.api.AiProvider;
import kr.soyuo.serox.ai.application.AiSearchService;
import kr.soyuo.serox.ai.context.ContextBuilder;
import kr.soyuo.serox.ai.provider.ClaudeProvider;
import kr.soyuo.serox.browser.html.HtmlDocumentParser;
import kr.soyuo.serox.browser.network.HttpBrowserEngine;
import kr.soyuo.serox.browser.network.HttpRequestDispatcher;
import kr.soyuo.serox.browser.network.HttpResponseHandler;
import kr.soyuo.serox.core.application.AppConfigurationLoader;
import kr.soyuo.serox.core.application.BrowserController;
import kr.soyuo.serox.core.application.DefaultBrowserController;
import kr.soyuo.serox.core.application.SessionKeeper;
import kr.soyuo.serox.core.application.SettingsService;
import kr.soyuo.serox.core.application.UsageRecorder;
import kr.soyuo.serox.core.event.InProcessBrowserEventBus;
import kr.soyuo.serox.search.api.SearchProvider;
import kr.soyuo.serox.search.application.SearchService;
import kr.soyuo.serox.search.fetch.FetchLimits;
import kr.soyuo.serox.search.fetch.PageFetcher;
import kr.soyuo.serox.search.provider.BraveSearchProvider;
import kr.soyuo.serox.storage.sqlite.SqliteStorage;

/** Composition root for application-scoped adapters and configuration. */
public final class AppBootstrap {
  private static final Logger LOGGER = Logger.getLogger(AppBootstrap.class.getName());

  /** Prevents instantiation of the composition root. */
  private AppBootstrap() {}

  /** Configures application services from the current process environment. */
  public static SeroxRuntime createRuntime() {
    SeroxLogging.configure();
    var configuration = AppConfigurationLoader.fromEnvironment(System.getenv());
    return createRuntime(configuration, resolveDataDirectory(System.getenv()));
  }

  static SeroxRuntime createRuntime(Map<String, String> environment) {
    SeroxLogging.configure();
    var configuration = AppConfigurationLoader.fromEnvironment(environment);
    return createRuntime(configuration, resolveDataDirectory(environment));
  }

  private static SeroxRuntime createRuntime(
      kr.soyuo.serox.core.application.AppConfiguration configuration, Path dataDirectory) {
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
    Optional<SqliteStorage> storage = openStorage(dataDirectory);
    Optional<UsageRecorder> usageRecorder =
        storage.map(
            sqlite ->
                new UsageRecorder(
                    eventBus, sqlite.history(), sqlite.recentSearches(), Clock.systemUTC()));
    AiSearchService aiSearchService = createAiSearchService(configuration, eventBus);
    var settingsService =
        new SettingsService(storage.map(sqlite -> sqlite.settings()), eventBus);
    settingsService.load();
    Optional<SessionKeeper> sessionKeeper =
        storage.map(sqlite -> new SessionKeeper(eventBus, sqlite.sessions()));
    return new SeroxRuntime(
        configuration,
        eventBus,
        browserController,
        engineExecutor,
        searchService,
        storage,
        usageRecorder,
        sessionKeeper,
        aiSearchService,
        settingsService);
  }

  private static AiSearchService createAiSearchService(
      kr.soyuo.serox.core.application.AppConfiguration configuration,
      kr.soyuo.serox.core.event.BrowserEventBus eventBus) {
    Optional<AiProvider> provider =
        configuration
            .anthropicApiKey()
            .map(key -> (AiProvider) ClaudeProvider.create(key.revealForRequest()));
    var fetcher =
        new PageFetcher(
            HttpRequestDispatcher.createDefault(
                Duration.ofSeconds(5), Duration.ofSeconds(8), 1024 * 1024),
            new HttpResponseHandler(),
            new HtmlDocumentParser(),
            FetchLimits.defaults());
    return new AiSearchService(provider, fetcher, new ContextBuilder(), eventBus, Clock.systemUTC());
  }

  /** Resolves the per-user profile directory that holds the local database. */
  static Path resolveDataDirectory(Map<String, String> environment) {
    String override = environment.get("SEROX_DATA_DIR");
    if (override != null && !override.isBlank()) {
      return Path.of(override);
    }
    String localAppData = environment.get("LOCALAPPDATA");
    if (localAppData != null && !localAppData.isBlank()) {
      return Path.of(localAppData, "Serox");
    }
    return Path.of(System.getProperty("user.home"), ".serox");
  }

  private static Optional<SqliteStorage> openStorage(Path dataDirectory) {
    try {
      return Optional.of(new SqliteStorage(dataDirectory.resolve("serox.db")));
    } catch (RuntimeException exception) {
      LOGGER.log(Level.WARNING, "Local storage is unavailable; running without persistence.");
      return Optional.empty();
    }
  }
}
