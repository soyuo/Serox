package kr.soyuo.serox.ui;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import kr.soyuo.serox.ai.api.EvidenceDocument;
import kr.soyuo.serox.core.event.AiAnswerChanged;
import kr.soyuo.serox.core.model.AiPanelPosition;
import kr.soyuo.serox.search.api.SearchResult;

/** Displays search results and their selected result details. */
final class SearchResultsView extends BorderPane {
  private final String query;
  private final Consumer<SearchResult> onOpenResult;
  private final VBox results = new VBox();
  private final BorderPane preview = new BorderPane();
  private final BorderPane content = new BorderPane();
  private final Button panelToggle = new Button();
  private final Button panelPosition = new Button();
  private boolean panelOpen = true;
  private AiPanelPosition panelSide = AiPanelPosition.RIGHT;
  private BiConsumer<Boolean, AiPanelPosition> onPanelChanged = (open, side) -> {};
  private Runnable onOpenAiSettings = () -> {};
  private Consumer<SearchResult> onOpenInNewTab;
  private final AiPanelView aiPanel = new AiPanelView(this::openEvidence);
  private java.net.URI shownEvidence;

  void setSummaryPreferences(kr.soyuo.serox.core.model.SummaryPreferences preferences) {
    aiPanel.setPresentation(preferences.keyPoints(), preferences.inlineCitations());
  }

  private SearchResult selected;
  private final ScrollPane listScroll;
  /** Whether the source pane beside the AI answer is open; it opens when a source is picked. */
  private boolean sourceOpen;
  private boolean hasResults;
  private List<SearchResult> found = List.of();
  private VBox banner;
  private VBox sourceNotice;
  private int readLimit = 5;

  /** How many top results the AI tries to read, so unread ones can be told apart. */
  void setReadLimit(int limit) {
    readLimit = Math.max(1, limit);
  }

  /** The results the AI tried to read but has no text for, in result order. */
  static List<SearchResult> unreadSources(
      List<SearchResult> found, List<EvidenceDocument> evidence, int limit) {
    java.util.Set<String> read = new java.util.HashSet<>();
    for (EvidenceDocument document : evidence) {
      read.add(trimSlash(document.uri().toString()));
    }
    return found.stream()
        .limit(limit)
        .filter(result -> !read.contains(trimSlash(result.uri().toString())))
        .toList();
  }

  private static String trimSlash(String address) {
    return address.endsWith("/") ? address.substring(0, address.length() - 1) : address;
  }

  /** Shows or clears the notice and row tags for sources the AI could not read. */
  private void showSourceNotice(AiAnswerChanged event) {
    if (sourceNotice != null) {
      results.getChildren().remove(sourceNotice);
      sourceNotice = null;
    }
    List<SearchResult> unread =
        event.phase() == AiAnswerChanged.Phase.COMPLETED
            ? unreadSources(found, event.evidence(), readLimit)
            : List.of();
    for (javafx.scene.Node row : results.getChildren()) {
      if (row instanceof Button item && item.getProperties().get("unread") instanceof Label tag) {
        tag.setVisible(unread.contains(item.getProperties().get("result")));
        tag.setManaged(tag.isVisible());
      }
    }
    if (unread.isEmpty()) {
      return;
    }
    int tried = Math.min(found.size(), readLimit);
    String title = tried + "곳 중 " + (tried - unread.size()) + "곳을 읽고 정리했어요";
    String detail = unread.size() + "곳은 읽지 못했어요. 접근을 막았거나 시간이 오래 걸렸어요.";
    Label heading = new Label(title);
    heading.getStyleClass().add("source-notice-title");
    Button retry = action("못 읽은 곳 다시 시도", () -> aiPanel.retry());
    retry.getStyleClass().setAll("button", "secondary-button", "action-button");
    sourceNotice = new VBox(UiTokens.SPACE_SM, heading, message(detail), retry);
    sourceNotice.getStyleClass().add("source-notice");
    sourceNotice.setAccessibleRole(javafx.scene.AccessibleRole.TEXT);
    sourceNotice.setAccessibleText(title + ". " + detail);
    results.getChildren().add(Math.min(1, results.getChildren().size()), sourceNotice);
  }

