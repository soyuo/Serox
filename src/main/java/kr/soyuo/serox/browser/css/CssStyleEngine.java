package kr.soyuo.serox.browser.css;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kr.soyuo.serox.browser.dom.Document;
import kr.soyuo.serox.browser.dom.Element;
import kr.soyuo.serox.browser.dom.SelectorMatcher;

/** Applies embedded and inline CSS with specificity, inheritance, and initial defaults. */
public final class CssStyleEngine {
  private static final Set<String> SUPPORTED_PROPERTIES =
      Set.of(
          "color",
          "background",
          "background-color",
          "font-size",
          "font-weight",
          "font-family",
          "text-decoration",
          "margin",
          "margin-top",
          "margin-right",
          "margin-bottom",
          "margin-left",
          "padding",
          "padding-top",
          "padding-right",
          "padding-bottom",
          "padding-left",
          "border",
          "border-width",
          "border-color",
          "width",
          "height",
          "display",
          "flex-direction",
          "flex-wrap",
          "justify-content",
          "align-items",
          "gap",
          "flex-grow",
          "flex",
          "text-align",
          "font-style",
          "text-transform",
          "max-width",
          "min-width",
          "min-height",
          "max-height",
          "row-gap",
          "flex-shrink",
          "line-height",
          "white-space",
          "visibility",
          "opacity",
          "border-style",
          "border-radius",
          "border-top-width",
          "border-right-width",
          "border-bottom-width",
          "border-left-width",
          "border-top-style",
          "border-right-style",
          "border-bottom-style",
          "border-left-style",
          "border-top-color",
          "border-right-color",
          "border-bottom-color",
          "border-left-color",
          "letter-spacing",
          "word-spacing",
          "position",
          "top",
          "right",
          "bottom",
          "left",
          "box-shadow",
          "overflow-wrap",
          "word-break",
          "box-sizing");
  /** Values every property takes; resolved after the cascade. */
  private static final Set<String> GLOBAL_KEYWORDS =
      Set.of("inherit", "initial", "unset", "revert", "revert-layer");

  /**
   * Properties that only matter for motion, a pointer, scrolling, printing or pseudo-element
   * content, none of which a page drawn once has.
   */
  private static final Set<String> NO_STATIC_EFFECT =
      Set.of(
          "cursor",
          "pointer-events",
          "user-select",
          "touch-action",
          "transition",
          "transition-property",
          "transition-duration",
          "transition-timing-function",
          "transition-delay",
          "transition-behavior",
          "animation",
          "animation-name",
          "animation-duration",
          "animation-timing-function",
          "animation-delay",
          "animation-iteration-count",
          "animation-direction",
          "animation-fill-mode",
          "animation-play-state",
          "animation-composition",
          "will-change",
          "scroll-behavior",
          "scroll-margin",
          "scroll-margin-top",
          "scroll-margin-bottom",
          "scroll-padding",
          "scroll-padding-top",
          "scroll-snap-type",
          "scroll-snap-align",
          "overscroll-behavior",
          "overscroll-behavior-x",
          "overscroll-behavior-y",
          "scrollbar-width",
          "scrollbar-color",
          "scrollbar-gutter",
          "caret-color",
          "accent-color",
          "resize",
          "speak",
          "text-rendering",
          "font-display",
          "font-kerning",
          "font-feature-settings",
          "font-variant-ligatures",
          "font-variant-numeric",
          "font-optical-sizing",
          "print-color-adjust",
          "color-scheme",
          "forced-color-adjust",
          "content-visibility",
          "contain",
          "contain-intrinsic-size",
          "isolation",
          "backface-visibility",
          "appearance",
          "outline-offset",
          "image-rendering",
          "content",
          "quotes",
          "counter-reset",
          "counter-increment",
          "page-break-inside",
          "page-break-before",
          "page-break-after",
          "break-inside",
          "break-before",
          "break-after",
          "orphans",
          "widows",
          "unicode-bidi",
          "direction",
          "zoom");

  private static final Set<String> INHERITED =
      Set.of(
          "line-height",
          "white-space",
          "visibility",
          "color",
          "font-size",
          "font-weight",
          "font-family",
          "text-align",
          "font-style",
          "text-transform");
  private static final java.util.regex.Pattern IMPORTANT =
      java.util.regex.Pattern.compile("\\s*!\\s*important\\s*$", java.util.regex.Pattern.CASE_INSENSITIVE);
  private static final java.util.regex.Pattern RELATIVE_LENGTH =
      java.util.regex.Pattern.compile("-?(?:\\d+\\.?\\d*|\\.\\d+)(?:em|rem|pt|vh|vw|vmin|vmax|ch|ex)");
  private static final java.util.regex.Pattern RELATIVE_TOKEN =
      java.util.regex.Pattern.compile("(-?(?:\\d+\\.?\\d*|\\.\\d+))(em|rem|pt|vh|vw|vmin|vmax|ch|ex)(?![a-z])");
  private static final java.util.regex.Pattern VARIABLE =
      java.util.regex.Pattern.compile("var\\(\\s*(--[\\w-]+)\\s*(?:,\\s*([^()]*))?\\)");
  private static final double ROOT_FONT_SIZE = 16;
  private final CssParser parser;

  public CssStyleEngine() {
    this(new CssParser());
  }

  public CssStyleEngine(CssParser parser) {
    this.parser = parser;
  }

