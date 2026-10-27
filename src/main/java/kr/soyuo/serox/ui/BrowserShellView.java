package kr.soyuo.serox.ui;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import kr.soyuo.serox.browser.dom.Document;
import kr.soyuo.serox.browser.render.CanvasPageView;
import kr.soyuo.serox.core.application.BrowserController;
import kr.soyuo.serox.core.application.DirectAddress;
import kr.soyuo.serox.core.application.NavigationInputClassifier;
import kr.soyuo.serox.core.application.NavigationTarget;
import kr.soyuo.serox.core.application.SearchQuery;
import kr.soyuo.serox.core.event.ActiveTabChanged;
import kr.soyuo.serox.core.event.BrowserEventBus;
import kr.soyuo.serox.core.event.ConnectionStatusChanged;
import kr.soyuo.serox.core.event.EventSubscription;
import kr.soyuo.serox.core.event.OperationFailed;
import kr.soyuo.serox.core.event.PageLoaded;
import kr.soyuo.serox.core.event.SearchCancelled;
import kr.soyuo.serox.core.event.SearchCompleted;
import kr.soyuo.serox.core.event.SearchFailed;
import kr.soyuo.serox.core.event.SearchStarted;
import kr.soyuo.serox.core.event.ServiceId;
import kr.soyuo.serox.core.event.ServiceStatus;
import kr.soyuo.serox.core.event.TabClosed;
import kr.soyuo.serox.core.event.TabStateChanged;
import kr.soyuo.serox.core.model.NavigationStatus;
import kr.soyuo.serox.core.model.TabId;
import kr.soyuo.serox.core.model.TabSnapshot;
import kr.soyuo.serox.search.application.SearchService;

/** Displays the Figma browser frame, home view, and service status. */
public final class BrowserShellView extends BorderPane {
  private final UiPreviewData previewData = UiPreviewData.sample();
  private final Label aiStatus = new Label("AI: " + previewData.ai().detail());
  private final Label mcpStatus =
      new Label(previewData.mcp().statusText() + " (" + previewData.mcp().detail() + ")");
  private final Region statusDot = new Region();
  private final Label navigationStatus = new Label("Ready");
  private TextField addressField;
  private HomeView homeView;
  private final EventSubscription connectionStatusSubscription;
  private final EventSubscription operationErrorSubscription;
  private final EventSubscription tabStateSubscription;
  private final EventSubscription activeTabSubscription;
  private final EventSubscription tabClosedSubscription;
  private final EventSubscription pageLoadedSubscription;
  private final EventSubscription searchStartedSubscription;
  private final EventSubscription searchCompletedSubscription;
  private final EventSubscription searchFailedSubscription;
  private final EventSubscription searchCancelledSubscription;
  private final Map<TabId, TabSnapshot> tabSnapshots = new HashMap<>();
  private final Map<TabId, PageLoaded> loadedPages = new HashMap<>();
  private final Map<TabId, HBox> tabButtons = new HashMap<>();
  private final Map<TabId, Label> tabTitles = new HashMap<>();
  private final HBox tabStrip = new HBox(UiTokens.SPACE_XS);
  private final BrowserController browserController;
  private final SearchService searchService;
  private TabId activeTabId;
  private Button backButton;
  private Button forwardButton;
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
  public BrowserShellView(BrowserController browserController, SearchService searchService) {
    this.browserController = Objects.requireNonNull(browserController, "browserController");
    this.searchService = Objects.requireNonNull(searchService, "searchService");
    BrowserEventBus eventBus = browserController.eventBus();
    Objects.requireNonNull(eventBus, "eventBus");
    getStyleClass().add("app-root");
    installWindowResizeHandlers();
    setTop(createBrowserChrome());
    homeView = new HomeView(previewData, this::showNotConnectedMessage);
    setCenter(homeView);
    setBottom(createStatusBar());
    installKeyboardShortcuts();

    var stylesheet = BrowserShellView.class.getResource("serox.css");
    if (stylesheet != null) {
      getStylesheets().add(stylesheet.toExternalForm());
    }
    connectionStatusSubscription =
        eventBus.subscribe(ConnectionStatusChanged.class, this::showConnectionStatus);
    operationErrorSubscription =
        eventBus.subscribe(OperationFailed.class, this::showOperationError);
    tabStateSubscription = eventBus.subscribe(TabStateChanged.class, this::showTabState);
    activeTabSubscription = eventBus.subscribe(ActiveTabChanged.class, this::showActiveTab);
    tabClosedSubscription = eventBus.subscribe(TabClosed.class, this::removeTab);
    pageLoadedSubscription = eventBus.subscribe(PageLoaded.class, this::showPage);
    searchStartedSubscription = eventBus.subscribe(SearchStarted.class, this::showSearchStarted);
    searchCompletedSubscription =
        eventBus.subscribe(SearchCompleted.class, this::showSearchCompleted);
    searchFailedSubscription = eventBus.subscribe(SearchFailed.class, this::showSearchFailed);
    searchCancelledSubscription =
        eventBus.subscribe(SearchCancelled.class, this::showSearchCancelled);
  }

