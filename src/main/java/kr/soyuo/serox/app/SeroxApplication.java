package kr.soyuo.serox.app;

import java.util.List;
import java.util.Optional;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import kr.soyuo.serox.mcp.server.StdioMcpServer;
import kr.soyuo.serox.ui.BrowserShellView;
import kr.soyuo.serox.ui.UiTokens;

/** Launches the Serox desktop browser application. */
public final class SeroxApplication extends Application {
  private SeroxRuntime runtime;
  private BrowserShellView browserShell;
  private StdioMcpServer mcpServer;

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
    Scene scene = new Scene(browserShell, 1100, 720);

    stage.initStyle(StageStyle.UNDECORATED);
    stage.setMinWidth(UiTokens.WINDOW_MIN_WIDTH);
    stage.setMinHeight(UiTokens.WINDOW_MIN_HEIGHT);
    stage.setScene(scene);
    stage.show();
  }

  @Override
  public void stop() {
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
