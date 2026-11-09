package kr.soyuo.serox.browser.css;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * RGBA color used by computed styles and canvas paint commands. Parses the CSS named colors, hex
 * in 3, 4, 6 and 8 digits, {@code rgb()}/{@code rgba()} and {@code hsl()}/{@code hsla()} in both the
 * comma and the space syntax.
 */
public record CssColor(int red, int green, int blue, double opacity) {
  private static final Map<String, CssColor> NAMED = new HashMap<>();

  static {
    String table =
        "aliceblue f0f8ff antiquewhite faebd7 aqua 00ffff aquamarine 7fffd4 azure f0ffff"
            + " beige f5f5dc bisque ffe4c4 black 000000 blanchedalmond ffebcd blue 0000ff"
            + " blueviolet 8a2be2 brown a52a2a burlywood deb887 cadetblue 5f9ea0"
            + " chartreuse 7fff00 chocolate d2691e coral ff7f50 cornflowerblue 6495ed"
            + " cornsilk fff8dc crimson dc143c cyan 00ffff darkblue 00008b darkcyan 008b8b"
            + " darkgoldenrod b8860b darkgray a9a9a9 darkgreen 006400 darkgrey a9a9a9"
            + " darkkhaki bdb76b darkmagenta 8b008b darkolivegreen 556b2f darkorange ff8c00"
            + " darkorchid 9932cc darkred 8b0000 darksalmon e9967a darkseagreen 8fbc8f"
            + " darkslateblue 483d8b darkslategray 2f4f4f darkslategrey 2f4f4f"
            + " darkturquoise 00ced1 darkviolet 9400d3 deeppink ff1493 deepskyblue 00bfff"
            + " dimgray 696969 dimgrey 696969 dodgerblue 1e90ff firebrick b22222"
            + " floralwhite fffaf0 forestgreen 228b22 fuchsia ff00ff gainsboro dcdcdc"
            + " ghostwhite f8f8ff gold ffd700 goldenrod daa520 gray 808080 green 008000"
            + " greenyellow adff2f grey 808080 honeydew f0fff0 hotpink ff69b4"
            + " indianred cd5c5c indigo 4b0082 ivory fffff0 khaki f0e68c lavender e6e6fa"
            + " lavenderblush fff0f5 lawngreen 7cfc00 lemonchiffon fffacd lightblue add8e6"
            + " lightcoral f08080 lightcyan e0ffff lightgoldenrodyellow fafad2"
            + " lightgray d3d3d3 lightgreen 90ee90 lightgrey d3d3d3 lightpink ffb6c1"
            + " lightsalmon ffa07a lightseagreen 20b2aa lightskyblue 87cefa"
            + " lightslategray 778899 lightslategrey 778899 lightsteelblue b0c4de"
            + " lightyellow ffffe0 lime 00ff00 limegreen 32cd32 linen faf0e6 magenta ff00ff"
            + " maroon 800000 mediumaquamarine 66cdaa mediumblue 0000cd mediumorchid ba55d3"
            + " mediumpurple 9370db mediumseagreen 3cb371 mediumslateblue 7b68ee"
            + " mediumspringgreen 00fa9a mediumturquoise 48d1cc mediumvioletred c71585"
            + " midnightblue 191970 mintcream f5fffa mistyrose ffe4e1 moccasin ffe4b5"
            + " navajowhite ffdead navy 000080 oldlace fdf5e6 olive 808000 olivedrab 6b8e23"
            + " orange ffa500 orangered ff4500 orchid da70d6 palegoldenrod eee8aa"
            + " palegreen 98fb98 paleturquoise afeeee palevioletred db7093 papayawhip ffefd5"
            + " peachpuff ffdab9 peru cd853f pink ffc0cb plum dda0dd powderblue b0e0e6"
            + " purple 800080 rebeccapurple 663399 red ff0000 rosybrown bc8f8f"
            + " royalblue 4169e1 saddlebrown 8b4513 salmon fa8072 sandybrown f4a460"
            + " seagreen 2e8b57 seashell fff5ee sienna a0522d silver c0c0c0 skyblue 87ceeb"
            + " slateblue 6a5acd slategray 708090 slategrey 708090 snow fffafa"
            + " springgreen 00ff7f steelblue 4682b4 tan d2b48c teal 008080 thistle d8bfd8"
            + " tomato ff6347 turquoise 40e0d0 violet ee82ee wheat f5deb3 white ffffff"
            + " whitesmoke f5f5f5 yellow ffff00 yellowgreen 9acd32"
            // System colors, as a light theme draws them.
            + " canvas ffffff canvastext 000000 linktext 0000ee visitedtext 551a8b"
            + " buttonface f0f0f0 buttontext 000000 field ffffff fieldtext 000000"
            + " graytext 808080 highlight 3390ff highlighttext ffffff";
    String[] parts = table.split(" ");
    for (int i = 0; i + 1 < parts.length; i += 2) {
      int rgb = Integer.parseInt(parts[i + 1], 16);
      NAMED.put(parts[i], new CssColor(rgb >> 16 & 0xff, rgb >> 8 & 0xff, rgb & 0xff, 1));
    }
    NAMED.put("transparent", new CssColor(0, 0, 0, 0));
  }

  /** Validates RGBA channels. */
  public CssColor {
    if (red < 0 || red > 255 || green < 0 || green > 255 || blue < 0 || blue > 255) {
      throw new IllegalArgumentException("color channels must be between 0 and 255");
    }
    if (opacity < 0 || opacity > 1) {
      throw new IllegalArgumentException("opacity must be between 0 and 1");
    }
  }