  /** Computes the initial supported CSS properties for every element in a document. */
  public StyleSnapshot compute(Document document) {
    List<CssRule> rules = new ArrayList<>();
    for (Element styleElement : document.getElementsByTagName("style")) {
      rules.addAll(parser.parse(styleElement.textContent()));
    }
    Map<Element, ComputedStyle> computed = new IdentityHashMap<>();
    List<String> unsupported = new ArrayList<>();
    Map<CssRule, Set<Element>> matched = new IdentityHashMap<>();
    List<Element> elements =
        document.descendants().stream()
            .filter(Element.class::isInstance)
            .map(Element.class::cast)
            .toList();
    Map<String, SelectorMatcher> matchers = new HashMap<>();
    for (CssRule rule : rules) {
      try {
        // One selector list becomes many rules; parse each selector once.
        SelectorMatcher matcher =
            matchers.computeIfAbsent(rule.selector(), SelectorMatcher::new);
        Set<Element> hits = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (Element element : elements) {
          if (matcher.matches(element)) {
            hits.add(element);
          }
        }
        matched.put(rule, hits);
      } catch (SelectorMatcher.SyntaxException unsupportedSelector) {
        if (!rule.selector().matches("(?s).*::?-(ms|moz|webkit|o)-.*")) {
          unsupported.add("selector: " + rule.selector());
        }
      }
    }
    // Each element's matching rules, in source order, so the cascade skips rules that missed.
    Map<Element, List<CssRule>> byElement = new IdentityHashMap<>();
    for (CssRule rule : rules) {
      Set<Element> hits = matched.get(rule);
      if (hits != null) {
        for (Element hit : hits) {
          byElement.computeIfAbsent(hit, key -> new ArrayList<>()).add(rule);
        }
      }
    }
    for (Element element : elements) {
      Map<String, Candidate> cascade = new HashMap<>();
      List<CssRule> own = byElement.getOrDefault(element, List.of());
      Map<String, String> custom = customProperties(element, own, computed);
      for (CssRule rule : own) {
        Specificity specificity = Specificity.of(rule.selector());
        rule.declarations()
            .forEach(
                (name, value) ->
                    apply(
                        cascade, name, value, specificity, rule.sourceOrder(), unsupported, custom));
      }
      element
          .attribute("style")
          .ifPresent(
              inline ->
                  parser
                      .parseDeclarations(inline)
                      .forEach(
                          (name, value) ->
                              apply(
                                  cascade,
                                  name,
                                  value,
                                  new Specificity(1_000, 0, 0),
                                  Integer.MAX_VALUE,
                                  unsupported,
                                  custom)));
      Map<String, String> values = defaults(element);
      values.putAll(custom);
      element
          .parent()
          .filter(Element.class::isInstance)
          .map(Element.class::cast)
          .map(computed::get)
          .ifPresent(
              parent -> {
                if (parent != null) {
                  for (String property : INHERITED) {
                    boolean heading = element.tagName().matches("h[1-6]");
                    if (!(property.equals("color") && element.tagName().equals("a"))
                        && !(property.equals("font-size") && heading)
                        && !(property.equals("font-weight") && heading)) {
                      values.put(property, parent.value(property));
                    }
                  }
                }
              });
      ComputedStyle parentStyle =
          element.parent().orElse(null) instanceof Element parentElement
              ? computed.get(parentElement)
              : null;
      Map<String, String> initial = defaults(element);
      cascade.forEach(
          (property, candidate) -> {
            String value = candidate.value();
            boolean inherit =
                value.equals("inherit")
                    || (value.equals("unset") || value.startsWith("revert"))
                        && INHERITED.contains(property);
            if (inherit) {
              String fromParent = parentStyle == null ? "" : parentStyle.value(property);
              value = fromParent.isEmpty() ? initial.getOrDefault(property, "") : fromParent;
            } else if (GLOBAL_KEYWORDS.contains(value)) {
              value = initial.getOrDefault(property, "");
            }
            if (value.isEmpty()) {
              values.remove(property);
            } else {
              values.put(property, value);
            }
          });
      String ownColor = values.getOrDefault("color", "black");
      values.replaceAll(
          (property, value) ->
              value.equalsIgnoreCase("currentcolor") && !property.equals("color")
                  ? ownColor
                  : value);
      if (values.getOrDefault("color", "").equalsIgnoreCase("currentcolor")) {
        values.put("color", parentStyle == null ? "black" : parentStyle.value("color"));
      }
      // Opacity multiplies down the tree; the painter reads the product.
      double parentOpacity =
          parentStyle == null ? 1 : parseOr(parentStyle.value("-serox-opacity"), 1);
      values.put(
          "-serox-opacity",
          trim(parentOpacity * Math.max(0, Math.min(1, opacityOf(values.get("opacity"))))));
      normalize(values, parentFontSize(element, computed));
      resolveUnits(values, parentFontSize(element, computed));
      computed.put(element, new ComputedStyle(values));
    }
    return new StyleSnapshot(computed, unsupported.stream().distinct().toList());
  }

