package kr.soyuo.serox.ui;

import java.util.Optional;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import kr.soyuo.serox.core.model.BrowserSettings;
import kr.soyuo.serox.core.model.KeyChord;
import kr.soyuo.serox.core.model.ShortcutAction;

/**
 * The "단축키" page: each action shows its keys and a button to change them. While a row waits for
 * a new key it takes the next key press; a key another action uses asks before taking it.
 */
final class ShortcutsPage extends ScrollPane {
  private final PrivacyActions actions;
  private final VBox rows = new VBox();
  private final Label message = new Label();
  private ShortcutAction waiting;
  private KeyChord conflictingChord;
  private ShortcutAction conflictingAction;

  ShortcutsPage(PrivacyActions actions) {
    this.actions = actions;
    getStyleClass().add("settings-scroll");
    setFitToWidth(true);
    setAccessibleText("단축키 설정");
    setFocusTraversable(true);
    addEventFilter(KeyEvent.KEY_PRESSED, this::onKey);
    VBox content = new VBox(UiTokens.SPACE_LG);
    content.getStyleClass().add("settings-info");
    content.setPadding(new Insets(32, 48, 32, 48));
    content.setMaxWidth(UiTokens.HOME_CONTENT_WIDTH);
    Label title = new Label("단축키");
    title.getStyleClass().add("settings-page-title");
    Label hint = new Label("자주 쓰는 동작의 키를 바꿀 수 있어요.");
    hint.setWrapText(true);
    hint.getStyleClass().add("search-state-message");
    VBox heading = new VBox(UiTokens.SPACE_XS, title, hint);
    Button reset = new Button("기본값으로 되돌리기");
    reset.getStyleClass().add("secondary-button");
    reset.setDisable(!actions.available());
    reset.setOnAction(event -> resetToDefaults());
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    HBox header = new HBox(UiTokens.SPACE_MD, heading, spacer, reset);
    header.setAlignment(Pos.TOP_LEFT);
    rows.getStyleClass().add("shortcut-list");
    message.setWrapText(true);
    message.getStyleClass().add("search-state-message");
    content.getChildren().addAll(header, rows, message);
    setContent(content);
    render();
  }

  private void render() {
    rows.getChildren().clear();
    for (ShortcutAction action : ShortcutAction.values()) {
      rows.getChildren().add(row(action));
    }
    if (waiting != null) {
      requestFocus();
    }
  }

  private HBox row(ShortcutAction action) {
    Label name = new Label(action.label());
    name.getStyleClass().add("privacy-query");
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    HBox row = new HBox(UiTokens.SPACE_MD, name, spacer);
    row.setAlignment(Pos.CENTER_RIGHT);
    row.getStyleClass().add("shortcut-row");
    row.setAccessibleText(action.label());
    if (action != waiting) {
      row.getChildren().addAll(keys(actions.settings().shortcut(action)), changeButton(action));
    } else if (conflictingChord == null) {
      Label prompt = new Label("새 단축키를 눌러 주세요…");
      prompt.getStyleClass().add("shortcut-prompt");
      row.getStyleClass().add("shortcut-row-waiting");
      row.getChildren().addAll(prompt, cancelButton());
    } else {
      Label warning =
          new Label(
              KeyChords.describe(conflictingChord)
                  + " 은 ‘"
                  + conflictingAction.label()
                  + "’와 겹쳐요.");
      warning.getStyleClass().add("shortcut-warning");
      Button replace = new Button("그래도 바꾸기");
      replace.getStyleClass().add("danger-button");
      replace.setOnAction(event -> replaceAnyway());
      row.getStyleClass().add("shortcut-row-conflict");
      row.getChildren().addAll(warning, replace, cancelButton());
    }
    return row;
  }

  private HBox keys(KeyChord chord) {
    HBox keys = new HBox(UiTokens.SPACE_XS);
    keys.setAlignment(Pos.CENTER_RIGHT);
    if (!chord.assigned()) {
      Label none = new Label("지정 안 됨");
      none.getStyleClass().add("search-state-message");
      keys.getChildren().add(none);
      return keys;
    }
    for (String part : chord.parts()) {
      Label cap = new Label(KeyChords.cap(part));
      cap.getStyleClass().add("keycap");
      keys.getChildren().add(cap);
    }
    keys.setAccessibleText(KeyChords.describe(chord));
    return keys;
  }

  private Button changeButton(ShortcutAction action) {
    Button change = new Button("변경");
    change.getStyleClass().add("secondary-button");
    change.setAccessibleText(action.label() + " 단축키 변경");
    change.setDisable(!actions.available());
    change.setOnAction(event -> startWaiting(action));
    return change;
  }

  private Button cancelButton() {
    Button cancel = new Button("취소");
    cancel.getStyleClass().add("secondary-button");
    cancel.setOnAction(event -> stopWaiting(""));
    return cancel;
  }

  private void startWaiting(ShortcutAction action) {
    waiting = action;
    conflictingChord = null;
    conflictingAction = null;
    message.setText("");
    render();
  }

  private void stopWaiting(String text) {
    waiting = null;
    conflictingChord = null;
    conflictingAction = null;
    message.setText(text);
    render();
  }

  /** While a row waits, the next key press is the new shortcut and goes nowhere else. */
  private void onKey(KeyEvent event) {
    if (waiting == null || conflictingChord != null) {
      return;
    }
    event.consume();
    if (event.getCode() == KeyCode.ESCAPE) {
      stopWaiting("");
      return;
    }
    if (KeyChords.isModifier(event.getCode())) {
      return;
    }
    Optional<KeyChord> pressed = KeyChords.fromEvent(event);
    if (pressed.isEmpty()) {
      message.setText("이 키는 쓸 수 없어요. Ctrl이나 Alt와 함께 눌러 주세요.");
      return;
    }
    KeyChord chord = pressed.orElseThrow();
    BrowserSettings settings = actions.settings();
    Optional<ShortcutAction> other = settings.actionUsing(chord, waiting);
    if (other.isPresent()) {
      conflictingChord = chord;
      conflictingAction = other.orElseThrow();
      message.setText("");
      render();
      return;
    }
    actions.save(settings.withShortcut(waiting, chord));
    stopWaiting("단축키를 바꿨어요.");
  }

  private void replaceAnyway() {
    actions.save(actions.settings().withShortcutTaken(waiting, conflictingChord));
    stopWaiting("단축키를 바꿨어요. ‘" + conflictingAction.label() + "’는 지정 안 됨이 돼요.");
  }

  private void resetToDefaults() {
    actions.save(actions.settings().withDefaultShortcuts());
    stopWaiting("모든 단축키를 기본값으로 되돌렸어요.");
  }
}
