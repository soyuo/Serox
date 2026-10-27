package kr.soyuo.serox.app;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import kr.soyuo.serox.ui.BrowserShellView;
import kr.soyuo.serox.ui.UiTokens;

/** Launches the Serox desktop browser application. */
public final class SeroxApplication extends Application {
  private SeroxRuntime runtime;
  private BrowserShellView browserShell;

  @Override
  public void start(Stage stage) {
    runtime = AppBootstrap.createRuntime();
    browserShell = new BrowserShellView(runtime.eventBus());
    browserShell.setStyle("-fx-font-family: \"" + UiTokens.FONT_FAMILY + "\";");
    Scene scene = new Scene(browserShell, 1100, 720);

    stage.initStyle(StageStyle.UNDECORATED);
    stage.setMinWidth(UiTokens.WINDOW_MIN_WIDTH);
    stage.setMinHeight(UiTokens.WINDOW_MIN_HEIGHT);
    stage.setScene(scene);
    stage.show();
  }

  @Override
  public void stop() {
    if (browserShell != null) {
      browserShell.close();
    }
    if (runtime != null) {
      runtime.close();
    }
  }

  public static void main(String[] args) {
    launch(args);
  }
}