  private void apply(
      Map<String, Candidate> cascade,
      String property,
      String value,
      Specificity specificity,
      int sourceOrder,
      List<String> unsupported,
      Map<String, String> custom) {
    if (property.startsWith("--")) {
      return;
    }
    if (value.contains("var(")) {
      String substituted = substituteVariables(value, custom);
      if (substituted == null) {
        unsupported.add(property + ": " + value);
        return;
      }
      if (substituted.equals(INVALID_AT_COMPUTED_TIME)
          || substituted.matches("(?i).*(^|\\s)initial(\\s|$).*") && !substituted.trim()
              .equalsIgnoreCase("initial")) {
        // The "space toggle" pattern: a toggle variable set to initial voids the declaration.
        return;
      }
      value = substituted;
    }
    String resolved = CssValues.resolveFunctions(value);
    if (resolved == null) {
      unsupported.add(property + ": " + value);
      return;
    }
    value = resolved;
    boolean important = IMPORTANT.matcher(value).find();
    if (important) {
      value = IMPORTANT.matcher(value).replaceFirst("").trim();
    }
    if (isVendorPrefixed(property) || NO_STATIC_EFFECT.contains(property)) {
      // Prefixed copies sit next to the standard property, and these change nothing in a page
      // drawn once without motion or a pointer, so they are not reported as ignored.
      return;
    }
    String keyword = value.trim().toLowerCase(java.util.Locale.ROOT);
    if (GLOBAL_KEYWORDS.contains(keyword)) {
      for (String longhand : longhandsOf(property)) {
        if (SUPPORTED_PROPERTIES.contains(longhand)) {
          put(cascade, longhand, keyword, specificity, sourceOrder, important);
        }
      }
      return;
    }
    Map<String, String> expanded = expand(property, value);
    if (expanded == null) {
      unsupported.add(property + ": " + value);
      return;
    }
    if (expanded.isEmpty()) {
      return;
    }
    for (Map.Entry<String, String> entry : expanded.entrySet()) {
      String name = entry.getKey();
      String part = entry.getValue();
      if (name.equals("-serox-background-image")) {
        unsupported.add("background-image: " + part);
        continue;
      }
      if (!SUPPORTED_PROPERTIES.contains(name) || !isSupportedValue(name, part)) {
        unsupported.add(property + ": " + value);
        continue;
      }
      put(cascade, name, part, specificity, sourceOrder, important);
    }
  }

  private static void put(
      Map<String, Candidate> cascade,
      String property,
      String value,
      Specificity specificity,
      int sourceOrder,
      boolean important) {
    Candidate candidate = new Candidate(value, specificity, sourceOrder, important);
    cascade.merge(property, candidate, (left, right) -> left.compareTo(right) <= 0 ? right : left);
  }

  private static boolean isVendorPrefixed(String property) {
    return property.startsWith("-webkit-")
        || property.startsWith("-moz-")
        || property.startsWith("-ms-")
        || property.startsWith("-o-");
  }

  /** The longhands a shorthand sets, or the property itself. */
  private static List<String> longhandsOf(String property) {
    return switch (property) {
      case "margin", "padding" ->
          List.of(property + "-top", property + "-right", property + "-bottom", property + "-left");
      case "border" -> List.of("border-width", "border-color", "border-style");
      case "font" ->
          List.of("font-style", "font-weight", "font-size", "line-height", "font-family");
      case "flex-flow" -> List.of("flex-direction", "flex-wrap");
      case "background" -> List.of("background-color");
      default -> List.of(property);
    };
  }