  /** Releases event subscriptions owned by this view. */
  public void close() {
    connectionStatusSubscription.close();
    operationErrorSubscription.close();
    tabStateSubscription.close();
    activeTabSubscription.close();
    tabClosedSubscription.close();
    pageLoadedSubscription.close();
    searchStartedSubscription.close();
    searchCompletedSubscription.close();
    searchFailedSubscription.close();
    searchCancelledSubscription.close();
  }

  private VBox createBrowserChrome() {
    VBox chrome = new VBox(createTitleBar(), createToolbar());
    chrome.getStyleClass().add("browser-chrome");
    return chrome;
  }

  private HBox createTitleBar() {
    tabStrip.setAlignment(Pos.CENTER_LEFT);
    ScrollPane tabs = new ScrollPane(tabStrip);
    tabs.setFitToHeight(true);
    tabs.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    tabs.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    tabs.getStyleClass().add("tab-strip-scroll");
    Button newTab = new Button("+");
    newTab.setAccessibleText("새 탭 열기");
    newTab.getStyleClass().add("new-tab-button");
    newTab.setOnAction(event -> browserController.createTab());
    HBox left = new HBox(UiTokens.SPACE_MD, SvgIconView.logo(16), tabs, newTab);
    left.setAlignment(Pos.CENTER_LEFT);
    left.getStyleClass().add("titlebar-left");
    HBox.setHgrow(left, Priority.ALWAYS);
    HBox.setHgrow(tabs, Priority.ALWAYS);

    Button minimize = windowButton(UiTokens.ICON_WINDOW_MINIMIZE, "창 최소화");
    minimize.setOnAction(event -> stage().setIconified(true));
    Button maximize = windowButton(UiTokens.ICON_WINDOW_MAXIMIZE, "창 최대화");
    maximize.setOnAction(event -> stage().setMaximized(!stage().isMaximized()));
    Button close = windowButton(UiTokens.ICON_WINDOW_CLOSE, "창 닫기");
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
    button.setGraphic(SvgIconView.icon(asset, UiTokens.WINDOW_ICON_SIZE));
    button.setAccessibleText(accessibleName);
    button.getStyleClass().addAll("window-control-button");
    return button;
  }

