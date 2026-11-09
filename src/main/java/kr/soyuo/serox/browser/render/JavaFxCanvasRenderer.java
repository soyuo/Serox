package kr.soyuo.serox.browser.render;

import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import kr.soyuo.serox.browser.css.ComputedStyle;
import kr.soyuo.serox.browser.css.CssColor;

/** Paints a display list onto a JavaFX Canvas. */
public final class JavaFxCanvasRenderer {
  /** Clears the canvas, clips to the viewport, and draws every command in order. */
  public void render(DisplayList displayList, Canvas canvas) {
    render(displayList, canvas, java.util.Map.of());
  }

  /** Paints available page images and retains placeholders for missing resources. */
  public void render(DisplayList displayList, Canvas canvas, java.util.Map<String, Image> images) {
    GraphicsContext graphics = canvas.getGraphicsContext2D();
    graphics.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
    graphics.save();
    graphics.beginPath();
    graphics.rect(0, 0, displayList.viewportWidth(), displayList.viewportHeight());
    graphics.closePath();
    graphics.clip();
    graphics.translate(-displayList.scrollX(), -displayList.scrollY());
    for (PaintCommand command : displayList.commands()) {
      paint(command, graphics, images);
    }
    graphics.restore();
  }

  private void paint(
      PaintCommand command, GraphicsContext graphics, java.util.Map<String, Image> images) {
    if (command instanceof PaintCommand.FillRect rect) {
      graphics.setFill(color(rect.color()));
      if (rect.radius() > 0) {
        double arc = rect.radius() * 2;
        graphics.fillRoundRect(rect.x(), rect.y(), rect.width(), rect.height(), arc, arc);
      } else {
        graphics.fillRect(rect.x(), rect.y(), rect.width(), rect.height());
      }
    } else if (command instanceof PaintCommand.StrokeRect rect) {
      graphics.setStroke(color(rect.color()));
      graphics.setLineWidth(rect.lineWidth());
      // The border sits inside the box, as CSS draws it.
      double inset = rect.lineWidth() / 2;
      double w = rect.width() - rect.lineWidth();
      double h = rect.height() - rect.lineWidth();
      if (rect.radius() > 0) {
        double arc = Math.max(0, rect.radius() * 2 - rect.lineWidth());
        graphics.strokeRoundRect(rect.x() + inset, rect.y() + inset, w, h, arc, arc);
      } else {
        graphics.strokeRect(rect.x() + inset, rect.y() + inset, w, h);
      }
    } else if (command instanceof PaintCommand.Text text) {
      graphics.setGlobalAlpha(DisplayListBuilder.opacity(text.style()));
      paintText(text, graphics);
      graphics.setGlobalAlpha(1);
    } else if (command instanceof PaintCommand.ImagePlaceholder image) {
      graphics.setGlobalAlpha(DisplayListBuilder.opacity(image.style()));
      Image decoded = images.get(image.source());
      if (decoded == null) {
        paintImagePlaceholder(image, graphics);
      } else {
        graphics.drawImage(decoded, image.x(), image.y(), image.width(), image.height());
      }
      graphics.setGlobalAlpha(1);
    } else if (command instanceof PaintCommand.Shadow shadow) {
      graphics.save();
      if (shadow.blur() > 0) {
        graphics.setEffect(new javafx.scene.effect.GaussianBlur(Math.min(63, shadow.blur())));
      }
      graphics.setFill(color(shadow.color()));
      double arc = shadow.radius() * 2;
      graphics.fillRoundRect(shadow.x(), shadow.y(), shadow.width(), shadow.height(), arc, arc);
      graphics.restore();
    } else if (command instanceof PaintCommand.FormControl control) {
      graphics.setGlobalAlpha(DisplayListBuilder.opacity(control.style()));
      paintFormControl(control, graphics);
      graphics.setGlobalAlpha(1);
    }
  }

  private void paintText(PaintCommand.Text text, GraphicsContext graphics) {
    ComputedStyle style = text.style();
    double fontSize = style.pixels("font-size", 16);
    String weightValue = style.value("font-weight").toLowerCase(java.util.Locale.ROOT);
    boolean bold =
        weightValue.equals("bold")
            || weightValue.equals("bolder")
            || (weightValue.matches("[1-9][0-9]{0,2}") && Integer.parseInt(weightValue) >= 600);
    FontWeight weight = bold ? FontWeight.BOLD : FontWeight.NORMAL;
    String styleValue = style.value("font-style");
    FontPosture posture =
        styleValue.equals("italic") || styleValue.equals("oblique")
            ? FontPosture.ITALIC
            : FontPosture.REGULAR;
    Font font = Font.font(style.value("font-family"), weight, posture, fontSize);
    graphics.setFont(font);
    graphics.setTextAlign(TextAlignment.LEFT);
    graphics.setTextBaseline(VPos.BASELINE);
    graphics.setFill(style.color("color").map(this::color).orElse(Color.BLACK));
    graphics.fillText(text.value(), text.x(), text.baselineY());
    if (text.link().isPresent() || style.value("text-decoration").contains("underline")) {
      javafx.scene.text.Text measurement = new javafx.scene.text.Text(text.value());
      measurement.setFont(font);
      double width = measurement.getLayoutBounds().getWidth();
      graphics.setStroke(style.color("color").map(this::color).orElse(Color.BLACK));
      graphics.setLineWidth(1);
      graphics.strokeLine(text.x(), text.baselineY() + 1, text.x() + width, text.baselineY() + 1);
    }
  }

  private void paintImagePlaceholder(
      PaintCommand.ImagePlaceholder image, GraphicsContext graphics) {
    graphics.setFill(Color.rgb(242, 244, 247));
    graphics.fillRect(image.x(), image.y(), image.width(), image.height());
    graphics.setStroke(Color.rgb(174, 181, 191));
    graphics.strokeRect(image.x(), image.y(), image.width(), image.height());
    graphics.strokeLine(
        image.x() + 8,
        image.y() + image.height() - 8,
        image.x() + image.width() / 2,
        image.y() + 12);
    graphics.strokeLine(
        image.x() + image.width() / 2,
        image.y() + 12,
        image.x() + image.width() - 8,
        image.y() + image.height() - 8);
    if (!image.altText().isBlank()) {
      graphics.setFill(Color.rgb(75, 82, 93));
      graphics.setFont(Font.font(11));
      graphics.fillText(
          image.altText(), image.x() + 6, image.y() + image.height() - 5, image.width() - 12);
    }
  }

  private void paintFormControl(PaintCommand.FormControl control, GraphicsContext graphics) {
    graphics.setFill(Color.rgb(250, 251, 252));
    graphics.fillRoundRect(control.x(), control.y(), control.width(), control.height(), 4, 4);
    graphics.setStroke(Color.rgb(190, 195, 202));
    graphics.strokeRoundRect(control.x(), control.y(), control.width(), control.height(), 4, 4);
  }

  private Color color(CssColor color) {
    return new Color(
        color.red() / 255.0, color.green() / 255.0, color.blue() / 255.0, color.opacity());
  }
}
