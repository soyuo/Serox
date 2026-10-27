package kr.soyuo.serox.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Asks before a secure (https) attempt is retried over plain http, as in the design. Nothing is
 * sent over http unless the user chooses it.
 */
final class HttpFallbackView extends StackPane {
  HttpFallbackView(String host, Runnable useHttp, Runnable cancel) {
    getStyleClass().add("error-page");
    Label heading = new Label("http로 연결할까요?");
    heading.getStyleClass().add("error-page-title");
    Label site = new Label(host);
    site.getStyleClass().add("search-state-message");
    Label detail =
        new Label(
            "보안 연결(https)에 실패했어요. http로 연결하면 주고받는 정보가 암호화되지 않아서 "
                + "다른 사람이 볼 수 있어요. 비밀번호나 카드 번호는 입력하지 마세요.");
    detail.setWrapText(true);
    detail.setAlignment(Pos.CENTER);
    detail.getStyleClass().add("search-state-message");
    Button cancelButton = new Button("취소");
    cancelButton.getStyleClass().add("primary-button");
    cancelButton.setOnAction(event -> cancel.run());
    Button httpButton = new Button("http로 연결");
    httpButton.getStyleClass().add("secondary-button");
    httpButton.setOnAction(event -> useHttp.run());
    HBox buttons = new HBox(UiTokens.SPACE_SM, cancelButton, httpButton);
    buttons.setAlignment(Pos.CENTER);
    VBox column = new VBox(UiTokens.SPACE_LG, heading, site, detail, buttons);
    column.setAlignment(Pos.CENTER);
    column.setMaxWidth(UiTokens.ERROR_PAGE_WIDTH);
    column.setPadding(new Insets(UiTokens.SPACE_LG * 2));
    getChildren().add(column);
    setAccessibleText("http로 연결할까요?");
  }
}
