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
          "min-width");
  private static final Set<String> INHERITED =
      Set.of(
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
      java.util.regex.Pattern.compile("-?(?:\\d+\\.?\\d*|\\.\\d+)(?:em|rem|pt)");
  private static final java.util.regex.Pattern RELATIVE_TOKEN =
      java.util.regex.Pattern.compile("(-?(?:\\d+\\.?\\d*|\\.\\d+))(em|rem|pt)");
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
    for (CssRule rule : rules) {
      try {
        Set<Element> hits = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        hits.addAll(new SelectorMatcher(rule.selector()).selectAll(document));
        matched.put(rule, hits);
      } catch (SelectorMatcher.SyntaxException unsupportedSelector) {
        unsupported.add("selector: " + rule.selector());
      }
    }
    for (Element element :
        document.descendants().stream()
            .filter(Element.class::isInstance)
            .map(Element.class::cast)
            .toList()) {
      Map<String, Candidate> cascade = new HashMap<>();
      Map<String, String> custom = customProperties(element, rules, matched, computed);
      for (CssRule rule : rules) {
        Set<Element> hits = matched.get(rule);
        if (hits != null && hits.contains(element)) {
          Specificity specificity = Specificity.of(rule.selector());
          rule.declarations()
              .forEach(
                  (name, value) ->
                      apply(
                          cascade, name, value, specificity, rule.sourceOrder(), unsupported, custom));
        }
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
      cascade.forEach((property, candidate) -> values.put(property, candidate.value()));
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
      value = substituted;
    }
    boolean important = IMPORTANT.matcher(value).find();
    if (important) {
      value = IMPORTANT.matcher(value).replaceFirst("").trim();
    }
    if (!SUPPORTED_PROPERTIES.contains(property) || !isSupportedValue(property, value)) {
      unsupported.add(property + ": " + value);
      return;
    }
    Candidate candidate = new Candidate(value, specificity, sourceOrder, important);
    cascade.merge(property, candidate, (left, right) -> left.compareTo(right) <= 0 ? right : left);
  }

  private boolean isSupportedValue(String property, String value) {
    String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
    return switch (property) {
      case "display" -> Set.of("block", "inline", "none", "flex").contains(normalized);
      case "flex-direction" -> Set.of("row", "column").contains(normalized);
      case "flex-wrap" -> Set.of("nowrap", "wrap").contains(normalized);
      case "justify-content" ->
          Set.of("flex-start", "center", "flex-end", "space-between", "space-around")
              .contains(normalized);
      case "align-items" ->
          Set.of("stretch", "flex-start", "center", "flex-end").contains(normalized);
      case "gap" -> isLength(normalized);
      case "flex-grow" -> isNonNegativeNumber(normalized);
      case "flex" -> isNonNegativeNumber(normalized.split("\\s+")[0]);
      case "color", "background", "background-color", "border-color" ->
          CssColor.parse(normalized).isPresent();
      case "font-size" -> isLength(normalized) || isPercentage(normalized);
      case "text-align" ->
          Set.of("left", "center", "right", "start", "end").contains(normalized);
      case "font-style" -> Set.of("normal", "italic", "oblique").contains(normalized);
      case "text-transform" ->
          Set.of("none", "uppercase", "lowercase", "capitalize").contains(normalized);
      case "max-width" ->
          normalized.equals("none") || isLength(normalized) || isPercentage(normalized);
      case "min-width" -> isLength(normalized) || isPercentage(normalized);
      case "font-weight" ->
          Set.of("normal", "bold", "bolder", "lighter").contains(normalized)
              || normalized.matches("[1-9]00");
      case "text-decoration" -> Set.of("none", "underline").contains(normalized);
      case "width" -> normalized.equals("auto") || isLength(normalized) || isPercentage(normalized);
      case "height" -> normalized.equals("auto") || isLength(normalized);
      case "margin", "margin-top", "margin-right", "margin-bottom", "margin-left" ->
          java.util.Arrays.stream(normalized.split("\\s+"))
              .allMatch(part -> part.equals("auto") || isLength(part));
      case "padding",
          "padding-top",
          "padding-right",
          "padding-bottom",
          "padding-left",
          "border-width" ->
          java.util.Arrays.stream(normalized.split("\\s+")).allMatch(this::isLength);
      case "border" -> isBorderValue(normalized);
      default -> true;
    };
  }

  private boolean isNonNegativeNumber(String value) {
    try {
      return Double.parseDouble(value) >= 0;
    } catch (NumberFormatException exception) {
      return false;
    }
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
      Element element,
      List<CssRule> rules,
      Map<CssRule, Set<Element>> matched,
      Map<Element, ComputedStyle> computed) {
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
      Set<Element> hits = matched.get(rule);
      if (hits != null && hits.contains(element)) {
        Specificity specificity = Specificity.of(rule.selector());
        rule.declarations()
            .forEach(
                (name, value) -> keepCustom(own, name, value, specificity, rule.sourceOrder()));
      }
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
  static String substituteVariables(String value, Map<String, String> custom) {
    String current = value;
    for (int round = 0; round < 6 && current.contains("var("); round++) {
      java.util.regex.Matcher matcher = VARIABLE.matcher(current);
      StringBuilder out = new StringBuilder();
      while (matcher.find()) {
        String found = custom.get(matcher.group(1));
        if (found == null) {
          found = matcher.group(2);
        }
        if (found == null) {
          return null;
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
