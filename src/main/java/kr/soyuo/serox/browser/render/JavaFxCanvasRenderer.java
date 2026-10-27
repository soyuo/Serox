package kr.soyuo.serox.browser.render;

import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
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
    GraphicsContext graphics = canvas.getGraphicsContext2D();
    graphics.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
    graphics.save();
    graphics.beginPath();
    graphics.rect(0, 0, displayList.viewportWidth(), displayList.viewportHeight());
    graphics.closePath();
    graphics.clip();
    graphics.translate(-displayList.scrollX(), -displayList.scrollY());
    for (PaintCommand command : displayList.commands()) {
      paint(command, graphics);
    }
    graphics.restore();
  }

  private void paint(PaintCommand command, GraphicsContext graphics) {
    if (command instanceof PaintCommand.FillRect rect) {
      graphics.setFill(color(rect.color()));
      graphics.fillRect(rect.x(), rect.y(), rect.width(), rect.height());
    } else if (command instanceof PaintCommand.StrokeRect rect) {
      graphics.setStroke(color(rect.color()));
      graphics.setLineWidth(rect.lineWidth());
      graphics.strokeRect(rect.x(), rect.y(), rect.width(), rect.height());
    } else if (command instanceof PaintCommand.Text text) {
      paintText(text, graphics);
    } else if (command instanceof PaintCommand.ImagePlaceholder image) {
      paintImagePlaceholder(image, graphics);
    } else if (command instanceof PaintCommand.FormControl control) {
      paintFormControl(control, graphics);
    }
  }

  private void paintText(PaintCommand.Text text, GraphicsContext graphics) {
    ComputedStyle style = text.style();
    double fontSize = style.pixels("font-size", 16);
    FontWeight weight =
        style.value("font-weight").equalsIgnoreCase("bold") ? FontWeight.BOLD : FontWeight.NORMAL;
    Font font = Font.font(style.value("font-family"), weight, FontPosture.REGULAR, fontSize);
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
