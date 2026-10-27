package kr.soyuo.serox.app;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.soyuo.serox.ai.api.AiProvider;
import kr.soyuo.serox.ai.application.AiConnectionService;
import kr.soyuo.serox.ai.application.AiSearchService;
import kr.soyuo.serox.ai.context.ContextBuilder;
import kr.soyuo.serox.ai.provider.AiProviders;
import kr.soyuo.serox.browser.html.HtmlDocumentParser;
import kr.soyuo.serox.browser.network.GuardedTransport;
import kr.soyuo.serox.browser.network.HttpBrowserEngine;
import kr.soyuo.serox.browser.network.HttpRequestDispatcher;
import kr.soyuo.serox.browser.network.HttpResponseHandler;
import kr.soyuo.serox.browser.network.PublicAddressPolicy;
import kr.soyuo.serox.core.application.AppConfigurationLoader;
import kr.soyuo.serox.core.application.BrowserController;
import kr.soyuo.serox.core.application.DefaultBrowserController;
import kr.soyuo.serox.core.application.LibraryService;
import kr.soyuo.serox.core.application.SessionKeeper;
import kr.soyuo.serox.core.application.SettingsService;
import kr.soyuo.serox.core.application.UsageRecorder;
import kr.soyuo.serox.core.event.InProcessBrowserEventBus;
import kr.soyuo.serox.search.api.SearchProvider;
import kr.soyuo.serox.search.application.SearchService;
import kr.soyuo.serox.search.fetch.FetchLimits;
import kr.soyuo.serox.search.fetch.PageFetcher;
import kr.soyuo.serox.search.provider.BraveSearchProvider;
import kr.soyuo.serox.search.provider.DuckDuckGoSearchProvider;
import kr.soyuo.serox.storage.sqlite.SqliteStorage;

/** Composition root for application-scoped adapters and configuration. */
public final class AppBootstrap {
  private static final Logger LOGGER = Logger.getLogger(AppBootstrap.class.getName());
  private static final Pattern VARIABLE = Pattern.compile("%([A-Za-z0-9_]+)%");

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
        storedSearchKey(dataDirectory)
            .or(() -> configuration.braveSearchApiKey())
            .map(key -> (SearchProvider) new BraveSearchProvider(key.revealForRequest()));
    var searchService = new SearchService(searchProvider, eventBus);
    searchService.setFallback(new DuckDuckGoSearchProvider());
    Optional<SqliteStorage> storage = openStorage(dataDirectory);
    var settingsService = new SettingsService(storage.map(sqlite -> sqlite.settings()), eventBus);
    settingsService.load();
    searchService.setSafeSearch(() -> settingsService.current().search().safeSearch());
    searchService.setEngine(() -> settingsService.current().search().engine());
    Optional<UsageRecorder> usageRecorder =
        storage.map(
            sqlite ->
                new UsageRecorder(
                    eventBus,
                    sqlite.history(),
                    sqlite.recentSearches(),
                    Clock.systemUTC(),
                    settingsService::current));
    AiSearchService aiSearchService = createAiSearchService(configuration, eventBus);
    aiSearchService.setAnswerLanguage(
        () ->
            switch (settingsService.current().summary().language()) {
              case "Korean" -> "Korean";
              case "app" -> settingsService.current().language().englishName();
              default -> "";
            });
    aiSearchService.setInstructions(() -> settingsService.current().summary().instructions());
    aiSearchService.setFetchLimits(
        () -> {
          var preferences = settingsService.current();
          Duration timeout = Duration.ofSeconds(preferences.search().timeoutSeconds());
          return new FetchLimits(
              preferences.search().evidencePages(),
              timeout,
              timeout,
              preferences.summary().sourceCharacters());
        });
    var aiConnections =
        new AiConnectionService(
            configuration, dataDirectory.resolve("credentials"), aiSearchService, eventBus);
    var libraryService =
        new LibraryService(
            storage.map(sqlite -> sqlite.history()),
            storage.map(sqlite -> sqlite.bookmarks()),
            Clock.systemUTC());
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
        settingsService,
        libraryService,
        aiConnections);
  }

  private static AiSearchService createAiSearchService(
      kr.soyuo.serox.core.application.AppConfiguration configuration,
      kr.soyuo.serox.core.event.BrowserEventBus eventBus) {
    Optional<AiProvider> provider = AiProviders.fromConfiguration(configuration);
    var fetcher = createPageFetcher();
    return new AiSearchService(
        provider, fetcher, new ContextBuilder(), eventBus, Clock.systemUTC());
  }

  /** Page reader shared by the AI features: small responses, short timeouts, few pages. */
  static PageFetcher createPageFetcher() {
    var client =
        java.net.http.HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(java.net.http.HttpClient.Redirect.NEVER)
            .build();
    var transport =
        new GuardedTransport(
            new HttpRequestDispatcher(client, Duration.ofMinutes(5), 1024 * 1024),
            new PublicAddressPolicy());
    return new PageFetcher(
        transport, new HttpResponseHandler(), new HtmlDocumentParser(), FetchLimits.defaults());
  }

  /** Resolves the per-user profile directory that holds the local database. */
  static Path resolveDataDirectory(Map<String, String> environment) {
    String override = environment.get("SEROX_DATA_DIR");
    if (override != null && !override.isBlank()) {
      return Path.of(expandVariables(override.strip(), environment));
    }
    String localAppData = environment.get("LOCALAPPDATA");
    if (localAppData != null && !localAppData.isBlank()) {
      return Path.of(localAppData, "Serox");
    }
    return Path.of(System.getProperty("user.home"), ".serox");
  }

  /** Replaces {@code %NAME%} references with environment values; unknown names stay as written. */
  static String expandVariables(String value, Map<String, String> environment) {
    Matcher matcher = VARIABLE.matcher(value);
    StringBuilder expanded = new StringBuilder();
    while (matcher.find()) {
      String name = matcher.group(1);
      String replacement = lookup(environment, name);
      if (replacement == null && "USERPROFILEDATA".equalsIgnoreCase(name)) {
        replacement = lookup(environment, "USERPROFILE");
      }
      matcher.appendReplacement(
          expanded, Matcher.quoteReplacement(replacement == null ? matcher.group() : replacement));
    }
    matcher.appendTail(expanded);
    return expanded.toString();
  }

  private static String lookup(Map<String, String> environment, String name) {
    for (Map.Entry<String, String> entry : environment.entrySet()) {
      if (entry.getKey().equalsIgnoreCase(name)
          && entry.getValue() != null
          && !entry.getValue().isBlank()) {
        return entry.getValue();
      }
    }
    return null;
  }

  private static Optional<kr.soyuo.serox.core.model.SecretValue> storedSearchKey(Path directory) {
    try {
      var store =
          new kr.soyuo.serox.ai.auth.ProtectedLocalStore(
              directory.resolve("credentials/search.dpapi"));
      var data = com.google.gson.JsonParser.parseString(store.read()).getAsJsonObject();
      return data.has("key")
          ? Optional.of(new kr.soyuo.serox.core.model.SecretValue(data.get("key").getAsString()))
          : Optional.empty();
    } catch (Exception unavailable) {
      LOGGER.log(
          Level.WARNING,
          "Stored search credentials are unavailable; using environment configuration.");
      return Optional.empty();
    }
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
