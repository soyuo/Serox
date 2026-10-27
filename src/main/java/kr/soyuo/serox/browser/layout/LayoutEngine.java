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
    double documentWidth =
        pass.boxes.stream().mapToDouble(box -> box.x() + box.width()).max().orElse(0);
    return new LayoutResult(constraints, pass.boxes, pass.textRuns, documentWidth, documentHeight);
  }

  private static final class Pass {
    private final StyleSnapshot styles;
    private final URI documentUri;
    private final List<LayoutBox> boxes = new ArrayList<>();
    private final List<TextRun> textRuns = new ArrayList<>();
    private int paintOrder;

    private Pass(StyleSnapshot styles, URI documentUri) {
      this.styles = styles;
      this.documentUri = documentUri;
    }

    private int nextPaintOrder() {
      return paintOrder++;
    }

    private double layoutBlock(
        Element element, double originX, double originY, double availableWidth) {
      return layoutBlock(element, originX, originY, availableWidth, false);
    }

    /**
     * Lays out one block. When {@code fillWidth} is set (a flex item whose width was decided by the
     * flex algorithm) the width property is ignored and the box takes {@code availableWidth}.
     */
    private double layoutBlock(
        Element element,
        double originX,
        double originY,
        double availableWidth,
        boolean fillWidth) {
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
      double contentWidth = fillWidth ? Double.NaN : style.pixels("width", Double.NaN);
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
      final int boxPaintOrder = nextPaintOrder();
      boxes.add(null);

      double flowHeight;
      if (style.value("display").equals("flex")) {
        flowHeight = layoutFlex(element, style, contentX, contentY, contentWidth);
      } else {
        InlineFlow flow = new InlineFlow(contentX, contentWidth, contentY);
        for (Node child : element.children()) {
          if (child instanceof TextNode textNode) {
            flow.addText(textNode.data(), style, linkOf(element));
          } else if (child instanceof Element childElement) {
            ComputedStyle childStyle = styles.styleOf(childElement);
            if (childStyle.value("display").equals("none")) {
              continue;
            }
            if (childStyle.value("display").equals("block")
                || childStyle.value("display").equals("flex")) {
              flow.finishLine();
              flow.discardPendingSpace();
              double childHeight =
                  layoutBlock(childElement, contentX, contentY + flow.height(), contentWidth);
              flow.addBlockHeight(childHeight);
            } else {
              flow.addInlineElement(childElement);
            }
          }
        }
        flow.finishLine();
        flowHeight = flow.height();
      }
      double contentHeight = style.pixels("height", flowHeight);
      if (!Double.isFinite(contentHeight)) {
        contentHeight = flowHeight;
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
              style,
              boxPaintOrder));
      return marginTop + borderBoxHeight + marginBottom;
    }

    /** Lays out the visible children of a flex container and returns the content height. */
    private double layoutFlex(
        Element container,
        ComputedStyle style,
        double contentX,
        double contentY,
        double contentWidth) {
      List<Element> items = new ArrayList<>();
      for (Node child : container.children()) {
        if (child instanceof Element element
            && !styles.styleOf(element).value("display").equals("none")) {
          items.add(element);
        }
      }
      if (items.isEmpty()) {
        return 0;
      }
      double gap = Math.max(0, style.pixels("gap", 0));
      return style.value("flex-direction").equals("column")
          ? layoutFlexColumn(items, style, contentX, contentY, contentWidth, gap)
          : layoutFlexRow(items, style, contentX, contentY, contentWidth, gap);
    }

    private double layoutFlexRow(
        List<Element> items,
        ComputedStyle style,
        double startX,
        double startY,
        double width,
        double gap) {
      int count = items.size();
      double[] base = new double[count];
      double[] grow = new double[count];
      for (int i = 0; i < count; i++) {
        base[i] = outerWidthOf(items.get(i), width);
        grow[i] = growOf(styles.styleOf(items.get(i)));
      }
      double baseSum = 0;
      double growSum = 0;
      for (int i = 0; i < count; i++) {
        baseSum += base[i];
        growSum += grow[i];
      }
      double free = width - baseSum - gap * (count - 1);
      double[] finalWidth = base.clone();
      double leading = 0;
      double between = gap;
      if (free > 0 && growSum > 0) {
        for (int i = 0; i < count; i++) {
          finalWidth[i] += free * grow[i] / growSum;
        }
      } else if (free < 0 && baseSum > 0) {
        for (int i = 0; i < count; i++) {
          finalWidth[i] = Math.max(0, base[i] + free * base[i] / baseSum);
        }
      } else if (free > 0) {
        switch (style.value("justify-content")) {
          case "center" -> leading = free / 2;
          case "flex-end" -> leading = free;
          case "space-between" -> between = count > 1 ? gap + free / (count - 1) : gap;
          case "space-around" -> {
            leading = free / (2 * count);
            between = gap + free / count;
          }
          default -> {}
        }
      }
      int[] boxStart = new int[count];
      int[] textStart = new int[count];
      double[] outerHeight = new double[count];
      double cursor = startX + leading;
      double lineHeight = 0;
      for (int i = 0; i < count; i++) {
        boxStart[i] = boxes.size();
        textStart[i] = textRuns.size();
        outerHeight[i] = layoutBlock(items.get(i), cursor, startY, finalWidth[i], true);
        cursor += finalWidth[i] + between;
        lineHeight = Math.max(lineHeight, outerHeight[i]);
      }
      String align = style.value("align-items");
      for (int i = 0; i < count; i++) {
        double spare = lineHeight - outerHeight[i];
        if (spare <= 0) {
          continue;
        }
        switch (align) {
          case "center" -> shift(boxStart[i], textStart[i], 0, spare / 2);
          case "flex-end" -> shift(boxStart[i], textStart[i], 0, spare);
          case "stretch" -> {
            if (styles.styleOf(items.get(i)).value("height").equals("auto")) {
              stretchBox(boxStart[i], spare);
            }
          }
          default -> {}
        }
      }
      return lineHeight;
    }

    private double layoutFlexColumn(
        List<Element> items,
        ComputedStyle style,
        double startX,
        double startY,
        double width,
        double gap) {
      String align = style.value("align-items");
      double cursorY = startY;
      for (Element item : items) {
        double explicit = styles.styleOf(item).pixels("width", Double.NaN);
        double itemWidth;
        if (Double.isFinite(explicit) || !align.equals("stretch")) {
          itemWidth = Math.min(width, outerWidthOf(item, width));
        } else {
          itemWidth = width;
        }
        int boxFrom = boxes.size();
        int textFrom = textRuns.size();
        double height = layoutBlock(item, startX, cursorY, itemWidth, true);
        double offset =
            switch (align) {
              case "center" -> (width - itemWidth) / 2;
              case "flex-end" -> width - itemWidth;
              default -> 0;
            };
        if (offset != 0) {
          shift(boxFrom, textFrom, offset, 0);
        }
        cursorY += height + gap;
      }
      return cursorY - startY - gap;
    }

    private double growOf(ComputedStyle style) {
      double grow = parseNumber(style.value("flex-grow"));
      String flex = style.value("flex").trim();
      if (!flex.isEmpty()) {
        grow = parseNumber(flex.split("\\s+")[0]);
      }
      return Math.max(0, grow);
    }

    private double parseNumber(String value) {
      try {
        return Double.parseDouble(value.trim());
      } catch (NumberFormatException exception) {
        return 0;
      }
    }

    private double borderWidthOf(ComputedStyle style) {
      double width = style.pixels("border-width", borderShorthandWidth(style));
      return width == 0 ? borderShorthandWidth(style) : width;
    }

    /**
     * The width a flex item wants, margins included: its explicit width, or else the width of its
     * widest line of content, found by laying it out once and discarding that layout.
     */
    private double outerWidthOf(Element item, double available) {
      ComputedStyle style = styles.styleOf(item);
      double chrome =
          side(style, "margin", 3, 0)
              + side(style, "margin", 1, 0)
              + side(style, "padding", 3, 0)
              + side(style, "padding", 1, 0)
              + 2 * borderWidthOf(style);
      double explicit = style.pixels("width", Double.NaN);
      if (Double.isFinite(explicit)) {
        return explicit + chrome;
      }
      if (!style.value("flex").isBlank()) {
        // The flex shorthand means a zero basis: the item's size comes only from its share.
        return chrome;
      }
      int boxFrom = boxes.size();
      int textFrom = textRuns.size();
      int order = paintOrder;
      layoutBlock(item, 0, 0, available, true);
      LayoutBox own = boxes.get(boxFrom);
      double right = own.contentX();
      for (int i = boxFrom + 1; i < boxes.size(); i++) {
        LayoutBox box = boxes.get(i);
        boolean sized = !box.style().value("width").equals("auto");
        if (box.style().value("display").equals("inline") || sized) {
          right = Math.max(right, box.x() + box.width());
        }
      }
      for (int i = textFrom; i < textRuns.size(); i++) {
        TextRun run = textRuns.get(i);
        double glyph = Math.max(1, run.style().pixels("font-size", 16) * 0.55);
        int glyphs = run.text().codePointCount(0, run.text().length());
        right = Math.max(right, run.x() + glyphs * glyph);
      }
      boxes.subList(boxFrom, boxes.size()).clear();
      textRuns.subList(textFrom, textRuns.size()).clear();
      paintOrder = order;
      // A little slack keeps the last glyph from wrapping because of floating-point rounding.
      double content = right - own.contentX();
      return Math.min(available, content > 0 ? content + chrome + 0.5 : chrome);
    }

    /** Moves everything laid out since the given positions. */
    private void shift(int boxFrom, int textFrom, double dx, double dy) {
      for (int i = boxFrom; i < boxes.size(); i++) {
        LayoutBox box = boxes.get(i);
        boxes.set(
            i,
            new LayoutBox(
                box.element(),
                box.x() + dx,
                box.y() + dy,
                box.width(),
                box.height(),
                box.contentX() + dx,
                box.contentY() + dy,
                box.contentWidth(),
                box.contentHeight(),
                box.style(),
                box.paintOrder()));
      }
      for (int i = textFrom; i < textRuns.size(); i++) {
        TextRun run = textRuns.get(i);
        textRuns.set(
            i,
            new TextRun(
                run.x() + dx,
                run.baselineY() + dy,
                run.text(),
                run.style(),
                run.link(),
                run.paintOrder()));
      }
    }

    /** Makes a flex item as tall as its line, as align-items stretch does. */
    private void stretchBox(int boxIndex, double extra) {
      LayoutBox box = boxes.get(boxIndex);
      boxes.set(
          boxIndex,
          new LayoutBox(
              box.element(),
              box.x(),
              box.y(),
              box.width(),
              box.height() + extra,
              box.contentX(),
              box.contentY(),
              box.contentWidth(),
              box.contentHeight() + extra,
              box.style(),
              box.paintOrder()));
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
      private boolean pendingSpace;

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
        StringBuilder run = new StringBuilder();
        double runX = cursorX;
        int offset = 0;
        while (offset < text.length()) {
          int codePoint = text.codePointAt(offset);
          if (Character.isWhitespace(codePoint)) {
            while (offset < text.length()) {
              int whitespace = text.codePointAt(offset);
              if (!Character.isWhitespace(whitespace)) {
                break;
              }
              offset += Character.charCount(whitespace);
            }
            pendingSpace = cursorX > startX || !run.isEmpty();
            continue;
          }
          int wordStart = offset;
          while (offset < text.length()) {
            int wordCodePoint = text.codePointAt(offset);
            if (Character.isWhitespace(wordCodePoint)) {
              break;
            }
            offset += Character.charCount(wordCodePoint);
          }
          String word = text.substring(wordStart, offset);
          lineHeight = Math.max(lineHeight, Math.ceil(fontSize * 1.3));
          double wordWidth = word.codePoints().count() * glyphWidth;
          double spacingWidth = pendingSpace && cursorX > startX ? glyphWidth * 0.55 : 0;
          if (cursorX > startX && cursorX + spacingWidth + wordWidth > startX + maxWidth) {
            flushRun(run, runX, style, link);
            advanceLine();
            pendingSpace = false;
            runX = cursorX;
          }
          if (pendingSpace && cursorX > startX) {
            runX = appendGlyph(' ', glyphWidth * 0.55, run, style, link, runX);
          }
          pendingSpace = false;
          for (int wordOffset = 0; wordOffset < word.length(); ) {
            int wordCodePoint = word.codePointAt(wordOffset);
            wordOffset += Character.charCount(wordCodePoint);
            runX = appendGlyph(wordCodePoint, glyphWidth, run, style, link, runX);
          }
        }
        flushRun(run, runX, style, link);
      }

      private double appendGlyph(
          int codePoint,
          double advance,
          StringBuilder run,
          ComputedStyle style,
          URI link,
          double runX) {
        if (cursorX + advance > startX + maxWidth && cursorX > startX) {
          flushRun(run, runX, style, link);
          advanceLine();
          if (Character.isWhitespace(codePoint)) {
            return cursorX;
          }
          runX = cursorX;
        }
        if (run.isEmpty()) {
          runX = cursorX;
        }
        run.appendCodePoint(codePoint);
        cursorX += advance;
        return runX;
      }

      private void addInlineElement(Element element) {
        ComputedStyle style = styles.styleOf(element);
        URI link = linkOf(element);
        if (element.tagName().equals("br")) {
          advanceLine();
          return;
        }
        if (element.tagName().equals("img")) {
          addInlineBox(element, style, 96, 64);
          return;
        }
        if (element.tagName().equals("input") || element.tagName().equals("button")) {
          double controlX = cursorX;
          double controlY = cursorY;
          double controlHeight = style.pixels("height", attributePixels(element, "height", 28));
          addInlineBox(element, style, 120, 28);
          String label =
              element.tagName().equals("input")
                  ? element.attribute("value").orElse(element.attribute("placeholder").orElse(""))
                  : element.textContent();
          if (!label.isBlank()) {
            double fontSize = style.pixels("font-size", 16);
            textRuns.add(
                new TextRun(
                    controlX + 8,
                    controlY + (controlHeight + fontSize) / 2,
                    label,
                    style,
                    Optional.ofNullable(link),
                    nextPaintOrder()));
          }
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
                  runX,
                  cursorY + fontSize,
                  run.toString(),
                  style,
                  Optional.ofNullable(link),
                  nextPaintOrder()));
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
        pendingSpace = false;
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
        pendingSpace = false;
      }

      private void discardPendingSpace() {
        pendingSpace = false;
      }

      private double height() {
        return Math.max(usedHeight, cursorY - startY);
      }

      private double attributePixels(Element element, String name, double fallback) {
        try {
          return Double.parseDouble(element.attribute(name).orElse(""));
        } catch (NumberFormatException exception) {
          return fallback;
        }
      }

      private void addInlineBox(
          Element element, ComputedStyle style, double defaultWidth, double defaultHeight) {
        double width = style.pixels("width", attributePixels(element, "width", defaultWidth));
        double height = style.pixels("height", attributePixels(element, "height", defaultHeight));
        if (cursorX + width > startX + maxWidth && cursorX > startX) {
          advanceLine();
        }
        boxes.add(
            new LayoutBox(
                element,
                cursorX,
                cursorY,
                width,
                height,
                cursorX,
                cursorY,
                width,
                height,
                style,
                nextPaintOrder()));
        cursorX += width;
        lineHeight = Math.max(lineHeight, height);
      }
    }
  }
}
