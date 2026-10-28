package kr.soyuo.serox.ui;

import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.parser.SVGLoader;
import com.github.weisj.jsvg.view.ViewBox;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.SVGPath;

/** Renders bundled Figma SVG assets to cached JavaFX images. */
final class SvgIconView {
  private static final Map<String, javafx.scene.image.Image> IMAGE_CACHE = new HashMap<>();
  private static final Map<String, javafx.scene.image.Image> DARK_CACHE = new HashMap<>();

  private SvgIconView() {}

  /** An icon that follows the theme: its dark strokes turn light when the app is dark. */
  static ImageView icon(String assetName, double size) {
    ImageView imageView =
        new ThemedIcon(load(assetName), DARK_CACHE.computeIfAbsent(assetName, SvgIconView::darkVariant));
    imageView.setFitWidth(size);
    imageView.setFitHeight(size);
    imageView.setPreserveRatio(true);
    imageView.setSmooth(true);
    imageView.getStyleClass().add("svg-icon");
    return imageView;
  }

  static StackPane logo(double size) {
    SVGPath mark = new SVGPath();
    mark.setContent(logoPath());
    mark.setFill(Color.WHITE);
    mark.setFillRule(FillRule.EVEN_ODD);
    mark.setScaleX(size / 192);
    mark.setScaleY(size / 192);
    StackPane logo = new StackPane(mark);
    logo.setMinSize(size, size);
    logo.setPrefSize(size, size);
    logo.setMaxSize(size, size);
    logo.getStyleClass().add("brand-logo");
    return logo;
  }

  private static String logoPath() {
    var resource = SvgIconView.class.getResource("assets/" + UiTokens.LOGO_ASSET);
    if (resource == null) {
      throw new IllegalArgumentException("Missing UI asset: 5e811.svg");
    }
    try (var input = resource.openStream()) {
      String svg = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
      var matcher = java.util.regex.Pattern.compile("<path[^>]*d=\"([^\"]+)\"").matcher(svg);
      if (!matcher.find()) {
        throw new IllegalArgumentException("Missing path in UI asset: 5e811.svg");
      }
      return matcher.group(1);
    } catch (java.io.IOException exception) {
      throw new IllegalStateException("Unable to read UI asset: 5e811.svg", exception);
    }
  }

  private static javafx.scene.image.Image darkVariant(String assetName) {
    javafx.scene.image.Image light = load(assetName);
    BufferedImage copy = SwingFXUtils.fromFXImage(light, null);
    lightenDarkPixels(copy);
    return SwingFXUtils.toFXImage(copy, null);
  }

  /**
   * Turns the dark grey and black pixels of an icon light, keeping alpha and any colored
   * pixel (green, blue, red) as it is. The bundled icons are drawn for a light background.
   */
  static void lightenDarkPixels(BufferedImage image) {
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        int argb = image.getRGB(x, y);
        int alpha = argb >>> 24;
        int red = (argb >> 16) & 0xFF;
        int green = (argb >> 8) & 0xFF;
        int blue = argb & 0xFF;
        int spread = Math.max(red, Math.max(green, blue)) - Math.min(red, Math.min(green, blue));
        int brightness = (red + green + blue) / 3;
        if (alpha > 0 && spread < 48 && brightness < 150) {
          image.setRGB(x, y, (alpha << 24) | 0xDDE3EE);
        }
      }
    }
  }

  private static javafx.scene.image.Image load(String assetName) {
    return IMAGE_CACHE.computeIfAbsent(assetName, SvgIconView::render);
  }

  private static javafx.scene.image.Image render(String assetName) {
    var resource = SvgIconView.class.getResource("assets/" + assetName);
    if (resource == null) {
      throw new IllegalArgumentException("Missing UI asset: " + assetName);
    }
    SVGDocument document = new SVGLoader().load(resource);
    ViewBox viewBox = document.viewBox();
    int width = Math.max(1, Math.round(viewBox.width));
    int height = Math.max(1, Math.round(viewBox.height));
    int scale = 4;
    BufferedImage bufferedImage =
        new BufferedImage(width * scale, height * scale, BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = bufferedImage.createGraphics();
    try {
      graphics.scale(scale, scale);
      document.render(null, graphics, viewBox);
    } finally {
      graphics.dispose();
    }
    return SwingFXUtils.toFXImage(bufferedImage, null);
  }
}
