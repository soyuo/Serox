package kr.soyuo.serox.browser.dom;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Base class for parented DOM nodes. */
public abstract class Node {
  private Node parent;
  private final List<Node> children = new ArrayList<>();

  /** Returns the parent node, if attached. */
  public final Optional<Node> parent() {
    return Optional.ofNullable(parent);
  }

  /** Returns an immutable snapshot of child nodes. */
  public final List<Node> children() {
    return List.copyOf(children);
  }

  /** Appends a child and moves it from any previous parent. */
  public final void appendChild(Node child) {
    Objects.requireNonNull(child, "child");
    if (child == this || isAncestorOf(child)) {
      throw new IllegalArgumentException("A DOM node cannot contain itself or its ancestor");
    }
    child.parent().ifPresent(parent -> parent.removeChild(child));
    children.add(child);
    child.parent = this;
  }

  /** Removes a child and clears its parent reference. */
  public final boolean removeChild(Node child) {
    if (!children.remove(child)) {
      return false;
    }
    child.parent = null;
    return true;
  }

  /** Returns the concatenated text descendants, excluding comments. */
  public String textContent() {
    StringBuilder text = new StringBuilder();
    appendTextContent(text);
    return text.toString();
  }

  protected void appendTextContent(StringBuilder text) {
    for (Node child : children) {
      child.appendTextContent(text);
    }
  }

  private boolean isAncestorOf(Node possibleAncestor) {
    for (Node current = this; current != null; current = current.parent) {
      if (current == possibleAncestor) {
        return true;
      }
    }
    return false;
  }
}
