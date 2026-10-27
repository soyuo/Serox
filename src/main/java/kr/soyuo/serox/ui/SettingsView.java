package kr.soyuo.serox.ui;

import java.util.List;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import kr.soyuo.serox.core.model.AiPanelPosition;
import kr.soyuo.serox.core.model.BrowserSettings;
import kr.soyuo.serox.core.model.DisplayDensity;
import kr.soyuo.serox.core.model.ThemeMode;

/** Edits the appearance settings; changes are stored in a draft until saved. */
final class SettingsView extends ScrollPane {
  private static final List<String> ACCENT_PRESETS =
      List.of("#2563EB", "#7C3AED", "#0F7B5F", "#D97706", "#DB2777", "#475569");

  private final Consumer<BrowserSettings> onSave;
  private final Runnable onClose;
  private final Label message = new Label();
  private final TextField accentField = new TextField();
  private BrowserSettings draft;

  SettingsView(BrowserSettings saved, Consumer<BrowserSettings> onSave, Runnable onClose) {
    this.onSave = onSave;
    this.onClose = onClose;
    this.draft = saved;
    getStyleClass().add("settings-scroll");
    setFitToWidth(true);
    setAccessibleText("설정");
    VBox content = new VBox(UiTokens.SPACE_LG);
    content.getStyleClass().add("settings-view");
    content.setPadding(new Insets(28, 40, 28, 40));
    content.setMaxWidth(UiTokens.HOME_CONTENT_WIDTH);
    Label title = new Label("설정");
    title.getStyleClass().add("search-query-heading");
    title.setPadding(Insets.EMPTY);
    message.getStyleClass().add("search-state-message");
    content.getChildren().addAll(title, themeSection(), accentSection(), fontSection());
    content.getChildren().addAll(panelSection(), densitySection(), message, buttons());
    setContent(content);
  }

  private VBox themeSection() {
    return section(
        "테마",
        choice(
            ThemeMode.values(),
            draft.theme(),
            mode ->
                switch (mode) {
                  case SYSTEM -> "시스템";
                  case LIGHT -> "라이트";
                  case DARK -> "다크";
                },
            mode -> update(withTheme(mode))));
  }

  private VBox accentSection() {
    HBox swatches = new HBox(UiTokens.SPACE_SM);
    for (String color : ACCENT_PRESETS) {
      Button swatch = new Button();
      swatch.setPrefSize(28, 28);
      swatch.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 14;");
      swatch.setAccessibleText("강조색 " + color);
      swatch.setOnAction(event -> setAccent(color));
      swatches.getChildren().add(swatch);
    }
    accentField.setText(draft.accentColor());
    accentField.setPrefColumnCount(8);
    accentField.setAccessibleText("강조색 16진수 값");
    accentField.setOnAction(event -> setAccent(accentField.getText().strip()));
    accentField.focusedProperty().addListener((o, was, focused) -> {
      if (!focused) {
        setAccent(accentField.getText().strip());
      }
    });
    return section("강조색", new HBox(UiTokens.SPACE_MD, swatches, accentField));
  }

  private VBox fontSection() {
    Slider slider = new Slider(80, 200, draft.fontScalePercent());
    slider.setMajorTickUnit(20);
    slider.setBlockIncrement(5);
    slider.setSnapToTicks(false);
    slider.setAccessibleText("글자 크기 퍼센트");
    Label value = new Label(draft.fontScalePercent() + "%");
    slider.valueProperty().addListener((o, was, now) -> {
      int percent = (int) Math.round(now.doubleValue() / 5.0) * 5;
      value.setText(percent + "%");
      update(withFontScale(percent));
    });
    HBox row = new HBox(UiTokens.SPACE_MD, slider, value);
    HBox.setHgrow(slider, javafx.scene.layout.Priority.ALWAYS);
    return section("글자 크기", row);
  }

  private VBox panelSection() {
    return section(
        "AI 패널 위치",
        choice(
            AiPanelPosition.values(),
            draft.aiPanelPosition(),
            position -> position == AiPanelPosition.LEFT ? "왼쪽" : "오른쪽",
            position -> update(withPanel(position))));
  }

  private VBox densitySection() {
    return section(
        "화면 밀도",
        choice(
            DisplayDensity.values(),
            draft.displayDensity(),
            density -> density == DisplayDensity.COMPACT ? "촘촘하게" : "여유 있게",
            density -> update(withDensity(density))));
  }

  private HBox buttons() {
    Button save = new Button("저장");
    save.getStyleClass().add("primary-button");
    save.setOnAction(
        event -> {
          onSave.accept(draft);
          onClose.run();
        });
    Button close = new Button("닫기");
    close.getStyleClass().add("secondary-button");
    close.setOnAction(event -> onClose.run());
    Region spacer = new Region();
    HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
    return new HBox(UiTokens.SPACE_SM, spacer, close, save);
  }

  private void setAccent(String color) {
    if (color.matches("#[0-9A-Fa-f]{6}")) {
      message.setText("");
      accentField.setText(color.toUpperCase(java.util.Locale.ROOT));
      update(withAccent(color.toUpperCase(java.util.Locale.ROOT)));
    } else {
      message.setText("강조색은 #2563EB처럼 6자리 16진수로 입력하세요.");
      accentField.setText(draft.accentColor());
    }
  }

  /** Called whenever the draft changes. */
  void update(BrowserSettings next) {
    draft = next;
  }

  BrowserSettings draft() {
    return draft;
  }

  private BrowserSettings withTheme(ThemeMode theme) {
    return new BrowserSettings(
        theme, draft.accentColor(), draft.fontScalePercent(), draft.aiPanelPosition(),
        draft.displayDensity());
  }

  private BrowserSettings withAccent(String accent) {
    return new BrowserSettings(
        draft.theme(), accent, draft.fontScalePercent(), draft.aiPanelPosition(),
        draft.displayDensity());
  }

  private BrowserSettings withFontScale(int percent) {
    return new BrowserSettings(
        draft.theme(), draft.accentColor(), percent, draft.aiPanelPosition(),
        draft.displayDensity());
  }

  private BrowserSettings withPanel(AiPanelPosition position) {
    return new BrowserSettings(
        draft.theme(), draft.accentColor(), draft.fontScalePercent(), position,
        draft.displayDensity());
  }

  private BrowserSettings withDensity(DisplayDensity density) {
    return new BrowserSettings(
        draft.theme(), draft.accentColor(), draft.fontScalePercent(), draft.aiPanelPosition(),
        density);
  }

  private static VBox section(String title, javafx.scene.Node control) {
    Label heading = new Label(title);
    heading.getStyleClass().add("search-section-heading");
    heading.setPadding(Insets.EMPTY);
    VBox box = new VBox(UiTokens.SPACE_SM, heading, control);
    box.getStyleClass().add("settings-section");
    return box;
  }

  private static <T> HBox choice(
      T[] values,
      T selected,
      java.util.function.Function<T, String> label,
      Consumer<T> onChange) {
    ToggleGroup group = new ToggleGroup();
    HBox row = new HBox(UiTokens.SPACE_LG);
    for (T value : values) {
      RadioButton radio = new RadioButton(label.apply(value));
      radio.setToggleGroup(group);
      radio.setSelected(value.equals(selected));
      radio.setAccessibleText(label.apply(value));
      radio.setOnAction(event -> onChange.accept(value));
      row.getChildren().add(radio);
    }
    return row;
  }
}