  /**
   * Turns one declaration into the longhands the engine keeps. Returns an empty map for parts that
   * have nothing to draw, and null when the value cannot be read at all.
   */
  private Map<String, String> expand(String property, String value) {
    String text = value.trim();
    String lower = text.toLowerCase(java.util.Locale.ROOT);
    Map<String, String> out = new java.util.LinkedHashMap<>();
    switch (property) {
      case "margin", "padding" -> {
        String[] parts = lower.split("\\s+");
        if (parts.length > 4) {
          return null;
        }
        String top = parts[0];
        String right = parts.length > 1 ? parts[1] : top;
        String bottom = parts.length > 2 ? parts[2] : top;
        String left = parts.length > 3 ? parts[3] : right;
        out.put(property + "-top", top);
        out.put(property + "-right", right);
        out.put(property + "-bottom", bottom);
        out.put(property + "-left", left);
      }
      case "margin-inline", "padding-inline", "margin-block", "padding-block" -> {
        String base = property.substring(0, property.indexOf('-'));
        boolean inline = property.endsWith("inline");
        String[] parts = lower.split("\\s+");
        String start = parts[0];
        String end = parts.length > 1 ? parts[1] : start;
        out.put(base + (inline ? "-left" : "-top"), start);
        out.put(base + (inline ? "-right" : "-bottom"), end);
      }
      case "margin-inline-start", "padding-inline-start" ->
          out.put(property.substring(0, property.indexOf('-')) + "-left", lower);
      case "margin-inline-end", "padding-inline-end" ->
          out.put(property.substring(0, property.indexOf('-')) + "-right", lower);
      case "margin-block-start", "padding-block-start" ->
          out.put(property.substring(0, property.indexOf('-')) + "-top", lower);
      case "margin-block-end", "padding-block-end" ->
          out.put(property.substring(0, property.indexOf('-')) + "-bottom", lower);
      case "flex-flow" -> {
        for (String part : lower.split("\\s+")) {
          if (part.equals("row") || part.equals("column")
              || part.equals("row-reverse") || part.equals("column-reverse")) {
            out.put("flex-direction", part);
          } else {
            out.put("flex-wrap", part);
          }
        }
      }
      case "column-gap", "grid-column-gap" -> out.put("gap", lower);
      case "grid-gap" -> out.put("gap", lower.split("\\s+")[0]);
      case "gap" -> {
        String[] parts = lower.split("\\s+");
        out.put("gap", parts.length > 1 ? parts[1] : parts[0]);
        out.put("row-gap", parts[0]);
      }
      case "place-items" -> out.put("align-items", lower.split("\\s+")[0]);
      case "text-decoration-line" -> out.put("text-decoration", lower);
      case "text-decoration" -> {
        if (lower.contains("underline")) {
          out.put("text-decoration", "underline");
        } else if (lower.contains("none") || lower.contains("line-through")
            || lower.contains("overline")) {
          out.put("text-decoration", "none");
        } else {
          return null;
        }
      }
      case "background" -> {
        // Only the color of a background is drawn; an image layer is reported on its own.
        if (lower.contains("url(") || lower.contains("gradient(")) {
          out.put("-serox-background-image", lower);
        }
        CssColor color = null;
        for (String token : colorTokens(lower.replaceAll("[a-z-]*gradient\\(.*\\)|url\\([^)]*\\)", " "))) {
          java.util.Optional<CssColor> parsed = CssColor.parse(token);
          if (parsed.isPresent()) {
            color = parsed.get();
            out.put("background-color", token);
          }
        }
        if (color == null && !lower.equals("none") && out.isEmpty()) {
          // Position, size or repeat alone: nothing to draw.
          return Map.of();
        }
        if (lower.equals("none")) {
          out.put("background-color", "transparent");
        }
      }
      case "background-image" -> {
        if (!lower.equals("none")) {
          return null;
        }
        return Map.of();
      }
      case "background-position", "background-size", "background-repeat",
          "background-attachment", "background-origin", "background-clip" -> {
        return Map.of();
      }
      case "font" -> {
        Map<String, String> font = expandFont(text);
        if (font == null) {
          return null;
        }
        out.putAll(font);
      }
      case "border-top", "border-right", "border-bottom", "border-left" -> {
        String[] border = borderParts(lower);
        if (border == null) {
          return null;
        }
        out.put(property + "-width", border[0]);
        out.put(property + "-style", border[1]);
        if (border[2] != null) {
          out.put(property + "-color", border[2]);
        }
      }
      case "border" -> {
        String[] border = borderParts(lower);
        if (border == null) {
          return null;
        }
        out.put("border-width", border[0]);
        out.put("border-style", border[1]);
        if (border[2] != null) {
          out.put("border-color", border[2]);
        }
        for (String side : List.of("top", "right", "bottom", "left")) {
          out.put("border-" + side + "-width", border[0]);
          out.put("border-" + side + "-style", border[1]);
          if (border[2] != null) {
            out.put("border-" + side + "-color", border[2]);
          }
        }
      }
      case "border-width", "border-style", "border-color" -> {
        String kind = property.substring("border-".length());
        String[] parts =
            kind.equals("color") ? colorTokens(lower).toArray(String[]::new) : lower.split("\\s+");
        if (parts.length == 0 || parts.length > 4) {
          return null;
        }
        out.put(property, parts[0]);
        String[] sides = {"top", "right", "bottom", "left"};
        for (int i = 0; i < 4; i++) {
          int index =
              switch (parts.length) {
                case 1 -> 0;
                case 2 -> i % 2;
                case 3 -> i == 3 ? 1 : i;
                default -> i;
              };
          out.put("border-" + sides[i] + "-" + kind, parts[index]);
        }
      }
      case "border-block", "border-inline" -> {
        String[] border = borderParts(lower);
        if (border == null) {
          return null;
        }
        List<String> sides =
            property.endsWith("block") ? List.of("top", "bottom") : List.of("left", "right");
        for (String side : sides) {
          out.put("border-" + side + "-width", border[0]);
          out.put("border-" + side + "-style", border[1]);
          if (border[2] != null) {
            out.put("border-" + side + "-color", border[2]);
          }
        }
      }
      case "border-radius" -> out.put("border-radius", lower.split("\\s*/\\s*")[0].split("\\s+")[0]);
      case "word-wrap" -> out.put("overflow-wrap", lower);
      case "inset" -> {
        String[] parts = lower.split("\\s+");
        String top = parts[0];
        String right = parts.length > 1 ? parts[1] : top;
        String bottom = parts.length > 2 ? parts[2] : top;
        String left = parts.length > 3 ? parts[3] : right;
        out.put("top", top);
        out.put("right", right);
        out.put("bottom", bottom);
        out.put("left", left);
      }
      case "outline" -> {
        if (lower.equals("none") || lower.equals("0") || lower.startsWith("0 ")
            || lower.startsWith("none ")) {
          return Map.of();
        }
        return null;
      }
      case "outline-style", "outline-width", "outline-color" -> {
        return lower.equals("none") || lower.equals("0") ? Map.of() : null;
      }
      default -> out.put(property, text);
    }
    return out;
  }

  /** The color-like tokens of a value, keeping functions such as rgb(...) whole. */
  private static List<String> colorTokens(String value) {
    List<String> tokens = new ArrayList<>();
    int depth = 0;
    StringBuilder current = new StringBuilder();
    for (char c : value.toCharArray()) {
      if (c == '(') {
        depth++;
      } else if (c == ')') {
        depth--;
      }
      if (Character.isWhitespace(c) && depth == 0) {
        if (!current.isEmpty()) {
          tokens.add(current.toString());
          current.setLength(0);
        }
      } else {
        current.append(c);
      }
    }
    if (!current.isEmpty()) {
      tokens.add(current.toString());
    }
    return tokens;
  }

