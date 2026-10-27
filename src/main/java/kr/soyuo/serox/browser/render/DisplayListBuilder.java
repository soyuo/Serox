package kr.soyuo.serox.browser.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import kr.soyuo.serox.browser.css.CssColor;
import kr.soyuo.serox.browser.layout.LayoutBox;
import kr.soyuo.serox.browser.layout.LayoutResult;
import kr.soyuo.serox.browser.layout.TextRun;

/** Converts positioned layout output into ordered canvas paint commands. */
public final class DisplayListBuilder {
  /** Creates paint commands for backgrounds, borders, replaced elements, and text. */
  public DisplayList build(LayoutResult layout) {
    List<PaintCommand> commands = new ArrayList<>();
    for (LayoutBox box : layout.boxes()) {
      CssColor background =
          box.style()
              .color("background-color")
              .or(() -> box.style().color("background"))
              .orElse(new CssColor(0, 0, 0, 0));
      if (background.opacity() > 0) {
        commands.add(
            new PaintCommand.FillRect(box.x(), box.y(), box.width(), box.height(), background));
      }
    }
    for (LayoutBox box : layout.boxes()) {
      String tagName = box.element().tagName();
      if (tagName.equals("img")) {
        commands.add(
            new PaintCommand.ImagePlaceholder(
                box.x(),
                box.y(),
                box.width(),
                box.height(),
                box.element().attribute("alt").orElse("Image"),
                box.style()));
      } else if (tagName.equals("input") || tagName.equals("button")) {
        commands.add(
            new PaintCommand.FormControl(
                box.x(), box.y(), box.width(), box.height(), tagName, box.style()));
      }
      double borderWidth =
          borderWidth(box.style().value("border"), box.style().pixels("border-width", 0));
      if (borderWidth > 0) {
        Optional<CssColor> borderColor = box.style().color("border-color");
        commands.add(
            new PaintCommand.StrokeRect(
                box.x(),
                box.y(),
                box.width(),
                box.height(),
                borderColor.orElse(new CssColor(0, 0, 0, 1)),
                borderWidth));
      }
    }
    for (TextRun run : layout.textRuns()) {
      commands.add(
          new PaintCommand.Text(run.x(), run.baselineY(), run.text(), run.style(), run.link()));
    }
    return new DisplayList(
        commands,
        layout.constraints().viewportWidth(),
        layout.constraints().viewportHeight(),
        layout.documentHeight(),
        layout.constraints().scrollX(),
        layout.constraints().scrollY());
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
}
