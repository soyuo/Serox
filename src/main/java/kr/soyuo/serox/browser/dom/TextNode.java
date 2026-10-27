package kr.soyuo.serox.browser.dom;

import java.util.Objects;

/** Mutable text content in the DOM tree. */
public final class TextNode extends Node {
  private String data;

  public TextNode(String data) {
    this.data = Objects.requireNonNull(data, "data");
  }

  public String data() {
    return data;
  }

  public void setData(String data) {
    this.data = Objects.requireNonNull(data, "data");
  }

  @Override
  protected void appendTextContent(StringBuilder text) {
    text.append(data);
  }
}
