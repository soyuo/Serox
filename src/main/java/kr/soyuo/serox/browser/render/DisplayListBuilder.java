package kr.soyuo.serox.browser.render;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import kr.soyuo.serox.browser.css.ComputedStyle;
import kr.soyuo.serox.browser.css.CssColor;
import kr.soyuo.serox.browser.layout.LayoutBox;
import kr.soyuo.serox.browser.layout.LayoutResult;
import kr.soyuo.serox.browser.layout.TextRun;

/** Converts positioned layout output into ordered canvas paint commands. */
public final class DisplayListBuilder {
  private static final String[] SIDES = {"top", "right", "bottom", "left"};

  /** Creates paint commands for backgrounds, borders, replaced elements, and text. */
  public DisplayList build(LayoutResult layout) {
    List<OrderedCommand> orderedCommands = new ArrayList<>();
    for (LayoutBox box : layout.boxes()) {
      if (hidden(box.style())) {
        continue;
      }
      double opacity = opacity(box.style());
      kr.soyuo.serox.browser.css.BoxShadow shadow =
          kr.soyuo.serox.browser.css.BoxShadow.parse(box.style().value("box-shadow"));
      if (shadow != null && !shadow.inset()) {
        double spread = shadow.spread();
        orderedCommands.add(
            new OrderedCommand(
                box.paintOrder(),
                -1,
                new PaintCommand.Shadow(
                    box.x() + shadow.offsetX() - spread,
                    box.y() + shadow.offsetY() - spread,
                    box.width() + 2 * spread,
                    box.height() + 2 * spread,
                    shadow.color().fade(opacity),
                    shadow.blur(),
                    radius(box))));
      }
      CssColor background =
          box.style()
              .color("background-color")
              .or(() -> box.style().color("background"))
              .orElse(new CssColor(0, 0, 0, 0))
              .fade(opacity);
      if (background.opacity() > 0) {
        orderedCommands.add(
            new OrderedCommand(
                box.paintOrder(),
                0,
                new PaintCommand.FillRect(
                    box.x(), box.y(), box.width(), box.height(), background, radius(box))));
      }
    }
    for (LayoutBox box : layout.boxes()) {
      if (hidden(box.style())) {
        continue;
      }
      String tagName = box.element().tagName();
      if (tagName.equals("img")) {
        orderedCommands.add(
            new OrderedCommand(
                box.paintOrder(),
                1,
                new PaintCommand.ImagePlaceholder(
                    box.x(),
                    box.y(),
                    box.width(),
                    box.height(),
                    box.element().attribute("alt").orElse("Image"),
                    box.element().attribute("src").orElse(""),
                    box.style())));
      } else if (tagName.equals("input") || tagName.equals("button")) {
        orderedCommands.add(
            new OrderedCommand(
                box.paintOrder(),
                1,
                new PaintCommand.FormControl(
                    box.x(), box.y(), box.width(), box.height(), tagName, box.style())));
      }
      addBorders(box, orderedCommands);
    }
    for (TextRun run : layout.textRuns()) {
      if (hidden(run.style())) {
        continue;
      }
      orderedCommands.add(
          new OrderedCommand(
              run.paintOrder(),
              3,
              new PaintCommand.Text(
                  run.x(), run.baselineY(), run.text(), run.style(), run.link())));
    }
    List<PaintCommand> commands =
        orderedCommands.stream()
            .sorted(
                Comparator.comparingInt(OrderedCommand::order)
                    .thenComparingInt(OrderedCommand::priority))
            .map(OrderedCommand::command)
            .toList();
    return new DisplayList(
        commands,
        layout.constraints().viewportWidth(),
        layout.constraints().viewportHeight(),
        layout.documentWidth(),
        layout.documentHeight(),
        layout.constraints().scrollX(),
        layout.constraints().scrollY());
  }

  /** One stroke when all four sides match, else a filled strip per side. */
  private void addBorders(LayoutBox box, List<OrderedCommand> out) {
    ComputedStyle style = box.style();
    double opacity = opacity(style);
    double[] width = new double[4];
    CssColor[] color = new CssColor[4];
    for (int i = 0; i < 4; i++) {
      width[i] = sideWidth(style, SIDES[i]);
      color[i] = sideColor(style, SIDES[i]).fade(opacity);
    }
    boolean uniform =
        width[0] == width[1] && width[1] == width[2] && width[2] == width[3]
            && color[0].equals(color[1]) && color[1].equals(color[2])
            && color[2].equals(color[3]);
    if (uniform) {
      if (width[0] > 0 && color[0].opacity() > 0) {
        out.add(
            new OrderedCommand(
                box.paintOrder(),
                2,
                new PaintCommand.StrokeRect(
                    box.x(), box.y(), box.width(), box.height(), color[0], width[0], radius(box))));
      }
      return;
    }
    double x = box.x();
    double y = box.y();
    double w = box.width();
    double h = box.height();
    double[][] strips = {
      {x, y, w, width[0]},
      {x + w - width[1], y, width[1], h},
      {x, y + h - width[2], w, width[2]},
      {x, y, width[3], h}
    };
    for (int i = 0; i < 4; i++) {
      if (width[i] > 0 && color[i].opacity() > 0) {
        out.add(
            new OrderedCommand(
                box.paintOrder(),
                2,
                new PaintCommand.FillRect(
                    strips[i][0], strips[i][1], strips[i][2], strips[i][3], color[i])));
      }
    }
  }

  private double sideWidth(ComputedStyle style, String side) {
    String sideStyle = style.value("border-" + side + "-style");
    if (sideStyle.isEmpty()) {
      sideStyle = style.value("border-style");
    }
    if (sideStyle.equals("none") || sideStyle.equals("hidden")) {
      return 0;
    }
    double own = style.pixels("border-" + side + "-width", Double.NaN);
    if (Double.isFinite(own)) {
      return Math.max(0, own);
    }
    return borderWidth(style.value("border"), style.pixels("border-width", 0));
  }

  private CssColor sideColor(ComputedStyle style, String side) {
    return style
        .color("border-" + side + "-color")
        .or(() -> style.color("border-color"))
        .orElse(new CssColor(0, 0, 0, 1));
  }

  private double radius(LayoutBox box) {
    ComputedStyle style = box.style();
    double percent = style.percentage("border-radius");
    double value =
        Double.isFinite(percent)
            ? Math.min(box.width(), box.height()) * percent / 100
            : style.pixels("border-radius", 0);
    return Math.max(0, Math.min(value, Math.min(box.width(), box.height()) / 2));
  }

  private static boolean hidden(ComputedStyle style) {
    String visibility = style.value("visibility");
    return visibility.equals("hidden") || visibility.equals("collapse")
        || opacity(style) <= 0;
  }

  static double opacity(ComputedStyle style) {
    String value = style.value("-serox-opacity");
    if (value.isEmpty()) {
      return 1;
    }
    try {
      return Math.max(0, Math.min(1, Double.parseDouble(value)));
    } catch (NumberFormatException unreadable) {
      return 1;
    }
  }

  private double borderWidth(String shorthand, double explicitWidth) {
    if (explicitWidth > 0 || shorthand.isBlank()) {
      return explicitWidth;
    }
    try {
      return Double.parseDouble(shorthand.trim().split("\\s+", 2)[0].replace("px", ""));
    } catch (NumberFormatException exception) {
      return 0;
    }
  }

  private record OrderedCommand(int order, int priority, PaintCommand command) {}
}
