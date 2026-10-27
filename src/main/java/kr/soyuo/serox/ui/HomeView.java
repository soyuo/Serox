package kr.soyuo.serox.ui;

import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Figma-inspired new-tab search and connection overview. */
final class HomeView extends VBox {
  HomeView(Consumer<String> onSearch) {
    getStyleClass().add("home-view");
    setAlignment(Pos.TOP_CENTER);

    HBox brand = new HBox(14, SvgIconView.logo(48), new Label("Serox"));
    brand.setAlignment(Pos.CENTER);
    brand.getStyleClass().add("brand-lockup");
    brand.getChildren().get(1).getStyleClass().add("brand-title");

    Label tagline = new Label("검색하면 AI가 읽고 정리해 드려요. 출처와 함께요.");
    tagline.getStyleClass().add("tagline");

    TextField searchField = new TextField();
    searchField.setPromptText("궁금한 것을 검색해 보세요…");
    searchField.setAccessibleText("Serox 검색어");
    searchField.getStyleClass().add("home-search-field");
    HBox searchInput = new HBox(12, SvgIconView.icon("eff36.svg", 20), searchField);
    searchInput.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(searchField, Priority.ALWAYS);
    searchInput.getStyleClass().add("home-search-input");

    Button searchButton = new Button("검색");
    searchButton.setAccessibleText("검색 실행");
    searchButton.getStyleClass().add("primary-button");
    searchButton.setDefaultButton(true);
    searchButton.setOnAction(event -> onSearch.accept(searchField.getText()));
    searchField.setOnAction(event -> onSearch.accept(searchField.getText()));
    HBox search = new HBox(10, searchInput, searchButton);
    search.setAlignment(Pos.CENTER);
    search.setMaxWidth(Double.MAX_VALUE);
    HBox.setHgrow(searchInput, Priority.ALWAYS);
    search.getStyleClass().add("home-search");

    HBox cards = new HBox(16, createRecentSearchCard(), createConnectionCard());
    cards.setAlignment(Pos.TOP_CENTER);
    cards.getStyleClass().add("home-cards");

    VBox content = new VBox(0, brand, tagline, search, cards);
    content.setAlignment(Pos.TOP_CENTER);
    content.setMaxWidth(680);
    content.setPadding(new Insets(106, 0, 40, 0));
    content.getStyleClass().add("home-content");
    getChildren().add(content);
  }

  private VBox createRecentSearchCard() {
    Label heading = new Label("최근 검색");
    heading.getStyleClass().add("card-heading");
    VBox entries =
        new VBox(
            recentSearch("자바 가상 스레드 장점"),
            recentSearch("Maven 의존성 충돌 해결"),
            recentSearch("JavaFX WebView 쿠키 설정"));
    entries.getStyleClass().add("recent-searches");
    VBox card = new VBox(10, heading, entries);
    card.getStyleClass().add("info-card");
    HBox.setHgrow(card, Priority.ALWAYS);
    return card;
  }

  private Label recentSearch(String query) {
    Label item = new Label(query);
    item.getStyleClass().add("recent-search-item");
    return item;
  }

  private VBox createConnectionCard() {
    Label heading = new Label("연결 상태");
    heading.getStyleClass().add("card-heading");
    Label aiStatus = new Label("AI 요약 켜짐");
    aiStatus.getStyleClass().add("connection-status");
    Label mcpStatus = new Label("MCP 서버 켜짐");
    mcpStatus.getStyleClass().add("connection-status");
    Label note = new Label("다른 AI 에이전트가 search_and_summarize 도구로 Serox를 호출할 수 있어요.");
    note.setWrapText(true);
    note.getStyleClass().add("card-note");
    VBox card = new VBox(10, heading, aiStatus, mcpStatus, note);
    card.getStyleClass().add("info-card");
    HBox.setHgrow(card, Priority.ALWAYS);
    return card;
  }
}
