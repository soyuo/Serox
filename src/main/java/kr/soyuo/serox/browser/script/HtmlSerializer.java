package kr.soyuo.serox.browser.script;

import java.util.Set;
import kr.soyuo.serox.browser.dom.Attribute;
import kr.soyuo.serox.browser.dom.CommentNode;
import kr.soyuo.serox.browser.dom.Element;
import kr.soyuo.serox.browser.dom.Node;
import kr.soyuo.serox.browser.dom.TextNode;

/** Turns DOM nodes back into HTML text for {@code innerHTML} and {@code outerHTML}. */
final class HtmlSerializer {
  private static final Set<String> VOID =
      Set.of(
          "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "source",
          "wbr");

  private HtmlSerializer() {}

  static String inner(Node node) {
    StringBuilder out = new StringBuilder();
    for (Node child : node.children()) {
      write(child, out);
    }
    return out.toString();
  }

  static String outer(Node node) {
    StringBuilder out = new StringBuilder();
    write(node, out);
    return out.toString();
  }

  private static void write(Node node, StringBuilder out) {
    if (node instanceof TextNode text) {
      out.append(escape(text.data(), false));
    } else if (node instanceof CommentNode comment) {
      out.append("<!--").append(comment.data()).append("-->");
    } else if (node instanceof Element element) {
      out.append('<').append(element.tagName());
      for (Attribute attribute : element.attributeEntries()) {
        out.append(' ')
            .append(attribute.name())
            .append("=\"")
            .append(escape(attribute.value(), true))
            .append('"');
      }
      out.append('>');
      if (!VOID.contains(element.tagName())) {
        for (Node child : element.children()) {
          write(child, out);
        }
        out.append("</").append(element.tagName()).append('>');
      }
    }
  }

  private static String escape(String value, boolean attribute) {
    String escaped = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    return attribute ? escaped.replace("\"", "&quot;") : escaped;
  }
}
