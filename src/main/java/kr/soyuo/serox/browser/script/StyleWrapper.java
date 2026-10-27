package kr.soyuo.serox.browser.script;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import kr.soyuo.serox.browser.dom.Element;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

/** {@code element.style}: reads and writes the element's inline {@code style} attribute. */
final class StyleWrapper extends ScriptableObject {
  private static final long serialVersionUID = 1L;
  private static final int MAX_STYLE_CHARS = 4000;

  private final transient DomHost host;
  private final transient Element element;

  StyleWrapper(DomHost host, Element element) {
    this.host = host;
    this.element = element;
    setParentScope(host.scope());
    setPrototype(ScriptableObject.getObjectPrototype(host.scope()));
  }

  @Override
  public String getClassName() {
    return "CSSStyleDeclaration";
  }

  private Map<String, String> declarations() {
    Map<String, String> result = new LinkedHashMap<>();
    for (String part : element.attribute("style").orElse("").split(";")) {
      int colon = part.indexOf(':');
      if (colon > 0) {
        result.put(part.substring(0, colon).strip().toLowerCase(Locale.ROOT), part.substring(colon + 1).strip());
      }
    }
    return result;
  }

  private void store(Map<String, String> declarations) {
    StringBuilder text = new StringBuilder();
    declarations.forEach((key, value) -> text.append(key).append(": ").append(value).append("; "));
    String joined = text.toString().strip();
    if (joined.isEmpty()) {
      element.removeAttribute("style");
    } else {
      element.setAttribute("style", joined.length() > MAX_STYLE_CHARS ? joined.substring(0, MAX_STYLE_CHARS) : joined);
    }
  }

  static String kebab(String camel) {
    StringBuilder out = new StringBuilder();
    for (char c : camel.toCharArray()) {
      if (Character.isUpperCase(c)) {
        out.append('-').append(Character.toLowerCase(c));
      } else {
        out.append(c);
      }
    }
    return out.toString();
  }

  @Override
  public Object get(String name, Scriptable start) {
    switch (name) {
      case "cssText":
        return element.attribute("style").orElse("");
      case "setProperty":
        return host.function(args -> {
          Map<String, String> all = declarations();
          all.put(DomHost.text(args, 0).toLowerCase(Locale.ROOT), DomHost.text(args, 1));
          store(all);
          return Undefined.instance;
        });
      case "getPropertyValue":
        return host.function(args -> declarations().getOrDefault(DomHost.text(args, 0).toLowerCase(Locale.ROOT), ""));
      case "removeProperty":
        return host.function(args -> {
          Map<String, String> all = declarations();
          String old = all.remove(DomHost.text(args, 0).toLowerCase(Locale.ROOT));
          store(all);
          return old == null ? "" : old;
        });
      default:
        break;
    }
    Object own = super.get(name, start);
    if (own != NOT_FOUND || "constructor".equals(name) || "toString".equals(name)) {
      return own;
    }
    return declarations().getOrDefault(kebab(name), "");
  }

  @Override
  public boolean has(String name, Scriptable start) {
    return true;
  }

  @Override
  public void put(String name, Scriptable start, Object value) {
    if ("cssText".equals(name)) {
      element.setAttribute("style", Context.toString(value));
      return;
    }
    Map<String, String> all = declarations();
    String text = Context.toString(value);
    if (text.isEmpty()) {
      all.remove(kebab(name));
    } else {
      all.put(kebab(name), text);
    }
    store(all);
  }
}
