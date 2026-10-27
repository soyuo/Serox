package kr.soyuo.serox.ui;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
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
import kr.soyuo.serox.ai.application.AiSearchService;
import kr.soyuo.serox.browser.dom.Document;
import kr.soyuo.serox.browser.render.CanvasPageView;
import kr.soyuo.serox.core.application.BrowserController;
import kr.soyuo.serox.core.application.DirectAddress;
import kr.soyuo.serox.core.application.LibraryService;
import kr.soyuo.serox.core.application.NavigationInputClassifier;
import kr.soyuo.serox.core.application.NavigationTarget;
import kr.soyuo.serox.core.application.SearchQuery;
import kr.soyuo.serox.core.application.SettingsService;
import kr.soyuo.serox.core.event.ActiveTabChanged;
import kr.soyuo.serox.core.event.AiAnswerChanged;
import kr.soyuo.serox.core.event.BrowserEventBus;
import kr.soyuo.serox.core.event.ConnectionStatusChanged;
import kr.soyuo.serox.core.event.EventSubscription;
import kr.soyuo.serox.core.event.NavigationFailed;
import kr.soyuo.serox.core.event.OperationFailed;
import kr.soyuo.serox.core.event.PageLoaded;
import kr.soyuo.serox.core.event.SearchCancelled;
import kr.soyuo.serox.core.event.SearchCompleted;
import kr.soyuo.serox.core.event.SearchFailed;
import kr.soyuo.serox.core.event.SearchStarted;
import kr.soyuo.serox.core.event.ServiceStatus;
import kr.soyuo.serox.core.event.SettingsChanged;
import kr.soyuo.serox.core.event.TabClosed;
import kr.soyuo.serox.core.event.TabStateChanged;
import kr.soyuo.serox.core.model.AiPanelPosition;
import kr.soyuo.serox.core.model.BrowserSettings;
import kr.soyuo.serox.core.model.KeyChord;
import kr.soyuo.serox.core.model.NavigationStatus;
import kr.soyuo.serox.core.model.ShortcutAction;
import kr.soyuo.serox.core.model.SiteInfo;
import kr.soyuo.serox.core.model.TabId;
import kr.soyuo.serox.core.model.TabSnapshot;
import kr.soyuo.serox.search.application.SearchService;

