package kr.soyuo.serox.browser.html;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import kr.soyuo.serox.browser.dom.CommentNode;
import kr.soyuo.serox.browser.dom.Document;
import kr.soyuo.serox.browser.dom.Element;
import kr.soyuo.serox.browser.dom.TextNode;

/** Builds an initial HTML DOM from tokenizer output with basic malformed-tag recovery. */
public final class HtmlDocumentParser {
  private static final Set<String> VOID_ELEMENTS =
      Set.of(
          "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param",
          "source", "track", "wbr");
  private static final Set<String> HEAD_ELEMENTS =
      Set.of("base", "link", "meta", "title", "style", "script");
  /** Deeper elements stay in the tree but do not nest further, so layout recursion is bounded. */
  static final int MAX_NESTING = 256;

  private final HtmlTokenizer tokenizer;

  public HtmlDocumentParser() {
    this(new HtmlTokenizer());
  }

  public HtmlDocumentParser(HtmlTokenizer tokenizer) {
    this.tokenizer = tokenizer;
  }

  /** Parses tokens into a document with html, head, and body roots. */
  public Document parse(URI documentUrl, String html) {
    Document document = new Document(documentUrl);
    Element htmlElement = new Element("html");
    Element head = new Element("head");
    Element body = new Element("body");
    document.appendChild(htmlElement);
    htmlElement.appendChild(head);
    htmlElement.appendChild(body);
    List<Element> stack = new ArrayList<>(List.of(htmlElement, body));

    for (HtmlToken token : tokenizer.tokenize(html)) {
      if (token instanceof HtmlToken.StartTag startTag) {
        startElement(startTag, htmlElement, head, body, stack);
      } else if (token instanceof HtmlToken.EndTag endTag) {
        closeElement(endTag.name(), stack, htmlElement, head, body);
      } else if (token instanceof HtmlToken.Text text) {
        if (!text.value().isEmpty()) {
          current(stack, body).appendChild(new TextNode(text.value()));
        }
      } else if (token instanceof HtmlToken.Comment comment) {
        current(stack, body).appendChild(new CommentNode(comment.value()));
      }
    }
    return document;
  }

  private void startElement(
      HtmlToken.StartTag token, Element html, Element head, Element body, List<Element> stack) {
    String name = token.name().toLowerCase(Locale.ROOT);
    if (name.equals("html")) {
      copyAttributes(token, html);
      return;
    }
    if (name.equals("head")) {
      stack.clear();
      stack.add(html);
      stack.add(head);
      copyAttributes(token, head);
      return;
    }
    if (name.equals("body")) {
      stack.clear();
      stack.add(html);
      stack.add(body);
      copyAttributes(token, body);
      return;
    }
    if (HEAD_ELEMENTS.contains(name) && body.children().isEmpty()) {
      stack.clear();
      stack.add(html);
      stack.add(head);
    } else if (stack.getLast() == head) {
      stack.clear();
      stack.add(html);
      stack.add(body);
    }
    if (name.equals("p") || name.equals("li")) {
      popOpenElement(name, stack, html);
    }
    Element parent = current(stack, body);
    Element element = new Element(name);
    copyAttributes(token, element);
    parent.appendChild(element);
    if (!token.selfClosing() && !VOID_ELEMENTS.contains(name) && stack.size() < MAX_NESTING) {
      stack.add(element);
    }
  }

  private void closeElement(
      String name, List<Element> stack, Element html, Element head, Element body) {
    for (int index = stack.size() - 1; index > 0; index--) {
      if (stack.get(index).tagName().equals(name)) {
        stack.subList(index, stack.size()).clear();
        if (stack.isEmpty()) {
          stack.add(html);
        }
        if (stack.size() == 1) {
          stack.add(body);
        }
        return;
      }
    }
    if (name.equals("head") || name.equals("body")) {
      stack.clear();
      stack.add(html);
      stack.add(body);
    }
  }

  private void popOpenElement(String name, List<Element> stack, Element html) {
    for (int index = stack.size() - 1; index > 1; index--) {
      if (stack.get(index).tagName().equals(name)) {
        stack.subList(index, stack.size()).clear();
        if (stack.size() == 1) {
          stack.add(html);
        }
        return;
      }
    }
  }

  private void copyAttributes(HtmlToken.StartTag token, Element element) {
    token.attributes().forEach(element::setAttribute);
  }

  private Element current(List<Element> stack, Element fallback) {
    return stack.isEmpty() ? fallback : stack.getLast();
  }
}
