package kr.soyuo.serox.browser.script;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.soyuo.serox.browser.dom.CommentNode;
import kr.soyuo.serox.browser.dom.Document;
import kr.soyuo.serox.browser.dom.Element;
import kr.soyuo.serox.browser.dom.Node;
import kr.soyuo.serox.browser.dom.SelectorMatcher;
import kr.soyuo.serox.browser.dom.TextNode;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

/** The script-side view of one DOM node: a document, an element, a text node or a comment. */
final class NodeWrapper extends ScriptableObject {
  private static final long serialVersionUID = 1L;
  private static final Object NOT_FOUND = Scriptable.NOT_FOUND;

  private final transient DomHost host;
  private final transient Node node;

  NodeWrapper(DomHost host, Node node) {
    this.host = host;
    this.node = node;
    setParentScope(host.scope());
    setPrototype(ScriptableObject.getObjectPrototype(host.scope()));
  }

  Node node() {
    return node;
  }

  @Override
  public String getClassName() {
    return node instanceof Element ? "HTMLElement" : node instanceof Document ? "HTMLDocument" : "Node";
  }

  @Override
  public Object get(String name, Scriptable start) {
    Object value = property(name);
    return value != NOT_FOUND ? value : super.get(name, start);
  }

  @Override
  public boolean has(String name, Scriptable start) {
    return property(name) != NOT_FOUND || super.has(name, start);
  }

  @Override
  public void put(String name, Scriptable start, Object value) {
    if (!assign(name, value)) {
      super.put(name, start, value);
    }
  }

  // ----- reading -----

  private Object property(String name) {
    Object common = commonProperty(name);
    if (common != NOT_FOUND) {
      return common;
    }
    if (node instanceof Document document) {
      return documentProperty(document, name);
    }
    if (node instanceof Element element) {
      return elementProperty(element, name);
    }
    if (node instanceof TextNode text) {
      return switch (name) {
        case "data", "nodeValue", "textContent", "wholeText" -> text.data();
        case "length" -> text.data().length();
        default -> NOT_FOUND;
      };
    }
    if (node instanceof CommentNode comment) {
      return switch (name) {
        case "data", "nodeValue", "textContent" -> comment.data();
        default -> NOT_FOUND;
      };
    }
    return NOT_FOUND;
  }

  private Object commonProperty(String name) {
    return switch (name) {
      case "nodeType" ->
          node instanceof Element ? 1 : node instanceof TextNode ? 3 : node instanceof CommentNode ? 8 : 9;
      case "nodeName" -> DomHost.tagName(node);
      case "parentNode" -> host.wrap(node.parent().orElse(null));
      case "parentElement" ->
          node.parent().filter(Element.class::isInstance).map(host::wrap).orElse(null);
      case "childNodes" -> host.array(node.children().stream().map(host::wrap).toList());
      case "firstChild" -> host.wrap(node.children().isEmpty() ? null : node.children().get(0));
      case "lastChild" ->
          host.wrap(node.children().isEmpty() ? null : node.children().get(node.children().size() - 1));
      case "nextSibling" -> host.wrap(sibling(1));
      case "previousSibling" -> host.wrap(sibling(-1));
      case "ownerDocument" -> host.wrap(host.document());
      case "hasChildNodes" -> host.function(args -> !node.children().isEmpty());
      case "appendChild" -> host.function(args -> {
        Node child = host.nodeArg(args, 0);
        node.appendChild(child);
        return host.wrap(child);
      });
      case "removeChild" -> host.function(args -> {
        Node child = host.nodeArg(args, 0);
        if (!node.removeChild(child)) {
          throw org.mozilla.javascript.ScriptRuntime.typeError("The node to be removed is not a child");
        }
        return host.wrap(child);
      });
      case "insertBefore" -> host.function(args -> {
        Node child = host.nodeArg(args, 0);
        Node reference = args.length > 1 && args[1] instanceof NodeWrapper w ? w.node() : null;
        insertBefore(child, reference);
        return host.wrap(child);
      });
      case "replaceChild" -> host.function(args -> {
        Node replacement = host.nodeArg(args, 0);
        Node existing = host.nodeArg(args, 1);
        node.replaceChild(existing, replacement);
        return host.wrap(existing);
      });
      case "contains" -> host.function(args -> contains(host.nodeArg(args, 0)));
      case "cloneNode" -> host.function(args -> host.wrap(
          clone(node, args.length > 0 && Context.toBoolean(args[0]))));
      case "addEventListener" -> host.function(args -> host.addListener(node, args));
      case "removeEventListener" -> host.function(args -> host.removeListener(node, args));
      case "dispatchEvent" -> host.function(args -> {
        Object event = args.length > 0 ? args[0] : null;
        String type = event instanceof Scriptable s
            ? Context.toString(ScriptableObject.getProperty(s, "type"))
            : "";
        return host.dispatch(node, type, true);
      });
      case "textContent" -> node instanceof Element || node instanceof Document ? node.textContent() : NOT_FOUND;
      default -> NOT_FOUND;
    };
  }

