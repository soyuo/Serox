package kr.soyuo.serox.ui;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import kr.soyuo.serox.core.model.McpAccess;
import kr.soyuo.serox.core.model.McpSettings;
import kr.soyuo.serox.ui.McpConnectionGuide.App;
import kr.soyuo.serox.ui.McpConnectionGuide.Transport;

/**
 * The "MCP 서버" page: switch the HTTP server on and off, choose its port and who may reach it,
 * and copy the setup text an agent app needs. A server that cannot start says why next to the
 * port or under the switch, and stays off.
 */
final class McpPage extends ScrollPane {
  private final McpActions actions;
  private final Consumer<String> copier;
  private final Label chip = new Label();
  private final VBox calls = new VBox();
  private final ToggleButton serverSwitch = new ToggleButton();
  private final TextField portField = new TextField();
  private final Label portError = new Label();
  private final Label message = new Label();
  private final Label caption = new Label();
  private final Label where = new Label();
  private final Label code = new Label();
  private final Label requirement = new Label();
  private final Button copy = new Button("복사");
  private Transport transport = Transport.HTTP;
  private App app = App.CLAUDE_CODE;
  private boolean running;

  McpPage(McpActions actions) {
    this(actions, McpPage::copyToClipboard);
  }

  /** {@code copier} receives the text of the copy button. */
  McpPage(McpActions actions, Consumer<String> copier) {
    this.actions = actions;
    this.copier = copier;
    this.running = actions.running();
    getStyleClass().add("settings-scroll");
    setFitToWidth(true);
    setAccessibleText("MCP 서버 설정");
    VBox content = new VBox(UiTokens.SPACE_LG);
    content.getStyleClass().add("settings-info");
    content.setPadding(new Insets(32, 48, 32, 48));
    Label title = new Label("MCP 서버");
    title.getStyleClass().add("settings-page-title");
    Label hint = new Label("다른 AI 에이전트가 Serox의 검색 + 요약을 도구로 부를 수 있게 해요.");
    hint.setWrapText(true);
    hint.getStyleClass().add("search-state-message");
    VBox left = new VBox(UiTokens.SPACE_MD, serverCard(), toolSection());
    HBox.setHgrow(left, Priority.ALWAYS);
    left.setMaxWidth(UiTokens.HOME_CONTENT_WIDTH / 2);
    VBox right = guide();
    HBox.setHgrow(right, Priority.ALWAYS);
    HBox columns = new HBox(UiTokens.SPACE_LG * 2, left, right);
    content.getChildren().addAll(title, hint, columns);
    setContent(content);
    showStatus(null);
    showGuide();
  }

  private VBox serverCard() {
    serverSwitch.getStyleClass().add("switch");
    serverSwitch.setAccessibleText("MCP 서버 켜기");
    serverSwitch.setSelected(running);
    serverSwitch.setOnAction(event -> toggleServer());
    chip.getStyleClass().add("status-chip");
    Label name = new Label("MCP 서버 켜기");
    name.getStyleClass().add("privacy-query");
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    HBox power = new HBox(UiTokens.SPACE_MD, name, spacer, chip, serverSwitch);
    power.setAlignment(Pos.CENTER_LEFT);
    power.getStyleClass().add("privacy-row");

    portField.setText(Integer.toString(actions.settings().port()));
    portField.setPrefColumnCount(6);
    portField.setAccessibleText("포트");
    portField.setOnAction(event -> commitPort());
    portField.focusedProperty().addListener((o, was, focused) -> {
      if (!focused) {
        commitPort();
      }
    });
    portError.setWrapText(true);
    portError.getStyleClass().add("field-error-text");
    portError.setManaged(false);
    portError.setVisible(false);
    Label portName = new Label("포트");
    portName.getStyleClass().add("privacy-query");
    VBox portLabel = new VBox(2, portName, portError);
    HBox.setHgrow(portLabel, Priority.ALWAYS);
    HBox port = new HBox(UiTokens.SPACE_MD, portLabel, portField);
    port.setAlignment(Pos.CENTER_LEFT);
    port.getStyleClass().add("privacy-row");

    HBox access =
        row(
            "접근 허용",
            choice(
                new McpAccess[] {McpAccess.LOCAL_ONLY, McpAccess.NETWORK},
                actions.settings().access(),
                value -> value == McpAccess.LOCAL_ONLY ? "이 컴퓨터만" : "같은 네트워크",
                this::changeAccess));

    ToggleButton autoStart = new ToggleButton();
    autoStart.getStyleClass().add("switch");
    autoStart.setAccessibleText("Serox를 켤 때 자동 실행");
    autoStart.setSelected(actions.settings().autoStart());
    autoStart.setText(autoStart.isSelected() ? "켜짐" : "꺼짐");
    autoStart.setOnAction(
        event -> {
          autoStart.setText(autoStart.isSelected() ? "켜짐" : "꺼짐");
          actions.save(actions.settings().withAutoStart(autoStart.isSelected()));
        });
    HBox auto = row("Serox를 켤 때 자동 실행", autoStart);

    message.setWrapText(true);
    message.getStyleClass().add("field-error-text");
    message.setManaged(false);
    message.setVisible(false);
    VBox card = new VBox(UiTokens.SPACE_SM, power, port, access, auto, message);
    card.getStyleClass().add("settings-section");
    return card;
  }

