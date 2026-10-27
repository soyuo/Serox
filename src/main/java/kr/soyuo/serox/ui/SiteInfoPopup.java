package kr.soyuo.serox.ui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import kr.soyuo.serox.core.application.SitePermissionService;
import kr.soyuo.serox.core.model.PermissionChoice;
import kr.soyuo.serox.core.model.SiteInfo;
import kr.soyuo.serox.core.model.SitePermission;

/**
 * The popup behind the address bar's lock: what kind of connection the page uses, and, as in the
 * design, a "site settings" list where each permission can be allowed or blocked for that site.
 */
final class SiteInfoPopup {
  private final SiteInfo info;
  private final SitePermissionService permissions;
  private final Runnable onChanged;
  private final boolean dark;
  private final VBox root = new VBox();
  private final ContextMenu popup;

  /** {@code onChanged} runs after a choice is saved, so the page can be loaded again. */
  SiteInfoPopup(
      SiteInfo info, SitePermissionService permissions, Runnable onChanged, boolean dark) {
    this.info = info;
    this.permissions = permissions;
    this.onChanged = onChanged;
    this.dark = dark;
    root.getStyleClass().addAll("app-root", "site-popup");
    if (dark) {
      root.getStyleClass().add("theme-dark");
    }
    var stylesheet = SiteInfoPopup.class.getResource("serox.css");
    if (stylesheet != null) {
      root.getStylesheets().add(stylesheet.toExternalForm());
    }
    root.setMinWidth(300);
    root.setMaxWidth(340);
    CustomMenuItem item = new CustomMenuItem(root, false);
    popup = new ContextMenu(item);
    showMain();
  }

  void show(Node anchor) {
    popup.show(anchor, javafx.geometry.Side.BOTTOM, 0, 4);
  }

  boolean isDark() {
    return dark;
  }

  // ----- views -----

  private void showMain() {
    boolean secure = info.level() == SiteInfo.Level.SECURE;
    Label title =
        new Label(
            switch (info.level()) {
              case SECURE -> "안전하게 연결됐어요";
              case INSECURE -> "안전하지 않은 연결이에요";
              case NONE -> info.host().isBlank() ? "Serox" : info.host();
            });
    title.getStyleClass().add(secure ? "site-popup-title-ok" : "site-popup-title");
    Label summary = new Label(info.summary());
    summary.setWrapText(true);
    summary.getStyleClass().add("search-state-message");
    VBox header = new VBox(UiTokens.SPACE_XS, title, summary);
    header.getStyleClass().add("site-popup-header");
    root.getChildren().setAll(header);
    if (info.host().isBlank()) {
      return;
    }
    root.getChildren()
        .addAll(
            valueRow("주소", info.host(), null),
            valueRow("인증서", secure ? "유효함" : "없음", secure ? "site-popup-ok" : null),
            valueRow("쿠키와 사이트 데이터", "저장하지 않아요", null),
            linkRow("사이트 설정", "", this::showPermissions));
  }

  private void showPermissions() {
    root.getChildren().setAll(header("사이트 설정", info.host() + "에서 허용할 기능이에요.", this::showMain));
    for (SitePermission permission : SitePermission.values()) {
      PermissionChoice choice = permissions.choice(info.host(), permission);
      linkRowInto(
          permission.label(),
          choice == PermissionChoice.ALLOW ? "허용" : "차단",
          () -> showDetail(permission));
    }
  }

  private void showDetail(SitePermission permission) {
    root.getChildren()
        .setAll(
            header(
                permission.label(),
                info.host() + "에서 이 권한을 어떻게 할까요?",
                this::showPermissions));
    PermissionChoice current = permissions.choice(info.host(), permission);
    ToggleGroup group = new ToggleGroup();
    RadioButton allow = choiceRow(group, "허용", permission.allowHint(), current == PermissionChoice.ALLOW);
    RadioButton block = choiceRow(group, "차단", permission.blockHint(), current == PermissionChoice.BLOCK);
    if (permission.changeable()) {
      allow.setOnAction(event -> choose(permission, PermissionChoice.ALLOW));
      block.setOnAction(event -> choose(permission, PermissionChoice.BLOCK));
    } else {
      allow.setDisable(true);
      block.setDisable(true);
      Label note = new Label("Serox가 이 기능을 지원하지 않아서 항상 차단돼요.");
      note.setWrapText(true);
      note.getStyleClass().add("search-state-message");
      note.getStyleClass().add("site-popup-note");
      root.getChildren().add(note);
    }
  }

  private void choose(SitePermission permission, PermissionChoice choice) {
    permissions.set(info.host(), permission, choice);
    onChanged.run();
    showPermissions();
  }

  // ----- pieces -----

  private Node header(String title, String subtitle, Runnable back) {
    Button backButton = new Button("‹");
    backButton.getStyleClass().add("site-popup-back");
    backButton.setAccessibleText("뒤로");
    backButton.setOnAction(event -> back.run());
    Label name = new Label(title);
    name.getStyleClass().add("site-popup-title");
    Label hint = new Label(subtitle);
    hint.setWrapText(true);
    hint.getStyleClass().add("search-state-message");
    VBox text = new VBox(UiTokens.SPACE_XS, name, hint);
    HBox.setHgrow(text, Priority.ALWAYS);
    HBox box = new HBox(UiTokens.SPACE_SM, backButton, text);
    box.setAlignment(Pos.CENTER_LEFT);
    box.getStyleClass().add("site-popup-header");
    return box;
  }

  private Node valueRow(String name, String value, String valueClass) {
    Label left = new Label(name);
    Label right = new Label(value);
    if (valueClass != null) {
      right.getStyleClass().add(valueClass);
    }
    right.getStyleClass().add("search-state-message");
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    HBox row = new HBox(UiTokens.SPACE_SM, left, spacer, right);
    row.setAlignment(Pos.CENTER_LEFT);
    row.getStyleClass().add("site-popup-row");
    return row;
  }

  private Node linkRow(String name, String value, Runnable open) {
    Label left = new Label(name);
    Label right = new Label(value.isEmpty() ? "›" : value + " ›");
    right.getStyleClass().add("search-state-message");
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    HBox row = new HBox(UiTokens.SPACE_SM, left, spacer, right);
    row.setAlignment(Pos.CENTER_LEFT);
    row.getStyleClass().addAll("site-popup-row", "site-popup-link");
    row.setOnMouseClicked(event -> open.run());
    row.setAccessibleText(name);
    return row;
  }

  private void linkRowInto(String name, String value, Runnable open) {
    root.getChildren().add(linkRow(name, value, open));
  }

  private RadioButton choiceRow(ToggleGroup group, String label, String hint, boolean selected) {
    RadioButton radio = new RadioButton(label);
    radio.setToggleGroup(group);
    radio.setSelected(selected);
    radio.getStyleClass().add("site-popup-radio");
    Label detail = new Label(hint);
    detail.setWrapText(true);
    detail.getStyleClass().add("search-state-message");
    VBox box = new VBox(UiTokens.SPACE_XS, radio, detail);
    box.getStyleClass().add("site-popup-choice");
    if (selected) {
      box.getStyleClass().add("site-popup-choice-selected");
    }
    root.getChildren().add(box);
    return radio;
  }
}