  private SearchResultsView(String query, Consumer<SearchResult> onOpenResult) {
    this.query = query;
    this.onOpenResult = onOpenResult;
    getStyleClass().add("search-results-view");

    results.getStyleClass().add("search-results-list");
    listScroll = new ScrollPane(results);
    listScroll.setFitToWidth(true);
    // The list fills the visible height, so a lone state message can sit in the middle.
    results
        .minHeightProperty()
        .bind(
            javafx.beans.binding.Bindings.createDoubleBinding(
                () -> listScroll.getViewportBounds().getHeight(), listScroll.viewportBoundsProperty()));
    listScroll.prefWidthProperty().bind(widthProperty().multiply(0.38));
    listScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    listScroll.getStyleClass().add("search-results-scroll");
    content.setLeft(listScroll);
    preview.getStyleClass().add("search-preview");
    content.setCenter(preview);
    setCenter(content);
    refreshLayout();
    setQueryHeading();
  }

  static SearchResultsView loading(String query, Runnable onCancel, Consumer<SearchResult> onOpen) {
    SearchResultsView view = new SearchResultsView(query, onOpen);
    view.results.getChildren().setAll(view.heading("검색 중"), view.message("검색 결과를 불러오고 있어요…"));
    Button cancel = new Button("취소");
    cancel.getStyleClass().add("secondary-button");
    cancel.setOnAction(event -> onCancel.run());
    view.preview.setCenter(view.state("검색 중", "검색 결과를 불러오고 있어요…", cancel));
    return view;
  }

  static SearchResultsView empty(String query, Runnable onRetry, Consumer<SearchResult> onOpen) {
    SearchResultsView view = new SearchResultsView(query, onOpen);
    VBox state =
        view.state(
            "검색 결과가 없어요",
            "“" + query + "”와 맞는 페이지를 찾지 못했어요.",
            null,
            "!");
    // Tips for a better query, as in the design.
    VBox tips =
        new VBox(
            UiTokens.SPACE_SM,
            view.message("· 철자가 맞는지 확인해 보세요."),
            view.message("· 더 짧거나 일반적인 검색어로 바꿔 보세요."),
            view.message("· 다른 표현으로 검색해 보세요."));
    tips.getStyleClass().add("search-tips");
    tips.setMaxWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
    Button retry = view.action("다시 검색", onRetry);
    retry.getStyleClass().setAll("button", "secondary-button", "action-button");
    state.getChildren().addAll(tips, retry);
    view.results
        .getChildren()
        .setAll(view.statusHeading("검색 결과가 없어요", "status-dot-ok"), state);
    view.preview.setCenter(view.sourcePlaceholder("보여드릴 페이지가 없어요", "다른 검색어로 다시 검색해 보세요."));
    return view;
  }

  static SearchResultsView cancelled(
      String query, Runnable onRetry, Consumer<SearchResult> onOpen) {
    SearchResultsView view = new SearchResultsView(query, onOpen);
    view.results
        .getChildren()
        .setAll(
            view.heading("검색 결과"),
            view.state("검색을 취소했어요", "원하면 다시 검색할 수 있어요.", view.action("다시 검색", onRetry)));
    view.preview.setCenter(
        view.state("검색이 취소됐어요", "검색을 다시 시작할 수 있어요.", view.action("다시 검색", onRetry)));
    return view;
  }

  static final String SEARCH_ERROR_TITLE = "검색 서비스에 연결하지 못했어요";

  static SearchResultsView error(
      String query,
      String error,
      Runnable onRetry,
      Runnable onOpenSettings,
      Consumer<SearchResult> onOpen) {
    SearchResultsView view = new SearchResultsView(query, onOpen);
    VBox state = view.state(SEARCH_ERROR_TITLE, error, null, "!");
    Button retry = view.action("다시 시도", onRetry);
    retry.getStyleClass().setAll("button", "secondary-button", "action-button");
    // The two choices sit side by side, as in the design.
    javafx.scene.layout.HBox choices =
        new javafx.scene.layout.HBox(
            UiTokens.SPACE_SM, view.action("검색 설정 열기", onOpenSettings), retry);
    choices.setAlignment(Pos.CENTER);
    state.getChildren().add(choices);
    state.setAccessibleRole(javafx.scene.AccessibleRole.TEXT);
    state.setAccessibleText(SEARCH_ERROR_TITLE + ". " + error);
    view.results.getChildren().setAll(view.statusHeading("검색 서비스 오류", "status-dot-error"), state);
    view.preview.setCenter(view.sourcePlaceholder("보여드릴 페이지가 없어요", "검색 설정을 확인해 주세요."));
    return view;
  }