  private static final Set<String> BORDER_STYLES =
      Set.of(
          "none", "hidden", "solid", "dashed", "dotted", "double", "groove", "ridge", "inset",
          "outset");

  /** Width, style and color (or null) of a border shorthand, or null when unreadable. */
  private String[] borderParts(String value) {
    String width = "medium";
    String style = "none";
    String color = null;
    for (String token : colorTokens(value)) {
      if (BORDER_STYLES.contains(token)) {
        style = token;
      } else if (Set.of("thin", "medium", "thick").contains(token) || isLength(token)) {
        width = token;
      } else if (CssColor.parse(token).isPresent() || token.equals("currentcolor")) {
        color = token;
      } else {
        return null;
      }
    }
    width =
        switch (width) {
          case "thin" -> "1px";
          case "medium" -> "3px";
          case "thick" -> "5px";
          default -> width;
        };
    if (style.equals("none") || style.equals("hidden")) {
      width = "0px";
    }
    return new String[] {width, style, color};
  }

  /** {@code [style] [variant] [weight] [stretch] size[/line-height] family}, or a system font. */
  private Map<String, String> expandFont(String value) {
    String lower = value.toLowerCase(java.util.Locale.ROOT).trim();
    if (Set.of("caption", "icon", "menu", "message-box", "small-caption", "status-bar")
        .contains(lower)) {
      return Map.of();
    }
    Map<String, String> out = new java.util.LinkedHashMap<>();
    out.put("font-style", "normal");
    out.put("font-weight", "normal");
    out.put("line-height", "normal");
    String[] tokens = value.trim().split("\\s+");
    int i = 0;
    for (; i < tokens.length; i++) {
      String token = tokens[i].toLowerCase(java.util.Locale.ROOT);
      if (token.equals("italic") || token.equals("oblique")) {
        out.put("font-style", token);
      } else if (token.equals("bold") || token.equals("bolder") || token.equals("lighter")
          || token.matches("[1-9]00")) {
        out.put("font-weight", token);
      } else if (!Set.of("normal", "small-caps", "condensed", "expanded", "semi-condensed",
              "semi-expanded")
          .contains(token)) {
        break;
      }
    }
    if (i >= tokens.length) {
      return null;
    }
    String size = tokens[i];
    int slash = size.indexOf('/');
    if (slash > 0) {
      out.put("line-height", size.substring(slash + 1));
      size = size.substring(0, slash);
    } else if (i + 1 < tokens.length && tokens[i + 1].startsWith("/")) {
      String rest = tokens[i + 1].substring(1);
      if (rest.isEmpty() && i + 2 < tokens.length) {
        rest = tokens[i + 2];
        i++;
      }
      out.put("line-height", rest);
      i++;
    }
    out.put("font-size", size);
    String family = String.join(" ", java.util.Arrays.copyOfRange(tokens, i + 1, tokens.length));
    if (family.isBlank()) {
      return null;
    }
    out.put("font-family", family);
    return out;
  }

