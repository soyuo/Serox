package kr.soyuo.serox.browser.dom;

import java.util.Objects;

/** Comment in the parsed document tree. */
public final class CommentNode extends Node {
  private final String data;

  public CommentNode(String data) {
    this.data = Objects.requireNonNull(data, "data");
  }

  public String data() {
    return data;
  }
}
