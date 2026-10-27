package kr.soyuo.serox.ui;

import java.util.Objects;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import kr.soyuo.serox.core.event.BrowserEventBus;
import kr.soyuo.serox.core.event.ConnectionStatusChanged;
import kr.soyuo.serox.core.event.EventSubscription;
import kr.soyuo.serox.core.event.OperationFailed;
import kr.soyuo.serox.core.event.ServiceId;
import kr.soyuo.serox.core.event.ServiceStatus;

/** Displays the Figma browser frame, home view, and service status. */
public final class BrowserShellView extends BorderPane {
  private final Label aiStatus = new Label("AI: Claude");
  private final Label mcpStatus = new Label("MCP 서버 켜짐 (search_and_summarize)");
  private final EventSubscription connectionStatusSubscription;
  private final EventSubscription operationErrorSubscription;
  private double dragOffsetX;
  private double dragOffsetY;
  private Cursor resizeCursor = Cursor.DEFAULT;
  private double resizeStartX;
  private double resizeStartY;
  private double resizeStartWidth;
  private double resizeStartHeight;
  private double resizeStartScreenX;
  private double resizeStartScreenY;

  /** Creates the browser shell and subscribes to service status events. */
  public BrowserShellView(BrowserEventBus eventBus) {
    Objects.requireNonNull(eventBus, "eventBus");
    getStyleClass().add("app-root");
    installWindowResizeHandlers();
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

  private VBox createBrowserChrome() {
    VBox chrome = new VBox(createTitleBar(), createToolbar());
    chrome.getStyleClass().add("browser-chrome");
    return chrome;
  }

  private HBox createTitleBar() {
    HBox activeTab = new HBox(10, new Label("새 탭"));
    activeTab.setAlignment(Pos.CENTER_LEFT);
    activeTab.getStyleClass().add("active-tab");
    Button closeTab = new Button();
    closeTab.setGraphic(SvgIconView.icon("65d4c.svg", 12));
    closeTab.setAccessibleText("현재 탭 닫기");
    closeTab.setDisable(true);
    closeTab.getStyleClass().add("tab-close");
    activeTab.getChildren().add(closeTab);

    HBox left = new HBox(12, SvgIconView.logo(16), activeTab);
    left.setAlignment(Pos.CENTER_LEFT);
    left.getStyleClass().add("titlebar-left");
    HBox.setHgrow(left, Priority.ALWAYS);

    Button minimize = windowButton("89d49.svg", "창 최소화");
    minimize.setOnAction(event -> stage().setIconified(true));
    Button maximize = windowButton("27a5b.svg", "창 최대화");
    maximize.setOnAction(event -> stage().setMaximized(!stage().isMaximized()));
    Button close = windowButton("71df2.svg", "창 닫기");
    close.getStyleClass().add("window-close-button");
    close.setOnAction(event -> stage().close());
    HBox controls = new HBox(minimize, maximize, close);
    controls.getStyleClass().add("window-controls");

    HBox bar = new HBox(left, controls);
    bar.setAlignment(Pos.CENTER_LEFT);
    bar.getStyleClass().add("titlebar");
    bar.setOnMousePressed(
        event -> {
          if (event.getClickCount() == 2) {
            stage().setMaximized(!stage().isMaximized());
          } else {
            dragOffsetX = event.getScreenX() - stage().getX();
            dragOffsetY = event.getScreenY() - stage().getY();
          }
        });
    bar.setOnMouseDragged(
        event -> {
          if (!stage().isMaximized() && event.getTarget() == bar) {
            stage().setX(event.getScreenX() - dragOffsetX);
            stage().setY(event.getScreenY() - dragOffsetY);
          }
        });
    return bar;
  }

  private Button windowButton(String asset, String accessibleName) {
    Button button = new Button();
    button.setGraphic(SvgIconView.icon(asset, 12));
    button.setAccessibleText(accessibleName);
    button.getStyleClass().addAll("window-control-button");
    return button;
  }

  private HBox createToolbar() {
    Button back = toolbarButton("b2c0a.svg", "뒤로");
    Button forward = toolbarButton("5404c.svg", "앞으로");
    back.setDisable(true);
    forward.setDisable(true);

    TextField address = new TextField();
    address.setPromptText("검색어 또는 주소를 입력하세요…");
    address.setAccessibleText("검색어 또는 웹 주소");
    address.getStyleClass().add("address-field");
    HBox.setHgrow(address, Priority.ALWAYS);
    HBox addressBox = new HBox(10, SvgIconView.icon("8b6e3.svg", 18), address);
    addressBox.setAlignment(Pos.CENTER_LEFT);
    addressBox.getStyleClass().add("address-box");
    HBox.setHgrow(addressBox, Priority.ALWAYS);

    Button history = toolbarButton("bf879.svg", "방문 기록");
    Button settings = toolbarButton("43cc8.svg", "설정");
    history.setDisable(true);
    settings.setDisable(true);
    HBox row = new HBox(4, back, forward, addressBox, history, settings);
    row.setAlignment(Pos.CENTER_LEFT);
    row.getStyleClass().add("toolbar");
    return row;
  }

  private Button toolbarButton(String asset, String accessibleName) {
    Button button = new Button();
    button.setGraphic(SvgIconView.icon(asset, 20));
    button.setAccessibleText(accessibleName);
    button.getStyleClass().add("toolbar-button");
    return button;
  }

  private HBox createStatusBar() {
    Region statusDot = new Region();
    statusDot.getStyleClass().add("status-dot");
    mcpStatus.setGraphic(statusDot);
    Label divider = new Label("|");
    divider.getStyleClass().add("status-divider");
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    HBox bar = new HBox(12, aiStatus, divider, spacer, mcpStatus);
    bar.setAlignment(Pos.CENTER_LEFT);
    bar.getStyleClass().add("status-bar");
    return bar;
  }

  private void showConnectionStatus(ConnectionStatusChanged event) {
    String status =
        event.safeMessage().isBlank() ? statusName(event.status()) : event.safeMessage();
    Label target = event.service() == ServiceId.MCP ? mcpStatus : aiStatus;
    String prefix = event.service() == ServiceId.MCP ? "MCP 서버 " : "AI: ";
    updateLabel(target, prefix + status);
  }

  private void showOperationError(OperationFailed event) {
    updateLabel(aiStatus, event.error().safeMessage());
  }

  private void updateLabel(Label label, String text) {
    Runnable update = () -> label.setText(text);
    if (Platform.isFxApplicationThread()) {
      update.run();
    } else {
      Platform.runLater(update);
    }
  }

  private String statusName(ServiceStatus status) {
    return switch (status) {
      case DISCONNECTED -> "꺼짐";
      case CONFIGURING -> "확인 중";
      case CONNECTED -> "켜짐";
      case FAILED -> "연결 실패";
    };
  }

  private void showNotConnectedMessage(String query) {
    if (query != null && !query.isBlank()) {
      updateLabel(aiStatus, "검색 엔진 연결 예정 · " + query.trim());
    }
  }

  private void installWindowResizeHandlers() {
    addEventFilter(MouseEvent.MOUSE_MOVED, this::updateResizeCursor);
    addEventFilter(
        MouseEvent.MOUSE_PRESSED,
        event -> {
          resizeCursor = cursorAt(event.getSceneX(), event.getSceneY());
          if (resizeCursor == Cursor.DEFAULT || stage().isMaximized()) {
            resizeCursor = Cursor.DEFAULT;
            return;
          }
          Stage window = stage();
          resizeStartX = window.getX();
          resizeStartY = window.getY();
          resizeStartWidth = window.getWidth();
          resizeStartHeight = window.getHeight();
          resizeStartScreenX = event.getScreenX();
          resizeStartScreenY = event.getScreenY();
          event.consume();
        });
    addEventFilter(
        MouseEvent.MOUSE_DRAGGED,
        event -> {
          if (resizeCursor != Cursor.DEFAULT) {
            resizeWindow(event.getScreenX(), event.getScreenY());
            event.consume();
          }
        });
    addEventFilter(MouseEvent.MOUSE_RELEASED, event -> resizeCursor = Cursor.DEFAULT);
    addEventFilter(
        MouseEvent.MOUSE_EXITED,
        event -> {
          if (resizeCursor == Cursor.DEFAULT) {
            setCursor(Cursor.DEFAULT);
          }
        });
  }

  private void updateResizeCursor(MouseEvent event) {
    if (stage().isMaximized()) {
      setCursor(Cursor.DEFAULT);
      return;
    }
    Cursor next = cursorAt(event.getSceneX(), event.getSceneY());
    setCursor(next);
  }

  private Cursor cursorAt(double x, double y) {
    double edge = 7;
    boolean left = x <= edge;
    boolean right = x >= getWidth() - edge;
    boolean top = y <= edge;
    boolean bottom = y >= getHeight() - edge;
    if (top && left || bottom && right) {
      return Cursor.NW_RESIZE;
    }
    if (top && right) {
      return Cursor.NE_RESIZE;
    }
    if (bottom && left) {
      return Cursor.SW_RESIZE;
    }
    if (bottom && right) {
      return Cursor.SE_RESIZE;
    }
    if (left) {
      return Cursor.W_RESIZE;
    }
    if (right) {
      return Cursor.E_RESIZE;
    }
    if (top) {
      return Cursor.N_RESIZE;
    }
    if (bottom) {
      return Cursor.S_RESIZE;
    }
    return Cursor.DEFAULT;
  }

  private void resizeWindow(double screenX, double screenY) {
    Stage window = stage();
    double deltaX = screenX - resizeStartScreenX;
    double deltaY = screenY - resizeStartScreenY;
    double minWidth = window.getMinWidth();
    double minHeight = window.getMinHeight();
    boolean fromLeft =
        resizeCursor == Cursor.NW_RESIZE
            || resizeCursor == Cursor.SW_RESIZE
            || resizeCursor == Cursor.W_RESIZE;
    boolean fromRight =
        resizeCursor == Cursor.NE_RESIZE
            || resizeCursor == Cursor.SE_RESIZE
            || resizeCursor == Cursor.E_RESIZE;
    boolean fromTop =
        resizeCursor == Cursor.NW_RESIZE
            || resizeCursor == Cursor.NE_RESIZE
            || resizeCursor == Cursor.N_RESIZE;
    boolean fromBottom =
        resizeCursor == Cursor.SW_RESIZE
            || resizeCursor == Cursor.SE_RESIZE
            || resizeCursor == Cursor.S_RESIZE;

    if (fromLeft) {
      double width = Math.max(minWidth, resizeStartWidth - deltaX);
      window.setX(resizeStartX + resizeStartWidth - width);
      window.setWidth(width);
    } else if (fromRight) {
      window.setWidth(Math.max(minWidth, resizeStartWidth + deltaX));
    }
    if (fromTop) {
      double height = Math.max(minHeight, resizeStartHeight - deltaY);
      window.setY(resizeStartY + resizeStartHeight - height);
      window.setHeight(height);
    } else if (fromBottom) {
      window.setHeight(Math.max(minHeight, resizeStartHeight + deltaY));
    }
  }

  private Stage stage() {
    return (Stage) getScene().getWindow();
  }
}