  /** The no-internet screen from the design: one message, one retry, and a matching source pane. */
  static SearchResultsView offline(String query, Runnable onRetry, Consumer<SearchResult> onOpen) {
    SearchResultsView view = new SearchResultsView(query, onOpen);
    view.results
        .getChildren()
        .setAll(
            view.statusHeading("오프라인", "status-dot-error"),
            view.state(
                "인터넷에 연결되어 있지 않아요",
                "연결을 확인한 뒤 다시 시도해 주세요.",
                view.action("다시 시도", onRetry),
                "!"));
    view.preview.setCenter(view.sourcePlaceholder("인터넷에 연결되지 않았어요", "연결을 확인한 뒤 다시 시도해 주세요."));
    return view;
  }

  static SearchResultsView results(
      String query, List<SearchResult> found, Consumer<SearchResult> onOpen) {
    SearchResultsView view = new SearchResultsView(query, onOpen);
    view.found = List.copyOf(found);
    view.results.getChildren().add(view.heading("검색 결과 " + found.size() + "곳"));
    view.installPanelControls();
    int number = 0;
    for (SearchResult result : found) {
      number++;
      Label badge = new Label(Integer.toString(number));
      badge.getStyleClass().add("source-badge");
      Label title = new Label(result.title());
      title.getStyleClass().add("search-result-title");
      Label url = new Label(result.uri().toString());
      url.getStyleClass().add("search-result-url");
      Label openTag = new Label("열람 중");
      openTag.getStyleClass().add("open-tag");
      openTag.setVisible(false);
      Region gap = new Region();
      HBox.setHgrow(gap, Priority.ALWAYS);
      title.setMinWidth(0);
      HBox.setHgrow(title, Priority.SOMETIMES);
      Label unreadTag = new Label("읽지 못함");
      unreadTag.getStyleClass().add("unread-tag");
      unreadTag.setVisible(false);
      unreadTag.setManaged(false);
      HBox head = new HBox(UiTokens.SPACE_SM + 2, badge, title, gap, unreadTag, openTag);
      head.setAlignment(Pos.CENTER_LEFT);
      Label snippet = new Label(result.snippet());
      snippet.setWrapText(true);
      snippet.getStyleClass().add("search-result-snippet");
      VBox content = new VBox(6, head, url, snippet);
      content.getStyleClass().add("search-result-content");
      Button item = new Button();
      item.setGraphic(content);
      item.setMaxWidth(Double.MAX_VALUE);
      item.setAccessibleText(number + "번, " + result.title() + ", " + result.uri());
      item.getStyleClass().add("search-result-item");
      item.getProperties().put("result", result);
      item.getProperties().put("tag", openTag);
      item.getProperties().put("unread", unreadTag);
      item.setOnAction(event -> view.select(result));
      view.results.getChildren().add(item);
    }
    if (!found.isEmpty()) {
      view.select(found.getFirst());
    }
    return view;
  }

  /** Marks the row of the result whose details are open, as the design does. */
  private void markOpenRow(SearchResult open) {
    for (javafx.scene.Node row : results.getChildren()) {
      if (row instanceof Button item && item.getProperties().get("result") != null) {
        boolean isOpen = item.getProperties().get("result").equals(open);
        item.getStyleClass().remove("search-result-open");
        if (isOpen) {
          item.getStyleClass().add("search-result-open");
        }
        if (item.getProperties().get("tag") instanceof Label tag) {
          tag.setVisible(isOpen);
        }
      }
    }
  }

  private void setQueryHeading() {
    Label label = new Label("‘" + query + "’ 검색 결과");
    label.getStyleClass().add("search-query-heading");
    setTop(label);
  }

