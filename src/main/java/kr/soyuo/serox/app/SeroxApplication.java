package kr.soyuo.serox.app;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import kr.soyuo.serox.core.model.McpSettings;
import kr.soyuo.serox.mcp.server.StdioMcpServer;
import kr.soyuo.serox.ui.BrowserShellView;
import kr.soyuo.serox.ui.McpActions;
import kr.soyuo.serox.ui.UiTokens;

/** Launches the Serox desktop browser application. */
public final class SeroxApplication extends Application {
  private static final Logger LOGGER = Logger.getLogger(SeroxApplication.class.getName());

  private SeroxRuntime runtime;
  private BrowserShellView browserShell;
  private StdioMcpServer mcpServer;
  private McpHttpController mcpHttp;
  private final kr.soyuo.serox.mcp.tool.McpCallLog mcpCalls =
      new kr.soyuo.serox.mcp.tool.McpCallLog();
  private volatile McpSettings mcpNow = McpSettings.defaults();

  @Override
  public void start(Stage stage) {
    runtime = AppBootstrap.createRuntime();
    List<String> recentQueries =
        runtime
            .usageRecorder()
            .map(recorder -> recorder.recentQueries(5).toCompletableFuture().join())
            .orElse(List.of());
    browserShell =
        new BrowserShellView(runtime.browserController(), runtime.searchService(), recentQueries);
    runtime
        .sessionKeeper()
        .ifPresentOrElse(
            keeper -> {
              var general = runtime.settingsService().current();
              keeper.restoreOrStart(
                  runtime.browserController(), general.startupMode(), general.startPageUrl());
            },
            () -> runtime.browserController().createTab());
    browserShell.setAiSearchService(runtime.aiSearchService());
    browserShell.setSearchActions(searchActions());
    browserShell.setAiConnections(
        runtime.aiConnections(), uri -> getHostServices().showDocument(uri.toString()));
    runtime.announceServiceStatus();
    browserShell.setSettingsService(runtime.settingsService());
    browserShell.setLibraryService(runtime.libraryService());
    browserShell.setPrivacyActions(runtime.usageRecorder(), runtime.libraryService());
    if (getParameters().getRaw().contains("--mcp")) {
      mcpServer =
          McpLauncher.create(
              System.getenv(), Optional.of(runtime.eventBus()), System.in, System.out);
      mcpServer.start();
    }
    mcpHttp =
        new McpHttpController(
            McpLauncher.lazyEndpointFactory(
                System.getenv(), mcpCalls, name -> mcpNow.searchToolEnabled()),
            System.getenv(),
            runtime.eventBus());
    McpSettings mcpSettings = loadMcpSettings();
    mcpNow = mcpSettings;
    if (mcpSettings.autoStart()) {
      var started = mcpHttp.start(mcpSettings);
      if (!started.running()) {
        LOGGER.log(Level.WARNING, "MCP HTTP server did not start: {0}", started.message());
      }
    }
    browserShell.setMcpActions(mcpActions(mcpSettings));
    Scene scene = new Scene(browserShell, 1100, 720);

    stage.initStyle(StageStyle.UNDECORATED);
    stage.setMinWidth(UiTokens.WINDOW_MIN_WIDTH);
    stage.setMinHeight(UiTokens.WINDOW_MIN_HEIGHT);
    stage.setScene(scene);
    stage.show();
  }

  /** Applies a protected search key without restarting the browser. */
  private kr.soyuo.serox.ui.SearchActions searchActions() {
    return new kr.soyuo.serox.ui.SearchActions() {
      @Override
      public boolean configured() {
        return runtime.searchService().configured();
      }

      @Override
      public java.util.concurrent.CompletionStage<Void> saveKey(String key) {
        var result = new java.util.concurrent.CompletableFuture<Void>();
        Thread.ofVirtual()
            .start(
                () -> {
                  try {
                    if (key.isBlank()
                        || key.length() > 16 * 1024
                        || key.contains("\n")
                        || key.contains("\r")) {
                      throw new IllegalArgumentException("Invalid search key");
                    }
                    var directory = AppBootstrap.resolveDataDirectory(System.getenv());
                    var store =
                        new kr.soyuo.serox.ai.auth.ProtectedLocalStore(
                            directory.resolve("credentials/search.dpapi"));
                    var data = new com.google.gson.JsonObject();
                    data.addProperty("key", key);
                    store.write(data.toString());
                    runtime
                        .searchService()
                        .setProvider(new kr.soyuo.serox.search.provider.BraveSearchProvider(key));
                    runtime
                        .eventBus()
                        .publish(
                            new kr.soyuo.serox.core.event.ConnectionStatusChanged(
                                java.time.Instant.now(),
                                kr.soyuo.serox.core.event.ServiceId.SEARCH,
                                kr.soyuo.serox.core.event.ServiceStatus.CONNECTED,
                                "키 설정됨 (Brave)"));
                    result.complete(null);
                  } catch (Exception failure) {
                    result.completeExceptionally(
                        new IllegalStateException("Search key could not be saved"));
                  }
                });
        return result;
      }
    };
  }

  /** Lets the MCP page start and stop the server and keep its settings. */
  private McpActions mcpActions(McpSettings initial) {
    return new McpActions() {
      private McpSettings current = initial;

      @Override
      public synchronized McpSettings settings() {
        return current;
      }

      @Override
      public synchronized void save(McpSettings settings) {
        current = settings;
        mcpNow = settings;
        runtime
            .storage()
            .ifPresent(
                storage ->
                    storage
                        .mcpSettings()
                        .save(settings)
                        .exceptionally(
                            failure -> {
                              LOGGER.log(Level.WARNING, "MCP settings could not be saved.");
                              return null;
                            }));
      }

      @Override
      public boolean running() {
        return mcpHttp.isRunning();
      }

      @Override
      public java.util.List<Call> recentCalls() {
        return mcpCalls.recent().stream()
            .map(entry -> new Call(entry.summary(), entry.client(), entry.at(), entry.success()))
            .toList();
      }

      @Override
      public Result start(McpSettings settings) {
        var result = mcpHttp.start(settings);
        return new Result(result.running(), result.portInUse(), result.message());
      }

      @Override
      public void stop() {
        mcpHttp.stop();
      }
    };
  }

  /** The saved MCP server settings; the safe defaults when nothing can be read. */
  private McpSettings loadMcpSettings() {
    try {
      return runtime
          .storage()
          .map(
              storage ->
                  storage
                      .mcpSettings()
                      .load()
                      .toCompletableFuture()
                      .orTimeout(3, TimeUnit.SECONDS)
                      .join())
          .orElse(McpSettings.defaults());
    } catch (RuntimeException unreadable) {
      LOGGER.log(Level.WARNING, "MCP settings could not be read; using the defaults.");
      return McpSettings.defaults();
    }
  }

  @Override
  public void stop() {
    if (mcpHttp != null) {
      mcpHttp.close();
    }
    if (mcpServer != null) {
      mcpServer.stop();
    }
    if (browserShell != null) {
      browserShell.close();
    }
    if (runtime != null) {
      runtime.close();
    }
  }

  public static void main(String[] args) {
    if (List.of(args).contains("--mcp-headless")) {
      McpLauncher.runHeadless(System.getenv());
      // The session is over; do not wait for any lingering non-daemon worker thread.
      System.exit(0);
      return;
    }
    launch(args);
  }
}
