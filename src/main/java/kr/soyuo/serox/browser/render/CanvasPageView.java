package kr.soyuo.serox.browser.render;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Orientation;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollBar;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import kr.soyuo.serox.browser.css.CssStyleEngine;
import kr.soyuo.serox.browser.css.StyleSnapshot;
import kr.soyuo.serox.browser.dom.Document;
import kr.soyuo.serox.browser.layout.LayoutConstraints;
import kr.soyuo.serox.browser.layout.LayoutEngine;
import kr.soyuo.serox.browser.layout.LayoutResult;
import kr.soyuo.serox.browser.layout.TextRun;

/** Resizable, scrollable JavaFX viewport for the initial page renderer. */
public final class CanvasPageView extends BorderPane {
  private static final PageImageLoader IMAGE_LOADER = PageImageLoader.createDefault();
  private final Canvas canvas = new Canvas();
  private final StackPane viewport = new StackPane(canvas);
  private final ScrollBar verticalScroll = new ScrollBar();
  private final ScrollBar horizontalScroll = new ScrollBar();
  private final Label fallbackNotice = new Label();
  private final CssStyleEngine styleEngine = new CssStyleEngine();
  private final LayoutEngine layoutEngine = new LayoutEngine();
  private final DisplayListBuilder displayListBuilder = new DisplayListBuilder();
  private final JavaFxCanvasRenderer renderer = new JavaFxCanvasRenderer();
  private final Map<String, javafx.scene.image.Image> images = new HashMap<>();
  private final Map<URI, CompletableFuture<java.awt.image.BufferedImage>> imageRequests =
      new HashMap<>();
  private long imageGeneration;
  private Document document;
  private StyleSnapshot lastStyles;
  private DisplayList lastDisplayList;
  private double previousWidth = -1;
  private double previousHeight = -1;
  private boolean updatingScrollBars;
  private final URI documentUri;
  private final Consumer<URI> navigationAction;
  private LayoutResult lastLayout;

  /** Creates a viewport for one parsed document. */
  public CanvasPageView(Document document) {
    this(document, URI.create("about:blank"), ignored -> {});
  }

  /** Creates a viewport whose links and supported GET forms navigate through the browser shell. */
  public CanvasPageView(Document document, URI documentUri, Consumer<URI> navigationAction) {
    this.document = Objects.requireNonNull(document, "document");
    this.documentUri = Objects.requireNonNull(documentUri, "documentUri");
    this.navigationAction = Objects.requireNonNull(navigationAction, "navigationAction");
    canvas.widthProperty().bind(viewport.widthProperty());
    canvas.heightProperty().bind(viewport.heightProperty());
    viewport.setOnScroll(this::handleScroll);
    canvas.setOnMouseClicked(this::handleClick);
    canvas.setOnMouseMoved(this::updatePointer);
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
    sceneProperty()
        .addListener(
            (observable, oldScene, newScene) -> {
              if (newScene == null) {
                cancelImages();
              } else {
                loadImages();
              }
            });
  }