  private boolean isSupportedValue(String property, String value) {
    String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
    return switch (property) {
      case "display" ->
          Set.of("block", "inline", "none", "flex", "inline-block", "inline-flex", "list-item",
                  "flow-root", "grid", "inline-grid", "contents", "table", "table-row",
                  "table-cell", "table-row-group", "table-header-group", "table-footer-group",
                  "table-caption", "inline-table", "block flex", "block flow", "inline flow-root")
              .contains(normalized);
      case "flex-direction" ->
          Set.of("row", "column", "row-reverse", "column-reverse").contains(normalized);
      case "flex-wrap" -> Set.of("nowrap", "wrap", "wrap-reverse").contains(normalized);
      case "row-gap" -> normalized.equals("normal") || isLength(normalized);
      case "flex-shrink" -> isNonNegativeNumber(normalized);
      case "line-height" ->
          normalized.equals("normal")
              || isLength(normalized)
              || isPercentage(normalized)
              || isNonNegativeNumber(normalized);
      case "white-space" ->
          Set.of("normal", "nowrap", "pre", "pre-wrap", "pre-line", "break-spaces")
              .contains(normalized);
      case "visibility" -> Set.of("visible", "hidden", "collapse").contains(normalized);
      case "opacity" -> isNonNegativeNumber(normalized) || isPercentage(normalized);
      case "min-height" -> normalized.equals("auto") || isLength(normalized);
      case "max-height" -> normalized.equals("none") || isLength(normalized);
      case "border-style",
          "border-top-style",
          "border-right-style",
          "border-bottom-style",
          "border-left-style" ->
          BORDER_STYLES.contains(normalized);
      case "border-top-width", "border-right-width", "border-bottom-width", "border-left-width" ->
          isLength(normalized) || Set.of("thin", "medium", "thick").contains(normalized);
      case "border-top-color", "border-right-color", "border-bottom-color", "border-left-color" ->
          CssColor.parse(normalized).isPresent() || normalized.equals("currentcolor");
      case "border-radius" -> isLength(normalized) || isPercentage(normalized);
      case "letter-spacing", "word-spacing" -> normalized.equals("normal") || isLength(normalized);
      case "box-sizing" -> Set.of("content-box", "border-box").contains(normalized);
      // Only static and relative positioning are laid out; others stay reported.
      case "position" -> Set.of("static", "relative").contains(normalized);
      case "top", "right", "bottom", "left" ->
          normalized.equals("auto") || isLength(normalized) || isSignedPercentage(normalized);
      case "box-shadow" -> normalized.equals("none") || BoxShadow.parse(normalized) != null;
      // Long words are broken to fit the line, which is what these values ask for.
      case "overflow-wrap" -> Set.of("break-word", "anywhere").contains(normalized);
      case "word-break" -> Set.of("break-all", "break-word").contains(normalized);

      case "justify-content" ->
          Set.of("flex-start", "center", "flex-end", "space-between", "space-around",
                  "space-evenly", "start", "end", "normal", "left", "right", "stretch")
              .contains(normalized);
      case "align-items" ->
          Set.of("stretch", "flex-start", "center", "flex-end", "start", "end", "baseline",
                  "normal", "self-start", "self-end")
              .contains(normalized);
      case "gap" -> isLength(normalized);
      case "flex-grow" -> isNonNegativeNumber(normalized);
      case "flex" -> isNonNegativeNumber(normalized.split("\\s+")[0]);
      case "color", "background", "background-color", "border-color" ->
          CssColor.parse(normalized).isPresent() || normalized.equals("currentcolor");
      case "text-align" ->
          Set.of("left", "center", "right", "start", "end", "justify", "-webkit-center")
              .contains(normalized);
      case "font-style" -> Set.of("normal", "italic", "oblique").contains(normalized);
      case "text-transform" ->
          Set.of("none", "uppercase", "lowercase", "capitalize").contains(normalized);
      case "max-width" ->
          normalized.equals("none") || isLength(normalized) || isPercentage(normalized)
              || Set.of("fit-content", "max-content", "min-content").contains(normalized);
      case "min-width" ->
          normalized.equals("auto") || isLength(normalized) || isPercentage(normalized)
              || Set.of("fit-content", "max-content", "min-content").contains(normalized);
      case "font-weight" ->
          Set.of("normal", "bold", "bolder", "lighter").contains(normalized)
              || normalized.matches("[1-9][0-9]{0,2}");
      case "font-size" ->
          isLength(normalized)
              || isPercentage(normalized)
              || Set.of("xx-small", "x-small", "small", "medium", "large", "x-large", "xx-large",
                      "xxx-large", "smaller", "larger")
                  .contains(normalized);
      case "text-decoration" -> Set.of("none", "underline").contains(normalized);
      case "width" ->
          normalized.equals("auto") || isLength(normalized) || isPercentage(normalized)
              || Set.of("fit-content", "max-content", "min-content").contains(normalized);
      case "height" ->
          normalized.equals("auto") || isLength(normalized)
              || Set.of("fit-content", "max-content", "min-content").contains(normalized);
      case "margin", "margin-top", "margin-right", "margin-bottom", "margin-left" ->
          java.util.Arrays.stream(normalized.split("\\s+"))
              .allMatch(part -> part.equals("auto") || isLength(part) || isSignedPercentage(part));
      case "padding",
          "padding-top",
          "padding-right",
          "padding-bottom",
          "padding-left",
          "border-width" ->
          java.util.Arrays.stream(normalized.split("\\s+"))
              .allMatch(part -> isLength(part)
                  || !property.equals("border-width") && isPercentage(part));
      case "border" -> isBorderValue(normalized);
      default -> true;
    };
  }

  /**
   * Maps values the layout handles the same way onto the ones it reads: table and list boxes are
   * blocks, cells and inline blocks flow inline, logical alignment words become physical ones, and
   * keyword font sizes become pixels.
   */
  private static void normalize(Map<String, String> values, double parentSize) {
    values.computeIfPresent(
        "display",
        (key, display) ->
            switch (display.toLowerCase(java.util.Locale.ROOT)) {
              case "list-item", "flow-root", "grid", "table", "table-row", "table-row-group",
                  "table-header-group", "table-footer-group", "table-caption", "block flow" ->
                  "block";
              case "inline-block", "inline-table", "inline-grid", "table-cell", "contents",
                  "inline flow-root" ->
                  "inline";
              case "inline-flex", "block flex" -> "flex";
              default -> display;
            });
    values.computeIfPresent(
        "justify-content",
        (key, value) ->
            switch (value) {
              case "start", "left", "normal", "stretch" -> "flex-start";
              case "end", "right" -> "flex-end";
              default -> value;
            });
    values.computeIfPresent(
        "align-items",
        (key, value) ->
            switch (value) {
              case "start", "self-start", "baseline" -> "flex-start";
              case "end", "self-end" -> "flex-end";
              case "normal" -> "stretch";
              default -> value;
            });
    values.computeIfPresent(
        "text-align",
        (key, value) ->
            switch (value) {
              case "justify", "start" -> "left";
              case "-webkit-center" -> "center";
              default -> value;
            });
    String size = values.getOrDefault("font-size", "").toLowerCase(java.util.Locale.ROOT);
    double keyword =
        switch (size) {
          case "xx-small" -> 9;
          case "x-small" -> 10;
          case "small" -> 13;
          case "medium" -> 16;
          case "large" -> 18;
          case "x-large" -> 24;
          case "xx-large" -> 32;
          case "xxx-large" -> 48;
          case "smaller" -> parentSize / 1.2;
          case "larger" -> parentSize * 1.2;
          default -> Double.NaN;
        };
    if (Double.isFinite(keyword)) {
      values.put("font-size", trim(keyword) + "px");
    }
    for (String side : List.of("top", "right", "bottom", "left")) {
      values.computeIfPresent(
          "border-" + side + "-width",
          (key, value) ->
              switch (value) {
                case "thin" -> "1px";
                case "medium" -> "3px";
                case "thick" -> "5px";
                default -> value;
              });
    }
  }