  /** A heading with a colored dot in front, like the design's status line. */
  private Label statusHeading(String text, String tone) {
    Label label = heading(text);
    javafx.scene.shape.Circle dot = new javafx.scene.shape.Circle(4);
    dot.getStyleClass().addAll("status-dot", tone);
    label.setGraphic(dot);
    label.setGraphicTextGap(8);
    return label;
  }

  private Label heading(String text) {
    Label label = new Label(text);
    label.getStyleClass().add("search-section-heading");
    return label;
  }

  private Label message(String text) {
    Label label = new Label(text);
    label.setWrapText(true);
    label.getStyleClass().add("search-state-message");
    return label;
  }

  private VBox state(String title, String description, Button action) {
    return state(title, description, action, null);
  }

  /** A centered state message with an optional round icon above it, as in the design. */
  private VBox state(String title, String description, Button action, String glyph) {
    Label heading = new Label(title);
    heading.getStyleClass().add("search-state-title");
    VBox state = new VBox(10, heading, message(description));
    if (action != null) state.getChildren().add(action);
    if (glyph != null) {
      Label icon = new Label(glyph);
      icon.getStyleClass().add("search-state-icon");
      icon.setMinSize(48, 48);
      icon.setAlignment(Pos.CENTER);
      icon.setAccessibleRole(javafx.scene.AccessibleRole.NODE);
      state.getChildren().addFirst(icon);
    }
    // Fill the column so the message sits in its middle, not under the heading.
    VBox.setVgrow(state, javafx.scene.layout.Priority.ALWAYS);
    state.setMaxHeight(Double.MAX_VALUE);
    state.setAlignment(Pos.CENTER);
    state.setPadding(new Insets(28));
    state.getStyleClass().add("search-state");
    return state;
  }

  private Button action(String title, Runnable action) {
    Button button = new Button(title);
    button.getStyleClass().addAll("primary-button", "action-button");
    // Keep the whole label; a squeezed row must not cut it to "다시 로그…".
    button.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
    button.setOnAction(event -> action.run());
    return button;
  }

  /**
   * Sets the remembered panel state and the callbacks for cancelling and retrying the AI answer.
   */
  void configureAiPanel(
      boolean open,
      AiPanelPosition side,
      BiConsumer<Boolean, AiPanelPosition> onChanged,
      Runnable onCancelAi,
      Runnable onRetryAi) {
    panelOpen = open;
    panelSide = side;
    onPanelChanged = onChanged;
    aiPanel.setActions(onCancelAi, onRetryAi);
    applyPanel();
  }

  private void installPanelControls() {
    hasResults = true;
    panelToggle.getStyleClass().add("secondary-button");
    panelToggle.setOnAction(
        event -> {
          panelOpen = !panelOpen;
          applyPanel();
          onPanelChanged.accept(panelOpen, panelSide);
        });
    panelPosition.getStyleClass().add("secondary-button");
    panelPosition.setOnAction(
        event -> {
          panelSide =
              panelSide == AiPanelPosition.RIGHT ? AiPanelPosition.LEFT : AiPanelPosition.RIGHT;
          applyPanel();
          onPanelChanged.accept(panelOpen, panelSide);
        });
    javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
    javafx.scene.layout.HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
    javafx.scene.layout.HBox bar =
        new javafx.scene.layout.HBox(
            UiTokens.SPACE_SM, getTop(), spacer, panelPosition, panelToggle);
    bar.setAlignment(Pos.CENTER_LEFT);
    bar.setPadding(new Insets(0, 24, 0, 0));
    setTop(bar);
    applyPanel();
  }

  private void applyPanel() {
    panelToggle.setText(panelOpen ? "AI 패널 닫기" : "AI 패널 열기");
    panelToggle.setAccessibleText(panelToggle.getText());
    panelPosition.setText(panelSide == AiPanelPosition.RIGHT ? "왼쪽으로 이동" : "오른쪽으로 이동");
    panelPosition.setAccessibleText(panelPosition.getText());
    panelPosition.setDisable(!panelOpen);
    // The answer fills the view, so moving it to a side means nothing there.
    panelPosition.setVisible(!aiMode());
    panelPosition.setManaged(panelPosition.isVisible());
    refreshLayout();
  }

