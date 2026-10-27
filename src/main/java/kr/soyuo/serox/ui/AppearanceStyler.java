package kr.soyuo.serox.ui;

import java.util.Locale;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import kr.soyuo.serox.core.model.BrowserSettings;
import kr.soyuo.serox.core.model.DisplayDensity;
import kr.soyuo.serox.core.model.ThemeMode;

/** Maps non-secret appearance settings onto JavaFX inline style and style classes. */
final class AppearanceStyler {
  static final double BASE_FONT_SIZE = 13;
  static final String DARK_CLASS = "theme-dark";
  static final String COMPACT_CLASS = "density-compact";

  /** Prevents instantiation of this stateless utility. */
  private AppearanceStyler() {}

  /** Inline style carrying the font family and size and the accent color tokens. */
  static String inlineStyle(BrowserSettings settings) {
    Color accent = Color.web(settings.accentColor());
    return "-fx-font-family: \""
        + UiTokens.FONT_FAMILY
        + "\"; -fx-font-size: "
        + String.format(Locale.ROOT, "%.2f", BASE_FONT_SIZE * settings.fontScalePercent() / 100.0)
        + "px; serox-primary: "
        + hex(accent)
        + "; serox-primary-hover: "
        + hex(accent.darker())
        + "; serox-logo: "
        + hex(accent)
        + ";";
  }

  /** Follows JavaFX's operating-system color preference; headless callers use the light default. */
  static boolean dark(BrowserSettings settings) {
    if (settings.theme() != ThemeMode.SYSTEM) {
      return settings.theme() == ThemeMode.DARK;
    }
    try {
      return javafx.application.Platform.getPreferences().getColorScheme()
          == javafx.application.ColorScheme.DARK;
    } catch (IllegalStateException notStarted) {
      return false;
    }
  }

  static boolean compact(BrowserSettings settings) {
    return settings.displayDensity() == DisplayDensity.COMPACT;
  }

  /** Applies the settings to the root node of the application. */
  static void apply(Node root, BrowserSettings settings) {
    root.setStyle(inlineStyle(settings));
    toggle(root, DARK_CLASS, dark(settings));
    toggle(root, COMPACT_CLASS, compact(settings));
  }

  private static void toggle(Node node, String styleClass, boolean on) {
    node.getStyleClass().remove(styleClass);
    if (on) {
      node.getStyleClass().add(styleClass);
    }
  }

  private static String hex(Color color) {
    return String.format(
        Locale.ROOT,
        "#%02x%02x%02x",
        Math.round(color.getRed() * 255),
        Math.round(color.getGreen() * 255),
        Math.round(color.getBlue() * 255));
  }
}