  private static double opacityOf(String value) {
    if (value == null || value.isBlank()) {
      return 1;
    }
    String text = value.trim();
    return text.endsWith("%")
        ? parseOr(text.substring(0, text.length() - 1), 100) / 100
        : parseOr(text, 1);
  }

  private static double parseOr(String text, double fallback) {
    try {
      return Double.parseDouble(text.trim());
    } catch (NumberFormatException | NullPointerException unreadable) {
      return fallback;
    }
  }

  private boolean isNonNegativeNumber(String value) {
    try {
      return Double.parseDouble(value) >= 0;
    } catch (NumberFormatException exception) {
      return false;
    }
  }

  private boolean isSignedPercentage(String value) {
    return value.startsWith("-") ? isPercentage(value.substring(1)) : isPercentage(value);
  }

  private boolean isPercentage(String value) {
    if (!value.endsWith("%")) {
      return false;
    }
    try {
      return Double.parseDouble(value.substring(0, value.length() - 1)) >= 0;
    } catch (NumberFormatException exception) {
      return false;
    }
  }

  private boolean isLength(String value) {
    if (RELATIVE_LENGTH.matcher(value).matches()) {
      return true;
    }
    String normalized = value.endsWith("px") ? value.substring(0, value.length() - 2) : value;
    try {
      Double.parseDouble(normalized);
      return true;
    } catch (NumberFormatException exception) {
      return false;
    }
  }

  private boolean isBorderValue(String value) {
    String[] parts = value.split("\\s+");
    return parts.length >= 1
        && isLength(parts[0])
        && java.util.Arrays.stream(parts)
            .skip(1)
            .allMatch(part -> part.equals("solid") || CssColor.parse(part).isPresent());
  }

  /**
   * The custom properties ({@code --name}) in effect for an element: those of its parent, then
   * those the element's own rules and inline style set.
   */
  private Map<String, String> customProperties(
      Element element, List<CssRule> rules, Map<Element, ComputedStyle> computed) {
    Map<String, String> custom = new HashMap<>();
    element
        .parent()
        .filter(Element.class::isInstance)
        .map(Element.class::cast)
        .map(computed::get)
        .ifPresent(
            parent ->
                parent
                    .values()
                    .forEach(
                        (name, value) -> {
                          if (name.startsWith("--")) {
                            custom.put(name, value);
                          }
                        }));
    Map<String, Candidate> own = new HashMap<>();
    for (CssRule rule : rules) {
      Specificity specificity = Specificity.of(rule.selector());
      rule.declarations()
          .forEach((name, value) -> keepCustom(own, name, value, specificity, rule.sourceOrder()));
    }
    element
        .attribute("style")
        .ifPresent(
            inline ->
                parser
                    .parseDeclarations(inline)
                    .forEach(
                        (name, value) ->
                            keepCustom(
                                own, name, value, new Specificity(1_000, 0, 0), Integer.MAX_VALUE)));
    own.forEach((name, candidate) -> custom.put(name, candidate.value()));
    return custom;
  }

  private static void keepCustom(
      Map<String, Candidate> own, String name, String value, Specificity specificity, int order) {
    if (name.startsWith("--")) {
      boolean important = IMPORTANT.matcher(value).find();
      String clean = IMPORTANT.matcher(value).replaceFirst("").trim();
      own.merge(
          name,
          new Candidate(clean, specificity, order, important),
          (left, right) -> left.compareTo(right) <= 0 ? right : left);
    }
  }

  /** Replaces {@code var(--x, fallback)} with its value; null when one has neither. */
  /** A var() that resolves to nothing: the declaration is dropped, as a browser does. */
  static final String INVALID_AT_COMPUTED_TIME = "\u0000invalid";

