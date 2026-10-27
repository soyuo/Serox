package kr.soyuo.serox.browser.dom;

import java.util.Objects;

/** Immutable name and value pair for a DOM element attribute. */
public record Attribute(String name, String value) {
  public Attribute {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(value, "value");
  }
}
