package kr.soyuo.serox.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import kr.soyuo.serox.core.model.AppLanguage;
import kr.soyuo.serox.core.model.BrowserSettings;
import kr.soyuo.serox.core.model.StartupMode;

/** The "일반" page: what the browser opens with, and the language of menus and summaries. */
final class GeneralPage extends ScrollPane {
  private final PrivacyActions actions;
  private final Label message = new Label();
  private final HBox addressRow = new HBox(UiTokens.SPACE_MD);
  private final TextField addressField = new TextField();

  GeneralPage(PrivacyActions actions) {
    this.actions = actions;
    getStyleClass().add("settings-scroll");
    setFitToWidth(true);
    setAccessibleText("일반 설정");
    VBox content = new VBox(UiTokens.SPACE_LG);
    content.getStyleClass().add("settings-info");
    content.setPadding(new Insets(32, 48, 32, 48));
    content.setMaxWidth(UiTokens.HOME_CONTENT_WIDTH);
    Label title = new Label("일반");
    title.getStyleClass().add("settings-page-title");
    Label hint = new Label("브라우저가 시작되고 동작하는 기본 방식을 정해요.");
    hint.setWrapText(true);
    hint.getStyleClass().add("search-state-message");
    message.setWrapText(true);
    message.getStyleClass().add("search-state-message");
    content.getChildren().addAll(title, hint, heading("시작"), startupRow(), addressRow());
    content.getChildren().addAll(languageRow(), message);
    setContent(content);
    showAddressRow(actions.settings().startupMode());
  }

  private HBox startupRow() {
    ToggleGroup group = new ToggleGroup();
    HBox choices = new HBox(UiTokens.SPACE_SM);
    StartupMode current = actions.settings().startupMode();
    for (StartupMode mode : StartupMode.values()) {
      ToggleButton option =
          new ToggleButton(
              switch (mode) {
                case HOME -> "홈 화면";
                case RESTORE_TABS -> "이전 탭 이어서";
                case SPECIFIC_PAGE -> "특정 페이지";
              });
      option.getStyleClass().add("segment-option");
      option.setToggleGroup(group);
      option.setSelected(mode == current);
      option.setAccessibleText("브라우저를 열면 " + option.getText());
      option.setDisable(!actions.available());
      option.setOnAction(
          event -> {
            if (option.isSelected()) {
              message.setText("");
              showAddressRow(mode);
              actions.save(actions.settings().withStartupMode(mode));
            } else {
              option.setSelected(true);
            }
          });
      choices.getChildren().add(option);
    }
    return row("브라우저를 열면", choices);
  }

  /** The address of the start page; it only matters, and only shows, for "특정 페이지". */
  private HBox addressRow() {
    addressField.setPromptText("https://example.com/");
    addressField.setText(actions.settings().startPageUrl());
    addressField.setPrefColumnCount(26);
    addressField.setAccessibleText("시작 페이지 주소");
    addressField.setDisable(!actions.available());
    addressField.setOnAction(event -> saveAddress());
    addressField.focusedProperty().addListener((o, was, focused) -> {
      if (!focused) {
        saveAddress();
      }
    });
    Label name = new Label("시작 페이지 주소");
    name.getStyleClass().add("privacy-query");
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    addressRow.getChildren().setAll(name, spacer, addressField);
    addressRow.setAlignment(Pos.CENTER_LEFT);
    addressRow.getStyleClass().add("privacy-row");
    return addressRow;
  }

  private void showAddressRow(StartupMode mode) {
    boolean visible = mode == StartupMode.SPECIFIC_PAGE;
    addressRow.setVisible(visible);
    addressRow.setManaged(visible);
  }

  private void saveAddress() {
    String text = addressField.getText().strip();
    if (text.equals(actions.settings().startPageUrl())) {
      return;
    }
    if (!text.isEmpty() && !BrowserSettings.isWebAddress(text)) {
      message.setText("시작 페이지는 http:// 또는 https:// 로 시작하는 주소여야 해요.");
      return;
    }
    message.setText(text.isEmpty() ? "시작 페이지를 비웠어요. 홈 화면이 열려요." : "시작 페이지를 저장했어요.");
    actions.save(actions.settings().withStartPageUrl(text));
  }

  private HBox languageRow() {
    ComboBox<AppLanguage> languages = new ComboBox<>();
    languages.getItems().setAll(AppLanguage.values());
    languages.setValue(actions.settings().language());
    languages.setAccessibleText("언어");
    languages.setDisable(!actions.available());
    languages.setCellFactory(list -> languageCell());
    languages.setButtonCell(languageCell());
    languages
        .valueProperty()
        .addListener(
            (observable, before, chosen) -> {
              if (chosen != null && chosen != actions.settings().language()) {
                message.setText("언어를 바꿨어요. AI 요약은 다음 검색부터 이 언어로 나와요.");
                actions.save(actions.settings().withLanguage(chosen));
              }
            });
    VBox name = new VBox(2);
    Label title = new Label("언어");
    title.getStyleClass().add("privacy-query");
    Label detail = new Label("AI 요약에 쓰는 기본 언어예요.");
    detail.getStyleClass().add("search-state-message");
    name.getChildren().addAll(title, detail);
    return row(name, languages);
  }

  private static ListCell<AppLanguage> languageCell() {
    return new ListCell<>() {
      @Override
      protected void updateItem(AppLanguage item, boolean empty) {
        super.updateItem(item, empty);
        setText(empty || item == null ? null : item.label());
      }
    };
  }

  private static HBox row(String label, Node control) {
    Label name = new Label(label);
    name.getStyleClass().add("privacy-query");
    return row(name, control);
  }

  private static HBox row(Node name, Node control) {
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    HBox row = new HBox(UiTokens.SPACE_MD, name, spacer, control);
    row.setAlignment(Pos.CENTER_LEFT);
    row.getStyleClass().add("privacy-row");
    return row;
  }

  private static Label heading(String text) {
    Label heading = new Label(text);
    heading.getStyleClass().add("search-section-heading");
    heading.setPadding(Insets.EMPTY);
    return heading;
  }
}