  static String substituteVariables(String value, Map<String, String> custom) {
    String current = value;
    for (int round = 0; round < 6 && current.contains("var("); round++) {
      java.util.regex.Matcher matcher = VARIABLE.matcher(current);
      StringBuilder out = new StringBuilder();
      while (matcher.find()) {
        String found = custom.get(matcher.group(1));
        // A custom property set to initial holds the guaranteed-invalid value on purpose.
        boolean voided = found != null && found.trim().equalsIgnoreCase("initial");
        if (found == null || voided) {
          found = matcher.group(2);
        }
        if (found == null) {
          return voided ? INVALID_AT_COMPUTED_TIME : null;
        }
        matcher.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(found.trim()));
      }
      matcher.appendTail(out);
      current = out.toString();
    }
    return current.contains("var(") ? null : current;
  }

  private static double parentFontSize(Element element, Map<Element, ComputedStyle> computed) {
    return element
        .parent()
        .filter(Element.class::isInstance)
        .map(Element.class::cast)
        .map(computed::get)
        .map(parent -> parent.pixels("font-size", ROOT_FONT_SIZE))
        .orElse(ROOT_FONT_SIZE);
  }

  /** Turns em, rem, pt and a percent font size into pixels, so later stages see only pixels. */
  private static void resolveUnits(Map<String, String> values, double parentSize) {
    String fontSize = values.get("font-size").trim().toLowerCase(java.util.Locale.ROOT);
    double size;
    try {
      if (fontSize.endsWith("%")) {
        size = parentSize * Double.parseDouble(fontSize.substring(0, fontSize.length() - 1)) / 100;
      } else if (fontSize.endsWith("rem")) {
        size = ROOT_FONT_SIZE * Double.parseDouble(fontSize.substring(0, fontSize.length() - 3));
      } else if (fontSize.endsWith("em")) {
        size = parentSize * Double.parseDouble(fontSize.substring(0, fontSize.length() - 2));
      } else if (fontSize.endsWith("pt")) {
        size = Double.parseDouble(fontSize.substring(0, fontSize.length() - 2)) * 96 / 72;
      } else {
        size =
            Double.parseDouble(
                fontSize.endsWith("px") ? fontSize.substring(0, fontSize.length() - 2) : fontSize);
      }
    } catch (NumberFormatException unreadable) {
      size = parentSize;
    }
    final double own = Math.max(0, Math.min(size, 1000));
    values.put("font-size", trim(own) + "px");
    values.replaceAll(
        (property, value) -> {
          if (property.equals("font-size") || !RELATIVE_TOKEN.matcher(value).find()) {
            return value;
          }
          return RELATIVE_TOKEN
              .matcher(value)
              .replaceAll(
                  match -> {
                    double number = Double.parseDouble(match.group(1));
                    double pixels =
                        switch (match.group(2)) {
                          case "em" -> number * own;
                          case "rem" -> number * ROOT_FONT_SIZE;
                          case "vw" -> number * CssValues.VIEWPORT_WIDTH / 100;
                          case "vh" -> number * CssValues.VIEWPORT_HEIGHT / 100;
                          case "vmin" ->
                              number * Math.min(CssValues.VIEWPORT_WIDTH, CssValues.VIEWPORT_HEIGHT)
                                  / 100;
                          case "vmax" ->
                              number * Math.max(CssValues.VIEWPORT_WIDTH, CssValues.VIEWPORT_HEIGHT)
                                  / 100;
                          case "ch", "ex" -> number * own * 0.5;
                          default -> number * 96 / 72;
                        };
                    return java.util.regex.Matcher.quoteReplacement(trim(pixels) + "px");
                  });
        });
  }

  private static String trim(double number) {
    return number == Math.rint(number)
        ? Long.toString((long) number)
        : Double.toString(Math.round(number * 100) / 100.0);
  }

  private Map<String, String> defaults(Element element) {
    Map<String, String> defaults = new HashMap<>();
    defaults.put("color", "black");
    defaults.put("background-color", "transparent");
    defaults.put("font-size", "16px");
    defaults.put("font-weight", "normal");
    defaults.put("font-family", "Serox Sans");
    defaults.put("display", defaultDisplay(element.tagName()));
    defaults.put("margin", "0px");
    defaults.put("padding", "0px");
    defaults.put("border-width", "0px");
    defaults.put("border-color", "black");
    defaults.put("width", "auto");
    defaults.put("height", "auto");
    defaults.put("flex-direction", "row");
    defaults.put("flex-wrap", "nowrap");
    defaults.put("justify-content", "flex-start");
    defaults.put("align-items", "stretch");
    defaults.put("gap", "0px");
    defaults.put("flex-grow", "0");
    defaults.put("text-align", "left");
    defaults.put("font-style", "normal");
    defaults.put("text-transform", "none");
    defaults.put("max-width", "none");
    defaults.put("min-width", "0px");
    defaults.put("line-height", "normal");
    defaults.put("white-space", "normal");
    defaults.put("visibility", "visible");
    defaults.put("opacity", "1");
    defaults.put("flex-shrink", "1");
    defaults.put("box-sizing", "content-box");
    defaults.put("position", "static");
    if (element.tagName().equals("body")) {
      defaults.put("margin", "8px");
    }
    if (element.tagName().matches("h[1-6]")) {
      defaults.put("font-weight", "bold");
      defaults.put(
          "font-size",
          switch (element.tagName()) {
            case "h1" -> "32px";
            case "h2" -> "24px";
            case "h3" -> "20px";
            default -> "16px";
          });
    }
    if (element.tagName().equals("p")) {
      defaults.put("margin", "16px 0px");
    }
    if (element.tagName().equals("a")) {
      defaults.put("color", "#0b57d0");
      defaults.put("text-decoration", "underline");
    }
    return defaults;
  }

  private String defaultDisplay(String tagName) {
    if (Set.of("head", "title", "meta", "link", "style", "script", "noscript").contains(tagName)) {
      return "none";
    }
    if (Set.of("span", "a", "strong", "em", "b", "i", "img", "label", "input", "button")
        .contains(tagName)) {
      return "inline";
    }
    return "block";
  }

  private record Candidate(
      String value, Specificity specificity, int sourceOrder, boolean important)
      implements Comparable<Candidate> {
    @Override
    public int compareTo(Candidate other) {
      if (important != other.important) {
        return important ? 1 : -1;
      }
      int specificityOrder = specificity.compareTo(other.specificity);
      return specificityOrder != 0
          ? specificityOrder
          : Integer.compare(sourceOrder, other.sourceOrder);
    }
  }
}
