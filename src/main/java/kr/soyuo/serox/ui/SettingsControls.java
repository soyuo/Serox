package kr.soyuo.serox.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Shared Figma settings primitives with wrapping labels and a compact stacked row layout. */
final class SettingsControls {
  private SettingsControls() {}

  static VBox content(String title, String hint) {
    VBox content = new VBox(UiTokens.SPACE_LG);
    content.setPadding(new Insets(32, 48, 32, 48));
    content.setMinWidth(0);
    content.setMaxWidth(UiTokens.SETTINGS_CONTENT_WIDTH);
    content.getStyleClass().add("settings-info");
    Label heading = label(title, "settings-page-title");
    content.getChildren().addAll(heading, label(hint, "search-state-message"));
    return content;
  }

  static void configure(ScrollPane scroll, VBox content) {
    scroll.getStyleClass().add("settings-scroll");
    scroll.setFitToWidth(true);
    scroll.setContent(content);
  }

  static Label label(String text, String style) {
    Label label = new Label(text);
    label.setWrapText(true);
    label.setMinWidth(0);
    label.getStyleClass().add(style);
    return label;
  }

  static VBox card(Node... nodes) {
    VBox card = new VBox(nodes);
    card.getStyleClass().add("connected-settings-card");
    return card;
  }

  static VBox row(String title, String hint, Node control) {
    VBox name = new VBox(4, label(title, "settings-row-title"));
    if (!hint.isEmpty()) {
      name.getChildren().add(label(hint, "search-state-message"));
    }
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    HBox line = new HBox(UiTokens.SPACE_LG, name, spacer, control);
    line.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(name, Priority.SOMETIMES);
    VBox row = new VBox(UiTokens.SPACE_SM, line);
    row.getStyleClass().add("connected-settings-row");
    row.widthProperty()
        .addListener(
            (observable, oldValue, newValue) -> {
              if (newValue.doubleValue() < 580 && line.getChildren().contains(control)) {
                line.getChildren().remove(control);
                row.getChildren().add(control);
              } else if (newValue.doubleValue() >= 580 && row.getChildren().contains(control)) {
                row.getChildren().remove(control);
                line.getChildren().add(control);
              }
            });
    return row;
  }
}