  private HBox createToolbar() {
    backButton = toolbarButton(UiTokens.ICON_BACK, "뒤로");
    forwardButton = toolbarButton(UiTokens.ICON_FORWARD, "앞으로");
    backButton.setDisable(true);
    forwardButton.setDisable(true);
    backButton.setOnAction(event -> runForActiveTab(browserController::goBack));
    forwardButton.setOnAction(event -> runForActiveTab(browserController::goForward));

    addressField = new TextField();
    addressField.setPromptText("검색어 또는 주소를 입력하세요…");
    addressField.setAccessibleText("검색어 또는 웹 주소, Ctrl+L로 이동");
    addressField.setOnAction(event -> navigateToAddress());
    addressField.addEventFilter(
        KeyEvent.KEY_PRESSED,
        event -> {
          if (event.getCode() == KeyCode.ESCAPE
              && activeTabId != null
              && searchService.cancel(activeTabId)) {
            event.consume();
          }
        });
    addressField.getStyleClass().add("address-field");
    HBox.setHgrow(addressField, Priority.ALWAYS);
    HBox addressBox =
        new HBox(
            UiTokens.SPACE_SM, SvgIconView.icon(UiTokens.ICON_ADDRESS_SEARCH, 18), addressField);
    addressBox.setAlignment(Pos.CENTER_LEFT);
    addressBox.getStyleClass().add("address-box");
    HBox.setHgrow(addressBox, Priority.ALWAYS);

    Button history = toolbarButton(UiTokens.ICON_HISTORY, "방문 기록");
    Button settings = toolbarButton(UiTokens.ICON_SETTINGS, "설정");
    history.setDisable(true);
    settings.setDisable(true);
    HBox row =
        new HBox(UiTokens.SPACE_XS, backButton, forwardButton, addressBox, history, settings);
    row.setAlignment(Pos.CENTER_LEFT);
    row.getStyleClass().add("toolbar");
    return row;
  }

  private Button toolbarButton(String asset, String accessibleName) {
    Button button = new Button();
    button.setGraphic(SvgIconView.icon(asset, UiTokens.TOOLBAR_ICON_SIZE));
    button.setAccessibleText(accessibleName);
    button.getStyleClass().add("toolbar-button");
    return button;
  }

  private HBox createStatusBar() {
    statusDot
        .getStyleClass()
        .setAll(
            "status-dot",
            previewData.mcp().enabled() ? "status-dot-connected" : "status-dot-disconnected");
    mcpStatus.setGraphic(statusDot);
    aiStatus.setAccessibleText("AI 연결 상태: " + previewData.ai().detail());
    mcpStatus.setAccessibleText(
        previewData.mcp().name() + " 상태: " + previewData.mcp().statusText());
    Label divider = new Label("|");
    divider.getStyleClass().add("status-divider");
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    HBox bar = new HBox(UiTokens.SPACE_MD, navigationStatus, divider, aiStatus, spacer, mcpStatus);
    bar.setAlignment(Pos.CENTER_LEFT);
    bar.getStyleClass().add("status-bar");
    return bar;
  }

  private void showConnectionStatus(ConnectionStatusChanged event) {
    String status =
        event.safeMessage().isBlank() ? statusName(event.status()) : event.safeMessage();
    Label target = event.service() == ServiceId.MCP ? mcpStatus : aiStatus;
    String prefix = event.service() == ServiceId.MCP ? "MCP 서버 " : "AI: ";
    Runnable update =
        () -> {
          target.setText(prefix + status);
          target.setAccessibleText(
              event.service() == ServiceId.MCP ? "MCP 서버 상태: " + status : "AI 연결 상태: " + status);
          if (event.service() == ServiceId.MCP) {
            statusDot.getStyleClass().setAll("status-dot", statusStyle(event.status()));
          }
        };
    FxThreadExecutor.execute(update);
  }

  private void showOperationError(OperationFailed event) {
    updateLabel(navigationStatus, event.error().safeMessage());
  }

  private void showSearchStarted(SearchStarted event) {
    FxThreadExecutor.execute(
        () -> {
          if (event.tabId().equals(activeTabId)) {
            addressField.setText(event.query());
            navigationStatus.setText("Searching…");
          }
        });
  }

