package kr.soyuo.serox.ui;

import java.util.List;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import kr.soyuo.serox.core.model.BrowserSettings;

/**
 * The settings screen from the design: a section sidebar, the selected page and, on the design
 * page, a live preview. Only appearance is editable; the other pages show the real state.
 */
final class SettingsView extends HBox {
  /** What the other pages report: the current status line of each connected service. */
  record ServiceSummary(String search, String ai, String mcp) {
    static ServiceSummary unknown() {
      return new ServiceSummary("확인 중", "확인 중", "확인 중");
    }
  }

  private enum Section {
    GENERAL("일반"),
    SEARCH("검색"),
    AI("AI 제공자"),
    SUMMARY("요약"),
    DESIGN("디자인"),
    PRIVACY("기록과 개인정보"),
    MCP("MCP 서버"),
    SHORTCUTS("단축키");

    private final String label;

    Section(String label) {
      this.label = label;
    }
  }

  private static final double PREVIEW_MIN_WIDTH = 1100;

  private final AppearancePage appearance;
  private final PreviewCard preview;
  private final ServiceSummary summary;
  private final StackPane page = new StackPane();

  SettingsView(
      BrowserSettings saved,
      Consumer<BrowserSettings> onPreview,
      Consumer<BrowserSettings> onApply,
      Runnable onCancel) {
    this(saved, onPreview, onApply, onCancel, ServiceSummary.unknown());
  }

  SettingsView(
      BrowserSettings saved,
      Consumer<BrowserSettings> onPreview,
      Consumer<BrowserSettings> onApply,
      Runnable onCancel,
      ServiceSummary summary) {
    this.summary = summary;
    this.preview = new PreviewCard(saved);
    this.appearance =
        new AppearancePage(
            saved,
            draft -> {
              preview.show(draft);
              onPreview.accept(draft);
            },
            onApply,
            onCancel);
    getStyleClass().add("settings-view-root");
    setAccessibleText("설정");
    VBox sidebar = new VBox(UiTokens.SPACE_SM);
    sidebar.getStyleClass().add("settings-sidebar");
    ToggleGroup group = new ToggleGroup();
    for (Section section : Section.values()) {
      ToggleButton item = new ToggleButton(section.label);
      item.getStyleClass().add("settings-nav");
      item.setToggleGroup(group);
      item.setMaxWidth(Double.MAX_VALUE);
      item.setAccessibleText(section.label);
      item.setOnAction(
          event -> {
            if (item.isSelected()) {
              open(section);
            } else {
              item.setSelected(true);
            }
          });
      if (section == Section.DESIGN) {
        item.setSelected(true);
      }
      sidebar.getChildren().add(item);
    }
    HBox.setHgrow(page, Priority.ALWAYS);
    getChildren().addAll(sidebar, page);
    open(Section.DESIGN);
  }

  private void open(Section section) {
    if (section == Section.DESIGN) {
      HBox design = new HBox(UiTokens.SPACE_LG * 3, appearance, preview);
      HBox.setHgrow(appearance, Priority.ALWAYS);
      preview.visibleProperty().bind(widthProperty().greaterThan(PREVIEW_MIN_WIDTH));
      preview.managedProperty().bind(preview.visibleProperty());
      page.getChildren().setAll(design);
    } else {
      page.getChildren().setAll(infoPage(section));
    }
  }

  private Node infoPage(Section section) {
    VBox content = new VBox(UiTokens.SPACE_LG);
    content.getStyleClass().add("settings-info");
    content.setPadding(new Insets(32, 48, 32, 48));
    content.setMaxWidth(UiTokens.HOME_CONTENT_WIDTH);
    Label title = new Label(section.label);
    title.getStyleClass().add("settings-page-title");
    content.getChildren().add(title);
    for (String line : lines(section)) {
      Label label = new Label(line);
      label.setWrapText(true);
      label.getStyleClass().add("search-state-message");
      content.getChildren().add(label);
    }
    ScrollPane scroll = new ScrollPane(content);
    scroll.setFitToWidth(true);
    scroll.getStyleClass().add("settings-scroll");
    return scroll;
  }

