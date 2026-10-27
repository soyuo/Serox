package kr.soyuo.serox.browser.render;

import java.util.List;
import java.util.Objects;
import javafx.geometry.Orientation;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollBar;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import kr.soyuo.serox.browser.css.CssStyleEngine;
import kr.soyuo.serox.browser.css.StyleSnapshot;
import kr.soyuo.serox.browser.dom.Document;
import kr.soyuo.serox.browser.layout.LayoutConstraints;
import kr.soyuo.serox.browser.layout.LayoutEngine;

/** Resizable, scrollable JavaFX viewport for the initial page renderer. */
public final class CanvasPageView extends BorderPane {
  private final Canvas canvas = new Canvas();
  private final StackPane viewport = new StackPane(canvas);
  private final ScrollBar verticalScroll = new ScrollBar();
  private final ScrollBar horizontalScroll = new ScrollBar();
  private final Label fallbackNotice = new Label();
  private final CssStyleEngine styleEngine = new CssStyleEngine();
  private final LayoutEngine layoutEngine = new LayoutEngine();
  private final DisplayListBuilder displayListBuilder = new DisplayListBuilder();
  private final JavaFxCanvasRenderer renderer = new JavaFxCanvasRenderer();
  private Document document;
  private StyleSnapshot lastStyles;
  private DisplayList lastDisplayList;
  private double previousWidth = -1;
  private double previousHeight = -1;
  private boolean updatingScrollBars;

  /** Creates a viewport for one parsed document. */
  public CanvasPageView(Document document) {
    this.document = Objects.requireNonNull(document, "document");
    canvas.widthProperty().bind(viewport.widthProperty());
    canvas.heightProperty().bind(viewport.heightProperty());
    viewport.setOnScroll(this::handleScroll);
    verticalScroll.setOrientation(Orientation.VERTICAL);
    horizontalScroll.setOrientation(Orientation.HORIZONTAL);
    verticalScroll
        .valueProperty()
        .addListener((observable, oldValue, newValue) -> renderAtScroll());
    horizontalScroll
        .valueProperty()
        .addListener((observable, oldValue, newValue) -> renderAtScroll());
    fallbackNotice.getStyleClass().add("render-fallback-notice");
    setCenter(viewport);
    setRight(verticalScroll);
    setBottom(new VBox(fallbackNotice, horizontalScroll));
  }

  /** Replaces the displayed document and resets its scroll position. */
  public void setDocument(Document document) {
    this.document = Objects.requireNonNull(document, "document");
    verticalScroll.setValue(0);
    horizontalScroll.setValue(0);
    renderPage();
  }

  /** Returns unsupported CSS declarations that were ignored during style calculation. */
  public List<String> unsupportedDeclarations() {
    return lastStyles == null ? List.of() : lastStyles.unsupportedDeclarations();
  }

  /** Returns the latest rendered display list, when layout has completed. */
  public java.util.Optional<DisplayList> displayList() {
    return java.util.Optional.ofNullable(lastDisplayList);
  }

  @Override
  protected void layoutChildren() {
    super.layoutChildren();
    double width = viewport.getWidth();
    double height = viewport.getHeight();
    if (width != previousWidth || height != previousHeight) {
      previousWidth = width;
      previousHeight = height;
      renderPage();
    }
  }

  private void handleScroll(ScrollEvent event) {
    if (event.isShiftDown()) {
      horizontalScroll.setValue(
          clamp(horizontalScroll.getValue() - event.getDeltaY(), horizontalScroll.getMax()));
    } else {
      verticalScroll.setValue(
          clamp(verticalScroll.getValue() - event.getDeltaY(), verticalScroll.getMax()));
    }
    event.consume();
  }

  private void renderAtScroll() {
    if (!updatingScrollBars) {
      renderPage();
    }
  }

  private void renderPage() {
    double width = Math.max(0, viewport.getWidth());
    double height = Math.max(0, viewport.getHeight());
    lastStyles = styleEngine.compute(document);
    var constraints =
        new LayoutConstraints(
            width, height, horizontalScroll.getValue(), verticalScroll.getValue());
    var layout = layoutEngine.layout(document, lastStyles, constraints);
    updateScrollBars(layout.documentWidth(), layout.documentHeight(), width, height);
    if (horizontalScroll.getValue() != constraints.scrollX()
        || verticalScroll.getValue() != constraints.scrollY()) {
      constraints =
          new LayoutConstraints(
              width, height, horizontalScroll.getValue(), verticalScroll.getValue());
      layout = layoutEngine.layout(document, lastStyles, constraints);
    }
    lastDisplayList = displayListBuilder.build(layout);
    renderer.render(lastDisplayList, canvas);
    boolean hasFallbacks = !lastStyles.unsupportedDeclarations().isEmpty();
    fallbackNotice.setVisible(hasFallbacks);
    fallbackNotice.setManaged(hasFallbacks);
    fallbackNotice.setText(
        hasFallbacks ? "Some page styles are not supported and were ignored." : "");
    fallbackNotice.setAccessibleText(
        hasFallbacks
            ? "Unsupported styles ignored: "
                + String.join(", ", lastStyles.unsupportedDeclarations())
            : "");
  }

  private void updateScrollBars(
      double documentWidth, double documentHeight, double viewportWidth, double viewportHeight) {
    updatingScrollBars = true;
    double maxHorizontal = Math.max(0, documentWidth - viewportWidth);
    final double maxVertical = Math.max(0, documentHeight - viewportHeight);
    horizontalScroll.setMin(0);
    horizontalScroll.setMax(maxHorizontal);
    horizontalScroll.setVisibleAmount(viewportWidth);
    horizontalScroll.setUnitIncrement(24);
    horizontalScroll.setBlockIncrement(Math.max(24, viewportWidth * 0.8));
    horizontalScroll.setValue(clamp(horizontalScroll.getValue(), maxHorizontal));
    verticalScroll.setMin(0);
    verticalScroll.setMax(maxVertical);
    verticalScroll.setVisibleAmount(viewportHeight);
    verticalScroll.setUnitIncrement(32);
    verticalScroll.setBlockIncrement(Math.max(32, viewportHeight * 0.8));
    verticalScroll.setValue(clamp(verticalScroll.getValue(), maxVertical));
    horizontalScroll.setDisable(maxHorizontal == 0);
    verticalScroll.setDisable(maxVertical == 0);
    updatingScrollBars = false;
  }

  private double clamp(double value, double max) {
    return Math.max(0, Math.min(value, max));
  }
}