  /** The banner title for a provider failure code, as in the design's failure states. */
  static String failureTitle(String code) {
    return switch (code) {
      case "unauthorized" -> "로그인이 만료됐어요";
      case "rate_limited" -> "사용 한도에 도달했어요";
      case "network" -> "AI에 연결하지 못했어요";
      case "timeout" -> "AI 응답이 늦어지고 있어요";
      default -> "요약하지 못했어요";
    };
  }

  /** The banner text under the title for a provider failure code, or empty to use the message. */
  static String failureDetail(String code) {
    return switch (code) {
      case "unauthorized" -> "다시 로그인하면 바로 요약해 드려요.";
      case "rate_limited" -> "다른 AI로 요약하거나 잠시 뒤 다시 시도해 주세요.";
      default -> "";
    };
  }

  /** The settings button label for a banner, or empty when the banner has none. */
  static String settingsAction(AiAnswerChanged.Phase phase, String code) {
    if (phase == AiAnswerChanged.Phase.UNAVAILABLE) {
      return "AI 연결하기";
    }
    if (phase != AiAnswerChanged.Phase.FAILED) {
      return "";
    }
    return switch (code) {
      case "unauthorized" -> "다시 로그인하기";
      case "rate_limited" -> "다른 AI로 요약";
      default -> "";
    };
  }

  /** Sets how a source opens in a new tab; without it the source opens in this tab. */
  void setOnOpenInNewTab(Consumer<SearchResult> action) {
    onOpenInNewTab = action;
  }

  /** Sets what the banner's settings button does. */
  void setOnOpenAiSettings(Runnable action) {
    onOpenAiSettings = action;
  }

  /** The card above the result list that explains why there is no summary, as in the design. */
  private VBox banner(AiAnswerChanged event) {
    boolean failed = event.phase() == AiAnswerChanged.Phase.FAILED;
    String code = failed ? event.failureCode() : "";
    String settingsLabel = settingsAction(event.phase(), code);
    boolean fixInSettings = !settingsLabel.isEmpty();
    boolean warning = code.equals("unauthorized") || code.equals("rate_limited");
    String title =
        switch (event.phase()) {
          case FAILED -> failureTitle(code);
          case CANCELLED -> "요약을 취소했어요";
          default -> "AI를 연결하면 요약해 드려요";
        };
    String detail =
        event.phase() == AiAnswerChanged.Phase.UNAVAILABLE
            ? "지금은 검색 결과만 보여드려요. 브라우저 로그인이나 API 키로 연결할 수 있어요."
            : !failureDetail(code).isEmpty()
                ? failureDetail(code)
                : event.message().isBlank() ? "검색 결과 목록은 그대로 볼 수 있어요." : event.message();
    Label heading = new Label(title);
    Label icon = new Label("!");
    icon.getStyleClass().add("ai-banner-icon");
    icon.setMinSize(20, 20);
    icon.setAlignment(javafx.geometry.Pos.CENTER);
    heading.setGraphic(icon);
    heading.setGraphicTextGap(UiTokens.SPACE_SM);
    heading.getStyleClass().add("ai-banner-title");
    VBox card = new VBox(UiTokens.SPACE_SM, heading, message(detail));
    javafx.scene.layout.HBox actions = new javafx.scene.layout.HBox(UiTokens.SPACE_SM);
    if (fixInSettings) {
      actions.getChildren().add(action(settingsLabel, onOpenAiSettings));
    }
    if (failed || event.phase() == AiAnswerChanged.Phase.CANCELLED) {
      Button retry = action("다시 시도", () -> aiPanel.retry());
      if (fixInSettings) {
        retry.getStyleClass().setAll("button", "secondary-button", "action-button");
      }
      actions.getChildren().add(retry);
    }
    if (!actions.getChildren().isEmpty()) {
      card.getChildren().add(actions);
    }
    card.setAccessibleRole(javafx.scene.AccessibleRole.TEXT);
    card.setAccessibleText(title + ". " + detail);
    card.getStyleClass().add("ai-banner");
    if (warning) {
      card.getStyleClass().add("ai-banner-warning");
    } else if (failed) {
      card.getStyleClass().add("ai-banner-error");
    }
    return card;
  }