  private void showSearchCompleted(SearchCompleted event) {
    FxThreadExecutor.execute(
        () -> {
          if (event.tabId().equals(activeTabId)) {
            addressField.setText(event.query());
            navigationStatus.setText(event.response().results().size() + " search results");
          }
        });
  }

  private void showSearchFailed(SearchFailed event) {
    FxThreadExecutor.execute(
        () -> {
          if (event.tabId().equals(activeTabId)) {
            addressField.setText(event.query());
            navigationStatus.setText(event.safeMessage());
          }
        });
  }

  private void showSearchCancelled(SearchCancelled event) {
    FxThreadExecutor.execute(
        () -> {
          if (event.tabId().equals(activeTabId)) {
            navigationStatus.setText("Search cancelled");
          }
        });
  }

  private void showTabState(TabStateChanged event) {
    FxThreadExecutor.execute(
        () -> {
          TabSnapshot snapshot = event.snapshot();
          tabSnapshots.put(snapshot.id(), snapshot);
          updateTab(snapshot);
          if (snapshot.id().equals(activeTabId)) {
            navigationStatus.setText(statusText(snapshot.navigationStatus()));
            updateHistoryButtons(snapshot);
            updateAddress(snapshot);
          }
        });
  }

  private void showActiveTab(ActiveTabChanged event) {
    FxThreadExecutor.execute(
        () -> {
          activeTabId = event.tabId().orElse(null);
          updateActiveTabStyle();
          TabSnapshot snapshot = tabSnapshots.get(activeTabId);
          if (snapshot == null) {
            addressField.clear();
            navigationStatus.setText("Ready");
            updateHistoryButtons(null);
          } else {
            updateAddress(snapshot);
            navigationStatus.setText(statusText(snapshot.navigationStatus()));
            updateHistoryButtons(snapshot);
          }
          PageLoaded page = loadedPages.get(activeTabId);
          if (page == null) {
            showHomeView();
          } else {
            showPage(page);
          }
        });
  }

  private void removeTab(TabClosed event) {
    searchService.cancel(event.tabId());
    FxThreadExecutor.execute(
        () -> {
          tabSnapshots.remove(event.tabId());
          loadedPages.remove(event.tabId());
          HBox button = tabButtons.remove(event.tabId());
          tabTitles.remove(event.tabId());
          if (button != null) {
            tabStrip.getChildren().remove(button);
          }
        });
  }

  private void updateTab(TabSnapshot snapshot) {
    Label title = tabTitles.get(snapshot.id());
    if (title == null) {
      title = new Label();
      title.setMaxWidth(190);
      Button close = new Button();
      close.setGraphic(SvgIconView.icon(UiTokens.ICON_TAB_CLOSE, UiTokens.WINDOW_ICON_SIZE));
      close.setAccessibleText("탭 닫기");
      close.getStyleClass().add("tab-close");
      close.addEventFilter(MouseEvent.MOUSE_CLICKED, MouseEvent::consume);
      close.setOnAction(
          event -> {
            event.consume();
            browserController.closeTab(snapshot.id());
          });
      HBox tab = new HBox(UiTokens.SPACE_SM, title, close);
      tab.setAlignment(Pos.CENTER_LEFT);
      tab.setOnMouseClicked(event -> browserController.activateTab(snapshot.id()));
      tabButtons.put(snapshot.id(), tab);
      tabTitles.put(snapshot.id(), title);
      tabStrip.getChildren().add(tab);
    }
    title.setText(snapshot.title().isBlank() ? "새 탭" : snapshot.title());
    HBox tab = tabButtons.get(snapshot.id());
    tab.setAccessibleText(title.getText());
    updateActiveTabStyle();
  }

  private void updateActiveTabStyle() {
    tabButtons.forEach(
        (tabId, button) ->
            button
                .getStyleClass()
                .setAll(tabId.equals(activeTabId) ? "active-tab" : "inactive-tab"));
  }

