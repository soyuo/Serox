package kr.soyuo.serox.browser.dom;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** HTML element with a normalized tag name and ordered attributes. */
public final class Element extends Node {
  private final String tagName;
  private final Map<String, String> attributes = new LinkedHashMap<>();

  /** Creates an element with a lowercase tag name. */
  public Element(String tagName) {
    this.tagName = tagName.toLowerCase(Locale.ROOT);
  }

  public String tagName() {
    return tagName;
  }

  public Map<String, String> attributes() {
    return Map.copyOf(attributes);
  }

  /** Returns attributes as immutable name/value records. */
  public List<Attribute> attributeEntries() {
    return attributes.entrySet().stream()
        .map(entry -> new Attribute(entry.getKey(), entry.getValue()))
        .toList();
  }

  public Optional<String> attribute(String name) {
    return Optional.ofNullable(attributes.get(name.toLowerCase(Locale.ROOT)));
  }

  public void setAttribute(String name, String value) {
    attributes.put(name.toLowerCase(Locale.ROOT), value);
  }

  public Optional<String> removeAttribute(String name) {
    return Optional.ofNullable(attributes.remove(name.toLowerCase(Locale.ROOT)));
  }
}
