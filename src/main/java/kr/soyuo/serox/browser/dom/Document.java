package kr.soyuo.serox.browser.dom;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Root of a parsed HTML document. */
public final class Document extends Node {
  private final URI documentUrl;

  public Document(URI documentUrl) {
    this.documentUrl = Objects.requireNonNull(documentUrl, "documentUrl");
  }

  public URI documentUrl() {
    return documentUrl;
  }

  /** Returns the document's html element, when present. */
  public Optional<Element> documentElement() {
    return children().stream()
        .filter(Element.class::isInstance)
        .map(Element.class::cast)
        .findFirst();
  }

  /** Returns the first head element, when present. */
  public Optional<Element> head() {
    return getElementsByTagName("head").stream().findFirst();
  }

  /** Returns the first body element, when present. */
  public Optional<Element> body() {
    return getElementsByTagName("body").stream().findFirst();
  }

  /** Finds the first element with the given ID. */
  public Optional<Element> getElementById(String id) {
    return elements().stream()
        .filter(element -> element.attribute("id").filter(id::equals).isPresent())
        .findFirst();
  }

  /** Finds elements containing the given whitespace-separated class token. */
  public List<Element> getElementsByClassName(String className) {
    return elements().stream().filter(element -> hasClass(element, className)).toList();
  }

  /** Finds elements by case-insensitive tag name; an asterisk selects every element. */
  public List<Element> getElementsByTagName(String tagName) {
    String normalized = tagName.toLowerCase(java.util.Locale.ROOT);
    return elements().stream()
        .filter(element -> normalized.equals("*") || element.tagName().equals(normalized))
        .toList();
  }

  /** Finds the first element matching the supported selector subset. */
  public Optional<Element> querySelector(String selector) {
    return querySelectorAll(selector).stream().findFirst();
  }

  /**
   * Finds elements matching tag, ID, class, and attribute selectors with descendant combinators.
   * Comma-separated selector groups are supported.
   */
  public List<Element> querySelectorAll(String selector) {
    Objects.requireNonNull(selector, "selector");
    List<Element> matches = new ArrayList<>();
    for (Element element : elements()) {
      if (java.util.Arrays.stream(selector.split(","))
          .map(String::trim)
          .anyMatch(group -> matchesGroup(element, group))) {
        matches.add(element);
      }
    }
    return List.copyOf(matches);
  }

  private List<Element> elements() {
    return descendants().stream()
        .filter(Element.class::isInstance)
        .map(Element.class::cast)
        .toList();
  }

  private boolean hasClass(Element element, String className) {
    return element.attribute("class").stream()
        .flatMap(value -> java.util.Arrays.stream(value.trim().split("\\s+")))
        .anyMatch(className::equals);
  }

  private boolean matchesGroup(Element element, String group) {
    if (group.isBlank()) {
      return false;
    }
    String[] parts = group.trim().split("\\s+");
    int partIndex = parts.length - 1;
    if (!matchesSimple(element, parts[partIndex])) {
      return false;
    }
    Node ancestor = element.parent().orElse(null);
    for (partIndex--; partIndex >= 0; partIndex--) {
      boolean found = false;
      while (ancestor != null) {
        if (ancestor instanceof Element parent && matchesSimple(parent, parts[partIndex])) {
          found = true;
          ancestor = parent.parent().orElse(null);
          break;
        }
        ancestor = ancestor.parent().orElse(null);
      }
      if (!found) {
        return false;
      }
    }
    return true;
  }

  private boolean matchesSimple(Element element, String selector) {
    int cursor = 0;
    int nameEnd = cursor;
    while (nameEnd < selector.length() && ".#[".indexOf(selector.charAt(nameEnd)) < 0) {
      nameEnd++;
    }
    String tag = selector.substring(0, nameEnd);
    if (!tag.isEmpty() && !tag.equals("*") && !element.tagName().equalsIgnoreCase(tag)) {
      return false;
    }
    cursor = nameEnd;
    while (cursor < selector.length()) {
      char marker = selector.charAt(cursor++);
      int end = cursor;
      if (marker == '[') {
        while (end < selector.length() && selector.charAt(end) != ']') {
          end++;
        }
        if (end == selector.length()
            || !matchesAttribute(element, selector.substring(cursor, end))) {
          return false;
        }
        cursor = end + 1;
      } else {
        while (end < selector.length() && ".#[".indexOf(selector.charAt(end)) < 0) {
          end++;
        }
        String value = selector.substring(cursor, end);
        if (marker == '#' && element.attribute("id").filter(value::equals).isEmpty()) {
          return false;
        }
        if (marker == '.' && !hasClass(element, value)) {
          return false;
        }
        cursor = end;
      }
    }
    return true;
  }

  private boolean matchesAttribute(Element element, String expression) {
    int equals = expression.indexOf('=');
    String name = (equals < 0 ? expression : expression.substring(0, equals)).trim();
    Optional<String> value = element.attribute(name);
    if (equals < 0) {
      return value.isPresent();
    }
    String expected = expression.substring(equals + 1).trim();
    if (expected.length() >= 2
        && (expected.startsWith("\"") && expected.endsWith("\"")
            || expected.startsWith("'") && expected.endsWith("'"))) {
      expected = expected.substring(1, expected.length() - 1);
    }
    return value.filter(expected::equals).isPresent();
  }
}
