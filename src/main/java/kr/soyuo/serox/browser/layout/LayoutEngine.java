package kr.soyuo.serox.browser.layout;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import kr.soyuo.serox.browser.css.ComputedStyle;
import kr.soyuo.serox.browser.css.StyleSnapshot;
import kr.soyuo.serox.browser.dom.Document;
import kr.soyuo.serox.browser.dom.Element;
import kr.soyuo.serox.browser.dom.Node;
import kr.soyuo.serox.browser.dom.TextNode;

/** Calculates an initial deterministic block/inline layout with a pixel-based box model. */
public final class LayoutEngine {
  /** Lays out the document body within the supplied viewport. */
  public LayoutResult layout(
      Document document, StyleSnapshot styles, LayoutConstraints constraints) {
    Pass pass = new Pass(styles, document.documentUrl());
    double documentHeight =
        document
            .body()
            .map(body -> pass.layoutBlock(body, 0, 0, constraints.viewportWidth()))
            .orElse(0.0);
    return new LayoutResult(constraints, pass.boxes, pass.textRuns, documentHeight);
  }

  private static final class Pass {
    private final StyleSnapshot styles;
    private final URI documentUri;
    private final List<LayoutBox> boxes = new ArrayList<>();
    private final List<TextRun> textRuns = new ArrayList<>();

    private Pass(StyleSnapshot styles, URI documentUri) {
      this.styles = styles;
      this.documentUri = documentUri;
    }

    private double layoutBlock(
        Element element, double originX, double originY, double availableWidth) {
      ComputedStyle style = styles.styleOf(element);
      if (style.value("display").equals("none")) {
        return 0;
      }
      double marginLeft = side(style, "margin", 3, 0);
      double marginRight = side(style, "margin", 1, 0);
      double marginTop = side(style, "margin", 0, 0);
      final double marginBottom = side(style, "margin", 2, 0);
      double paddingLeft = side(style, "padding", 3, 0);
      double paddingRight = side(style, "padding", 1, 0);
      double paddingTop = side(style, "padding", 0, 0);
      final double paddingBottom = side(style, "padding", 2, 0);
      double borderWidth = style.pixels("border-width", borderShorthandWidth(style));
      if (borderWidth == 0) {
        borderWidth = borderShorthandWidth(style);
      }
      double outerX = originX + marginLeft;
      double outerY = originY + marginTop;
      double contentWidth = style.pixels("width", Double.NaN);
      if (!Double.isFinite(contentWidth)) {
        contentWidth =
            Math.max(
                0,
                availableWidth
                    - marginLeft
                    - marginRight
                    - paddingLeft
                    - paddingRight
                    - 2 * borderWidth);
      }
      final double borderBoxWidth = contentWidth + paddingLeft + paddingRight + 2 * borderWidth;
      double contentX = outerX + borderWidth + paddingLeft;
      double contentY = outerY + borderWidth + paddingTop;
      final int boxIndex = boxes.size();
      boxes.add(null);

      InlineFlow flow = new InlineFlow(contentX, contentWidth, contentY);
      for (Node child : element.children()) {
        if (child instanceof TextNode textNode) {
          flow.addText(textNode.data(), style, linkOf(element));
        } else if (child instanceof Element childElement) {
          ComputedStyle childStyle = styles.styleOf(childElement);
          if (childStyle.value("display").equals("none")) {
            continue;
          }
          if (childStyle.value("display").equals("block")) {
            flow.finishLine();
            double childHeight =
                layoutBlock(childElement, contentX, contentY + flow.height(), contentWidth);
            flow.addBlockHeight(childHeight);
          } else {
            flow.addInlineElement(childElement);
          }
        }
      }
      flow.finishLine();
      double contentHeight = style.pixels("height", flow.height());
      if (!Double.isFinite(contentHeight)) {
        contentHeight = flow.height();
      }
      double borderBoxHeight = contentHeight + paddingTop + paddingBottom + 2 * borderWidth;
      boxes.set(
          boxIndex,
          new LayoutBox(
              element,
              outerX,
              outerY,
              borderBoxWidth,
              borderBoxHeight,
              contentX,
              contentY,
              contentWidth,
              contentHeight,
              style));
      return marginTop + borderBoxHeight + marginBottom;
    }

    private URI linkOf(Element element) {
      for (Node current = element; current instanceof Element currentElement; ) {
        if (currentElement.tagName().equals("a")) {
          try {
            return documentUri.resolve(currentElement.attribute("href").orElse(""));
          } catch (IllegalArgumentException exception) {
            return null;
          }
        }
        current = currentElement.parent().orElse(null);
      }
      return null;
    }

