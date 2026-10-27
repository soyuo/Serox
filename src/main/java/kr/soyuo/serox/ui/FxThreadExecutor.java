package kr.soyuo.serox.ui;

import javafx.application.Platform;

/** Runs a UI update on the JavaFX Application Thread. */
final class FxThreadExecutor {
  private FxThreadExecutor() {}

  static void execute(Runnable action) {
    if (Platform.isFxApplicationThread()) {
      action.run();
    } else {
      Platform.runLater(action);
    }
  }
}
