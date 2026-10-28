package kr.soyuo.serox.browser.dom;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Matches the simple selectors scripts use: tag, {@code #id}, {@code .class}, {@code [attr]},
 * {@code [attr=value]}, {@code :root}, {@code :first-child}, {@code :last-child}, descendant and
 * child combinators, and comma lists. Anything else is an error, as in a browser.
 */
public final class SelectorMatcher {
  /** The selector text is not one this matcher understands. */
  public static final class SyntaxException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    SyntaxException(String selector) {
      super("'" + selector + "' is not a valid selector");
    }
  }

  private record Compound(
      String tag,
      String id,
      List<String> classes,
      List<String[]> attributes,
      List<String> pseudos,
      boolean child) {}

  private final List<List<Compound>> alternatives = new ArrayList<>();

  public SelectorMatcher(String selector) {
    if (selector == null || selector.isBlank() || selector.length() > 500) {
      throw new SyntaxException(String.valueOf(selector));
    }
    for (String part : selector.split(",", -1)) {
      alternatives.add(parseChain(part.strip(), selector));
    }
  }

  /** All elements below {@code root}, in document order, that match. */
  public List<Element> selectAll(Node root) {
    List<Element> found = new ArrayList<>();
    for (Node node : root.descendants()) {
      if (node instanceof Element element && matches(element)) {
        found.add(element);
      }
    }
    return found;
  }

  public Optional<Element> selectFirst(Node root) {
    return selectAll(root).stream().findFirst();
  }

  public boolean matches(Element element) {
    for (List<Compound> chain : alternatives) {
      if (matchesChain(element, chain, chain.size() - 1)) {
        return true;
      }
    }
    return false;
  }

  private boolean matchesChain(Element element, List<Compound> chain, int index) {
    Compound compound = chain.get(index);
    if (!matchesCompound(element, compound)) {
      return false;
    }
    if (index == 0) {
      return true;
    }
    Node parent = element.parent().orElse(null);
    if (compound.child()) {
      return parent instanceof Element parentElement
          && matchesChain(parentElement, chain, index - 1);
    }
    while (parent != null) {
      if (parent instanceof Element ancestor && matchesChain(ancestor, chain, index - 1)) {
        return true;
      }
      parent = parent.parent().orElse(null);
    }
    return false;
  }

  private static boolean matchesCompound(Element element, Compound compound) {
    if (!compound.tag().isEmpty()
        && !compound.tag().equals("*")
        && !compound.tag().equals(element.tagName())) {
      return false;
    }
    if (!compound.id().isEmpty() && !element.attribute("id").orElse("").equals(compound.id())) {
      return false;
    }
    List<String> classNames = List.of(element.attribute("class").orElse("").strip().split("\\s+"));
    for (String wanted : compound.classes()) {
      if (!classNames.contains(wanted)) {
        return false;
      }
    }
    for (String pseudo : compound.pseudos()) {
      if (pseudo.equals("root")) {
        if (element.parent().filter(Document.class::isInstance).isEmpty()
            || ((Document) element.parent().orElseThrow()).documentElement().orElse(null)
                != element) {
          return false;
        }
        continue;
      }
      List<Node> siblings =
          element
              .parent()
              .map(parent -> parent.children().stream().filter(Element.class::isInstance).toList())
              .orElse(List.of());
      if (siblings.isEmpty()
          || (pseudo.equals("first-child") && siblings.get(0) != element)
          || (pseudo.equals("last-child") && siblings.get(siblings.size() - 1) != element)) {
        return false;
      }
    }
    for (String[] attribute : compound.attributes()) {
      Optional<String> value = element.attribute(attribute[0]);
      if (value.isEmpty() || (attribute[1] != null && !value.get().equals(attribute[1]))) {
        return false;
      }
    }
    return true;
  }

  private static List<Compound> parseChain(String text, String whole) {
    List<Compound> chain = new ArrayList<>();
    boolean child = false;
    int i = 0;
    while (i < text.length()) {
      char c = text.charAt(i);
      if (Character.isWhitespace(c)) {
        i++;
        continue;
      }
      if (c == '>') {
        child = true;
        i++;
        continue;
      }
      int end = i;
      int bracket = 0;
      while (end < text.length()
          && (bracket > 0
              || (!Character.isWhitespace(text.charAt(end)) && text.charAt(end) != '>'))) {
        if (text.charAt(end) == '[') {
          bracket++;
        } else if (text.charAt(end) == ']') {
          bracket--;
        }
        end++;
      }
      chain.add(parseCompound(text.substring(i, end), child, whole));
      child = false;
      i = end;
    }
    if (chain.isEmpty()) {
      throw new SyntaxException(whole);
    }
    return chain;
  }

  private static Compound parseCompound(String text, boolean child, String whole) {
    List<String> classes = new ArrayList<>();
    List<String[]> attributes = new ArrayList<>();
    List<String> pseudos = new ArrayList<>();
    String id = "";
    int i = 0;
    while (i < text.length() && (isNameChar(text.charAt(i)) || text.charAt(i) == '*')) {
      i++;
    }
    String tag = text.substring(0, i).toLowerCase(Locale.ROOT);
    while (i < text.length()) {
      char marker = text.charAt(i);
      if (marker == '#' || marker == '.') {
        int end = i + 1;
        while (end < text.length() && isNameChar(text.charAt(end))) {
          end++;
        }
        String name = text.substring(i + 1, end);
        if (name.isEmpty()) {
          throw new SyntaxException(whole);
        }
        if (marker == '#') {
          id = name;
        } else {
          classes.add(name);
        }
        i = end;
      } else if (marker == ':') {
        int end = i + 1;
        while (end < text.length() && isNameChar(text.charAt(end))) {
          end++;
        }
        String name = text.substring(i + 1, end).toLowerCase(Locale.ROOT);
        if (!name.equals("root") && !name.equals("first-child") && !name.equals("last-child")) {
          throw new SyntaxException(whole);
        }
        pseudos.add(name);
        i = end;
      } else if (marker == '[') {
        int end = text.indexOf(']', i);
        if (end < 0) {
          throw new SyntaxException(whole);
        }
        String body = text.substring(i + 1, end);
        int equals = body.indexOf('=');
        if (equals < 0) {
          attributes.add(new String[] {body.strip().toLowerCase(Locale.ROOT), null});
        } else {
          String value = body.substring(equals + 1).strip();
          boolean quoted =
              value.length() >= 2
                  && ((value.startsWith("\"") && value.endsWith("\""))
                      || (value.startsWith("'") && value.endsWith("'")));
          if (quoted) {
            value = value.substring(1, value.length() - 1);
          }
          attributes.add(
              new String[] {body.substring(0, equals).strip().toLowerCase(Locale.ROOT), value});
        }
        i = end + 1;
      } else {
        throw new SyntaxException(whole);
      }
    }
    return new Compound(tag, id, classes, attributes, pseudos, child);
  }

  private static boolean isNameChar(char c) {
    return Character.isLetterOrDigit(c) || c == '-' || c == '_';
  }
}
