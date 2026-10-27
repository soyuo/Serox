package kr.soyuo.serox.app;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import kr.soyuo.serox.ui.BrowserShellView;

/** Launches the Serox desktop browser application. */
public final class SeroxApplication extends Application {
  private SeroxRuntime runtime;
  private BrowserShellView browserShell;

  @Override
  public void start(Stage stage) {
    runtime = AppBootstrap.createRuntime();
    browserShell = new BrowserShellView(runtime.eventBus());
    Scene scene = new Scene(browserShell, 1100, 720);

    stage.initStyle(StageStyle.UNDECORATED);
    stage.setMinWidth(760);
    stage.setMinHeight(560);
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
