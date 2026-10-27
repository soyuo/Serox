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
          "display");
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
    if (!SUPPORTED_PROPERTIES.contains(property)) {
      unsupported.add(property + ": " + value);
      return;
    }
    Candidate candidate = new Candidate(value, specificity, sourceOrder);
    cascade.merge(property, candidate, (left, right) -> left.compareTo(right) <= 0 ? right : left);
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