  private void updateAddress(TabSnapshot snapshot) {
    addressField.setText(snapshot.currentUri().map(Object::toString).orElse(""));
  }

  private void navigateToAddress() {
    if (activeTabId != null && !addressField.getText().isBlank()) {
      String input = addressField.getText().strip();
      try {
        NavigationTarget target = new NavigationInputClassifier().classify(input);
        if (target instanceof SearchQuery query) {
          searchService.search(activeTabId, query.value());
        } else {
          searchService.cancel(activeTabId);
          browserController.navigate(activeTabId, ((DirectAddress) target).uri().toString());
        }
      } catch (RuntimeException exception) {
        browserController.navigate(activeTabId, input);
      }
    }
  }

  private void showPage(PageLoaded event) {
    FxThreadExecutor.execute(
        () -> {
          loadedPages.put(event.tabId(), event);
          if (event.tabId().equals(activeTabId)) {
            event.page().document().ifPresent(this::showDocument);
          }
        });
  }

  private void showDocument(Document document) {
    PageLoaded page = loadedPages.get(activeTabId);
    if (page != null) {
      setCenter(
          new CanvasPageView(
              document,
              page.page().uri(),
              uri -> browserController.navigate(page.tabId(), uri.toString())));
    }
  }

  private void showHomeView() {
    homeView = new HomeView(previewData, this::showNotConnectedMessage);
    setCenter(homeView);
  }

  private void updateHistoryButtons(TabSnapshot snapshot) {
    backButton.setDisable(snapshot == null || !snapshot.canGoBack());
    forwardButton.setDisable(snapshot == null || !snapshot.canGoForward());
  }

  private void runForActiveTab(
      java.util.function.Function<TabId, java.util.concurrent.CompletionStage<TabSnapshot>>
          action) {
    if (activeTabId != null) {
      action.apply(activeTabId);
    }
  }

  private void updateLabel(Label label, String text) {
    Runnable update = () -> label.setText(text);
    FxThreadExecutor.execute(update);
  }

  private String statusText(NavigationStatus status) {
    return switch (status) {
      case IDLE -> "Ready";
      case LOADING -> "Loading…";
      case SUCCEEDED -> "Done";
      case FAILED -> "Page failed to load";
      case CANCELLED -> "Navigation cancelled";
    };
  }

  private String statusName(ServiceStatus status) {
    return switch (status) {
      case DISCONNECTED -> "꺼짐";
      case CONFIGURING -> "확인 중";
      case CONNECTED -> "켜짐";
      case FAILED -> "연결 실패";
    };
  }

  private String statusStyle(ServiceStatus status) {
    return switch (status) {
      case DISCONNECTED -> "status-dot-disconnected";
      case CONFIGURING -> "status-dot-configuring";
      case CONNECTED -> "status-dot-connected";
      case FAILED -> "status-dot-failed";
    };
  }

  private void installKeyboardShortcuts() {
    sceneProperty()
        .addListener(
            (observable, oldScene, newScene) -> {
              if (newScene != null) {
                newScene
                    .getAccelerators()
                    .put(
                        new KeyCodeCombination(KeyCode.L, KeyCombination.CONTROL_DOWN),
                        () -> {
                          addressField.requestFocus();
                          addressField.selectAll();
                        });
                newScene
                    .getAccelerators()
                    .put(
                        new KeyCodeCombination(KeyCode.T, KeyCombination.CONTROL_DOWN),
                        () -> browserController.createTab());
                newScene
                    .getAccelerators()
                    .put(
                        new KeyCodeCombination(KeyCode.W, KeyCombination.CONTROL_DOWN),
                        () -> {
                          if (activeTabId != null) {
                            browserController.closeTab(activeTabId);
                          }
                        });
                newScene
                    .getAccelerators()
                    .put(
                        new KeyCodeCombination(KeyCode.K, KeyCombination.CONTROL_DOWN),
                        homeView::focusSearchField);
              }
            });
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
