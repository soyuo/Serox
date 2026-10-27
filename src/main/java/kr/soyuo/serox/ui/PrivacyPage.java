package kr.soyuo.serox.ui;

import java.util.List;
import java.util.function.Predicate;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** The "기록과 개인정보" page: what is stored, and buttons to delete it after a confirmation. */
final class PrivacyPage extends ScrollPane {
  private final PrivacyActions actions;
  private final Predicate<String> confirm;
  private final VBox recent = new VBox(UiTokens.SPACE_SM);
  private final Label message = new Label();

  PrivacyPage(PrivacyActions actions) {
    this(actions, PrivacyPage::askUser);
  }

  /** {@code confirm} receives the question and answers whether the user agreed. */
  PrivacyPage(PrivacyActions actions, Predicate<String> confirm) {
    this.actions = actions;
    this.confirm = confirm;
    getStyleClass().add("settings-scroll");
    setFitToWidth(true);
    VBox content = new VBox(UiTokens.SPACE_LG);
    content.getStyleClass().add("settings-info");
    content.setPadding(new Insets(32, 48, 32, 48));
    content.setMaxWidth(UiTokens.HOME_CONTENT_WIDTH);
    Label title = new Label("기록과 개인정보");
    title.getStyleClass().add("settings-page-title");
    Label hint = text("무엇을 저장하고, 무엇을 지울지 정해요.");
    content.getChildren().addAll(title, hint);
    for (String line :
        List.of(
            "방문 기록은 90일 동안, 최대 5,000개까지 이 컴퓨터에만 저장해요.",
            "북마크, 최근 검색, 열려 있던 탭도 이 컴퓨터의 데이터 폴더에만 저장해요.",
            "데이터 폴더: %LOCALAPPDATA%\\Serox")) {
      content.getChildren().add(text(line));
    }
    content.getChildren().add(heading("최근 검색 기록"));
    recent.getStyleClass().add("privacy-list");
    content.getChildren().add(recent);
    content.getChildren().add(heading("지우기"));
    content.getChildren().add(clearRow("검색 기록 모두 지우기", "검색 기록을 모두 지울까요?", actions::clearQueries, true));
    content.getChildren().add(clearRow("방문 기록 모두 지우기", "방문 기록을 모두 지울까요?", actions::clearVisits, false));
    message.getStyleClass().add("search-state-message");
    content.getChildren().add(message);
    setContent(content);
    refresh();
  }

  private void refresh() {
    actions
        .recentQueries()
        .thenAccept(queries -> FxThreadExecutor.execute(() -> showRecent(queries)));
  }

  private void showRecent(List<String> queries) {
    recent.getChildren().clear();
    if (!actions.available()) {
      recent.getChildren().add(text("이 컴퓨터에 저장할 수 없어서 기록이 없어요."));
      return;
    }
    if (queries.isEmpty()) {
      recent.getChildren().add(text("검색 기록이 없어요."));
      return;
    }
    for (String query : queries) {
      Label name = new Label(query);
      name.getStyleClass().add("privacy-query");
      name.setMaxWidth(Double.MAX_VALUE);
      HBox.setHgrow(name, Priority.ALWAYS);
      Button delete = new Button("삭제");
      delete.getStyleClass().add("secondary-button");
      delete.setAccessibleText("‘" + query + "’ 검색 기록 삭제");
      delete.setOnAction(
          event -> actions.deleteQuery(query).thenRun(() -> FxThreadExecutor.execute(this::refresh)));
      HBox row = new HBox(UiTokens.SPACE_MD, name, delete);
      row.setAlignment(Pos.CENTER_LEFT);
      row.getStyleClass().add("privacy-row");
      recent.getChildren().add(row);
    }
  }

  private HBox clearRow(
      String label,
      String question,
      java.util.function.Supplier<java.util.concurrent.CompletionStage<Void>> action,
      boolean refreshList) {
    Label name = new Label(label);
    name.getStyleClass().add("privacy-query");
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    Button button = new Button("지우기");
    button.getStyleClass().add("danger-button");
    button.setAccessibleText(label);
    button.setDisable(!actions.available());
    button.setOnAction(
        event -> {
          if (!confirm.test(question)) {
            return;
          }
          action
              .get()
              .thenRun(
                  () ->
                      FxThreadExecutor.execute(
                          () -> {
                            message.setText("지웠어요.");
                            if (refreshList) {
                              refresh();
                            }
                          }));
        });
    HBox row = new HBox(UiTokens.SPACE_MD, name, spacer, button);
    row.setAlignment(Pos.CENTER_LEFT);
    row.getStyleClass().add("privacy-row");
    return row;
  }

  private static Label heading(String text) {
    Label label = new Label(text);
    label.getStyleClass().add("search-section-heading");
    return label;
  }

  private static Label text(String text) {
    Label label = new Label(text);
    label.setWrapText(true);
    label.getStyleClass().add("search-state-message");
    return label;
  }

  /** The confirmation window from the design: a short question and a red "지우기" button. */
  private static boolean askUser(String question) {
    if (!Platform.isFxApplicationThread()) {
      return false;
    }
    ButtonType clear = new ButtonType("지우기", ButtonBar.ButtonData.OK_DONE);
    ButtonType cancel = new ButtonType("취소", ButtonBar.ButtonData.CANCEL_CLOSE);
    Alert alert = new Alert(Alert.AlertType.NONE, "지운 기록은 되돌릴 수 없어요.", cancel, clear);
    alert.setTitle("확인");
    alert.setHeaderText(question);
    alert.getDialogPane().getStylesheets().add(PrivacyPage.class.getResource("serox.css").toExternalForm());
    alert.getDialogPane().getStyleClass().add("confirm-dialog");
    return alert.showAndWait().filter(button -> button == clear).isPresent();
  }
}
