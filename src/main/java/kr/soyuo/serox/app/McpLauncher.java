package kr.soyuo.serox.app;

import java.io.InputStream;
import java.io.OutputStream;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import kr.soyuo.serox.ai.api.AiProvider;
import kr.soyuo.serox.ai.application.SearchSummaryService;
import kr.soyuo.serox.ai.context.ContextBuilder;
import kr.soyuo.serox.ai.provider.AiProviders;
import kr.soyuo.serox.core.application.AppConfigurationLoader;
import kr.soyuo.serox.core.event.BrowserEventBus;
import kr.soyuo.serox.mcp.security.AccessPolicy;
import kr.soyuo.serox.mcp.security.EnvironmentApprovalGate;
import kr.soyuo.serox.mcp.server.McpEndpoint;
import kr.soyuo.serox.mcp.server.StdioMcpServer;
import kr.soyuo.serox.mcp.tool.McpCallLog;
import kr.soyuo.serox.mcp.tool.SearchAndSummarizeTool;
import kr.soyuo.serox.mcp.tool.ToolRegistry;
import kr.soyuo.serox.search.api.SearchProvider;
import kr.soyuo.serox.search.provider.BraveSearchProvider;
import kr.soyuo.serox.search.provider.DuckDuckGoSearchProvider;

/** Builds the MCP server for an agent that launches Serox with {@code --mcp}. */
public final class McpLauncher {
  static final String VERSION = "0.1.0";
  private static final int MAX_CONCURRENT_CALLS = 2;
  private static final int MAX_CALLS_PER_MINUTE = 20;

  /** Prevents instantiation of this launcher utility. */
  private McpLauncher() {}

  /**
   * Creates a server that offers only {@code search_and_summarize}, which the user must approve
   * through {@code SEROX_MCP_APPROVED_TOOLS}. Status changes go to {@code eventBus} when present.
   */
  public static StdioMcpServer create(
      Map<String, String> environment,
      Optional<BrowserEventBus> eventBus,
      InputStream input,
      OutputStream output) {
    return new StdioMcpServer(endpointFactory(environment).get(), input, output, eventBus);
  }

  /** Like {@link #endpointFactory}, but the services are only built when the first agent connects. */
  public static Supplier<McpEndpoint> lazyEndpointFactory(Map<String, String> environment) {
    return lazyEndpointFactory(environment, new McpCallLog(), name -> true);
  }

  /** Like {@link #lazyEndpointFactory(Map)}, with a call record and a switch for each tool. */
  public static Supplier<McpEndpoint> lazyEndpointFactory(
      Map<String, String> environment, McpCallLog log, Predicate<String> enabled) {
    return new Supplier<>() {
      private Supplier<McpEndpoint> built;

      @Override
      public synchronized McpEndpoint get() {
        if (built == null) {
          built = endpointFactory(environment, log, enabled);
        }
        return built.get();
      }
    };
  }

  /**
   * Makes endpoints that offer only {@code search_and_summarize} under the same approval, rate
   * limit and services, so every client session of an HTTP server shares them.
   */
  public static Supplier<McpEndpoint> endpointFactory(Map<String, String> environment) {
    return endpointFactory(environment, new McpCallLog(), name -> true);
  }

  /** Like {@link #endpointFactory(Map)}, with a call record and a switch for each tool. */
  public static Supplier<McpEndpoint> endpointFactory(
      Map<String, String> environment, McpCallLog log, Predicate<String> enabled) {
    var configuration = AppConfigurationLoader.fromEnvironment(environment);
    Optional<SearchProvider> search =
        configuration
            .braveSearchApiKey()
            .map(key -> (SearchProvider) new BraveSearchProvider(key.revealForRequest()))
            .or(() -> Optional.of(new DuckDuckGoSearchProvider()));
    Optional<AiProvider> ai = AiProviders.fromConfiguration(configuration);
    var summary =
        new SearchSummaryService(
            search, ai, AppBootstrap.createPageFetcher(), new ContextBuilder(), Clock.systemUTC());
    var policy =
        new AccessPolicy(
            new EnvironmentApprovalGate(environment),
            Set.of(SearchAndSummarizeTool.NAME),
            MAX_CONCURRENT_CALLS,
            MAX_CALLS_PER_MINUTE,
            Clock.systemUTC());
    var registry =
        new ToolRegistry(policy)
            .register(new SearchAndSummarizeTool(summary))
            .setCallLog(log)
            .setEnabled(enabled);
    return () -> {
      var endpoint = new McpEndpoint(VERSION);
      registry.installInto(endpoint);
      return endpoint;
    };
  }

  /** Serves one session on the process streams without opening a window; blocks until it ends. */
  public static void runHeadless(Map<String, String> environment) {
    SeroxLogging.configure();
    var server = create(environment, Optional.empty(), System.in, System.out);
    server.start().toCompletableFuture().join();
  }
}