  private void showBanner(VBox next) {
    if (banner != null) {
      results.getChildren().remove(banner);
    }
    banner = next;
    if (banner != null) {
      results.getChildren().add(Math.min(1, results.getChildren().size()), banner);
    }
    applyPanel();
  }

  /** Opens or closes the AI panel, as its button does; results without a panel ignore it. */
  void togglePanel() {
    if (hasResults) {
      panelToggle.fire();
    }
  }

  /** Opens the source a step after ({@code 1}) or before ({@code -1}) the open one, to the ends. */
  void selectRelative(int step) {
    if (found.isEmpty()) {
      return;
    }
    int index = selected == null ? -1 : found.indexOf(selected);
    int next = index < 0 ? (step > 0 ? 0 : found.size() - 1) : index + step;
    sourceOpen = true;
    select(found.get(Math.max(0, Math.min(found.size() - 1, next))));
    refreshLayout();
  }

  /** Opens the AI panel if it was closed; used by the address bar's summary chip. */
  void openAiPanel() {
    if (!panelOpen) {
      panelOpen = true;
      applyPanel();
      onPanelChanged.accept(panelOpen, panelSide);
    }
  }

  /** Forwards the latest AI answer state to the panel; search results are unaffected. */
  void showAi(AiAnswerChanged event) {
    boolean unavailable =
        hasResults
            && (event.phase() == AiAnswerChanged.Phase.UNAVAILABLE
                || event.phase() == AiAnswerChanged.Phase.FAILED
                || event.phase() == AiAnswerChanged.Phase.CANCELLED);
    showBanner(unavailable ? banner(event) : null);
    if (hasResults) {
      showSourceNotice(event);
    }
    aiPanel.show(event);
    if (event.phase() == AiAnswerChanged.Phase.PREPARING) {
      // A new answer starts with the whole width again.
      sourceOpen = false;
    }
    if (selected != null
        && !selected.uri().equals(shownEvidence)
        && aiPanel.evidenceFor(selected.uri()).isPresent()) {
      select(selected);
    }
    refreshLayout();
  }

  /** The AI answer owns the view: results exist, the panel is open and no banner replaces it. */
  private boolean aiMode() {
    return hasResults && panelOpen && banner == null;
  }

  /**
   * With the AI answer the answer takes the whole width from the start, sources listed under it.
   * Picking a source pushes the answer aside and opens the source pane on the right; its close
   * button gives the width back. Without the answer the result list and preview show instead.
   */
  private void refreshLayout() {
    aiPanel.prefWidthProperty().unbind();
    aiPanel.getStyleClass().remove("ai-panel-wide");
    preview.prefWidthProperty().unbind();
    if (aiMode()) {
      aiPanel.getStyleClass().add("ai-panel-wide");
      content.setLeft(null);
      content.setCenter(aiPanel);
      preview.setTop(sourceCloseBar());
      if (sourceOpen) {
        preview.setPrefWidth(UiTokens.SOURCE_PANE_WIDTH);
        preview.setMinWidth(UiTokens.SOURCE_PANE_WIDTH);
        content.setRight(preview);
      } else {
        content.setRight(null);
      }
    } else {
      preview.setTop(null);
      preview.setPrefWidth(Region.USE_COMPUTED_SIZE);
      preview.setMinWidth(Region.USE_COMPUTED_SIZE);
      content.setRight(null);
      content.setLeft(listScroll);
      content.setCenter(preview);
    }
  }

  /** The bar with the X that closes the source pane. */
  private HBox sourceCloseBar() {
    Button close = new Button("✕");
    close.getStyleClass().add("secondary-button");
    close.setAccessibleText("출처 닫기");
    close.setOnAction(event -> closeSource());
    HBox bar = new HBox(close);
    bar.setAlignment(Pos.CENTER_RIGHT);
    bar.setPadding(new Insets(UiTokens.SPACE_SM, UiTokens.SPACE_LG, 0, UiTokens.SPACE_LG));
    return bar;
  }

  /** Closes the source pane so the answer takes the width back. */
  void closeSource() {
    sourceOpen = false;
    refreshLayout();
  }

