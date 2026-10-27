package kr.soyuo.serox.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import kr.soyuo.serox.ai.api.EvidenceDocument;
import kr.soyuo.serox.ai.citation.Citation;
import kr.soyuo.serox.ai.citation.CitedAnswer;
import kr.soyuo.serox.core.event.AiAnswerChanged;

/** Shows the AI summary, key points and verified sources for one search. */
final class AiPanelView extends VBox {
  private static final Pattern CITATION_MARKER = Pattern.compile("\\[S(\\d+)]");
  private static final String DISCLAIMER = "AI 요약에는 오류가 있을 수 있어요. 중요한 내용은 출처에서 확인하세요.";

  private final Consumer<EvidenceDocument> onSource;
  private final VBox body = new VBox(UiTokens.SPACE_LG);
  private Runnable onCancel = () -> {};
  private Runnable onRetry = () -> {};

  AiPanelView(Consumer<EvidenceDocument> onSource) {
    this.onSource = onSource;
    getStyleClass().add("ai-panel");
    setAccessibleText("AI 요약 패널");
    body.getStyleClass().add("ai-panel-body");
    ScrollPane scroll = new ScrollPane(body);
    scroll.setFitToWidth(true);
    scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    scroll.getStyleClass().add("ai-panel-scroll");
    VBox.setVgrow(scroll, Priority.ALWAYS);
    getChildren().add(scroll);
  }

  /** Runs the retry action, for the banner that replaces the panel in failure states. */
  void retry() {
    onRetry.run();
  }

  void setActions(Runnable onCancel, Runnable onRetry) {
    this.onCancel = onCancel;
    this.onRetry = onRetry;
  }

  private Button actionButton(String text, Runnable action) {
    Button button = new Button(text);
    button.getStyleClass().add("secondary-button");
    button.setAccessibleText(text);
    button.setOnAction(event -> action.run());
    return button;
  }