/** Displays the Figma browser frame, home view, and service status. */
public final class BrowserShellView extends BorderPane {
  private UiPreviewData previewData = UiPreviewData.pending();
  private final Label aiStatus = new Label("AI: 확인 중");
  private final Label searchStatus = new Label("검색: 확인 중");
  private final Label mcpStatus = new Label("MCP 서버 확인 중");
  private final Region statusDot = new Region();
  private final Label navigationStatus = new Label("Ready");
  private TextField addressField;
  private HBox addressBox;
  private boolean aiSearchActive;
  private boolean errorShown;
  private final Button aiChip = new Button("AI 요약");
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
  private final EventSubscription aiAnswerSubscription;
  private final EventSubscription navigationFailedSubscription;
  private SearchResultsView resultsView;
  private javafx.scene.Scene acceleratorScene;
  private final List<KeyCombination> installedAccelerators = new java.util.ArrayList<>();
  private boolean aiPanelOpen = true;
  private AiPanelPosition aiPanelSide = AiPanelPosition.RIGHT;
  private AiSearchService aiSearchService;
  private kr.soyuo.serox.ai.application.AiConnectionService aiConnections;
  private java.util.function.Consumer<java.net.URI> openBrowser;
  private SearchActions searchActions = SearchActions.none();
  private BrowserSettings shownAppearance = BrowserSettings.defaults();
  private final javafx.beans.value.ChangeListener<javafx.application.ColorScheme>
      systemThemeListener =
          (observable, before, after) ->
              FxThreadExecutor.execute(() -> AppearanceStyler.apply(this, shownAppearance));
  private PrivacyActions privacyActions = PrivacyActions.none();
  private McpActions mcpActions = McpActions.none();
  private SettingsService settingsService;
  private kr.soyuo.serox.core.application.SitePermissionService sitePermissions;
  private LibraryService libraryService;
  private final Button bookmarkButton = new Button("★");
  private EventSubscription settingsSubscription;
  private javafx.scene.Node viewBeforeSettings;
  private final Button siteInfoButton = new Button();
  private SiteInfo siteInfo = SiteInfo.of(Optional.empty());
  private final Map<TabId, TabSnapshot> tabSnapshots = new HashMap<>();
  private final Map<TabId, PageLoaded> loadedPages = new HashMap<>();
  private final Map<TabId, String> schemelessInput = new HashMap<>();
  private final Map<TabId, HBox> tabButtons = new HashMap<>();
  private final Map<TabId, Label> tabTitles = new HashMap<>();
  private final RecentSearchHistory recentSearches = new RecentSearchHistory();
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
    this(browserController, searchService, List.of());
  }

  /** Creates the browser shell seeded with stored recent queries, newest first. */
  public BrowserShellView(
      BrowserController browserController,
      SearchService searchService,
      List<String> storedRecentQueries) {
    this.browserController = Objects.requireNonNull(browserController, "browserController");
    this.searchService = Objects.requireNonNull(searchService, "searchService");
    BrowserEventBus eventBus = browserController.eventBus();
    Objects.requireNonNull(eventBus, "eventBus");
    storedRecentQueries.reversed().forEach(recentSearches::add);
    getStyleClass().add("app-root");
    installWindowResizeHandlers();
    setTop(createBrowserChrome());
    homeView = new HomeView(previewData, recentSearches.queries(), this::searchFromHome);
    setCenter(homeView);
    centerProperty().addListener((observable, before, after) -> refreshAiRing());
    setBottom(createStatusBar());
    installKeyboardShortcuts();
    centerProperty()
        .addListener(
            (observable, oldView, newView) -> {
              if (oldView instanceof SettingsView && settingsService != null) {
                applySettings(settingsService.current());
              }
            });

    var stylesheet = BrowserShellView.class.getResource("serox.css");
    if (stylesheet != null) {
      getStylesheets().add(stylesheet.toExternalForm());
    }
    javafx.application.Platform.getPreferences()
        .colorSchemeProperty()
        .addListener(systemThemeListener);
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
    aiAnswerSubscription = eventBus.subscribe(AiAnswerChanged.class, this::showAiAnswer);
    navigationFailedSubscription =
        eventBus.subscribe(NavigationFailed.class, this::showNavigationFailed);
  }

  /** Supplies the AI service used to cancel an in-progress answer. */
  /** Connects the MCP settings page to the HTTP server and its saved settings. */
  public void setMcpActions(McpActions actions) {
    this.mcpActions = Objects.requireNonNull(actions, "actions");
  }

  /** Connects the privacy page to the stored data it can delete. */
  public void setPrivacyActions(
      java.util.Optional<kr.soyuo.serox.core.application.UsageRecorder> recorder,
      kr.soyuo.serox.core.application.LibraryService library) {
    privacyActions =
        new PrivacyActions() {
          @Override
          public boolean available() {
            return recorder.isPresent() && library.available();
          }

          @Override
          public java.util.concurrent.CompletionStage<java.util.List<String>> recentQueries() {
            return recorder
                .map(r -> r.recentQueries(UiTokens.PRIVACY_LIST_LIMIT))
                .orElseGet(
                    () ->
                        java.util.concurrent.CompletableFuture.completedFuture(
                            java.util.List.of()));
          }

          @Override
          public java.util.concurrent.CompletionStage<Void> deleteQuery(String query) {
            return recorder
                .map(r -> r.deleteQuery(query))
                .orElseGet(() -> java.util.concurrent.CompletableFuture.completedFuture(null));
          }

          @Override
          public java.util.concurrent.CompletionStage<Void> clearQueries() {
            return recorder
                .map(kr.soyuo.serox.core.application.UsageRecorder::clearQueries)
                .orElseGet(() -> java.util.concurrent.CompletableFuture.completedFuture(null));
          }

          @Override
          public java.util.concurrent.CompletionStage<Void> clearVisits() {
            return library.clearHistory();
          }

          @Override
          public kr.soyuo.serox.core.model.BrowserSettings settings() {
            return settingsService == null
                ? kr.soyuo.serox.core.model.BrowserSettings.defaults()
                : settingsService.current();
          }

          @Override
          public java.util.concurrent.CompletionStage<Void> save(
              kr.soyuo.serox.core.model.BrowserSettings settings) {
            return settingsService == null
                ? java.util.concurrent.CompletableFuture.completedFuture(null)
                : settingsService.save(settings);
          }
        };
  }

  public void setAiSearchService(AiSearchService aiSearchService) {
    this.aiSearchService = aiSearchService;
  }

  public void setSearchActions(SearchActions actions) {
    searchActions = java.util.Objects.requireNonNull(actions, "actions");
  }

  /**
   * Uses the operating system browser for provider authorization, outside Serox's page renderer.
   */
  public void setAiConnections(
      kr.soyuo.serox.ai.application.AiConnectionService connections,
      java.util.function.Consumer<java.net.URI> openBrowser) {
    this.aiConnections = connections;
    this.openBrowser = openBrowser;
  }

  /** Connects history and bookmark storage to the toolbar entry points. */
  public void setLibraryService(LibraryService libraryService) {
    this.libraryService = libraryService;
  }

  private void showLibrary(LibraryView.Section section) {
    if (libraryService == null || activeTabId == null) {
      return;
    }
    TabId tab = activeTabId;
    if (!(getCenter() instanceof LibraryView) && !(getCenter() instanceof SettingsView)) {
      viewBeforeSettings = getCenter();
    }
    setCenter(
        new LibraryView(
            libraryService,
            section,
            uri -> {
              closeSettings();
              browserController.navigate(tab, uri.toString());
            },
            this::closeSettings));
  }

  private void bookmarkCurrentPage() {
    PageLoaded page = loadedPages.get(activeTabId);
    if (libraryService == null || page == null) {
      navigationStatus.setText("북마크할 페이지가 없어요.");
      return;
    }
    libraryService
        .addBookmark(page.page().uri(), page.page().title())
        .thenAccept(
            added ->
                FxThreadExecutor.execute(
                    () ->
                        navigationStatus.setText(added ? "북마크에 추가했어요." : "이미 북마크했거나 저장할 수 없어요.")));
  }

  /** Connects the settings store, applying the current values and following later changes. */
  public void setSettingsService(SettingsService settingsService) {
    this.settingsService = settingsService;
    applySettings(settingsService.current());
    settingsSubscription =
        browserController
            .eventBus()
            .subscribe(
                SettingsChanged.class,
                event -> FxThreadExecutor.execute(() -> applySettings(event.settings())));
  }

  private void applySettings(BrowserSettings settings) {
    shownAppearance = settings;
    AppearanceStyler.apply(this, settings);
    aiPanelSide = settings.aiPanelPosition();
    refreshAccelerators();
  }

  private void showSettings() {
    if (settingsService == null) {
      return;
    }
    if (!(getCenter() instanceof SettingsView) && !(getCenter() instanceof LibraryView)) {
      viewBeforeSettings = getCenter();
    }
    setCenter(
        new SettingsView(
            settingsService.current(),
            preview -> {
              shownAppearance = preview;
              AppearanceStyler.apply(this, preview);
            },
            settings ->
                settingsService.save(
                    settings
                        .withPrivacyOf(settingsService.current())
                        .withGeneralOf(settingsService.current())
                        .withShortcutsOf(settingsService.current())
                        .withContentPreferencesOf(settingsService.current())),
            this::cancelSettings,
            new SettingsView.ServiceSummary(
                statusText(searchStatus), statusText(aiStatus), statusText(mcpStatus)),
            privacyActions,
            mcpActions,
            aiConnections,
            openBrowser,
            searchActions));
  }

  /** The status label's text without its "검색:" or "AI:" prefix. */
  private static String statusText(Label label) {
    String text = label.getText() == null ? "" : label.getText();
    int colon = text.indexOf(": ");
    return colon >= 0 ? text.substring(colon + 2) : text;
  }

  private void cancelSettings() {
    applySettings(settingsService.current());
    closeSettings();
  }

  private void closeSettings() {
    if (getCenter() instanceof SettingsView || getCenter() instanceof LibraryView) {
      setCenter(viewBeforeSettings != null ? viewBeforeSettings : homeView);
    }
  }

  /** Releases event subscriptions owned by this view. */
  public void close() {
    javafx.application.Platform.getPreferences()
        .colorSchemeProperty()
        .removeListener(systemThemeListener);
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
    aiAnswerSubscription.close();
    navigationFailedSubscription.close();
    if (settingsSubscription != null) {
      settingsSubscription.close();
    }
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
    updateSiteInfo(Optional.empty());
    siteInfoButton.setOnAction(event -> showSiteInfo());
    bookmarkButton.getStyleClass().addAll("toolbar-button", "bookmark-button");
    bookmarkButton.setAccessibleText("현재 페이지를 북마크에 추가");
    bookmarkButton.setOnAction(event -> bookmarkCurrentPage());
    HBox.setHgrow(addressField, Priority.ALWAYS);
    addressBox =
        new HBox(
            UiTokens.SPACE_SM,
            SvgIconView.icon(UiTokens.ICON_ADDRESS_SEARCH, 18),
            siteInfoButton,
            addressField,
            bookmarkButton,
            aiChip);
    addressBox.setAlignment(Pos.CENTER_LEFT);
    addressBox.getStyleClass().add("address-box");
    aiChip.getStyleClass().add("ai-chip");
    aiChip.setGraphic(
        SvgIconView.icon(UiTokens.ICON_AI_SPARKLE_SMALL, UiTokens.AI_SPARKLE_SMALL_SIZE));
    aiChip.setAccessibleText("AI 요약 패널 열기");
    aiChip.setVisible(false);
    aiChip.managedProperty().bind(aiChip.visibleProperty());
    aiChip.setOnAction(
        event -> {
          if (resultsView != null) {
            resultsView.openAiPanel();
          }
        });
    HBox.setHgrow(addressBox, Priority.ALWAYS);

    Button history = toolbarButton(UiTokens.ICON_HISTORY, "방문 기록");
    history.setOnAction(event -> showLibrary(LibraryView.Section.HISTORY));
    Button settings = toolbarButton(UiTokens.ICON_SETTINGS, "설정");

    settings.setOnAction(event -> showSettings());
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
    for (Label status : List.of(navigationStatus, searchStatus, aiStatus, mcpStatus)) {
      status.setMaxWidth(UiTokens.STATUS_TEXT_MAX_WIDTH);
      status.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
      javafx.scene.control.Tooltip tip = new javafx.scene.control.Tooltip();
      tip.textProperty().bind(status.textProperty());
      status.setTooltip(tip);
    }
    Label divider = new Label("|");
    divider.getStyleClass().add("status-divider");
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    HBox bar =
        new HBox(
            UiTokens.SPACE_MD,
            navigationStatus,
            divider,
            searchStatus,
            aiStatus,
            spacer,
            mcpStatus);
    bar.setAlignment(Pos.CENTER_LEFT);
    bar.getStyleClass().add("status-bar");
    return bar;
  }

  private void showConnectionStatus(ConnectionStatusChanged event) {
    String status =
        event.safeMessage().isBlank() ? statusName(event.status()) : event.safeMessage();
    FxThreadExecutor.execute(
        () -> {
          boolean on = event.status() == ServiceStatus.CONNECTED;
          switch (event.service()) {
            case SEARCH -> {
              searchStatus.setText("검색: " + status);
              searchStatus.setAccessibleText("검색 연결 상태: " + status);
            }
            case AI -> {
              aiStatus.setText("AI: " + status);
              aiStatus.setAccessibleText("AI 연결 상태: " + status);
              previewData =
                  previewData.withAi(new UiPreviewData.ServicePreview("AI 요약", status, on));
            }
            case MCP -> {
              mcpStatus.setText("MCP 서버 " + status);
              mcpStatus.setAccessibleText("MCP 서버 상태: " + status);
              statusDot.getStyleClass().setAll("status-dot", statusStyle(event.status()));
              previewData =
                  previewData.withMcp(new UiPreviewData.ServicePreview("MCP 서버", status, on));
            }
            default -> {}
          }
          if (getCenter() == homeView) {
            showHomeView();
          }
        });
  }

  private void showNavigationFailed(NavigationFailed event) {
    FxThreadExecutor.execute(
        () -> {
          if (!event.tabId().equals(activeTabId)) {
            return;
          }
          addressField.setText(event.address());
          updateSiteInfo(Optional.empty());
          navigationStatus.setText("열 수 없음");
          String typed = schemelessInput.remove(event.tabId());
          if (typed != null
              && event.address().startsWith("https://")
              && (event.reason() == NavigationFailed.Reason.UNREACHABLE
                  || event.reason() == NavigationFailed.Reason.TIMEOUT)) {
            errorShown = true;
            navigationStatus.setText("http 연결 확인 필요");
            setCenter(
                new HttpFallbackView(
                    URI.create(event.address()).getHost(),
                    () -> browserController.navigate(event.tabId(), "http://" + typed),
                    () -> showNavigationFailure(event)));
            refreshAiRing();
            return;
          }
          showNavigationFailure(event);
        });
  }

  private void showNavigationFailure(NavigationFailed event) {
    FxThreadExecutor.execute(
        () -> {
          ErrorPageView.Actions actions =
              new ErrorPageView.Actions(
                  () -> browserController.navigate(event.tabId(), event.address()),
                  () -> runSearch(event.address()),
                  () -> {
                    addressField.requestFocus();
                    addressField.selectAll();
                  },
                  () -> browserController.createTab());
          errorShown = true;
          setCenter(ErrorPageView.of(event.reason(), event.address(), actions));
          refreshAiRing();
        });
  }

  private void showOperationError(OperationFailed event) {
    updateLabel(navigationStatus, event.error().safeMessage());
  }

  private void showSearchStarted(SearchStarted event) {
    FxThreadExecutor.execute(
        () -> {
          if (event.tabId().equals(activeTabId)) {
            addressField.setText(event.query());
            updateSiteInfo(Optional.empty());
            navigationStatus.setText("검색 중…");
            setCenter(
                SearchResultsView.loading(
                    event.query(),
                    () -> searchService.cancel(event.tabId()),
                    result -> browserController.navigate(event.tabId(), result.uri().toString())));
          }
        });
  }

  private void showSearchCompleted(SearchCompleted event) {
    FxThreadExecutor.execute(
        () -> {
          if (event.tabId().equals(activeTabId)) {
            addressField.setText(event.query());
            updateSiteInfo(Optional.empty());
            int count = event.response().results().size();
            navigationStatus.setText("검색 결과 " + count + "개");
            if (count == 0) {
              setCenter(
                  SearchResultsView.empty(
                      event.query(),
                      () -> searchService.search(event.tabId(), event.query()),
                      result ->
                          browserController.navigate(event.tabId(), result.uri().toString())));
            } else {
              resultsView =
                  SearchResultsView.results(
                      event.query(),
                      event.response().results(),
                      result -> browserController.navigate(event.tabId(), result.uri().toString()));
              resultsView.configureAiPanel(
                  aiPanelOpen,
                  aiPanelSide,
                  (open, side) -> {
                    aiPanelOpen = open;
                    aiPanelSide = side;
                  },
                  () -> {
                    if (aiSearchService != null) {
                      aiSearchService.cancel(event.tabId());
                    }
                  },
                  () -> searchService.search(event.tabId(), event.query()));
              setCenter(resultsView);
            }
          }
        });
  }

  private void showSearchFailed(SearchFailed event) {
    FxThreadExecutor.execute(
        () -> {
          if (event.tabId().equals(activeTabId)) {
            addressField.setText(event.query());
            updateSiteInfo(Optional.empty());
            navigationStatus.setText(event.offline() ? "오프라인" : event.safeMessage());
            Runnable retry = () -> searchService.search(event.tabId(), event.query());
            java.util.function.Consumer<kr.soyuo.serox.search.api.SearchResult> open =
                result -> browserController.navigate(event.tabId(), result.uri().toString());
            setCenter(
                event.offline()
                    ? SearchResultsView.offline(event.query(), retry, open)
                    : SearchResultsView.error(event.query(), event.safeMessage(), retry, open));
          }
        });
  }

  private void showAiAnswer(AiAnswerChanged event) {
    FxThreadExecutor.execute(
        () -> {
          if (event.tabId().equals(activeTabId)
              && resultsView != null
              && getCenter() == resultsView) {
            if (settingsService != null) {
              resultsView.setSummaryPreferences(settingsService.current().summary());
            }
            resultsView.showAi(event);
            aiSearchActive =
                event.phase() == AiAnswerChanged.Phase.PREPARING
                    || event.phase() == AiAnswerChanged.Phase.STREAMING
                    || event.phase() == AiAnswerChanged.Phase.COMPLETED;
            refreshAiRing();
          }
        });
  }

  /**
   * Keeps the address bar in step with what is shown: the cyan ring and the summary chip while the
   * results carry an AI summary (as in the design), and a red address while an error page is up.
   */
  private void refreshAiRing() {
    if (addressBox == null) {
      return;
    }
    boolean on = aiSearchActive && resultsView != null && getCenter() == resultsView;
    boolean error = errorShown && getCenter() instanceof ErrorPageView;
    aiChip.setVisible(on);
    addressBox.getStyleClass().removeAll("address-ai", "address-error");
    if (on) {
      addressBox.getStyleClass().add("address-ai");
    } else if (error) {
      addressBox.getStyleClass().add("address-error");
    }
  }

  private void showSearchCancelled(SearchCancelled event) {
    FxThreadExecutor.execute(
        () -> {
          if (event.tabId().equals(activeTabId)) {
            navigationStatus.setText("검색 취소됨");
            setCenter(
                SearchResultsView.cancelled(
                    event.query(),
                    () -> searchService.search(event.tabId(), event.query()),
                    result -> browserController.navigate(event.tabId(), result.uri().toString())));
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
            if (snapshot == null || snapshot.currentUri().isEmpty()) {
              addressField.requestFocus();
              addressField.selectAll();
            }
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
      title.setMaxWidth(UiTokens.TAB_TITLE_MAX_WIDTH);
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

  private void updateSiteInfo(Optional<URI> pageAddress) {
    siteInfo = SiteInfo.of(pageAddress);
    String label =
        switch (siteInfo.level()) {
          case SECURE -> "보안 연결";
          case INSECURE -> "보안 주의";
          case NONE -> "Serox";
        };
    siteInfoButton.setText(label);
    siteInfoButton.setAccessibleText("사이트 연결 정보: " + label);
    siteInfoButton
        .getStyleClass()
        .setAll(
            "button",
            "site-info-button",
            switch (siteInfo.level()) {
              case SECURE -> "site-info-secure";
              case INSECURE -> "site-info-insecure";
              case NONE -> "site-info-none";
            });
  }

  private void showSiteInfo() {
    if (sitePermissions == null) {
      return;
    }
    new SiteInfoPopup(
            siteInfo,
            sitePermissions,
            this::reloadActivePage,
            getStyleClass().contains("theme-dark"))
        .show(siteInfoButton);
  }

  /** Loads the shown page again, for example after a site permission changed. */
  private void reloadActivePage() {
    PageLoaded page = loadedPages.get(activeTabId);
    if (page != null) {
      browserController.navigate(activeTabId, page.page().uri().toString());
    }
  }

  /** Connects the per-site permissions the site info popup shows and changes. */
  public void setSitePermissions(kr.soyuo.serox.core.application.SitePermissionService service) {
    sitePermissions = java.util.Objects.requireNonNull(service, "service");
  }

  private void navigateToAddress() {
    if (activeTabId != null && !addressField.getText().isBlank()) {
      String input = addressField.getText().strip();
      try {
        NavigationTarget target = new NavigationInputClassifier().classify(input);
        if (target instanceof SearchQuery query) {
          runSearch(query.value());
        } else {
          searchService.cancel(activeTabId);
          if (!input.regionMatches(true, 0, "http://", 0, 7)
              && !input.regionMatches(true, 0, "https://", 0, 8)) {
            schemelessInput.put(activeTabId, input);
          }
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
          schemelessInput.remove(event.tabId());
          if (event.tabId().equals(activeTabId)) {
            updateSiteInfo(Optional.of(event.page().uri()));
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
    updateSiteInfo(Optional.empty());
    homeView = new HomeView(previewData, recentSearches.queries(), this::searchFromHome);
    setCenter(homeView);
  }

  private void searchFromHome(String query) {
    runSearch(query);
  }

  private void runSearch(String query) {
    if (activeTabId == null || query == null || query.isBlank()) {
      return;
    }
    String normalized = query.strip();
    recentSearches.add(normalized);
    searchService.search(activeTabId, normalized);
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
              acceleratorScene = newScene;
              refreshAccelerators();
            });
  }

  /** Registers the keys the user chose (or the defaults), replacing the ones set before. */
  private void refreshAccelerators() {
    if (acceleratorScene == null) {
      return;
    }
    var accelerators = acceleratorScene.getAccelerators();
    installedAccelerators.forEach(accelerators::remove);
    installedAccelerators.clear();
    BrowserSettings settings =
        settingsService == null ? BrowserSettings.defaults() : settingsService.current();
    for (ShortcutAction action : ShortcutAction.values()) {
      KeyChord chord = settings.shortcut(action);
      if (chord.assigned()) {
        register(accelerators, KeyChords.toCombination(chord), () -> runShortcut(action));
      }
    }
    // Ctrl+K has always put the cursor in the home search box; it stays unless a chosen key needs
    // it.
    KeyCodeCombination homeSearch = new KeyCodeCombination(KeyCode.K, KeyCombination.CONTROL_DOWN);
    if (!accelerators.containsKey(homeSearch)) {
      register(accelerators, homeSearch, () -> homeView.focusSearchField());
    }
  }

  private void register(
      Map<KeyCombination, Runnable> accelerators, KeyCombination combination, Runnable action) {
    accelerators.put(combination, action);
    installedAccelerators.add(combination);
  }

  private void runShortcut(ShortcutAction action) {
    SearchResultsView results = getCenter() instanceof SearchResultsView view ? view : null;
    switch (action) {
      case FOCUS_SEARCH -> {
        addressField.requestFocus();
        addressField.selectAll();
      }
      case TOGGLE_AI -> {
        if (results != null) {
          results.togglePanel();
        }
      }
      case NEXT_SOURCE -> {
        if (results != null) {
          results.selectRelative(1);
        }
      }
      case PREVIOUS_SOURCE -> {
        if (results != null) {
          results.selectRelative(-1);
        }
      }
      case NEW_TAB -> browserController.createTab();
      case CLOSE_TAB -> {
        if (activeTabId != null) {
          browserController.closeTab(activeTabId);
        }
      }
      case OPEN_HISTORY -> showLibrary(LibraryView.Section.HISTORY);
      case OPEN_SETTINGS -> showSettings();
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
