package kr.soyuo.serox.ui;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import kr.soyuo.serox.core.model.SearchPreferences;

/** Search settings that apply to subsequent provider requests and evidence collection. */
final class SearchPage extends ScrollPane {
  SearchPage(PrivacyActions settings, SearchActions search) {
    setAccessibleText("검색 설정");
    VBox content = SettingsControls.content("검색", "검색 결과를 가져오고 읽는 방식을 정해요.");
    VBox card = SettingsControls.card();
    card.setMaxWidth(862);
    Button provider = new Button("Brave Search API");
    provider.setGraphic(SvgIconView.icon(UiTokens.ICON_SETTINGS_DROPDOWN, 14));
    provider.setContentDisplay(javafx.scene.control.ContentDisplay.RIGHT);
    provider.setPrefWidth(220);
    provider.setDisable(true);
    card.getChildren().add(SettingsControls.row("검색 API", "키가 없으면 DuckDuckGo, 키가 있으면 Brave Search API를 써요.", provider));
    Label message =
        SettingsControls.label(
            search.configured()
                ? "API 키 설정됨 · 다음 검색에서 연결을 확인해요."
                : "키 없이 DuckDuckGo로 검색하고 있어요. Brave 키를 넣으면 Brave로 바뀌어요.",
            "search-state-message");
    PasswordField key = new PasswordField();
    key.setPrefWidth(260);
    key.setAccessibleText("Brave Search API 키");
    key.setPromptText("API 키 입력");
    Button save = new Button("키 저장");
    save.setOnAction(
        event -> {
          String secret = key.getText();
          key.clear();
          save.setDisable(true);
          search
              .saveKey(secret)
              .whenComplete(
                  (ignored, failure) ->
                      Platform.runLater(
                          () -> {
                            save.setDisable(false);
                            message.setText(
                                failure == null
                                    ? "API 키를 암호화해 저장했어요. 다음 검색부터 사용해요."
                                    : "키를 저장하지 못했어요. 키와 보안 저장소를 확인하세요.");
                          }));
        });
    card.getChildren()
        .add(
            SettingsControls.row(
                "API 키", "키는 Windows 사용자 계정으로 암호화해 저장해요.", new HBox(8, key, save)));
    Label count = new Label(settings.settings().search().evidencePages() + "개");
    Button minus = new Button("−");
    Button plus = new Button("+");
    Runnable refresh =
        () -> {
          int value = settings.settings().search().evidencePages();
          count.setText(value + "개");
          minus.setDisable(value <= 1);
          plus.setDisable(value >= 10);
        };
    minus.setOnAction(
        event -> {
          update(
              settings,
              settings
                  .settings()
                  .search()
                  .withEvidencePages(settings.settings().search().evidencePages() - 1));
          refresh.run();
        });
    plus.setOnAction(
        event -> {
          update(
              settings,
              settings
                  .settings()
                  .search()
                  .withEvidencePages(settings.settings().search().evidencePages() + 1));
          refresh.run();
        });
    refresh.run();
    HBox stepper = new HBox(12, minus, count, plus);
    stepper.setAlignment(javafx.geometry.Pos.CENTER);
    card.getChildren()
        .add(SettingsControls.row("읽을 결과 수", "AI 요약을 위해 본문을 읽는 최대 페이지 수예요.", stepper));
    String mode =
        switch (settings.settings().search().safeSearch()) {
          case "off" -> "끄기";
          case "strict" -> "엄격";
          default -> "보통";
        };
    card.getChildren()
        .add(
            SettingsControls.row(
                "안전검색",
                "검색 제공자의 안전검색 필터를 사용해요.",
                SettingsControls.choices(
                    new String[] {"끄기", "보통", "엄격"},
                    mode,
                    chosen ->
                        update(
                            settings,
                            settings
                                .settings()
                                .search()
                                .withSafeSearch(
                                    switch (chosen) {
                                      case "끄기" -> "off";
                                      case "엄격" -> "strict";
                                      default -> "moderate";
                                    })))));
    Slider timeout = new Slider(10, 300, settings.settings().search().timeoutSeconds());
    timeout.setPrefWidth(196);
    timeout.setAccessibleText("본문 수집 제한 시간 (초)");
    Label seconds = new Label();
    seconds.textProperty().bind(timeout.valueProperty().asString("%.0f초"));
    timeout
        .valueChangingProperty()
        .addListener(
            (observable, before, changing) -> {
              if (!changing)
                update(
                    settings,
                    settings
                        .settings()
                        .search()
                        .withTimeoutSeconds((int) Math.round(timeout.getValue())));
            });
    timeout.setOnKeyReleased(
        event ->
            update(
                settings,
                settings
                    .settings()
                    .search()
                    .withTimeoutSeconds((int) Math.round(timeout.getValue()))));
    timeout.setOnMouseReleased(
        event ->
            update(
                settings,
                settings
                    .settings()
                    .search()
                    .withTimeoutSeconds((int) Math.round(timeout.getValue()))));
    card.getChildren()
        .add(
            SettingsControls.row(
                "본문 수집 제한 시간", "10초부터 5분까지 설정할 수 있어요.", sliderBox(timeout, seconds)));
    var robots = SettingsControls.switchControl("수집을 막은 사이트는 건너뛰기", false, value -> {});
    robots.setDisable(true);
    card.getChildren()
        .add(
            SettingsControls.row(
                "수집을 막은 사이트는 건너뛰기",
                "robots.txt 해석은 아직 지원하지 않아요. 접근 오류가 있는 페이지는 항상 건너뛰어요.",
                robots));
    content.getChildren().addAll(card, message);
    SettingsControls.configure(this, content);
  }

  /** The design's slider: value on top, thin track, and the two ends labelled below. */
  private static VBox sliderBox(Slider slider, Label value) {
    slider.getStyleClass().add("serox-slider");
    value.getStyleClass().add("slider-value");
    HBox top = new HBox(value);
    top.setAlignment(Pos.CENTER_RIGHT);
    Label low = new Label("10초");
    Label high = new Label("5분");
    low.getStyleClass().add("slider-range");
    high.getStyleClass().add("slider-range");
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    VBox box = new VBox(4, top, slider, new HBox(low, spacer, high));
    box.setPrefWidth(slider.getPrefWidth());
    box.setMaxWidth(slider.getPrefWidth());
    return box;
  }

  private static void update(PrivacyActions actions, SearchPreferences value) {
    actions.save(actions.settings().withSearchPreferences(value));
  }
}