  /** Renders the latest state of the AI answer. */
  void show(AiAnswerChanged event) {
    body.getChildren().clear();
    switch (event.phase()) {
      case PREPARING ->
          body.getChildren()
              .add(
                  summaryCard(
                      note("검색 결과 페이지를 읽고 있어요…"), actionButton("취소", () -> onCancel.run())));
      case STREAMING ->
          body.getChildren()
              .add(
                  summaryCard(
                      note("답변을 작성하고 있어요…"),
                      paragraph(event.text()),
                      actionButton("취소", () -> onCancel.run())));
      case COMPLETED -> showAnswer(event.answer().orElseThrow(), event.evidence());
      case UNAVAILABLE -> body.getChildren().add(summaryCard(note(event.message())));
      case FAILED ->
          body.getChildren()
              .add(
                  summaryCard(
                      errorNote(event.message()), actionButton("다시 시도", () -> onRetry.run())));
      case CANCELLED ->
          body.getChildren()
              .add(
                  summaryCard(
                      note("AI 요약을 취소했어요."), actionButton("다시 시도", () -> onRetry.run())));
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
    body.getChildren().add(readCount(evidence.size()));
    if (!summary.isEmpty()) {
      body.getChildren().add(summaryCard(citedParagraph(String.join(" ", summary))));
    } else {
      body.getChildren().add(summaryCard());
    }
    if (!points.isEmpty()) {
      VBox rows = new VBox(UiTokens.SPACE_MD, heading("핵심 정리"));
      for (String point : points) {
        rows.getChildren().add(citedParagraph(point));
      }
      body.getChildren().add(rows);
    }
    VBox sources = new VBox(UiTokens.SPACE_SM);
    sources.getChildren().add(heading("출처 " + answer.citations().size() + "곳, 누르면 원문을 볼 수 있어요"));
    if (answer.citations().isEmpty()) {
      sources.getChildren().add(note("확인된 출처가 없어 이 답변은 근거를 검증하지 못했어요."));
    }
    for (Citation citation : answer.citations()) {
      evidence.stream()
          .filter(document -> document.sourceId().equals(citation.sourceId()))
          .findFirst()
          .ifPresent(document -> sources.getChildren().add(sourceButton(citation, document)));
    }
    if (!answer.invalidSourceIds().isEmpty()) {
      sources.getChildren().add(note("존재하지 않는 출처 표시는 제외했어요."));
    }
    sources.getChildren().add(footnote(DISCLAIMER));
    body.getChildren().add(sources);
  }

  /** The green-dot line above the summary: how many result pages the answer is based on. */
  private HBox readCount(int pages) {
    Region dot = new Region();
    dot.getStyleClass().addAll("status-dot", "status-dot-connected");
    Label text = new Label("검색 결과 " + pages + "곳을 읽고 정리했어요");
    text.getStyleClass().add("search-state-message");
    HBox line = new HBox(UiTokens.SPACE_SM, dot, text);
    line.setAlignment(Pos.CENTER_LEFT);
    return line;
  }

  /** The tinted card with the sparkle label that holds the summary text. */
  private VBox summaryCard(javafx.scene.Node... content) {
    HBox label =
        new HBox(
            UiTokens.SPACE_SM,
            SvgIconView.icon(UiTokens.ICON_AI_SPARKLE, UiTokens.AI_SPARKLE_SIZE),
            sectionLabel("AI 요약"));
    label.setAlignment(Pos.CENTER_LEFT);
    VBox card = new VBox(UiTokens.SPACE_SM + 2, label);
    card.getChildren().addAll(content);
    card.getStyleClass().add("ai-summary-card");
    return card;
  }

  private Label sectionLabel(String text) {
    Label label = new Label(text);
    label.getStyleClass().add("ai-summary-label");
    return label;
  }

  /** Text with each [S1] marker drawn as a small numbered chip, as in the design. */
  private TextFlow citedParagraph(String text) {
    TextFlow flow = new TextFlow();
    flow.getStyleClass().add("ai-panel-flow");
    Matcher matcher = CITATION_MARKER.matcher(text);
    int last = 0;
    while (matcher.find()) {
      addText(flow, text.substring(last, matcher.start()));
      Label chip = new Label(matcher.group(1));
      chip.getStyleClass().add("citation-chip");
      chip.setAccessibleText("출처 " + matcher.group(1));
      flow.getChildren().add(chip);
      last = matcher.end();
    }
    addText(flow, text.substring(last));
    return flow;
  }

  private void addText(TextFlow flow, String text) {
    if (!text.isEmpty()) {
      Text run = new Text(text);
      run.getStyleClass().add("ai-panel-run");
      flow.getChildren().add(run);
    }
  }

  private Button sourceButton(Citation citation, EvidenceDocument document) {
    Label badge = new Label(citation.sourceId().replaceFirst("^S", ""));
    badge.getStyleClass().add("source-badge");
    Label name = new Label(citation.title());
    name.setWrapText(true);
    name.getStyleClass().add("source-title");
    Label url = new Label(citation.uri().toString());
    url.getStyleClass().add("source-url");
    VBox text = new VBox(UiTokens.SPACE_XS, name, url);
    HBox.setHgrow(text, Priority.ALWAYS);
    HBox content = new HBox(UiTokens.SPACE_SM + 2, badge, text);
    content.setAlignment(Pos.CENTER_LEFT);
    Button button = new Button();
    button.setGraphic(content);
    button.setMaxWidth(Double.MAX_VALUE);
    button.setAccessibleText("출처 " + citation.sourceId() + ", " + citation.title());
    button.getStyleClass().add("source-card");
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

  private Label footnote(String text) {
    Label label = note(text);
    label.getStyleClass().add("ai-footnote");
    return label;
  }

  private Label errorNote(String text) {
    Label label = note(text);
    label.getStyleClass().add("ai-panel-error");
    return label;
  }
}
