package kr.soyuo.serox.ui;

import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Figma-inspired new-tab search and connection overview. */
final class HomeView extends VBox {
  private final TextField searchField = new TextField();

  HomeView(UiPreviewData previewData, Consumer<String> onSearch) {
    getStyleClass().add("home-view");
    setAlignment(Pos.TOP_CENTER);

    HBox brand =
        new HBox(UiTokens.SPACE_MD, SvgIconView.logo(UiTokens.HOME_LOGO_SIZE), new Label("Serox"));
    brand.setAlignment(Pos.CENTER);
    brand.getStyleClass().add("brand-lockup");
    brand.getChildren().get(1).getStyleClass().add("brand-title");

    Label tagline = new Label("검색하면 AI가 읽고 정리해 드려요. 출처와 함께요.");
    tagline.getStyleClass().add("tagline");

    searchField.setPromptText("궁금한 것을 검색해 보세요…");
    searchField.setAccessibleText("Serox 검색어, Ctrl+K로 이동");
    searchField.getStyleClass().add("home-search-field");
    HBox searchInput =
        new HBox(
            UiTokens.SPACE_MD,
            SvgIconView.icon(UiTokens.ICON_HOME_SEARCH, UiTokens.TOOLBAR_ICON_SIZE),
            searchField);
    searchInput.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(searchField, Priority.ALWAYS);
    searchInput.getStyleClass().add("home-search-input");

    Button searchButton = new Button("검색");
    searchButton.setAccessibleText("검색 실행, Ctrl+K로 검색어 입력");
    searchButton.getStyleClass().add("primary-button");
    searchButton.setDefaultButton(true);
    searchButton.setOnAction(event -> onSearch.accept(searchField.getText()));
    searchField.setOnAction(event -> onSearch.accept(searchField.getText()));
    HBox search = new HBox(UiTokens.SPACE_SM, searchInput, searchButton);
    search.setAlignment(Pos.CENTER);
    search.setMaxWidth(Double.MAX_VALUE);
    HBox.setHgrow(searchInput, Priority.ALWAYS);
    search.getStyleClass().add("home-search");

    HBox cards =
        new HBox(
            UiTokens.HOME_CARD_GAP,
            createRecentSearchCard(previewData.recentSearches()),
            createConnectionCard(previewData));
    cards.setAlignment(Pos.TOP_CENTER);
    cards.getStyleClass().add("home-cards");

    VBox content = new VBox(0, brand, tagline, search, cards);
    content.setAlignment(Pos.TOP_CENTER);
    content.setMaxWidth(UiTokens.HOME_CONTENT_WIDTH);
    content.setPadding(new Insets(106, 0, 40, 0));
    content.getStyleClass().add("home-content");
    heightProperty()
        .addListener(
            (observable, oldHeight, newHeight) -> {
              double topPadding = Math.max(24, Math.min(106, newHeight.doubleValue() - 405));
              content.setPadding(new Insets(topPadding, 0, 32, 0));
            });
    getChildren().add(content);
  }

  private VBox createRecentSearchCard(java.util.List<String> recentSearches) {
    Label heading = new Label("최근 검색");
    heading.getStyleClass().add("card-heading");
    VBox entries = new VBox();
    recentSearches.stream().map(this::recentSearch).forEach(entries.getChildren()::add);
    if (recentSearches.isEmpty()) {
      Label emptyState = new Label("최근 검색이 없습니다.");
      emptyState.setAccessibleText("검색 기록이 없습니다.");
      emptyState.getStyleClass().add("empty-state");
      entries.getChildren().add(emptyState);
    }
    entries.getStyleClass().add("recent-searches");
    VBox card = new VBox(UiTokens.SPACE_SM, heading, entries);
    card.setAccessibleRole(AccessibleRole.PARENT);
    card.setAccessibleText("최근 검색 패널");
    card.setPrefWidth(UiTokens.HOME_CARD_WIDTH);
    card.setPrefHeight(UiTokens.HOME_CARD_HEIGHT);
    card.setMaxHeight(UiTokens.HOME_CARD_HEIGHT);
    card.getStyleClass().add("info-card");
    HBox.setHgrow(card, Priority.ALWAYS);
    return card;
  }

  private Label recentSearch(String query) {
    Label item = new Label(query);
    item.getStyleClass().add("recent-search-item");
    return item;
  }

  private VBox createConnectionCard(UiPreviewData previewData) {
    Label heading = new Label("연결 상태");
    heading.getStyleClass().add("card-heading");
    Label aiStatus = serviceStatus(previewData.ai());
    Label mcpStatus = serviceStatus(previewData.mcp());
    Label note = new Label(previewData.mcpDescription());
    note.setWrapText(true);
    note.getStyleClass().add("card-note");
    VBox card = new VBox(UiTokens.SPACE_SM, heading, aiStatus, mcpStatus, note);
    card.setAccessibleRole(AccessibleRole.PARENT);
    card.setAccessibleText("AI 및 MCP 연결 상태 패널");
    card.setPrefWidth(UiTokens.HOME_CARD_WIDTH);
    card.setPrefHeight(UiTokens.HOME_CARD_HEIGHT);
    card.setMaxHeight(UiTokens.HOME_CARD_HEIGHT);
    card.getStyleClass().add("info-card");
    HBox.setHgrow(card, Priority.ALWAYS);
    return card;
  }

  private Label serviceStatus(UiPreviewData.ServicePreview service) {
    Label status = new Label(service.statusText());
    Region dot = new Region();
    dot.getStyleClass().add(service.enabled() ? "connection-dot-on" : "connection-dot-off");
    status.setGraphic(dot);
    status.getStyleClass().add("connection-status");
    status.setAccessibleText(service.name() + " 상태: " + (service.enabled() ? "켜짐" : "꺼짐"));
    return status;
  }

  void focusSearchField() {
    searchField.requestFocus();
    searchField.selectAll();
  }
}
