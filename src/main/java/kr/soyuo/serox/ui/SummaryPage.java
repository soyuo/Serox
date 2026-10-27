package kr.soyuo.serox.ui;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import kr.soyuo.serox.core.model.SummaryPreferences;

/** Summary controls retain citation validation and the global 24,000-character evidence cap. */
final class SummaryPage extends ScrollPane {
  SummaryPage(PrivacyActions settings) {
    setAccessibleText("요약 설정");
    VBox content = SettingsControls.content("요약", "AI가 답변을 정리하는 방식을 정해요.");
    VBox card = SettingsControls.card();
    card.setMaxWidth(860);
    SummaryPreferences initial = settings.settings().summary();
    String language =
        switch (initial.language()) {
          case "Korean" -> "한국어";
          case "app" -> "일반 설정 언어";
          default -> "검색어와 같게";
        };
    card.getChildren()
        .add(
            SettingsControls.row(
                "요약 언어",
                "다음 검색부터 적용해요.",
                SettingsControls.choices(
                    new String[] {"검색어와 같게", "한국어", "일반 설정 언어"},
                    language,
                    chosen ->
                        update(
                            settings,
                            settings
                                .settings()
                                .summary()
                                .withLanguage(
                                    switch (chosen) {
                                      case "한국어" -> "Korean";
                                      case "일반 설정 언어" -> "app";
                                      default -> "question";
                                    })))));
    card.getChildren()
        .add(
            SettingsControls.row(
                "핵심 정리 표시",
                "요약 아래에 주요 항목을 따로 표시해요.",
                SettingsControls.switchControl(
                    "핵심 정리 표시",
                    initial.keyPoints(),
                    value ->
                        update(settings, settings.settings().summary().withKeyPoints(value)))));
    card.getChildren()
        .add(
            SettingsControls.row(
                "문장마다 출처 표시",
                "끄더라도 출처 검증과 출처 목록은 유지돼요.",
                SettingsControls.switchControl(
                    "문장마다 출처 표시",
                    initial.inlineCitations(),
                    value ->
                        update(
                            settings, settings.settings().summary().withInlineCitations(value)))));
    var longText = SettingsControls.choices(new String[] {"앞부분만", "나눠서 요약"}, "앞부분만", value -> {});
    longText.setDisable(true);
    card.getChildren()
        .add(
            SettingsControls.row(
                "긴 본문 처리", "현재는 제한 길이의 앞부분을 읽어요. 여러 번 나눠 호출하는 요약은 아직 지원하지 않아요.", longText));
    card.getChildren()
        .add(
            SettingsControls.row(
                "출처당 읽을 글자 수",
                "전체 요청의 근거 본문은 최대 24,000자로 제한돼요.",
                SettingsControls.choices(
                    new String[] {"3,000자", "8,000자", "20,000자"},
                    String.format(java.util.Locale.ROOT, "%,d자", initial.sourceCharacters()),
                    chosen ->
                        update(
                            settings,
                            settings
                                .settings()
                                .summary()
                                .withSourceCharacters(
                                    Integer.parseInt(chosen.replace(",", "").replace("자", "")))))));
    var fallback = SettingsControls.switchControl("실패해도 검색 결과 유지", true, value -> {});
    fallback.setDisable(true);
    card.getChildren()
        .add(SettingsControls.row("실패해도 검색 결과 유지", "일반 검색과 출처는 항상 유지하고 AI 오류를 함께 안내해요.", fallback));
    TextArea instructions = new TextArea(initial.instructions());
    instructions.setAccessibleText("요약 지침");
    instructions.setPrefRowCount(3);
    instructions.setWrapText(true);
    instructions.setTextFormatter(
        new TextFormatter<String>(
            change -> change.getControlNewText().length() <= 2000 ? change : null));
    Button reset = new Button("기본값으로 되돌리기");
    reset.setOnAction(event -> instructions.setText(SummaryPreferences.DEFAULT_INSTRUCTIONS));
    Label message = SettingsControls.label("지침은 저장 버튼을 누르면 다음 검색부터 사용해요.", "search-state-message");
    Button save = new Button("지침 저장");
    save.getStyleClass().add("connection-primary");
    save.setOnAction(
        event ->
            settings
                .save(
                    settings
                        .settings()
                        .withSummaryPreferences(
                            settings.settings().summary().withInstructions(instructions.getText())))
                .whenComplete(
                    (ignored, failure) ->
                        Platform.runLater(
                            () ->
                                message.setText(
                                    failure == null ? "요약 지침을 적용했어요." : "지침을 저장하지 못했어요."))));
    content
        .getChildren()
        .addAll(
            card,
            SettingsControls.label("요약 지침", "settings-row-title"),
            instructions,
            new HBox(12, reset, save),
            message);
    SettingsControls.configure(this, content);
  }

  private static void update(PrivacyActions actions, SummaryPreferences value) {
    actions.save(actions.settings().withSummaryPreferences(value));
  }
}
