package kr.soyuo.serox.ui;

import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

final class HomeView extends VBox {
    HomeView(Consumer<String> onSearch) {
        getStyleClass().add("home-view");
        setAlignment(Pos.TOP_CENTER);
        setPadding(new Insets(88, 24, 40, 24));
        setSpacing(0);

        Label logoMark = new Label("S");
        logoMark.getStyleClass().add("brand-mark-large");

        Label title = new Label("Serox");
        title.getStyleClass().add("brand-title");

        HBox brand = new HBox(14, logoMark, title);
        brand.setAlignment(Pos.CENTER);

        Label tagline = new Label("검색하면 AI가 읽고 정리해 드려요. 출처와 함께요.");
        tagline.getStyleClass().add("tagline");
        tagline.setWrapText(true);

        TextField searchField = new TextField();
        searchField.setPromptText("궁금한 것을 검색해 보세요…");
        searchField.setAccessibleText("Serox 검색");
        searchField.getStyleClass().add("home-search-field");
        HBox.setHgrow(searchField, Priority.ALWAYS);

        Button searchButton = new Button("검색");
        searchButton.getStyleClass().add("primary-button");
        searchButton.setDefaultButton(true);
        searchButton.setOnAction(event -> onSearch.accept(searchField.getText()));
        searchField.setOnAction(event -> onSearch.accept(searchField.getText()));

        HBox searchRow = new HBox(10, searchField, searchButton);
        searchRow.setAlignment(Pos.CENTER);
        searchRow.getStyleClass().add("home-search");

        VBox recentCard = createRecentSearchCard();
        VBox connectionCard = createConnectionCard();
        HBox cards = new HBox(16, recentCard, connectionCard);
        cards.setAlignment(Pos.TOP_CENTER);
        cards.getStyleClass().add("home-cards");

        VBox content = new VBox(18, brand, tagline, searchRow, cards);
        content.setAlignment(Pos.TOP_CENTER);
        content.setMaxWidth(680);
        content.getStyleClass().add("home-content");

        Region topSpacer = new Region();
        VBox.setVgrow(topSpacer, Priority.ALWAYS);
        getChildren().addAll(topSpacer, content);
    }

    private VBox createRecentSearchCard() {
        Label heading = new Label("최근 검색");
        heading.getStyleClass().add("card-heading");

        VBox entries = new VBox(
                recentSearch("자바 가상 스레드 장점"),
                recentSearch("Maven 의존성 충돌 해결"),
                recentSearch("JavaFX WebView 구조"));
        entries.getStyleClass().add("recent-searches");

        VBox card = new VBox(12, heading, entries);
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

        Label aiStatus = new Label("●  AI 요약 연결 전");
        aiStatus.getStyleClass().add("connection-status");
        Label mcpStatus = new Label("●  MCP 서버 연결 전");
        mcpStatus.getStyleClass().add("connection-status");

        Label note = new Label("Search API와 AI, MCP 연결은 각 기능 단계에서 설정합니다.");
        note.setWrapText(true);
        note.getStyleClass().add("card-note");

        VBox card = new VBox(12, heading, aiStatus, mcpStatus, note);
        card.getStyleClass().add("info-card");
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }
}
