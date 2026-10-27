package kr.soyuo.serox.ui;

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

/** Displays the browser chrome, home view, and application status. */
public final class BrowserShellView extends BorderPane {
  private final Label statusLabel = new Label("Serox · 준비됨");

  /** Creates the initial browser shell with disconnected controls. */
  public BrowserShellView() {
    getStyleClass().add("app-root");
    setTop(createBrowserChrome());
    setCenter(new HomeView(this::showNotConnectedMessage));
    setBottom(createStatusBar());

    var stylesheet = BrowserShellView.class.getResource("serox.css");
    if (stylesheet != null) {
      getStylesheets().add(stylesheet.toExternalForm());
    }
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