  private Object documentProperty(Document document, String name) {
    return switch (name) {
      case "documentElement" -> host.wrap(document.documentElement().orElse(null));
      case "body" -> host.wrap(document.body().orElse(null));
      case "head" -> host.wrap(document.head().orElse(null));
      case "title" ->
          document.getElementsByTagName("title").stream().findFirst().map(Node::textContent).orElse("");
      case "readyState" -> "loading";
      case "URL", "documentURI" -> document.documentUrl().toString();
      case "cookie" -> "";
      case "referrer", "domain" ->
          "domain".equals(name) && document.documentUrl().getHost() != null
              ? document.documentUrl().getHost()
              : "";
      case "createElement" -> host.function(args -> {
        String tag = DomHost.text(args, 0).strip();
        if (!tag.matches("[A-Za-z][A-Za-z0-9-]{0,30}")) {
          throw org.mozilla.javascript.ScriptRuntime.typeError("Invalid tag name");
        }
        return host.wrap(new Element(tag));
      });
      case "createTextNode" -> host.function(args -> host.wrap(new TextNode(DomHost.text(args, 0))));
      case "createComment" -> host.function(args -> host.wrap(new CommentNode(DomHost.text(args, 0))));
      case "getElementById" -> host.function(args ->
          host.wrap(document.getElementById(DomHost.text(args, 0)).orElse(null)));
      case "getElementsByTagName" -> host.function(args ->
          host.array(document.getElementsByTagName(DomHost.text(args, 0)).stream().map(host::wrap).toList()));
      case "getElementsByClassName" -> host.function(args ->
          host.array(document.getElementsByClassName(DomHost.text(args, 0)).stream().map(host::wrap).toList()));
      case "querySelector" -> host.function(args -> query(document, DomHost.text(args, 0), false));
      case "querySelectorAll" -> host.function(args -> query(document, DomHost.text(args, 0), true));
      case "write", "writeln" -> host.function(args -> {
        StringBuilder html = new StringBuilder();
        for (Object arg : args) {
          html.append(Context.toString(arg));
        }
        Node target = document.body().map(Node.class::cast).orElse(document);
        for (Node added : host.parseFragment(html.toString())) {
          target.appendChild(added);
        }
        return Undefined.instance;
      });
      default -> NOT_FOUND;
    };
  }

  private Object elementProperty(Element element, String name) {
    return switch (name) {
      case "tagName" -> DomHost.tagName(element);
      case "id" -> element.attribute("id").orElse("");
      case "className" -> element.attribute("class").orElse("");
      case "innerText" -> element.textContent();
      case "innerHTML" -> HtmlSerializer.inner(element);
      case "outerHTML" -> HtmlSerializer.outer(element);
      case "children" -> host.array(
          element.children().stream().filter(Element.class::isInstance).map(host::wrap).toList());
      case "firstElementChild" -> host.wrap(
          element.children().stream().filter(Element.class::isInstance).findFirst().orElse(null));
      case "childElementCount" ->
          (int) element.children().stream().filter(Element.class::isInstance).count();
      case "style" -> new StyleWrapper(host, element);
      case "classList" -> new ClassListWrapper(host, element);
      case "value", "href", "src", "title", "alt", "type", "name", "placeholder", "rel", "target",
          "action", "method" -> element.attribute(name).orElse("");
      case "hidden", "disabled", "checked", "selected", "required", "readOnly" ->
          element.attribute(name.toLowerCase()).isPresent();
      case "setAttribute" -> host.function(args -> {
        element.setAttribute(DomHost.text(args, 0), DomHost.text(args, 1));
        return Undefined.instance;
      });
      case "getAttribute" -> host.function(args ->
          element.attribute(DomHost.text(args, 0)).orElse(null));
      case "hasAttribute" -> host.function(args -> element.attribute(DomHost.text(args, 0)).isPresent());
      case "removeAttribute" -> host.function(args -> {
        element.removeAttribute(DomHost.text(args, 0));
        return Undefined.instance;
      });
      case "remove" -> host.function(args -> {
        element.parent().ifPresent(parent -> parent.removeChild(element));
        return Undefined.instance;
      });
      case "click" -> host.function(args -> {
        host.dispatch(element, "click", true);
        return Undefined.instance;
      });
      case "focus", "blur", "scrollIntoView" -> host.function(args -> Undefined.instance);
      case "getElementsByTagName" -> host.function(args -> host.array(
          new SelectorMatcher(DomHost.text(args, 0)).selectAll(element).stream().map(host::wrap).toList()));
      case "getElementsByClassName" -> host.function(args -> host.array(
          new SelectorMatcher("." + DomHost.text(args, 0)).selectAll(element).stream().map(host::wrap).toList()));
      case "querySelector" -> host.function(args -> query(element, DomHost.text(args, 0), false));
      case "querySelectorAll" -> host.function(args -> query(element, DomHost.text(args, 0), true));
      case "matches" -> host.function(args -> new SelectorMatcher(DomHost.text(args, 0)).matches(element));
      case "closest" -> host.function(args -> {
        SelectorMatcher matcher = new SelectorMatcher(DomHost.text(args, 0));
        Node current = element;
        while (current instanceof Element candidate) {
          if (matcher.matches(candidate)) {
            return host.wrap(candidate);
          }
          current = candidate.parent().orElse(null);
        }
        return null;
      });
      default -> NOT_FOUND;
    };
  }

