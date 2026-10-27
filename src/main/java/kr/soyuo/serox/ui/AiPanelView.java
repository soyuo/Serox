package kr.soyuo.serox.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import kr.soyuo.serox.ai.api.EvidenceDocument;
import kr.soyuo.serox.ai.citation.Citation;
import kr.soyuo.serox.ai.citation.CitedAnswer;
import kr.soyuo.serox.core.event.AiAnswerChanged;

/** Shows the AI summary, key points and verified sources for one search. */
final class AiPanelView extends VBox {
  private final Consumer<EvidenceDocument> onSource;
  private final VBox body = new VBox(UiTokens.SPACE_MD);

  AiPanelView(Consumer<EvidenceDocument> onSource) {
    this.onSource = onSource;
    getStyleClass().add("ai-panel");
    setAccessibleText("AI 요약 패널");
    Label title = new Label("AI 요약");
    title.getStyleClass().add("ai-panel-title");
    body.getStyleClass().add("ai-panel-body");
    ScrollPane scroll = new ScrollPane(body);
    scroll.setFitToWidth(true);
    scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    scroll.getStyleClass().add("ai-panel-scroll");
    VBox.setVgrow(scroll, javafx.scene.layout.Priority.ALWAYS);
    getChildren().addAll(title, scroll);
  }

  /** Renders the latest state of the AI answer. */
  void show(AiAnswerChanged event) {
    body.getChildren().clear();
    switch (event.phase()) {
      case PREPARING -> body.getChildren().add(note("검색 결과 페이지를 읽고 있어요…"));
      case STREAMING -> {
        body.getChildren().add(note("답변을 작성하고 있어요…"));
        body.getChildren().add(paragraph(event.text()));
      }
      case COMPLETED -> showAnswer(event.answer().orElseThrow(), event.evidence());
      case UNAVAILABLE -> body.getChildren().add(note(event.message()));
      case FAILED -> body.getChildren().add(errorNote(event.message()));
      case CANCELLED -> body.getChildren().add(note("AI 요약을 취소했어요."));
    }
  }

  private void showAnswer(CitedAnswer answer, List<EvidenceDocument> evidence) {
    List<String> summary = new ArrayList<>();
    List<String> points = new ArrayList<>();
    for (String line : answer.text().split("\\R")) {
      String trimmed = line.strip();
      if (trimmed.isEmpty()) {
        continue;
      }
      if (trimmed.matches("^([-•*]|\\d+[.)])\\s+.*")) {
        points.add(trimmed.replaceFirst("^([-•*]|\\d+[.)])\\s+", ""));
      } else {
        summary.add(trimmed);
      }
    }
    if (!summary.isEmpty()) {
      body.getChildren().addAll(heading("요약"), paragraph(String.join(" ", summary)));
    }
    if (!points.isEmpty()) {
      body.getChildren().add(heading("핵심 항목"));
      for (String point : points) {
        body.getChildren().add(paragraph("• " + point));
      }
    }
    body.getChildren().add(heading("출처"));
    if (answer.citations().isEmpty()) {
      body.getChildren().add(note("확인된 출처가 없어 이 답변은 근거를 검증하지 못했어요."));
    }
    for (Citation citation : answer.citations()) {
      evidence.stream()
          .filter(document -> document.sourceId().equals(citation.sourceId()))
          .findFirst()
          .ifPresent(document -> body.getChildren().add(sourceButton(citation, document)));
    }
    if (!answer.invalidSourceIds().isEmpty()) {
      body.getChildren().add(note("존재하지 않는 출처 표시는 제외했어요."));
    }
  }

  private Button sourceButton(Citation citation, EvidenceDocument document) {
    Label name = new Label("[" + citation.sourceId() + "] " + citation.title());
    name.setWrapText(true);
    name.getStyleClass().add("search-result-title");
    Label url = new Label(citation.uri().toString());
    url.getStyleClass().add("search-result-url");
    VBox content = new VBox(UiTokens.SPACE_XS, name, url);
    Button button = new Button();
    button.setGraphic(content);
    button.setMaxWidth(Double.MAX_VALUE);
    button.setAccessibleText("출처 " + citation.sourceId() + ", " + citation.title());
    button.getStyleClass().add("search-result-item");
    button.setOnAction(event -> onSource.accept(document));
    return button;
  }

  private Label heading(String text) {
    Label label = new Label(text);
    label.getStyleClass().add("search-section-heading");
    return label;
  }

  private Label paragraph(String text) {
    Label label = new Label(text);
    label.setWrapText(true);
    label.setPadding(new Insets(0));
    label.getStyleClass().add("ai-panel-text");
    return label;
  }

  private Label note(String text) {
    Label label = new Label(text);
    label.setWrapText(true);
    label.getStyleClass().add("search-state-message");
    return label;
  }

  private Label errorNote(String text) {
    Label label = note(text);
    label.getStyleClass().add("ai-panel-error");
    return label;
  }
}