  private VBox sourcePlaceholder(String heading, String detail) {
    Label title = new Label(heading);
    title.getStyleClass().add("search-state-title");
    VBox card =
        new VBox(
            UiTokens.SPACE_MD,
            SvgIconView.icon(UiTokens.ICON_PREVIEW_PLACEHOLDER, UiTokens.PLACEHOLDER_ICON_SIZE),
            title,
            message(detail));
    card.setAlignment(Pos.CENTER);
    card.getStyleClass().add("source-placeholder");
    VBox.setVgrow(card, javafx.scene.layout.Priority.ALWAYS);
    VBox holder = new VBox(card);
    holder.setPadding(new Insets(UiTokens.SPACE_LG));
    holder.getStyleClass().add("source-holder");
    return holder;
  }

  private void openInNewTab(SearchResult result) {
    // The page now has its own tab, so give the width back to the answer.
    closeSource();
    (onOpenInNewTab != null ? onOpenInNewTab : onOpenResult).accept(result);
  }

  /** Opens the source pane with a source picked under the answer. */
  private void openEvidence(EvidenceDocument document) {
    sourceOpen = true;
    showEvidence(document);
    refreshLayout();
  }

  /** The source pane from the design: a header with the source, its address and an open button. */
  private void showEvidence(EvidenceDocument document) {
    shownEvidence = document.uri();
    Label number = new Label("원문 " + document.sourceId().replaceFirst("^S", ""));
    number.getStyleClass().add("source-pane-number");
    Label address = new Label(document.uri().toString());
    address.getStyleClass().add("source-pane-address");
    address.setMaxWidth(Double.MAX_VALUE);
    address.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
    javafx.scene.layout.HBox.setHgrow(address, javafx.scene.layout.Priority.ALWAYS);
    Button open = new Button("새 탭에서 열기");
    open.getStyleClass().add("secondary-button");
    open.setAccessibleText("새 탭에서 원문 열기");
    open.setOnAction(
        event ->
            openInNewTab(
                new SearchResult(document.sourceId(), document.uri(), document.title(), "", 1)));
    javafx.scene.layout.HBox header =
        new javafx.scene.layout.HBox(UiTokens.SPACE_SM, number, address, open);
    header.setAlignment(Pos.CENTER_LEFT);
    header.getStyleClass().add("source-pane-header");

    Label title = new Label(document.title());
    title.setWrapText(true);
    title.getStyleClass().add("search-preview-title");
    Label excerpt = new Label(document.text());
    excerpt.setWrapText(true);
    excerpt.getStyleClass().add("search-result-snippet");
    ScrollPane excerptScroll = new ScrollPane(excerpt);
    excerptScroll.setFitToWidth(true);
    excerptScroll.getStyleClass().add("search-results-scroll");
    VBox.setVgrow(excerptScroll, javafx.scene.layout.Priority.ALWAYS);
    VBox card = new VBox(UiTokens.SPACE_MD, title, excerptScroll);
    card.getStyleClass().add("source-pane-card");
    VBox.setVgrow(card, javafx.scene.layout.Priority.ALWAYS);
    VBox details = new VBox(UiTokens.SPACE_SM, header, card);
    details.setPadding(new Insets(UiTokens.SPACE_LG));
    details.getStyleClass().add("source-holder");
    preview.setCenter(details);
  }

  private void select(SearchResult result) {
    selected = result;
    markOpenRow(result);
    // When the AI has read this page, show what it read, not just a button to open the page.
    var read = aiPanel.evidenceFor(result.uri());
    if (read.isPresent()) {
      showEvidence(read.get());
      return;
    }
    shownEvidence = null;
    Label title = new Label(result.title());
    title.setWrapText(true);
    title.getStyleClass().add("search-preview-title");
    Label url = new Label(result.uri().toString());
    url.setWrapText(true);
    url.getStyleClass().add("search-result-url");
    Label snippet = new Label(result.snippet());
    snippet.setWrapText(true);
    snippet.getStyleClass().add("search-result-snippet");
    Button open = action("페이지 열기", () -> onOpenResult.accept(selected));
    VBox details = new VBox(14, title, url, snippet, open);
    details.setPadding(new Insets(40));
    details.getStyleClass().add("search-preview-content");
    preview.setCenter(details);
  }
}
