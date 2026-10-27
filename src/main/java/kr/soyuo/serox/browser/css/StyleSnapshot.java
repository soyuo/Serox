package kr.soyuo.serox.browser.css;

import java.util.List;
import java.util.Map;
import kr.soyuo.serox.browser.dom.Element;

/** Computed styles indexed by DOM element identity. */
public record StyleSnapshot(
    Map<Element, ComputedStyle> styles, List<String> unsupportedDeclarations) {
  public StyleSnapshot {
    styles = Map.copyOf(styles);
    unsupportedDeclarations = List.copyOf(unsupportedDeclarations);
  }

  public ComputedStyle styleOf(Element element) {
    return styles.getOrDefault(element, new ComputedStyle(Map.of()));
  }
}