  private Object query(Node root, String selector, boolean all) {
    try {
      SelectorMatcher matcher = new SelectorMatcher(selector);
      if (all) {
        return host.array(matcher.selectAll(root).stream().map(host::wrap).toList());
      }
      return host.wrap(matcher.selectFirst(root).orElse(null));
    } catch (SelectorMatcher.SyntaxException invalid) {
      throw Context.reportRuntimeError(invalid.getMessage());
    }
  }

  // ----- writing -----

  private boolean assign(String name, Object value) {
    if (node instanceof TextNode text && List.of("data", "nodeValue", "textContent").contains(name)) {
      text.setData(Context.toString(value));
      return true;
    }
    if (node instanceof Document document && "title".equals(name)) {
      document.getElementsByTagName("title").stream()
          .findFirst()
          .ifPresent(title -> setText(title, Context.toString(value)));
      return true;
    }
    if (!(node instanceof Element element)) {
      return false;
    }
    String text = Context.toString(value);
    switch (name) {
      case "id" -> element.setAttribute("id", text);
      case "className" -> element.setAttribute("class", text);
      case "textContent", "innerText" -> setText(element, text);
      case "innerHTML" -> {
        for (Node child : element.children()) {
          element.removeChild(child);
        }
        for (Node added : host.parseFragment(text)) {
          element.appendChild(added);
        }
      }
      case "value", "href", "src", "title", "alt", "type", "name", "placeholder", "rel", "target",
          "action", "method" -> element.setAttribute(name, text);
      case "hidden", "disabled", "checked", "selected", "required", "readOnly" -> {
        if (Context.toBoolean(value)) {
          element.setAttribute(name.toLowerCase(), "");
        } else {
          element.removeAttribute(name.toLowerCase());
        }
      }
      default -> {
        return false;
      }
    }
    return true;
  }

  private static void setText(Node target, String text) {
    for (Node child : target.children()) {
      target.removeChild(child);
    }
    if (!text.isEmpty()) {
      target.appendChild(new TextNode(text));
    }
  }

  private Node sibling(int step) {
    Node parent = node.parent().orElse(null);
    if (parent == null) {
      return null;
    }
    List<Node> siblings = parent.children();
    int index = siblings.indexOf(node) + step;
    return index >= 0 && index < siblings.size() ? siblings.get(index) : null;
  }

  private void insertBefore(Node child, Node reference) {
    if (reference == null) {
      node.appendChild(child);
      return;
    }
    List<Node> existing = new ArrayList<>(node.children());
    int index = existing.indexOf(reference);
    if (index < 0) {
      throw org.mozilla.javascript.ScriptRuntime.typeError("The reference node is not a child");
    }
    existing.remove(child);
    existing.add(Math.min(index, existing.size()), child);
    for (Node each : existing) {
      node.appendChild(each);
    }
  }

  private boolean contains(Node other) {
    for (Node current = other; current != null; current = current.parent().orElse(null)) {
      if (current == node) {
        return true;
      }
    }
    return false;
  }

  private static Node clone(Node source, boolean deep) {
    Node copy;
    if (source instanceof Element element) {
      Element made = new Element(element.tagName());
      for (Map.Entry<String, String> attribute : new LinkedHashMap<>(element.attributes()).entrySet()) {
        made.setAttribute(attribute.getKey(), attribute.getValue());
      }
      copy = made;
    } else if (source instanceof TextNode text) {
      copy = new TextNode(text.data());
    } else if (source instanceof CommentNode comment) {
      copy = new CommentNode(comment.data());
    } else {
      throw org.mozilla.javascript.ScriptRuntime.typeError("This node cannot be cloned");
    }
    if (deep) {
      for (Node child : source.children()) {
        copy.appendChild(clone(child, true));
      }
    }
    return copy;
  }
}