  private List<String> lines(Section section) {
    return switch (section) {
      case GENERAL ->
          List.of(
              "Serox는 별도의 일반 설정이 없어요.",
              "모양은 ‘디자인’에서, 키와 연결은 아래 각 항목에서 확인할 수 있어요.");
      case SEARCH ->
          List.of(
              "상태: " + summary.search(),
              "검색은 Brave Search를 사용해요. 키는 환경 변수 SEROX_BRAVE_SEARCH_API_KEY로만 읽고 저장하지 않아요.",
              "키가 없어도 주소 입력과 탭 탐색은 그대로 쓸 수 있어요.");
      case AI ->
          List.of(
              "상태: " + summary.ai(),
              "AI 요약은 Claude를 사용해요. 키는 환경 변수 SEROX_ANTHROPIC_API_KEY로만 읽고 저장하지 않아요.",
              "AI 제공자에 문제가 생겨도 검색 결과와 탐색은 막히지 않아요.");
      case SUMMARY ->
          List.of(
              "검색 결과 페이지의 본문을 읽고 출처와 함께 요약해요.",
              "AI로 보내기 전에 이메일, 토큰, 카드번호 같은 민감한 정보는 가려요.",
              "AI 요약에는 오류가 있을 수 있어요. 중요한 내용은 출처에서 확인하세요.");
      case PRIVACY ->
          List.of(
              "방문 기록은 90일 동안, 최대 5,000개까지 이 컴퓨터에만 저장해요.",
              "북마크, 최근 검색, 열려 있던 탭도 이 컴퓨터의 데이터 폴더에만 저장해요.",
              "데이터 폴더: %LOCALAPPDATA%\\Serox (지우려면 앱을 종료한 뒤 폴더를 삭제하세요)");
      case MCP ->
          List.of(
              "상태: " + summary.mcp(),
              "다른 AI 에이전트가 search_and_summarize 도구로 Serox를 호출할 수 있어요.",
              "사용하려면 에이전트 설정에 SEROX_MCP_APPROVED_TOOLS=search_and_summarize를 적고"
                  + " Serox를 --mcp 또는 --mcp-headless로 실행하세요.",
              "허용하지 않은 도구와 브라우저 제어, 파일 접근은 지원하지 않아요.");
      case SHORTCUTS ->
          List.of("Ctrl+L  주소창으로 이동", "Ctrl+T  새 탭", "Ctrl+W  탭 닫기", "Ctrl+K  검색창으로 이동");
      case DESIGN -> List.of();
    };
  }

  /** The current, unsaved draft. */
  BrowserSettings draft() {
    return appearance.draft();
  }

  /** The small mock of the result screen that reacts to the accent color. */
  private static final class PreviewCard extends VBox {
    private final Label summaryBox = new Label("AI 요약");
    private final Label chip = new Label("출처 1");
    private final VBox summaryCard = new VBox(UiTokens.SPACE_XS);

    PreviewCard(BrowserSettings initial) {
      super(UiTokens.SPACE_SM);
      getStyleClass().add("settings-preview");
      setMinWidth(0);
      setPrefWidth(420);
      setMaxWidth(420);
      Label heading = new Label("미리보기");
      heading.getStyleClass().add("search-section-heading");
      summaryBox.getStyleClass().add("preview-summary-label");
      Label text = new Label("가상 스레드는 OS 스레드보다 훨씬 가볍다.");
      text.setWrapText(true);
      text.getStyleClass().add("preview-summary-text");
      summaryCard.getStyleClass().add("preview-summary-card");
      summaryCard.getChildren().addAll(summaryBox, text);
      chip.getStyleClass().add("preview-chip");
      VBox left =
          new VBox(UiTokens.SPACE_SM, summaryCard, line(176), line(106), chip);
      left.setPadding(new Insets(12));
      left.getStyleClass().add("preview-left");
      VBox right = new VBox(UiTokens.SPACE_SM, line(-1), line(-1), line(116));
      right.setPadding(new Insets(12));
      right.getStyleClass().add("preview-right");
      HBox.setHgrow(right, Priority.ALWAYS);
      Region topBar = new Region();
      topBar.getStyleClass().add("preview-topbar");
      HBox body = new HBox(left, right);
      left.setPrefWidth(200);
      VBox frame = new VBox(topBar, body);
      frame.getStyleClass().add("preview-frame");
      VBox.setVgrow(body, Priority.ALWAYS);
      getChildren().addAll(heading, frame);
      show(initial);
    }

    private static Region line(double width) {
      Region line = new Region();
      line.getStyleClass().add("preview-line");
      if (width > 0) {
        line.setMaxWidth(width);
      }
      return line;
    }

    /** Tints the mock with the draft's accent color. */
    void show(BrowserSettings settings) {
      String accent = settings.accentColor();
      chip.setStyle("-fx-background-color: " + accent + ";");
      summaryCard.setStyle(
          "-fx-background-color: " + accent + "1F;");
    }
  }
}
