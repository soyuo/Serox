package kr.soyuo.serox.ui;

import java.util.List;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ScrollPane;
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
      List.of("#2563EB", "#0B6E6E", "#6B3FC4", "#B4540A", "#151A23");
  private static final Integer[] FONT_SCALES = {90, 100, 115};

  private final Consumer<BrowserSettings> onPreview;
  private final Consumer<BrowserSettings> onApply;
  private final Runnable onCancel;
  private final Label message = new Label();
  private final TextField accentField = new TextField();
  private BrowserSettings draft;

  /**
   * Edits stay in a draft. {@code onPreview} shows the draft without saving it, {@code onApply}
   * saves it, and {@code onCancel} abandons it; the caller restores the saved appearance.
   */
  SettingsView(
      BrowserSettings saved,
      Consumer<BrowserSettings> onPreview,
      Consumer<BrowserSettings> onApply,
      Runnable onCancel) {
    this.onPreview = onPreview;
    this.onApply = onApply;
    this.onCancel = onCancel;
    this.draft = saved;
    getStyleClass().add("settings-scroll");
    setFitToWidth(true);
    setAccessibleText("설정");
    rebuild();
  }

  private void rebuild() {
    VBox content = new VBox(UiTokens.SPACE_LG);
    content.getStyleClass().add("settings-view");
    content.setPadding(new Insets(28, 40, 28, 40));
    content.setMaxWidth(UiTokens.HOME_CONTENT_WIDTH);
    Label title = new Label("설정");
    title.getStyleClass().add("search-query-heading");
    title.setPadding(Insets.EMPTY);
    Label hint = new Label("변경 내용은 미리 보기로 먼저 보여 주고, ‘적용’을 눌러야 저장돼요.");
    hint.setWrapText(true);
    hint.getStyleClass().add("search-state-message");
    message.getStyleClass().add("search-state-message");
    message.setText("");
    content.getChildren().addAll(title, hint, themeSection(), accentSection(), fontSection());
    content.getChildren().addAll(panelSection(), densitySection(), message, buttons());
    setContent(content);
  }


  private VBox themeSection() {
    return section(
        "테마",
        choice(
            new ThemeMode[] {ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM},
            draft.theme(),
            mode ->
                switch (mode) {
                  case SYSTEM -> "시스템";
                  case LIGHT -> "밝게";
                  case DARK -> "어둡게";
                },
            mode -> update(withTheme(mode))));
  }

  private VBox accentSection() {
    HBox swatches = new HBox(UiTokens.SPACE_MD);
    for (String color : ACCENT_PRESETS) {
      Button swatch = new Button();
      swatch.getStyleClass().add("accent-swatch");
      swatch.setStyle("-fx-background-color: " + color + "; -fx-border-color: " + color + ";");
      swatch.setAccessibleText("강조색 " + color);
      if (color.equalsIgnoreCase(draft.accentColor())) {
        swatch.getStyleClass().add("accent-swatch-selected");
        swatch.setGraphic(SvgIconView.icon(UiTokens.ICON_CHECK, UiTokens.CHECK_SIZE));
      }
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
    return section("강조 색", new HBox(UiTokens.SPACE_LG, swatches, accentField));
  }

  private VBox fontSection() {
    return section(
        "글자 크기",
        choice(
            FONT_SCALES,
            draft.fontScalePercent(),
            percent -> percent < 100 ? "작게" : percent == 100 ? "보통" : "크게",
            percent -> update(withFontScale(percent))));
  }

  private VBox panelSection() {
    return section(
        "AI 패널 위치",
        choice(
            new AiPanelPosition[] {AiPanelPosition.LEFT, AiPanelPosition.RIGHT},
            draft.aiPanelPosition(),
            position -> position == AiPanelPosition.LEFT ? "왼쪽" : "오른쪽",
            position -> update(withPanel(position))));
  }

  private VBox densitySection() {
    return section(
        "화면 밀도",
        choice(
            new DisplayDensity[] {DisplayDensity.COMFORTABLE, DisplayDensity.COMPACT},
            draft.displayDensity(),
            density -> density == DisplayDensity.COMPACT ? "촘촘하게" : "여유롭게",
            density -> update(withDensity(density))));
  }

  private HBox buttons() {
    Button apply = new Button("적용");
    apply.getStyleClass().add("primary-button");
    apply.setOnAction(
        event -> {
          onApply.accept(draft);
          message.setText("설정을 저장했어요.");
        });
    Button cancel = new Button("취소");
    cancel.getStyleClass().add("secondary-button");
    cancel.setOnAction(event -> onCancel.run());
    Button restore = new Button("기본값 복원");
    restore.getStyleClass().add("secondary-button");
    restore.setOnAction(
        event -> {
          draft = BrowserSettings.defaults();
          rebuild();
          onPreview.accept(draft);
          message.setText("기본값을 미리 보기로 보여 주고 있어요. ‘적용’을 눌러 저장하세요.");
        });
    Region spacer = new Region();
    HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
    return new HBox(UiTokens.SPACE_SM, restore, spacer, cancel, apply);
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
    onPreview.accept(next);
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

  /** A row of segmented options as in the design: one is selected and tinted. */
  private static <T> HBox choice(
      T[] values,
      T selected,
      java.util.function.Function<T, String> label,
      Consumer<T> onChange) {
    ToggleGroup group = new ToggleGroup();
    HBox row = new HBox(UiTokens.SPACE_SM);
    for (T value : values) {
      ToggleButton option = new ToggleButton(label.apply(value));
      option.getStyleClass().add("segment-option");
      option.setToggleGroup(group);
      option.setSelected(value.equals(selected));
      option.setAccessibleText(label.apply(value));
      option.setOnAction(
          event -> {
            if (option.isSelected()) {
              onChange.accept(value);
            } else {
              option.setSelected(true);
            }
          });
      row.getChildren().add(option);
    }
    return row;
  }
}
