package kr.soyuo.serox.browser.script;

import java.util.ArrayList;
import java.util.List;
import kr.soyuo.serox.browser.dom.Element;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

/** {@code element.classList}: add, remove, toggle and test the element's class names. */
final class ClassListWrapper extends ScriptableObject {
  private static final long serialVersionUID = 1L;

  private final transient DomHost host;
  private final transient Element element;

  ClassListWrapper(DomHost host, Element element) {
    this.host = host;
    this.element = element;
    setParentScope(host.scope());
    setPrototype(ScriptableObject.getObjectPrototype(host.scope()));
  }

  @Override
  public String getClassName() {
    return "DOMTokenList";
  }

  private List<String> names() {
    List<String> result = new ArrayList<>();
    for (String name : element.attribute("class").orElse("").strip().split("\\s+")) {
      if (!name.isEmpty() && !result.contains(name)) {
        result.add(name);
      }
    }
    return result;
  }

  private void store(List<String> names) {
    if (names.isEmpty()) {
      element.removeAttribute("class");
    } else {
      element.setAttribute("class", String.join(" ", names));
    }
  }

  @Override
  public Object get(String name, Scriptable start) {
    return switch (name) {
      case "length" -> names().size();
      case "value" -> element.attribute("class").orElse("");
      case "contains" -> host.function(args -> names().contains(DomHost.text(args, 0)));
      case "add" -> host.function(args -> {
        List<String> all = names();
        for (Object arg : args) {
          String token = Context.toString(arg);
          if (!all.contains(token)) {
            all.add(token);
          }
        }
        store(all);
        return Undefined.instance;
      });
      case "remove" -> host.function(args -> {
        List<String> all = names();
        for (Object arg : args) {
          all.remove(Context.toString(arg));
        }
        store(all);
        return Undefined.instance;
      });
      case "toggle" -> host.function(args -> {
        List<String> all = names();
        String token = DomHost.text(args, 0);
        boolean present = all.contains(token);
        boolean want = args.length > 1 ? Context.toBoolean(args[1]) : !present;
        if (want && !present) {
          all.add(token);
        } else if (!want) {
          all.remove(token);
        }
        store(all);
        return want;
      });
      default -> super.get(name, start);
    };
  }

  @Override
  public boolean has(String name, Scriptable start) {
    return List.of("length", "value", "contains", "add", "remove", "toggle").contains(name)
        || super.has(name, start);
  }
}