  /** The same color with its alpha multiplied by {@code factor}. */
  public CssColor fade(double factor) {
    return new CssColor(red, green, blue, clamp(opacity * factor, 0, 1));
  }

  /** Parses a CSS color, or nothing when the value is not one this parser knows. */
  public static Optional<CssColor> parse(String value) {
    String normalized = value.trim().toLowerCase(Locale.ROOT);
    CssColor named = NAMED.get(normalized);
    if (named != null) {
      return Optional.of(named);
    }
    try {
      if (normalized.startsWith("#")) {
        return hex(normalized.substring(1));
      }
      int open = normalized.indexOf('(');
      if (open > 0 && normalized.endsWith(")")) {
        String function = normalized.substring(0, open).strip();
        String[] args = arguments(normalized.substring(open + 1, normalized.length() - 1));
        return switch (function) {
          case "rgb", "rgba" -> rgb(args);
          case "hsl", "hsla" -> hsl(args);
          default -> Optional.empty();
        };
      }
    } catch (RuntimeException unreadable) {
      return Optional.empty();
    }
    return Optional.empty();
  }

  private static Optional<CssColor> hex(String hex) {
    if (!hex.matches("[0-9a-f]{3,8}")) {
      return Optional.empty();
    }
    return switch (hex.length()) {
      case 3, 4 -> {
        int r = Integer.parseInt(hex.substring(0, 1).repeat(2), 16);
        int g = Integer.parseInt(hex.substring(1, 2).repeat(2), 16);
        int b = Integer.parseInt(hex.substring(2, 3).repeat(2), 16);
        double a = hex.length() == 4 ? Integer.parseInt(hex.substring(3).repeat(2), 16) / 255.0 : 1;
        yield Optional.of(new CssColor(r, g, b, a));
      }
      case 6, 8 -> {
        int r = Integer.parseInt(hex.substring(0, 2), 16);
        int g = Integer.parseInt(hex.substring(2, 4), 16);
        int b = Integer.parseInt(hex.substring(4, 6), 16);
        double a = hex.length() == 8 ? Integer.parseInt(hex.substring(6, 8), 16) / 255.0 : 1;
        yield Optional.of(new CssColor(r, g, b, a));
      }
      default -> Optional.empty();
    };
  }

  /** Splits "1, 2, 3, .5" and "1 2 3 / 50%" into their parts. */
  private static String[] arguments(String body) {
    String text = body.replace("/", " ").replace(",", " ").strip();
    return text.isEmpty() ? new String[0] : text.split("\\s+");
  }

  private static Optional<CssColor> rgb(String[] args) {
    if (args.length != 3 && args.length != 4) {
      return Optional.empty();
    }
    int[] channel = new int[3];
    for (int i = 0; i < 3; i++) {
      String part = args[i];
      double number =
          part.endsWith("%") ? number(part.substring(0, part.length() - 1)) * 2.55 : number(part);
      channel[i] = (int) Math.round(clamp(number, 0, 255));
    }
    return Optional.of(new CssColor(channel[0], channel[1], channel[2], alpha(args)));
  }

  private static Optional<CssColor> hsl(String[] args) {
    if (args.length != 3 && args.length != 4) {
      return Optional.empty();
    }
    String hueText = args[0];
    double hue;
    if (hueText.endsWith("deg")) {
      hue = number(hueText.substring(0, hueText.length() - 3));
    } else if (hueText.endsWith("turn")) {
      hue = number(hueText.substring(0, hueText.length() - 4)) * 360;
    } else if (hueText.endsWith("rad")) {
      hue = Math.toDegrees(number(hueText.substring(0, hueText.length() - 3)));
    } else {
      hue = number(hueText);
    }
    double saturation = clamp(number(args[1].replace("%", "")) / 100, 0, 1);
    double lightness = clamp(number(args[2].replace("%", "")) / 100, 0, 1);
    hue = ((hue % 360) + 360) % 360 / 360;
    double q =
        lightness < 0.5
            ? lightness * (1 + saturation)
            : lightness + saturation - lightness * saturation;
    double p = 2 * lightness - q;
    int r = (int) Math.round(hueToRgb(p, q, hue + 1.0 / 3) * 255);
    int g = (int) Math.round(hueToRgb(p, q, hue) * 255);
    int b = (int) Math.round(hueToRgb(p, q, hue - 1.0 / 3) * 255);
    return Optional.of(new CssColor(r, g, b, alpha(args)));
  }

  private static double hueToRgb(double p, double q, double t) {
    if (t < 0) {
      t += 1;
    }
    if (t > 1) {
      t -= 1;
    }
    if (t < 1.0 / 6) {
      return p + (q - p) * 6 * t;
    }
    if (t < 0.5) {
      return q;
    }
    if (t < 2.0 / 3) {
      return p + (q - p) * (2.0 / 3 - t) * 6;
    }
    return p;
  }

  private static double alpha(String[] args) {
    if (args.length < 4) {
      return 1;
    }
    String part = args[3];
    double value =
        part.endsWith("%") ? number(part.substring(0, part.length() - 1)) / 100 : number(part);
    return clamp(value, 0, 1);
  }

  private static double number(String text) {
    if (text.equals("none")) {
      return 0;
    }
    return Double.parseDouble(text);
  }

  private static double clamp(double value, double low, double high) {
    return Math.max(low, Math.min(high, value));
  }
}
