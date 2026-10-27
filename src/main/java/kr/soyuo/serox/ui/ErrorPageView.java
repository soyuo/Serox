package kr.soyuo.serox.ui;

import java.util.List;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import kr.soyuo.serox.core.event.NavigationFailed;

/** The full-page error shown when an address cannot be opened, as in the design. */
final class ErrorPageView extends StackPane {
  /** What each button does; the view only decides how they look. */
  record Actions(Runnable retry, Runnable searchInstead, Runnable retype, Runnable newTab) {}

  private ErrorPageView(
      String mark, String title, String detail, List<String> tips, Button first, Button second) {
    getStyleClass().add("error-page");
    Label icon = new Label(mark);
    icon.getStyleClass().add("error-page-icon");
    icon.setAlignment(Pos.CENTER);
    Label heading = new Label(title);
    heading.getStyleClass().add("error-page-title");
    Label explanation = new Label(detail);
    explanation.setWrapText(true);
    explanation.getStyleClass().add("search-state-message");
    explanation.setAlignment(Pos.CENTER);
    Label tipsTitle = new Label("해 볼 수 있는 방법");
    tipsTitle.getStyleClass().add("error-page-tips-title");
    VBox tipBox = new VBox(UiTokens.SPACE_SM, tipsTitle);
    for (String tip : tips) {
      Label line = new Label("· " + tip);
      line.setWrapText(true);
      line.getStyleClass().add("search-state-message");
      tipBox.getChildren().add(line);
    }
    tipBox.getStyleClass().add("error-page-tips");
    tipBox.setMaxWidth(UiTokens.ERROR_PAGE_WIDTH);
    HBox buttons = new HBox(UiTokens.SPACE_SM, first, second);
    buttons.setAlignment(Pos.CENTER);
    VBox column = new VBox(UiTokens.SPACE_LG, icon, heading, explanation, tipBox, buttons);
    column.setAlignment(Pos.CENTER);
    column.setMaxWidth(UiTokens.ERROR_PAGE_WIDTH);
    column.setPadding(new Insets(UiTokens.SPACE_LG * 2));
    getChildren().add(column);
    setAccessibleText(title);
  }

  /** Builds the page for one failure; {@code address} is what the user typed or followed. */
  static ErrorPageView of(NavigationFailed.Reason reason, String address, Actions actions) {
    return switch (reason) {
      case INVALID_ADDRESS ->
          new ErrorPageView(
              "!",
              "주소 형식이 올바르지 않아요",
              "“" + address + "”은(는) 올바른 웹 주소가 아니에요.",
              List.of(
                  "주소가 http:// 또는 https:// 로 시작하는지 확인해 보세요.",
                  "공백이나 잘못 찍힌 점(.)이 없는지 확인해 보세요.",
                  "웹 주소가 아니라 검색어라면, 검색해서 AI 요약을 받아 보세요."),
              primary("검색어로 검색하기", actions.searchInstead()),
              secondary("주소 다시 입력", actions.retype()));
      case NOT_FOUND ->
          new ErrorPageView(
              "?",
              "사이트를 찾을 수 없어요",
              host(address) + " 주소를 찾지 못했어요.",
              List.of(
                  "주소의 철자가 맞는지 확인해 보세요.",
                  "주소가 바뀌었거나 사라진 사이트일 수 있어요.",
                  "주소 대신 검색어로 찾아볼 수도 있어요."),
              primary("다시 시도", actions.retry()),
              secondary("검색어로 검색하기", actions.searchInstead()));
      case TIMEOUT ->
          new ErrorPageView(
              "!",
              "사이트에 연결할 수 없어요",
              host(address) + " 이(가) 응답하지 않아요. (연결 시간 초과)",
              List.of(
                  "인터넷 연결이 되어 있는지 확인해 보세요.",
                  "잠시 뒤에 다시 시도해 보세요.",
                  "방화벽이나 프록시가 이 사이트를 막고 있지 않은지 확인해 보세요."),
              primary("다시 시도", actions.retry()),
              secondary("새 탭으로 가기", actions.newTab()));
      case UNREACHABLE ->
          new ErrorPageView(
              "!",
              "사이트에 연결할 수 없어요",
              host(address) + " 에 연결하지 못했어요.",
              List.of(
                  "인터넷 연결이 되어 있는지 확인해 보세요.",
                  "주소가 맞는지 확인해 보세요.",
                  "잠시 뒤에 다시 시도해 보세요."),
              primary("다시 시도", actions.retry()),
              secondary("새 탭으로 가기", actions.newTab()));
    };
  }

  private static String host(String address) {
    try {
      String host = java.net.URI.create(address).getHost();
      return host == null ? address : host;
    } catch (IllegalArgumentException invalid) {
      return address;
    }
  }

  private static Button primary(String text, Runnable action) {
    Button button = new Button(text);
    button.getStyleClass().add("primary-button");
    button.setAccessibleText(text);
    button.setOnAction(event -> action.run());
    return button;
  }

  private static Button secondary(String text, Runnable action) {
    Button button = new Button(text);
    button.getStyleClass().add("secondary-button");
    button.setAccessibleText(text);
    button.setOnAction(event -> action.run());
    return button;
  }
}
