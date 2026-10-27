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

  private SearchResultsView(String query, Consumer<SearchResult> onOpenResult) {
    this.query = query;
    this.onOpenResult = onOpenResult;
    getStyleClass().add("search-results-view");
    results.getStyleClass().add("search-results-list");
    ScrollPane listScroll = new ScrollPane(results);
    listScroll.setFitToWidth(true);
    listScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    listScroll.getStyleClass().add("search-results-scroll");
    content.setLeft(listScroll);
    preview.getStyleClass().add("search-preview");
    content.setCenter(preview);
    setCenter(content);
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

  static SearchResultsView results(
      String query, List<SearchResult> found, Consumer<SearchResult> onOpen) {
    SearchResultsView view = new SearchResultsView(query, onOpen);
    view.results.getChildren().add(view.heading(found.size() + "개 결과"));
    view.installPanelControls();
    for (SearchResult result : found) {
      Button item = new Button();
      Label title = new Label(result.title());
      title.getStyleClass().add("search-result-title");
      Label url = new Label(result.uri().toString());
      url.getStyleClass().add("search-result-url");
      Label snippet = new Label(result.snippet());
      snippet.setWrapText(true);
      snippet.getStyleClass().add("search-result-snippet");
      VBox content = new VBox(6, title, url, snippet);
      content.getStyleClass().add("search-result-content");
      item.setGraphic(content);
      item.setMaxWidth(Double.MAX_VALUE);
      item.setAccessibleText(result.title() + ", " + result.uri());
      item.getStyleClass().add("search-result-item");
      item.setOnAction(event -> view.select(result));
      view.results.getChildren().add(item);
    }
    if (!found.isEmpty()) {
      view.select(found.getFirst());
    }
    return view;
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
    if (panelOpen) {
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
  }

  /** Forwards the latest AI answer state to the panel; search results are unaffected. */
  void showAi(AiAnswerChanged event) {
    aiPanel.show(event);
  }

  private void showEvidence(EvidenceDocument document) {
    Label title = new Label(document.title());
    title.setWrapText(true);
    title.getStyleClass().add("search-preview-title");
    Label url = new Label(document.uri().toString());
    url.setWrapText(true);
    url.getStyleClass().add("search-result-url");
    Label excerpt = new Label(document.text());
    excerpt.setWrapText(true);
    excerpt.getStyleClass().add("search-result-snippet");
    ScrollPane excerptScroll = new ScrollPane(excerpt);
    excerptScroll.setFitToWidth(true);
    excerptScroll.getStyleClass().add("search-results-scroll");
    Button open =
        action(
            "원문 페이지 열기",
            () ->
                onOpenResult.accept(
                    new SearchResult(
                        document.sourceId(), document.uri(), document.title(), "", 1)));
    VBox details = new VBox(14, title, url, excerptScroll, open);
    details.setPadding(new Insets(40));
    details.getStyleClass().add("search-preview-content");
    preview.setCenter(details);
  }

  private void select(SearchResult result) {
    selected = result;
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