  /** Replaces the displayed document and resets its scroll position. */
  public void setDocument(Document document) {
    this.document = Objects.requireNonNull(document, "document");
    verticalScroll.setValue(0);
    horizontalScroll.setValue(0);
    if (getScene() != null) {
      loadImages();
    } else {
      cancelImages();
    }
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
    lastLayout = layout;
    lastDisplayList = displayListBuilder.build(layout);
    renderer.render(lastDisplayList, canvas, images);
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

  private void cancelImages() {
    imageGeneration++;
    imageRequests.values().forEach(request -> request.cancel(true));
    imageRequests.clear();
    images.clear();
  }

  private void loadImages() {
    cancelImages();
    long generation = imageGeneration;
    for (var element : document.getElementsByTagName("img")) {
      String source = element.attribute("src").orElse("");
      var resolved = PageImageLoader.resolve(document.documentUrl(), source);
      if (resolved.isEmpty()) {
        continue;
      }
      URI uri = resolved.orElseThrow();
      if (!imageRequests.containsKey(uri) && imageRequests.size() >= PageImageLoader.MAX_IMAGES) {
        continue;
      }
      var request = imageRequests.computeIfAbsent(uri, IMAGE_LOADER::load);
      request.thenAccept(
          image ->
              Platform.runLater(
                  () -> {
                    if (generation == imageGeneration && getScene() != null) {
                      images.put(source, SwingFXUtils.toFXImage(image, null));
                      if (lastDisplayList != null) {
                        renderer.render(lastDisplayList, canvas, images);
                      }
                    }
                  }));
    }
  }

  private void handleClick(MouseEvent event) {
    if (lastLayout == null) {
      return;
    }
    double x = event.getX() + horizontalScroll.getValue();
    double y = event.getY() + verticalScroll.getValue();
    for (int index = lastLayout.textRuns().size() - 1; index >= 0; index--) {
      TextRun run = lastLayout.textRuns().get(index);
      if (run.link().isPresent() && contains(run, x, y)) {
        navigationAction.accept(run.link().orElseThrow());
        event.consume();
        return;
      }
    }
    for (int index = lastLayout.boxes().size() - 1; index >= 0; index--) {
      var box = lastLayout.boxes().get(index);
      if (!contains(box.x(), box.y(), box.width(), box.height(), x, y)) {
        continue;
      }
      if (box.element().tagName().equals("input")
          && box.element().attribute("type").orElse("text").equalsIgnoreCase("submit")) {
        submitGetForm(box.element());
        event.consume();
        return;
      }
      if (box.element().tagName().equals("input")
          && List.of("text", "search", "url", "email")
              .contains(box.element().attribute("type").orElse("text").toLowerCase())) {
        var dialog =
            new javafx.scene.control.TextInputDialog(box.element().attribute("value").orElse(""));
        dialog.setTitle("Form input");
        dialog.setHeaderText("Enter a value for this page field");
        dialog.showAndWait().ifPresent(value -> box.element().setAttribute("value", value));
        renderPage();
        event.consume();
        return;
      }
      if (box.element().tagName().equals("button")
          && box.element().attribute("type").orElse("submit").equalsIgnoreCase("submit")) {
        submitGetForm(box.element());
        event.consume();
        return;
      }
    }
  }

  private void updatePointer(MouseEvent event) {
    if (lastLayout == null) {
      canvas.setCursor(Cursor.DEFAULT);
      return;
    }
    double x = event.getX() + horizontalScroll.getValue();
    double y = event.getY() + verticalScroll.getValue();
    boolean link =
        lastLayout.textRuns().stream()
            .anyMatch(run -> run.link().isPresent() && contains(run, x, y));
    boolean control =
        lastLayout.boxes().stream()
            .anyMatch(
                box ->
                    List.of("input", "button").contains(box.element().tagName())
                        && contains(box.x(), box.y(), box.width(), box.height(), x, y));
    canvas.setCursor(link || control ? Cursor.HAND : Cursor.DEFAULT);
  }

  private boolean contains(TextRun run, double x, double y) {
    javafx.scene.text.Text measurement = new javafx.scene.text.Text(run.text());
    double fontSize = run.style().pixels("font-size", 16);
    measurement.setFont(javafx.scene.text.Font.font(fontSize));
    return contains(
        run.x(),
        run.baselineY() - fontSize,
        measurement.getLayoutBounds().getWidth(),
        fontSize * 1.25,
        x,
        y);
  }

  private boolean contains(
      double left, double top, double width, double height, double x, double y) {
    return x >= left && x <= left + width && y >= top && y <= top + height;
  }

  private void submitGetForm(kr.soyuo.serox.browser.dom.Element control) {
    var form = control.parent().orElse(null);
    while (form != null
        && !(form instanceof kr.soyuo.serox.browser.dom.Element element
            && element.tagName().equals("form"))) {
      form = form.parent().orElse(null);
    }
    if (!(form instanceof kr.soyuo.serox.browser.dom.Element element)
        || !element.attribute("method").orElse("get").equalsIgnoreCase("get")) {
      return;
    }
    String query =
        element.descendants().stream()
            .filter(kr.soyuo.serox.browser.dom.Element.class::isInstance)
            .map(kr.soyuo.serox.browser.dom.Element.class::cast)
            .filter(input -> input.tagName().equals("input"))
            .filter(input -> input.attribute("name").filter(name -> !name.isBlank()).isPresent())
            .filter(
                input ->
                    !List.of("submit", "button", "reset", "file", "checkbox", "radio")
                        .contains(input.attribute("type").orElse("text").toLowerCase()))
            .map(
                input ->
                    encode(input.attribute("name").orElseThrow())
                        + "="
                        + encode(input.attribute("value").orElse("")))
            .collect(java.util.stream.Collectors.joining("&"));
    URI action = documentUri.resolve(element.attribute("action").orElse(documentUri.toString()));
    String target = action.toString();
    int fragmentIndex = target.indexOf('#');
    String fragment = fragmentIndex < 0 ? "" : target.substring(fragmentIndex);
    if (fragmentIndex >= 0) {
      target = target.substring(0, fragmentIndex);
    }
    if (!query.isEmpty()) {
      target += (action.getRawQuery() == null ? "?" : "&") + query;
    }
    navigationAction.accept(URI.create(target + fragment));
  }

  private String encode(String value) {
    return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
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
