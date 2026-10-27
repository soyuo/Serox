import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/**
 * Draws the Serox logo mark from the UI asset onto the brand color and writes the window icon
 * (PNG) and the installer icon (ICO). Run: java scripts/icon/GenerateIcon.java
 */
public final class GenerateIcon {
  private static final Path LOGO = Path.of("src/main/resources/kr/soyuo/serox/ui/assets/5e811.svg");
  private static final Path PNG = Path.of("src/main/resources/kr/soyuo/serox/ui/assets/serox-icon.png");
  private static final Path ICO = Path.of("scripts/icon/serox.ico");
  private static final int[] SIZES = {256, 48, 32, 16};

  public static void main(String[] args) throws IOException {
    String svg = Files.readString(LOGO, StandardCharsets.UTF_8);
    var matcher = Pattern.compile("<path[^>]*d=\"([^\"]+)\"").matcher(svg);
    if (!matcher.find()) {
      throw new IllegalStateException("no path in logo");
    }
    Path2D.Double mark = parse(matcher.group(1));
    var bounds = mark.getBounds2D();
    ImageIO.write(draw(256, mark, bounds), "png", PNG.toFile());
    writeIco(mark, bounds);
  }

  private static BufferedImage draw(int size, Path2D.Double mark, java.awt.geom.Rectangle2D b) {
    var image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
    var g = image.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    g.setColor(new Color(0x2563EB));
    g.fill(new RoundRectangle2D.Double(0, 0, size, size, size * 0.22, size * 0.22));
    double scale = size * 0.64 / Math.max(b.getWidth(), b.getHeight());
    var at = new AffineTransform();
    at.translate(size / 2.0, size / 2.0);
    at.scale(scale, scale);
    at.translate(-b.getCenterX(), -b.getCenterY());
    g.setColor(Color.WHITE);
    g.fill(at.createTransformedShape(mark));
    g.dispose();
    return image;
  }

  /** The logo only uses M, L, H, V and Z with absolute coordinates. */
  private static Path2D.Double parse(String d) {
    var path = new Path2D.Double(Path2D.WIND_EVEN_ODD);
    var tokens = Pattern.compile("[MLHVZ]|-?[0-9]*[.]?[0-9]+").matcher(d);
    char command = 'M';
    double x = 0;
    double y = 0;
    boolean first = true;
    while (tokens.find()) {
      String token = tokens.group();
      if (Character.isLetter(token.charAt(0))) {
        command = token.charAt(0);
        if (command == 'Z') {
          path.closePath();
        }
        first = true;
        continue;
      }
      double value = Double.parseDouble(token);
      switch (command) {
        case 'H' -> {
          x = value;
          path.lineTo(x, y);
        }
        case 'V' -> {
          y = value;
          path.lineTo(x, y);
        }
        default -> {
          double nextY = Double.parseDouble(nextNumber(tokens));
          x = value;
          y = nextY;
          if (command == 'M' && first) {
            path.moveTo(x, y);
          } else {
            path.lineTo(x, y);
          }
        }
      }
      first = false;
    }
    return path;
  }

  private static String nextNumber(java.util.regex.Matcher tokens) {
    tokens.find();
    return tokens.group();
  }

  private static void writeIco(Path2D.Double mark, java.awt.geom.Rectangle2D bounds)
      throws IOException {
    var images = new byte[SIZES.length][];
    for (int i = 0; i < SIZES.length; i++) {
      var out = new ByteArrayOutputStream();
      ImageIO.write(draw(SIZES[i], mark, bounds), "png", out);
      images[i] = out.toByteArray();
    }
    var file = new ByteArrayOutputStream();
    var data = new DataOutputStream(file);
    data.write(new byte[] {0, 0, 1, 0});
    data.writeShort(Short.reverseBytes((short) SIZES.length));
    int offset = 6 + 16 * SIZES.length;
    for (int i = 0; i < SIZES.length; i++) {
      int dimension = SIZES[i] >= 256 ? 0 : SIZES[i];
      data.write(new byte[] {(byte) dimension, (byte) dimension, 0, 0});
      data.writeShort(Short.reverseBytes((short) 1));
      data.writeShort(Short.reverseBytes((short) 32));
      data.writeInt(Integer.reverseBytes(images[i].length));
      data.writeInt(Integer.reverseBytes(offset));
      offset += images[i].length;
    }
    for (byte[] image : images) {
      data.write(image);
    }
    Files.write(ICO, file.toByteArray());
  }
}
