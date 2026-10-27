package kr.soyuo.serox.ui;

import java.net.URI;
import java.util.List;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import kr.soyuo.serox.core.application.LibraryService;
import kr.soyuo.serox.core.model.Bookmark;
import kr.soyuo.serox.core.model.HistoryEntry;

/** Lists stored visit history and bookmarks and opens an entry in the active tab. */
final class LibraryView extends VBox {
  /** Which list is shown. */
  enum Section {
    HISTORY,
    BOOKMARKS
  }

  private final LibraryService library;
  private final Consumer<URI> onOpen;
  private final VBox list = new VBox();
  private Section section;

  LibraryView(LibraryService library, Section section, Consumer<URI> onOpen, Runnable onClose) {
    this.library = library;
    this.section = section;
    this.onOpen = onOpen;
    getStyleClass().add("library-view");
    setAccessibleText("방문 기록과 북마크");
    setSpacing(UiTokens.SPACE_MD);
    setPadding(new Insets(28, 40, 28, 40));
    Button history = tab("방문 기록", Section.HISTORY);
    Button bookmarks = tab("북마크", Section.BOOKMARKS);
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    Button close = new Button("닫기");
    close.getStyleClass().add("secondary-button");
    close.setOnAction(event -> onClose.run());
    HBox header = new HBox(UiTokens.SPACE_SM, history, bookmarks, spacer, close);
    list.getStyleClass().add("search-results-list");
    ScrollPane scroll = new ScrollPane(list);
    scroll.setFitToWidth(true);
    scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    scroll.getStyleClass().add("search-results-scroll");
    scroll.setMaxWidth(Double.MAX_VALUE);
    scroll.setPrefWidth(UiTokens.HOME_CONTENT_WIDTH);
    VBox.setVgrow(scroll, Priority.ALWAYS);
    getChildren().addAll(header, scroll);
    reload();
  }

  private Button tab(String text, Section target) {
    Button button = new Button(text);
    button.getStyleClass().add("secondary-button");
    button.setAccessibleText(text);
    button.setOnAction(
        event -> {
          section = target;
          reload();
        });
    return button;
  }

  private void reload() {
    list.getChildren().setAll(note("불러오는 중…"));
    if (!library.available()) {
      list.getChildren().setAll(note("저장소를 사용할 수 없어 기록과 북마크를 표시할 수 없어요."));
      return;
    }
    if (section == Section.HISTORY) {
      library
          .recentHistory()
          .thenAccept(entries -> FxThreadExecutor.execute(() -> showHistory(entries)));
    } else {
      library
          .bookmarks()
          .thenAccept(entries -> FxThreadExecutor.execute(() -> showBookmarks(entries)));
    }
  }

  private void showHistory(List<HistoryEntry> entries) {
    if (section != Section.HISTORY) {
      return;
    }
    list.getChildren().clear();
    if (entries.isEmpty()) {
      list.getChildren().add(note("아직 방문 기록이 없어요."));
    }
    for (HistoryEntry entry : entries) {
      list.getChildren().add(row(entry.title(), entry.uri(), null));
    }
  }

  private void showBookmarks(List<Bookmark> entries) {
    if (section != Section.BOOKMARKS) {
      return;
    }
    list.getChildren().clear();
    if (entries.isEmpty()) {
      list.getChildren().add(note("북마크가 없어요. 주소 표시줄의 ★로 현재 페이지를 추가하세요."));
    }
    for (Bookmark entry : entries) {
      Button remove = new Button("삭제");
      remove.getStyleClass().add("secondary-button");
      remove.setAccessibleText("북마크 삭제: " + entry.title());
      remove.setOnAction(
          event -> library.removeBookmark(entry.id()).thenRun(() -> FxThreadExecutor.execute(this::reload)));
      list.getChildren().add(row(entry.title(), entry.uri(), remove));
    }
  }

  private HBox row(String title, URI uri, Button extra) {
    Label name = new Label(title.isBlank() ? uri.toString() : title);
    name.getStyleClass().add("search-result-title");
    name.setTextOverrun(OverrunStyle.ELLIPSIS);
    name.setMaxWidth(Double.MAX_VALUE);
    Label address = new Label(uri.toString());
    address.getStyleClass().add("search-result-url");
    address.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);
    address.setMaxWidth(Double.MAX_VALUE);
    VBox text = new VBox(UiTokens.SPACE_XS, name, address);
    Button open = new Button();
    open.setGraphic(text);
    open.setMaxWidth(Double.MAX_VALUE);
    open.setAccessibleText((title.isBlank() ? uri.toString() : title) + ", " + uri);
    open.getStyleClass().add("search-result-item");
    open.setOnAction(event -> onOpen.accept(uri));
    HBox.setHgrow(open, Priority.ALWAYS);
    HBox row = new HBox(UiTokens.SPACE_SM, open);
    if (extra != null) {
      row.getChildren().add(extra);
    }
    row.setFillHeight(true);
    return row;
  }

  private Label note(String text) {
    Label label = new Label(text);
    label.setWrapText(true);
    label.getStyleClass().add("search-state-message");
    return label;
  }
}