  private VBox toolSection() {
    Label heading = new Label("에이전트가 받아 가는 도구");
    heading.getStyleClass().add("search-section-heading");
    heading.setPadding(Insets.EMPTY);
    Label name = new Label("search_and_summarize");
    name.getStyleClass().add("tool-name");
    Label detail = new Label("검색어를 받아 웹 검색 결과를 읽고 요약과 출처를 돌려줘요.");
    detail.setWrapText(true);
    detail.getStyleClass().add("search-state-message");
    Label input = new Label("query: string");
    input.getStyleClass().add("tool-name");
    VBox text = new VBox(UiTokens.SPACE_XS, name, detail, input);
    HBox.setHgrow(text, Priority.ALWAYS);
    ToggleButton toolSwitch = new ToggleButton();
    toolSwitch.getStyleClass().add("switch");
    toolSwitch.setAccessibleText("search_and_summarize 도구 켜기");
    toolSwitch.setSelected(actions.settings().searchToolEnabled());
    toolSwitch.setText(toolSwitch.isSelected() ? "켜짐" : "꺼짐");
    toolSwitch.setOnAction(
        event -> {
          toolSwitch.setText(toolSwitch.isSelected() ? "켜짐" : "꺼짐");
          actions.save(actions.settings().withSearchToolEnabled(toolSwitch.isSelected()));
        });
    HBox tool = new HBox(UiTokens.SPACE_MD, text, toolSwitch);
    tool.setAlignment(Pos.CENTER_LEFT);
    tool.getStyleClass().add("privacy-row");
    tool.setPadding(new Insets(12, 16, 12, 16));

    Label callsHeading = new Label("최근 호출");
    callsHeading.getStyleClass().add("search-section-heading");
    callsHeading.setPadding(Insets.EMPTY);
    calls.getStyleClass().add("settings-section");
    refreshCalls();
    // Calls arrive while the page is open, so the list follows them until the page leaves.
    Timeline refresher =
        new Timeline(new KeyFrame(Duration.seconds(3), event -> refreshCalls()));
    refresher.setCycleCount(Animation.INDEFINITE);
    sceneProperty()
        .addListener(
            (observable, before, after) -> {
              if (after == null) {
                refresher.stop();
              } else {
                refreshCalls();
                refresher.play();
              }
            });
    return new VBox(UiTokens.SPACE_SM, heading, tool, callsHeading, calls);
  }

