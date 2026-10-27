package kr.soyuo.serox.browser.css;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kr.soyuo.serox.browser.dom.Document;
import kr.soyuo.serox.browser.dom.Element;

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
          "flex");
  private static final Set<String> INHERITED =
      Set.of("color", "font-size", "font-weight", "font-family");
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
    for (Element element :
        document.descendants().stream()
            .filter(Element.class::isInstance)
            .map(Element.class::cast)
            .toList()) {
      Map<String, Candidate> cascade = new HashMap<>();
      for (CssRule rule : rules) {
        if (rule.selector().contains(":")
            || rule.selector().contains(">")
            || rule.selector().contains("+")
            || rule.selector().contains("~")) {
          unsupported.add("selector: " + rule.selector());
          continue;
        }
        if (document.querySelectorAll(rule.selector()).contains(element)) {
          Specificity specificity = Specificity.of(rule.selector());
          rule.declarations()
              .forEach(
                  (name, value) ->
                      apply(cascade, name, value, specificity, rule.sourceOrder(), unsupported));
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
                                  unsupported)));
      Map<String, String> values = defaults(element);
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
      List<String> unsupported) {
    if (!SUPPORTED_PROPERTIES.contains(property) || !isSupportedValue(property, value)) {
      unsupported.add(property + ": " + value);
      return;
    }
    Candidate candidate = new Candidate(value, specificity, sourceOrder);
    cascade.merge(property, candidate, (left, right) -> left.compareTo(right) <= 0 ? right : left);
  }

  private boolean isSupportedValue(String property, String value) {
    String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
    if (normalized.contains("!important")) {
      return false;
    }
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
      case "font-size" -> isLength(normalized);
      case "font-weight" -> Set.of("normal", "bold", "400", "700").contains(normalized);
      case "text-decoration" -> Set.of("none", "underline").contains(normalized);
      case "width" -> normalized.equals("auto") || isLength(normalized) || isPercentage(normalized);
      case "height" -> normalized.equals("auto") || isLength(normalized);
      case "margin",
          "margin-top",
          "margin-right",
          "margin-bottom",
          "margin-left",
          "padding",
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

  private record Candidate(String value, Specificity specificity, int sourceOrder)
      implements Comparable<Candidate> {
    @Override
    public int compareTo(Candidate other) {
      int specificityOrder = specificity.compareTo(other.specificity);
      return specificityOrder != 0
          ? specificityOrder
          : Integer.compare(sourceOrder, other.sourceOrder);
    }
  }
}
