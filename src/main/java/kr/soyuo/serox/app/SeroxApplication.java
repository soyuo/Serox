package kr.soyuo.serox.app;

import kr.soyuo.serox.ui.BrowserShellView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class SeroxApplication extends Application {
    @Override
    public void start(Stage stage) {
        BrowserShellView browserShell = new BrowserShellView();
        Scene scene = new Scene(browserShell, 1100, 720);

        stage.setTitle("Serox");
        stage.setMinWidth(760);
        stage.setMinHeight(560);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
