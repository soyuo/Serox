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
            keeper -> keeper.restoreOrStart(runtime.browserController()),
            () -> runtime.browserController().createTab());
    browserShell.setAiSearchService(runtime.aiSearchService());
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
            McpLauncher.lazyEndpointFactory(System.getenv()), System.getenv(), runtime.eventBus());
    McpSettings mcpSettings = loadMcpSettings();
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
                  storage.mcpSettings().load().toCompletableFuture().orTimeout(3, TimeUnit.SECONDS).join())
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