  /** Rebuilds the recent-calls list from the log. */
  void refreshCalls() {
    List<McpActions.Call> recent = actions.recentCalls();
    calls.getChildren().clear();
    if (recent.isEmpty()) {
      Label none = new Label("아직 에이전트가 부른 적이 없어요.");
      none.getStyleClass().add("search-state-message");
      none.setPadding(new Insets(12, 16, 12, 16));
      calls.getChildren().add(none);
      return;
    }
    Instant now = Instant.now();
    for (McpActions.Call call : recent.subList(0, Math.min(5, recent.size()))) {
      Label query = new Label(call.summary().isBlank() ? "(검색어 없음)" : call.summary());
      query.getStyleClass().add("privacy-query");
      String who = call.client().isBlank() ? "에이전트" : call.client();
      Label when = new Label(who + ", " + ago(call.at(), now));
      when.getStyleClass().add("search-state-message");
      VBox text = new VBox(2, query, when);
      HBox.setHgrow(text, Priority.ALWAYS);
      Label result = new Label(call.success() ? "성공" : "실패");
      result
          .getStyleClass()
          .addAll("status-chip", call.success() ? "status-chip-ok" : "status-chip-error");
      HBox row = new HBox(UiTokens.SPACE_MD, text, result);
      row.setAlignment(Pos.CENTER_LEFT);
      row.getStyleClass().add("privacy-row");
      calls.getChildren().add(row);
    }
  }

  /** A short "how long ago" in the app's wording. */
  static String ago(Instant at, Instant now) {
    long seconds = Math.max(0, java.time.Duration.between(at, now).getSeconds());
    if (seconds < 60) {
      return "방금 전";
    }
    if (seconds < 3600) {
      return seconds / 60 + "분 전";
    }
    if (seconds < 86400) {
      return seconds / 3600 + "시간 전";
    }
    return seconds / 86400 + "일 전";
  }

  private VBox guide() {
    Label heading = new Label("에이전트에 연결하는 방법");
    heading.getStyleClass().add("search-section-heading");
    heading.setPadding(Insets.EMPTY);
    Label transportName = smallHeading("연결 방식");
    HBox transports =
        choice(Transport.values(), transport, Transport::label, value -> {
          transport = value;
          showGuide();
        });
    caption.setWrapText(true);
    caption.getStyleClass().add("search-state-message");
    Label appName = smallHeading("사용하는 앱");
    HBox apps =
        choice(App.values(), app, App::label, value -> {
          app = value;
          showGuide();
        });
    where.getStyleClass().add("search-state-message");
    where.setWrapText(true);
    where.setMinHeight(Region.USE_PREF_SIZE);
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    copy.getStyleClass().add("secondary-button");
    copy.setOnAction(event -> copySnippet());
    HBox whereRow = new HBox(UiTokens.SPACE_MD, where, spacer, copy);
    whereRow.setAlignment(Pos.CENTER_LEFT);
    code.getStyleClass().add("code-block");
    code.setMaxWidth(Double.MAX_VALUE);
    code.setWrapText(true);
    code.setMinHeight(Region.USE_PREF_SIZE);
    code.setTextOverrun(javafx.scene.control.OverrunStyle.CLIP);
    code.setAccessibleText("연결 설정");
    Label bold = new Label("연결 설정은 한 번 직접 넣어야 해요. ");
    bold.getStyleClass().add("info-strong");
    Label rest =
        new Label("에이전트가 Serox를 알아서 찾지는 않아요. 연결하고 나면 에이전트가 도구 이름·설명·입력값을 자동으로 읽어서, 필요할 때 스스로 호출해요.");
    rest.setWrapText(true);
    rest.getStyleClass().add("info-text");
    VBox info = new VBox(UiTokens.SPACE_XS, bold, rest);
    info.getStyleClass().add("info-box");
    requirement.setWrapText(true);
    requirement.getStyleClass().add("search-state-message");
    VBox box =
        new VBox(
            UiTokens.SPACE_SM,
            heading,
            transportName,
            transports,
            caption,
            appName,
            apps,
            whereRow,
            code,
            info,
            requirement);
    return box;
  }

  private static Label smallHeading(String text) {
    Label label = new Label(text);
    label.getStyleClass().add("search-state-message");
    return label;
  }

