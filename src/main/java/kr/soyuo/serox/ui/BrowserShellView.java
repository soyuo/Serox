package kr.soyuo.serox.ui;

import java.util.Objects;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import kr.soyuo.serox.core.event.BrowserEventBus;
import kr.soyuo.serox.core.event.ConnectionStatusChanged;
import kr.soyuo.serox.core.event.EventSubscription;
import kr.soyuo.serox.core.event.OperationFailed;
import kr.soyuo.serox.core.event.ServiceId;
import kr.soyuo.serox.core.event.ServiceStatus;

/** Displays the browser chrome, home view, and application status. */
public final class BrowserShellView extends BorderPane {
  private final Label statusLabel = new Label("Serox · 준비됨");
  private final EventSubscription connectionStatusSubscription;
  private final EventSubscription operationErrorSubscription;

  /** Creates the initial browser shell and subscribes to owned service status events. */
  public BrowserShellView(BrowserEventBus eventBus) {
    Objects.requireNonNull(eventBus, "eventBus");
    getStyleClass().add("app-root");
    setTop(createBrowserChrome());
    setCenter(new HomeView(this::showNotConnectedMessage));
    setBottom(createStatusBar());

    var stylesheet = BrowserShellView.class.getResource("serox.css");
    if (stylesheet != null) {
      getStylesheets().add(stylesheet.toExternalForm());
    }
    connectionStatusSubscription =
        eventBus.subscribe(ConnectionStatusChanged.class, this::showConnectionStatus);
    operationErrorSubscription =
        eventBus.subscribe(OperationFailed.class, this::showOperationError);
  }

  /** Releases event subscriptions owned by this view. */
  public void close() {
    connectionStatusSubscription.close();
    operationErrorSubscription.close();
  }

  private void showConnectionStatus(ConnectionStatusChanged event) {
    String detail =
        event.safeMessage().isBlank()
            ? serviceName(event.service()) + " · " + statusName(event.status())
            : event.safeMessage();
    updateStatusLabel(detail);
  }

  private void showOperationError(OperationFailed event) {
    updateStatusLabel(event.error().safeMessage());
  }

  private void updateStatusLabel(String text) {
    Runnable update = () -> statusLabel.setText(text);
    if (Platform.isFxApplicationThread()) {
      update.run();
    } else {
      Platform.runLater(update);
    }
  }

  private String serviceName(ServiceId service) {
    return switch (service) {
      case BROWSER_ENGINE -> "브라우저";
      case SEARCH -> "검색";
      case AI -> "AI";
      case MCP -> "MCP";
      case STORAGE -> "저장소";
    };
  }

  private String statusName(ServiceStatus status) {
    return switch (status) {
      case DISCONNECTED -> "연결 안 됨";
      case CONFIGURING -> "확인 중";
      case CONNECTED -> "연결됨";
      case FAILED -> "연결 실패";
    };
  }

  private VBox createBrowserChrome() {
    VBox chrome = new VBox(createTabBar(), createToolbar());
    chrome.getStyleClass().add("browser-chrome");
    return chrome;
  }

  private HBox createTabBar() {
    Label brandMark = new Label("S");
    brandMark.getStyleClass().add("brand-mark-small");

    Label activeTab = new Label("새 탭");
    activeTab.getStyleClass().add("active-tab");

    Button closeTab = new Button("×");
    closeTab.getStyleClass().add("tab-close");
    closeTab.setAccessibleText("현재 탭 닫기");
    closeTab.setDisable(true);

    HBox tab = new HBox(10, activeTab, closeTab);
    tab.setAlignment(Pos.CENTER_LEFT);
    tab.getStyleClass().add("tab-chip");

    Button addTab = new Button("+");
    addTab.getStyleClass().add("quiet-button");
    addTab.setAccessibleText("새 탭");
    addTab.setDisable(true);
    addTab.setTooltip(new Tooltip("탭 기능은 탐색 코어 단계에서 연결됩니다."));

    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);

    HBox row = new HBox(12, brandMark, tab, addTab, spacer);
    row.setAlignment(Pos.CENTER_LEFT);
    row.setPadding(new Insets(6, 14, 0, 14));
    row.getStyleClass().add("tab-bar");
    return row;
  }

  private HBox createToolbar() {
    Button back = toolbarButton("‹", "뒤로");
    Button forward = toolbarButton("›", "앞으로");
    Button reload = toolbarButton("↻", "새로고침");
    back.setDisable(true);
    forward.setDisable(true);
    reload.setDisable(true);

    TextField addressBar = new TextField();
    addressBar.setPromptText("검색어 또는 주소를 입력하세요");
    addressBar.setAccessibleText("검색어 또는 웹 주소");
    addressBar.getStyleClass().add("address-bar");
    HBox.setHgrow(addressBar, Priority.ALWAYS);

    Button history = toolbarButton("◷", "방문 기록");
    Button settings = toolbarButton("⚙", "설정");
    history.setDisable(true);
    settings.setDisable(true);

    HBox row = new HBox(8, back, forward, reload, addressBar, history, settings);
    row.setAlignment(Pos.CENTER);
    row.setPadding(new Insets(10, 16, 12, 16));
    row.getStyleClass().add("toolbar");
    return row;
  }

  private Button toolbarButton(String text, String accessibleText) {
    Button button = new Button(text);
    button.getStyleClass().add("icon-button");
    button.setAccessibleText(accessibleText);
    button.setTooltip(new Tooltip(accessibleText + " 기능은 이후 단계에서 연결됩니다."));
    return button;
  }

  private HBox createStatusBar() {
    Label status = new Label("MVP UI · 탐색 엔진 미연결");
    status.getStyleClass().add("status-muted");

    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);

    HBox bar = new HBox(statusLabel, spacer, status);
    bar.setAlignment(Pos.CENTER_LEFT);
    bar.setPadding(new Insets(8, 16, 8, 16));
    bar.getStyleClass().add("status-bar");
    return bar;
  }

  private void showNotConnectedMessage(String query) {
    String value = query == null || query.isBlank() ? "" : " · " + query.trim();
    statusLabel.setText("검색 엔진 연결 예정" + value);
  }
}
