package kr.soyuo.serox.ui;

import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

/**
 * An icon with a light-background and a dark-background picture. It shows the one that fits the
 * nearest {@code app-root} above it and switches when that root gains or loses {@code theme-dark}.
 */
final class ThemedIcon extends ImageView {
  private static final String ROOT_CLASS = "app-root";
  private static final String DARK_CLASS = "theme-dark";

  private final Image light;
  private final Image dark;
  private final ListChangeListener<String> watcher = change -> refresh();
  private ObservableList<String> watched;

  ThemedIcon(Image light, Image dark) {
    super(light);
    this.light = light;
    this.dark = dark;
    sceneProperty().addListener((observable, before, after) -> attach());
    parentProperty().addListener((observable, before, after) -> attach());
  }

  private void attach() {
    if (watched != null) {
      watched.removeListener(watcher);
      watched = null;
    }
    if (getScene() != null) {
      for (Node node = this; node != null; node = node.getParent()) {
        if (node.getStyleClass().contains(ROOT_CLASS)) {
          watched = node.getStyleClass();
          watched.addListener(watcher);
          break;
        }
      }
    }
    refresh();
  }

  private void refresh() {
    setImage(watched != null && watched.contains(DARK_CLASS) ? dark : light);
  }
}