    private double side(ComputedStyle style, String property, int sideIndex, double fallback) {
      String sideName =
          sideIndex == 0 ? "top" : sideIndex == 1 ? "right" : sideIndex == 2 ? "bottom" : "left";
      String value = style.value(property + "-" + sideName);
      if (value.isBlank()) {
        value = style.value(property);
      }
      if (value.isBlank()) {
        return fallback;
      }
      String[] parts = value.trim().split("\\s+");
      String selected;
      if (parts.length == 1) {
        selected = parts[0];
      } else if (parts.length == 2) {
        selected = parts[sideIndex == 0 || sideIndex == 2 ? 0 : 1];
      } else if (parts.length == 3) {
        selected = parts[sideIndex == 0 ? 0 : sideIndex == 2 ? 2 : 1];
      } else {
        selected = parts[Math.min(sideIndex, parts.length - 1)];
      }
      try {
        return Double.parseDouble(selected.replace("px", ""));
      } catch (NumberFormatException exception) {
        return fallback;
      }
    }

    private double borderShorthandWidth(ComputedStyle style) {
      String border = style.value("border");
      if (border.isBlank()) {
        return 0;
      }
      String width = border.trim().split("\\s+", 2)[0].replace("px", "");
      try {
        return Double.parseDouble(width);
      } catch (NumberFormatException exception) {
        return 0;
      }
    }

    private final class InlineFlow {
      private final double startX;
      private final double maxWidth;
      private final double startY;
      private double cursorX;
      private double cursorY;
      private double lineHeight;
      private double usedHeight;

      private InlineFlow(double startX, double maxWidth, double startY) {
        this.startX = startX;
        this.maxWidth = maxWidth;
        this.startY = startY;
        cursorX = startX;
        cursorY = startY;
      }

      private void addText(String text, ComputedStyle style, URI link) {
        double fontSize = style.pixels("font-size", 16);
        double glyphWidth = Math.max(1, fontSize * 0.55);
        lineHeight = Math.max(lineHeight, Math.ceil(fontSize * 1.3));
        StringBuilder run = new StringBuilder();
        double runX = cursorX;
        for (int offset = 0; offset < text.length(); ) {
          int codePoint = text.codePointAt(offset);
          offset += Character.charCount(codePoint);
          if (codePoint == '\n' || codePoint == '\r') {
            flushRun(run, runX, style, link);
            advanceLine();
            runX = cursorX;
            continue;
          }
          boolean whitespace = Character.isWhitespace(codePoint);
          double advance = whitespace ? glyphWidth * 0.55 : glyphWidth;
          if (cursorX + advance > startX + maxWidth && cursorX > startX) {
            flushRun(run, runX, style, link);
            advanceLine();
            runX = cursorX;
            if (whitespace) {
              continue;
            }
          }
          if (run.isEmpty()) {
            runX = cursorX;
          }
          run.appendCodePoint(codePoint);
          cursorX += advance;
        }
        flushRun(run, runX, style, link);
      }

      private void addInlineElement(Element element) {
        ComputedStyle style = styles.styleOf(element);
        URI link = linkOf(element);
        if (element.tagName().equals("br")) {
          advanceLine();
          return;
        }
        if (element.tagName().equals("img")) {
          String alt = element.attribute("alt").orElse("Image");
          addText("[" + alt + "]", style, link);
          return;
        }
        for (Node child : element.children()) {
          if (child instanceof TextNode textNode) {
            addText(textNode.data(), style, link);
          } else if (child instanceof Element nested) {
            addInlineElement(nested);
          }
        }
      }

      private void flushRun(StringBuilder run, double runX, ComputedStyle style, URI link) {
        if (!run.isEmpty()) {
          double fontSize = style.pixels("font-size", 16);
          textRuns.add(
              new TextRun(
                  runX, cursorY + fontSize, run.toString(), style, Optional.ofNullable(link)));
          run.setLength(0);
        }
      }

      private void advanceLine() {
        if (lineHeight == 0) {
          lineHeight = 20;
        }
        cursorY += lineHeight;
        usedHeight = Math.max(usedHeight, cursorY - startY);
        lineHeight = 0;
        cursorX = startX;
      }

      private void finishLine() {
        if (cursorX > startX || lineHeight > 0) {
          advanceLine();
        }
      }

      private void addBlockHeight(double height) {
        usedHeight = Math.max(usedHeight, cursorY - startY + height);
        cursorY += height;
        cursorX = startX;
      }

      private double height() {
        return Math.max(usedHeight, cursorY - startY);
      }
    }
  }
}
