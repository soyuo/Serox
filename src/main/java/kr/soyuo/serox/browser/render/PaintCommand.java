package kr.soyuo.serox.browser.render;

import java.net.URI;
import java.util.Optional;
import kr.soyuo.serox.browser.css.ComputedStyle;
import kr.soyuo.serox.browser.css.CssColor;

/** One backend-neutral operation in a page display list. */
public sealed interface PaintCommand
    permits PaintCommand.FillRect,
        PaintCommand.StrokeRect,
        PaintCommand.Text,
        PaintCommand.ImagePlaceholder,
        PaintCommand.FormControl {
  /** Fills a rectangular area with a color. */
  record FillRect(double x, double y, double width, double height, CssColor color)
      implements PaintCommand {}

  /** Strokes a rectangular border. */
  record StrokeRect(
      double x, double y, double width, double height, CssColor color, double lineWidth)
      implements PaintCommand {}

  /** Draws styled text with an optional link destination. */
  record Text(double x, double baselineY, String value, ComputedStyle style, Optional<URI> link)
      implements PaintCommand {}

  /** Draws a visual fallback when image bytes are unavailable. */
  record ImagePlaceholder(
      double x,
      double y,
      double width,
      double height,
      String altText,
      String source,
      ComputedStyle style)
      implements PaintCommand {}

  /** Draws a minimal input or button control. */
  record FormControl(
      double x, double y, double width, double height, String controlType, ComputedStyle style)
      implements PaintCommand {}
}
