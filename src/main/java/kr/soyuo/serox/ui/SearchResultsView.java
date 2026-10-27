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
  private final AiPanelView aiPanel = new AiPanelView(this::showEvidence);
  private SearchResult selected;
  private final ScrollPane listScroll;
  private boolean summaryFocus;
  private boolean hasResults;
  private VBox banner;

  private SearchResultsView(String query, Consumer<SearchResult> onOpenResult) {
    this.query = query;
    this.onOpenResult = onOpenResult;
    getStyleClass().add("search-results-view");

    results.getStyleClass().add("search-results-list");
    listScroll = new ScrollPane(results);
    listScroll.setFitToWidth(true);
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
    view.results
        .getChildren()
        .setAll(
            view.heading("검색 결과"),
            view.state("결과가 없어요", "다른 검색어로 다시 시도해 보세요.", view.action("다시 검색", onRetry)));
    view.preview.setCenter(view.state("검색 결과가 없습니다", "검색어를 바꾸어 다시 검색해 보세요.", null));
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

  static SearchResultsView error(
      String query, String error, Runnable onRetry, Consumer<SearchResult> onOpen) {
    SearchResultsView view = new SearchResultsView(query, onOpen);
    view.results
        .getChildren()
        .setAll(
            view.heading("검색 결과"), view.state("검색하지 못했어요", error, view.action("다시 시도", onRetry)));
    view.preview.setCenter(view.state("검색에 실패했어요", error, view.action("다시 시도", onRetry)));
    return view;
  }

  /** The no-internet screen from the design: one message, one retry, and a matching source pane. */
  static SearchResultsView offline(String query, Runnable onRetry, Consumer<SearchResult> onOpen) {
    SearchResultsView view = new SearchResultsView(query, onOpen);
    view.results
        .getChildren()
        .setAll(
            view.heading("오프라인"),
            view.state(
                "인터넷에 연결되어 있지 않아요", "연결을 확인한 뒤 다시 시도해 주세요.", view.action("다시 시도", onRetry)));
    view.preview.setCenter(view.sourcePlaceholder("인터넷에 연결되지 않았어요", "연결을 확인한 뒤 다시 시도해 주세요."));
    return view;
  }

  static SearchResultsView results(
      String query, List<SearchResult> found, Consumer<SearchResult> onOpen) {
    SearchResultsView view = new SearchResultsView(query, onOpen);
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
      HBox head = new HBox(UiTokens.SPACE_SM + 2, badge, title, gap, openTag);
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
    Label heading = new Label(title);
    heading.getStyleClass().add("search-state-title");
    VBox state = new VBox(10, heading, message(description));
    if (action != null) state.getChildren().add(action);
    state.setAlignment(Pos.CENTER);
    state.setPadding(new Insets(28));
    state.getStyleClass().add("search-state");
    return state;
  }

  private Button action(String title, Runnable action) {
    Button button = new Button(title);
    button.getStyleClass().add("primary-button");
    button.setOnAction(event -> action.run());
    return button;
  }

  /** Sets the remembered panel state and the callbacks for cancelling and retrying the AI answer. */
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
    setLeft(null);
    setRight(null);
    aiPanel.getStyleClass().removeAll("ai-panel-left");
    if (panelOpen && banner == null) {
      if (panelSide == AiPanelPosition.LEFT) {
        aiPanel.getStyleClass().add("ai-panel-left");
        setLeft(aiPanel);
      } else {
        setRight(aiPanel);
      }
    }
    panelToggle.setText(panelOpen ? "AI 패널 닫기" : "AI 패널 열기");
    panelToggle.setAccessibleText(panelToggle.getText());
    panelPosition.setText(panelSide == AiPanelPosition.RIGHT ? "왼쪽으로 이동" : "오른쪽으로 이동");
    panelPosition.setAccessibleText(panelPosition.getText());
    panelPosition.setDisable(!panelOpen);
    refreshLayout();
  }

  /** The card above the result list that explains why there is no summary, as in the design. */
  private VBox banner(AiAnswerChanged event) {
    boolean failed = event.phase() == AiAnswerChanged.Phase.FAILED;
    String title =
        switch (event.phase()) {
          case FAILED -> "요약하지 못했어요";
          case CANCELLED -> "요약을 취소했어요";
          default -> "AI를 연결하면 요약해 드려요";
        };
    String detail =
        event.message().isBlank() ? "검색 결과 목록은 그대로 볼 수 있어요." : event.message();
    Label heading = new Label(title);
    heading.getStyleClass().add("ai-banner-title");
    VBox card = new VBox(UiTokens.SPACE_SM, heading, message(detail));
    if (failed || event.phase() == AiAnswerChanged.Phase.CANCELLED) {
      card.getChildren().add(action("다시 시도", () -> aiPanel.retry()));
    }
    card.getStyleClass().add("ai-banner");
    if (failed) {
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
    aiPanel.show(event);
    boolean focus = event.phase() == AiAnswerChanged.Phase.COMPLETED;
    if (focus != summaryFocus) {
      summaryFocus = focus;
      refreshLayout();
      if (focus) {
        preview.setCenter(sourcePlaceholder());
      } else if (selected != null) {
        select(selected);
      }
    }
  }

  /**
   * With a finished summary the design gives the summary the width and keeps a fixed source pane on
   * the other side; the result list steps aside because the sources are listed in the summary.
   */
  private void refreshLayout() {
    boolean focused = summaryFocus && panelOpen;
    content.setLeft(focused ? null : listScroll);
    aiPanel.prefWidthProperty().unbind();
    if (focused) {
      aiPanel.prefWidthProperty().bind(widthProperty().subtract(UiTokens.SOURCE_PANE_WIDTH));
      if (!aiPanel.getStyleClass().contains("ai-panel-wide")) {
        aiPanel.getStyleClass().add("ai-panel-wide");
      }
    } else {
      aiPanel.prefWidthProperty().bind(widthProperty().multiply(0.3));
      aiPanel.getStyleClass().remove("ai-panel-wide");
    }
  }

  /** The dashed card shown until the user picks a source, as in the design. */
  private VBox sourcePlaceholder() {
    return sourcePlaceholder("출처를 누르면 원문이 열려요", "고른 출처의 페이지가 여기에 보여요.");
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

  /** The source pane from the design: a header with the source, its address and an open button. */
  private void showEvidence(EvidenceDocument document) {
    Label number = new Label("원문 " + document.sourceId().replaceFirst("^S", ""));
    number.getStyleClass().add("source-pane-number");
    Label address = new Label(document.uri().toString());
    address.getStyleClass().add("source-pane-address");
    address.setMaxWidth(Double.MAX_VALUE);
    address.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
    javafx.scene.layout.HBox.setHgrow(address, javafx.scene.layout.Priority.ALWAYS);
    Button open =
        new Button("새 탭에서 열기");
    open.getStyleClass().add("secondary-button");
    open.setAccessibleText("새 탭에서 원문 열기");
    open.setOnAction(
        event ->
            onOpenResult.accept(
                new SearchResult(document.sourceId(), document.uri(), document.title(), "", 1)));
    javafx.scene.layout.HBox header = new javafx.scene.layout.HBox(UiTokens.SPACE_SM, number, address, open);
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