  private void toggleServer() {
    message.setVisible(false);
    message.setManaged(false);
    if (serverSwitch.isSelected()) {
      applyResult(actions.start(actions.settings()));
    } else {
      actions.stop();
      running = false;
      showStatus(null);
    }
  }

  private void applyResult(McpActions.Result result) {
    running = result.running();
    serverSwitch.setSelected(running);
    showStatus(running ? null : result);
  }

  /** The chip and the messages: running, off, or why it did not start. */
  private void showStatus(McpActions.Result failure) {
    chip.getStyleClass().removeAll("status-chip-ok", "status-chip-error");
    portField.getStyleClass().remove("field-error");
    portError.setVisible(false);
    portError.setManaged(false);
    message.setVisible(false);
    message.setManaged(false);
    serverSwitch.setText(running ? "켜짐" : "꺼짐");
    if (running) {
      chip.setText("실행 중");
      chip.getStyleClass().add("status-chip-ok");
    } else if (failure != null) {
      chip.setText("시작하지 못했어요");
      chip.getStyleClass().add("status-chip-error");
      if (failure.portInUse()) {
        portField.getStyleClass().add("field-error");
        portError.setText(failure.message() + " 다른 번호를 입력해 주세요.");
        portError.setVisible(true);
        portError.setManaged(true);
      } else {
        message.setText(failure.message());
        message.setVisible(true);
        message.setManaged(true);
      }
    } else {
      chip.setText("꺼짐");
    }
  }

  private void commitPort() {
    String text = portField.getText().strip();
    int number;
    try {
      number = Integer.parseInt(text);
    } catch (NumberFormatException notANumber) {
      number = -1;
    }
    if (!McpSettings.validPort(number)) {
      portField.getStyleClass().add("field-error");
      portError.setText(
          "포트는 " + McpSettings.MIN_PORT + "~" + McpSettings.MAX_PORT + " 사이의 숫자여야 해요.");
      portError.setVisible(true);
      portError.setManaged(true);
      return;
    }
    if (number == actions.settings().port()) {
      if (portError.isVisible() && !chip.getStyleClass().contains("status-chip-error")) {
        showStatus(null);
      }
      return;
    }
    actions.save(actions.settings().withPort(number));
    portField.setText(Integer.toString(number));
    if (running) {
      applyResult(actions.start(actions.settings()));
    } else {
      // A new port may well fix an earlier "port in use", so the old failure is dropped.
      showStatus(null);
    }
    showGuide();
  }

  private void changeAccess(McpAccess access) {
    actions.save(actions.settings().withAccess(access));
    restartIfRunning();
    showGuide();
  }

  private void restartIfRunning() {
    if (running) {
      applyResult(actions.start(actions.settings()));
    }
  }

  private void showGuide() {
    McpSettings settings = actions.settings();
    caption.setText(McpConnectionGuide.caption(transport, settings.port()));
    McpConnectionGuide.Snippet snippet =
        McpConnectionGuide.snippet(transport, app, settings.port());
    where.setText(snippet.where());
    code.setText(snippet.text());
    String needs =
        McpConnectionGuide.requirement(transport, settings.access() == McpAccess.NETWORK);
    requirement.setText(needs);
    requirement.setVisible(!needs.isEmpty());
    requirement.setManaged(!needs.isEmpty());
    copy.setText("복사");
  }

  private void copySnippet() {
    copier.accept(code.getText());
    copy.setText("복사했어요");
    PauseTransition revert = new PauseTransition(Duration.seconds(1.5));
    revert.setOnFinished(event -> copy.setText("복사"));
    revert.play();
  }

  private static void copyToClipboard(String text) {
    ClipboardContent content = new ClipboardContent();
    content.putString(text);
    Clipboard.getSystemClipboard().setContent(content);
  }

  private static HBox row(String label, Node control) {
    Label name = new Label(label);
    name.getStyleClass().add("privacy-query");
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    HBox row = new HBox(UiTokens.SPACE_MD, name, spacer, control);
    row.setAlignment(Pos.CENTER_LEFT);
    row.getStyleClass().add("privacy-row");
    return row;
  }

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
