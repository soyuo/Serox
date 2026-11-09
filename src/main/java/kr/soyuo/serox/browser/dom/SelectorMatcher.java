package kr.soyuo.serox.browser.dom;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Matches CSS selectors for style rules and script queries: type, universal, {@code #id},
 * {@code .class}, attribute selectors with every operator ({@code = ~= |= ^= $= *=}, and the
 * {@code i} flag), the four combinators (descendant, {@code >}, {@code +}, {@code ~}), selector
 * lists, and the common pseudo-classes: structural ({@code :first-child}, {@code :nth-child()} and
 * friends, {@code :empty}, {@code :root}), logical ({@code :not() :is() :where() :has()}), and form
 * and language states. Pseudo-classes that need user interaction ({@code :hover}, {@code :focus},
 * ...) and pseudo-elements ({@code ::before}, ...) are valid but never match, because the page is
 * drawn once without them. Anything else is an error, as in a browser.
 */
public final class SelectorMatcher {
  /** The selector text is not one this matcher understands. */
  public static final class SyntaxException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    SyntaxException(String selector) {
      super("'" + selector + "' is not a valid selector");
    }
  }

  private static final int MAX_LENGTH = 2_000;

  /** Interaction and history states: valid, but false in a static drawing. */
  private static final List<String> NEVER =
      List.of(
          "hover",
          "active",
          "focus",
          "focus-visible",
          "focus-within",
          "visited",
          "target",
          "target-within",
          "current",
          "past",
          "future",
          "playing",
          "paused",
          "user-invalid",
          "user-valid",
          "invalid",
          "autofill",
          "-webkit-autofill",
          "fullscreen",
          "modal",
          "picture-in-picture",
          "popover-open",
          "open",
          "closed",
          "placeholder-shown",
          "indeterminate",
          "default",
          "defined",
          "scope",
          "host",
          "blank",
          "local-link");

  private enum Combinator {
    DESCENDANT,
    CHILD,
    ADJACENT,
    SIBLING
  }

  private record Attr(String name, String op, String value, boolean ignoreCase) {}

  private record Pseudo(String name, String argument, List<SelectorMatcher> nested, int[] nth) {}

  /** One compound selector, and how it relates to the compound before it in the chain. */
  private record Compound(
      String tag,
      String id,
      List<String> classes,
      List<Attr> attributes,
      List<Pseudo> pseudos,
      boolean pseudoElement,
      Combinator combinator) {}

  private final List<List<Compound>> alternatives = new ArrayList<>();

  /** For {@code :has()}: the chains start relative to the tested element. */
  private final boolean relative;

  public SelectorMatcher(String selector) {
    this(selector, false);
  }

  private SelectorMatcher(String selector, boolean relative) {
    this.relative = relative;
    if (selector == null || selector.isBlank() || selector.length() > MAX_LENGTH) {
      throw new SyntaxException(String.valueOf(selector));
    }
    for (String part : splitTopLevel(selector, ',', selector)) {
      alternatives.add(parseChain(part.strip(), selector, relative));
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

  /** {@code :has()}: whether some element related to {@code anchor} matches a relative chain. */
  private boolean matchesRelative(Element anchor) {
    for (List<Compound> chain : alternatives) {
      Combinator first = chain.get(0).combinator();
      List<Element> candidates =
          switch (first) {
            case CHILD -> childElements(anchor);
            case ADJACENT -> nextSibling(anchor).map(List::of).orElse(List.of());
            case SIBLING -> followingSiblings(anchor);
            case DESCENDANT -> descendantElements(anchor);
          };
      for (Element candidate : candidates) {
        for (Element end : endsFrom(candidate, chain, 0)) {
          if (end != null) {
            return true;
          }
        }
      }
    }
    return false;
  }

  /** Elements reached by walking the rest of a relative chain forward from {@code start}. */
  private List<Element> endsFrom(Element start, List<Compound> chain, int index) {
    if (!matchesCompound(start, chain.get(index))) {
      return List.of();
    }
    if (index == chain.size() - 1) {
      return List.of(start);
    }
    Combinator next = chain.get(index + 1).combinator();
    List<Element> candidates =
        switch (next) {
          case CHILD -> childElements(start);
          case ADJACENT -> nextSibling(start).map(List::of).orElse(List.of());
          case SIBLING -> followingSiblings(start);
          case DESCENDANT -> descendantElements(start);
        };
    List<Element> ends = new ArrayList<>();
    for (Element candidate : candidates) {
      ends.addAll(endsFrom(candidate, chain, index + 1));
      if (!ends.isEmpty()) {
        break;
      }
    }
    return ends;
  }

  private boolean matchesChain(Element element, List<Compound> chain, int index) {
    Compound compound = chain.get(index);
    if (!matchesCompound(element, compound)) {
      return false;
    }
    if (index == 0) {
      return true;
    }
    switch (compound.combinator()) {
      case CHILD -> {
        return element.parent().orElse(null) instanceof Element parent
            && matchesChain(parent, chain, index - 1);
      }
      case ADJACENT -> {
        Optional<Element> previous = previousSibling(element);
        return previous.isPresent() && matchesChain(previous.get(), chain, index - 1);
      }
      case SIBLING -> {
        for (Element previous : precedingSiblings(element)) {
          if (matchesChain(previous, chain, index - 1)) {
            return true;
          }
        }
        return false;
      }
      default -> {
        Node parent = element.parent().orElse(null);
        while (parent != null) {
          if (parent instanceof Element ancestor && matchesChain(ancestor, chain, index - 1)) {
            return true;
          }
          parent = parent.parent().orElse(null);
        }
        return false;
      }
    }
  }

  private boolean matchesCompound(Element element, Compound compound) {
    if (compound.pseudoElement()) {
      return false;
    }
    if (!compound.tag().isEmpty()
        && !compound.tag().equals("*")
        && !compound.tag().equals(element.tagName())) {
      return false;
    }
    if (!compound.id().isEmpty() && !element.attribute("id").orElse("").equals(compound.id())) {
      return false;
    }
    if (!compound.classes().isEmpty()) {
      String classNames = element.attribute("class").orElse("");
      for (String wanted : compound.classes()) {
        if (!hasToken(classNames, wanted)) {
          return false;
        }
      }
    }
    for (Attr attribute : compound.attributes()) {
      if (!matchesAttribute(element, attribute)) {
        return false;
      }
    }
    for (Pseudo pseudo : compound.pseudos()) {
      if (!matchesPseudo(element, pseudo)) {
        return false;
      }
    }
    return true;
  }

  private static boolean matchesAttribute(Element element, Attr attribute) {
    Optional<String> present = element.attribute(attribute.name());
    if (present.isEmpty()) {
      return false;
    }
    if (attribute.op().isEmpty()) {
      return true;
    }
    String actual = present.get();
    String wanted = attribute.value();
    if (attribute.ignoreCase()) {
      actual = actual.toLowerCase(Locale.ROOT);
      wanted = wanted.toLowerCase(Locale.ROOT);
    }
    return switch (attribute.op()) {
      case "=" -> actual.equals(wanted);
      case "~=" -> !wanted.isEmpty() && hasToken(actual, wanted);
      case "|=" -> actual.equals(wanted) || actual.startsWith(wanted + "-");
      case "^=" -> !wanted.isEmpty() && actual.startsWith(wanted);
      case "$=" -> !wanted.isEmpty() && actual.endsWith(wanted);
      case "*=" -> !wanted.isEmpty() && actual.contains(wanted);
      default -> false;
    };
  }

  /** Whether a space-separated list holds {@code token}, without splitting it. */
  private static boolean hasToken(String list, String token) {
    int from = 0;
    while (true) {
      int at = list.indexOf(token, from);
      if (at < 0) {
        return false;
      }
      int end = at + token.length();
      boolean startOk = at == 0 || Character.isWhitespace(list.charAt(at - 1));
      boolean endOk = end == list.length() || Character.isWhitespace(list.charAt(end));
      if (startOk && endOk) {
        return true;
      }
      from = at + 1;
    }
  }

  private boolean matchesPseudo(Element element, Pseudo pseudo) {
    String tag = element.tagName();
    return switch (pseudo.name()) {
      case "root" ->
          element.parent().orElse(null) instanceof Document document
              && document.documentElement().orElse(null) == element;
      case "first-child" -> siblings(element).indexOf(element) == 0;
      case "last-child" -> {
        List<Element> all = siblings(element);
        yield !all.isEmpty() && all.get(all.size() - 1) == element;
      }
      case "only-child" -> siblings(element).size() == 1;
      case "first-of-type" -> sameType(element).indexOf(element) == 0;
      case "last-of-type" -> {
        List<Element> all = sameType(element);
        yield !all.isEmpty() && all.get(all.size() - 1) == element;
      }
      case "only-of-type" -> sameType(element).size() == 1;
      case "nth-child" -> nth(pseudo.nth(), siblings(element).indexOf(element) + 1);
      case "nth-last-child" -> {
        List<Element> all = siblings(element);
        yield nth(pseudo.nth(), all.size() - all.indexOf(element));
      }
      case "nth-of-type" -> nth(pseudo.nth(), sameType(element).indexOf(element) + 1);
      case "nth-last-of-type" -> {
        List<Element> all = sameType(element);
        yield nth(pseudo.nth(), all.size() - all.indexOf(element));
      }
      case "empty" ->
          element.children().stream()
              .allMatch(child -> child instanceof CommentNode
                  || child instanceof TextNode text && text.textContent().isEmpty());
      case "not" -> pseudo.nested().stream().noneMatch(inner -> inner.matches(element));
      case "is", "where", "matches", "-webkit-any", "-moz-any" ->
          pseudo.nested().stream().anyMatch(inner -> inner.matches(element));
      case "has" -> pseudo.nested().stream().anyMatch(inner -> inner.matchesRelative(element));
      case "lang" -> language(element).map(lang -> langMatches(lang, pseudo.argument()))
          .orElse(false);
      case "dir" -> pseudo.argument().equalsIgnoreCase(direction(element));
      case "link", "any-link", "-webkit-any-link" ->
          (tag.equals("a") || tag.equals("area")) && element.attribute("href").isPresent();
      case "checked" ->
          (tag.equals("input") && element.attribute("checked").isPresent())
              || (tag.equals("option") && element.attribute("selected").isPresent());
      case "disabled" -> isFormControl(tag) && element.attribute("disabled").isPresent();
      case "enabled" -> isFormControl(tag) && element.attribute("disabled").isEmpty();
      case "required" -> isFormControl(tag) && element.attribute("required").isPresent();
      case "optional" -> isFormControl(tag) && element.attribute("required").isEmpty();
      case "read-only" -> !isEditable(element);
      case "read-write" -> isEditable(element);
      default -> false;
    };
  }

  private static boolean isFormControl(String tag) {
    return List.of("input", "button", "select", "textarea", "option", "optgroup", "fieldset")
        .contains(tag);
  }

  private static boolean isEditable(Element element) {
    String tag = element.tagName();
    return (tag.equals("input") || tag.equals("textarea"))
            && element.attribute("readonly").isEmpty()
            && element.attribute("disabled").isEmpty()
        || element.attribute("contenteditable").map(value -> !value.equals("false")).orElse(false);
  }

  private static Optional<String> language(Element element) {
    Node node = element;
    while (node != null) {
      if (node instanceof Element current && current.attribute("lang").isPresent()) {
        return current.attribute("lang");
      }
      node = node.parent().orElse(null);
    }
    return Optional.empty();
  }

  private static boolean langMatches(String lang, String argument) {
    for (String wanted : argument.split(",")) {
      String range = unquote(wanted.strip()).toLowerCase(Locale.ROOT);
      String actual = lang.toLowerCase(Locale.ROOT);
      if (range.equals("*") || actual.equals(range) || actual.startsWith(range + "-")) {
        return true;
      }
    }
    return false;
  }

  private static String direction(Element element) {
    Node node = element;
    while (node != null) {
      if (node instanceof Element current) {
        String dir = current.attribute("dir").orElse("").toLowerCase(Locale.ROOT);
        if (dir.equals("rtl") || dir.equals("ltr")) {
          return dir;
        }
      }
      node = node.parent().orElse(null);
    }
    return "ltr";
  }

  /** Whether position {@code index} (from 1) fits {@code a*n + b} for some n >= 0. */
  private static boolean nth(int[] ab, int index) {
    int a = ab[0];
    int b = ab[1];
    if (a == 0) {
      return index == b;
    }
    int difference = index - b;
    return difference % a == 0 && difference / a >= 0;
  }

  private static List<Element> siblings(Element element) {
    return element
        .parent()
        .map(parent -> childElements(parent))
        .orElse(List.of(element));
  }

  private static List<Element> sameType(Element element) {
    return siblings(element).stream()
        .filter(sibling -> sibling.tagName().equals(element.tagName()))
        .toList();
  }

  private static List<Element> childElements(Node parent) {
    List<Element> elements = new ArrayList<>();
    for (Node child : parent.children()) {
      if (child instanceof Element element) {
        elements.add(element);
      }
    }
    return elements;
  }

  private static List<Element> descendantElements(Node root) {
    List<Element> elements = new ArrayList<>();
    for (Node node : root.descendants()) {
      if (node instanceof Element element) {
        elements.add(element);
      }
    }
    return elements;
  }

  private static Optional<Element> previousSibling(Element element) {
    List<Element> all = siblings(element);
    int index = all.indexOf(element);
    return index > 0 ? Optional.of(all.get(index - 1)) : Optional.empty();
  }

  private static Optional<Element> nextSibling(Element element) {
    List<Element> all = siblings(element);
    int index = all.indexOf(element);
    return index >= 0 && index + 1 < all.size() ? Optional.of(all.get(index + 1)) : Optional.empty();
  }

  private static List<Element> precedingSiblings(Element element) {
    List<Element> all = siblings(element);
    int index = all.indexOf(element);
    return index > 0 ? all.subList(0, index) : List.of();
  }

  private static List<Element> followingSiblings(Element element) {
    List<Element> all = siblings(element);
    int index = all.indexOf(element);
    return index >= 0 ? all.subList(index + 1, all.size()) : List.of();
  }

  // ---- parsing ----

  /** Splits on {@code separator} outside brackets, parentheses and quotes. */
  private static List<String> splitTopLevel(String text, char separator, String whole) {
    List<String> parts = new ArrayList<>();
    int depth = 0;
    char quote = 0;
    int start = 0;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (quote != 0) {
        if (c == '\\') {
          i++;
        } else if (c == quote) {
          quote = 0;
        }
      } else if (c == '"' || c == '\'') {
        quote = c;
      } else if (c == '(' || c == '[') {
        depth++;
      } else if (c == ')' || c == ']') {
        depth--;
        if (depth < 0) {
          throw new SyntaxException(whole);
        }
      } else if (c == separator && depth == 0) {
        parts.add(text.substring(start, i));
        start = i + 1;
      }
    }
    if (depth != 0 || quote != 0) {
      throw new SyntaxException(whole);
    }
    parts.add(text.substring(start));
    return parts;
  }

  private static List<Compound> parseChain(String text, String whole, boolean relative) {
    List<Compound> chain = new ArrayList<>();
    Combinator combinator = Combinator.DESCENDANT;
    boolean explicit = false;
    int i = 0;
    while (i < text.length()) {
      char c = text.charAt(i);
      if (Character.isWhitespace(c)) {
        i++;
        continue;
      }
      if (c == '>' || c == '+' || c == '~') {
        if (explicit || (chain.isEmpty() && !relative)) {
          throw new SyntaxException(whole);
        }
        combinator =
            c == '>' ? Combinator.CHILD : c == '+' ? Combinator.ADJACENT : Combinator.SIBLING;
        explicit = true;
        i++;
        continue;
      }
      int end = compoundEnd(text, i, whole);
      chain.add(parseCompound(text.substring(i, end), combinator, whole));
      combinator = Combinator.DESCENDANT;
      explicit = false;
      i = end;
    }
    if (chain.isEmpty() || explicit) {
      throw new SyntaxException(whole);
    }
    return chain;
  }

  /** The end of the compound starting at {@code start}: the next top-level space or combinator. */
  private static int compoundEnd(String text, int start, String whole) {
    int depth = 0;
    char quote = 0;
    int i = start;
    for (; i < text.length(); i++) {
      char c = text.charAt(i);
      if (quote != 0) {
        if (c == '\\') {
          i++;
        } else if (c == quote) {
          quote = 0;
        }
        continue;
      }
      if (c == '"' || c == '\'') {
        quote = c;
      } else if (c == '(' || c == '[') {
        depth++;
      } else if (c == ')' || c == ']') {
        depth--;
      } else if (depth == 0
          && (Character.isWhitespace(c) || c == '>' || c == '+' || c == '~')) {
        break;
      } else if (c == '\\') {
        i++;
      }
    }
    if (depth != 0 || quote != 0) {
      throw new SyntaxException(whole);
    }
    return i;
  }

  private static Compound parseCompound(String text, Combinator combinator, String whole) {
    List<String> classes = new ArrayList<>();
    List<Attr> attributes = new ArrayList<>();
    List<Pseudo> pseudos = new ArrayList<>();
    boolean pseudoElement = false;
    String id = "";
    int i = 0;
    if (i < text.length() && text.charAt(i) == '*') {
      i++;
    } else {
      while (i < text.length() && isNameChar(text.charAt(i))) {
        i++;
      }
    }
    // A namespace prefix such as "svg|rect" or "*|div" is ignored.
    if (i < text.length() && text.charAt(i) == '|' && (i + 1 >= text.length()
        || text.charAt(i + 1) != '=')) {
      int startTag = ++i;
      if (i < text.length() && text.charAt(i) == '*') {
        i++;
      } else {
        while (i < text.length() && isNameChar(text.charAt(i))) {
          i++;
        }
      }
      text = text.substring(startTag);
      i -= startTag;
    }
    String tag = unescape(text.substring(0, i)).toLowerCase(Locale.ROOT);
    while (i < text.length()) {
      char marker = text.charAt(i);
      if (marker == '#' || marker == '.') {
        int end = nameEnd(text, i + 1);
        String name = unescape(text.substring(i + 1, end));
        if (name.isEmpty()) {
          throw new SyntaxException(whole);
        }
        if (marker == '#') {
          id = name;
        } else {
          classes.add(name);
        }
        i = end;
      } else if (marker == '[') {
        int end = closing(text, i, '[', ']', whole);
        attributes.add(parseAttribute(text.substring(i + 1, end), whole));
        i = end + 1;
      } else if (marker == ':') {
        boolean element = i + 1 < text.length() && text.charAt(i + 1) == ':';
        int nameStart = i + (element ? 2 : 1);
        int end = nameEnd(text, nameStart);
        String name = text.substring(nameStart, end).toLowerCase(Locale.ROOT);
        if (name.isEmpty()) {
          throw new SyntaxException(whole);
        }
        String argument = null;
        if (end < text.length() && text.charAt(end) == '(') {
          int close = closing(text, end, '(', ')', whole);
          argument = text.substring(end + 1, close).strip();
          end = close + 1;
        }
        i = end;
        if (element || isLegacyPseudoElement(name)) {
          pseudoElement = true;
          continue;
        }
        pseudos.add(parsePseudo(name, argument, whole));
      } else {
        throw new SyntaxException(whole);
      }
    }
    return new Compound(tag, id, classes, attributes, pseudos, pseudoElement, combinator);
  }

  private static boolean isLegacyPseudoElement(String name) {
    return List.of("before", "after", "first-line", "first-letter").contains(name)
        || name.startsWith("-webkit-") && !name.equals("-webkit-any")
            && !name.equals("-webkit-any-link") && !name.equals("-webkit-autofill")
        || name.startsWith("-moz-") && !name.equals("-moz-any");
  }

  private static Pseudo parsePseudo(String name, String argument, String whole) {
    switch (name) {
      case "not", "is", "where", "matches", "-webkit-any", "-moz-any", "has" -> {
        if (argument == null || argument.isEmpty()) {
          throw new SyntaxException(whole);
        }
        List<SelectorMatcher> nested = new ArrayList<>();
        boolean relative = name.equals("has");
        for (String part : splitTopLevel(argument, ',', whole)) {
          try {
            nested.add(new SelectorMatcher(part.strip(), relative));
          } catch (SyntaxException invalid) {
            // :is() and :where() are forgiving; :not() and :has() are not.
            if (name.equals("not") || name.equals("has")) {
              throw new SyntaxException(whole);
            }
          }
        }
        return new Pseudo(name, argument, nested, null);
      }
      case "nth-child", "nth-last-child", "nth-of-type", "nth-last-of-type" -> {
        if (argument == null) {
          throw new SyntaxException(whole);
        }
        String formula = argument;
        int of = formula.toLowerCase(Locale.ROOT).indexOf(" of ");
        if (of >= 0) {
          // "An+B of S" filters is not supported; treat as plain An+B.
          formula = formula.substring(0, of);
        }
        return new Pseudo(name, argument, List.of(), parseNth(formula.strip(), whole));
      }
      case "lang", "dir" -> {
        if (argument == null || argument.isEmpty()) {
          throw new SyntaxException(whole);
        }
        return new Pseudo(name, argument, List.of(), null);
      }
      default -> {
        boolean known =
            NEVER.contains(name)
                || List.of(
                        "root",
                        "first-child",
                        "last-child",
                        "only-child",
                        "first-of-type",
                        "last-of-type",
                        "only-of-type",
                        "empty",
                        "link",
                        "any-link",
                        "-webkit-any-link",
                        "checked",
                        "disabled",
                        "enabled",
                        "required",
                        "optional",
                        "read-only",
                        "read-write")
                    .contains(name);
        if (!known || argument != null && !name.equals("host")) {
          throw new SyntaxException(whole);
        }
        // Interaction states never match: their name maps to the default branch (false).
        return new Pseudo(NEVER.contains(name) ? "-never" : name, argument, List.of(), null);
      }
    }
  }

  /** Parses {@code an+b}, {@code odd}, {@code even} or a plain number. */
  private static int[] parseNth(String formula, String whole) {
    String text = formula.toLowerCase(Locale.ROOT).replace(" ", "");
    if (text.equals("odd")) {
      return new int[] {2, 1};
    }
    if (text.equals("even")) {
      return new int[] {2, 0};
    }
    try {
      int n = text.indexOf('n');
      if (n < 0) {
        return new int[] {0, Integer.parseInt(text)};
      }
      String aText = text.substring(0, n);
      int a =
          aText.isEmpty() || aText.equals("+")
              ? 1
              : aText.equals("-") ? -1 : Integer.parseInt(aText);
      String bText = text.substring(n + 1);
      int b = bText.isEmpty() ? 0 : Integer.parseInt(bText.startsWith("+") ? bText.substring(1) : bText);
      return new int[] {a, b};
    } catch (NumberFormatException invalid) {
      throw new SyntaxException(whole);
    }
  }

  private static Attr parseAttribute(String body, String whole) {
    body = body.strip();
    int op = -1;
    for (int i = 0; i < body.length(); i++) {
      char c = body.charAt(i);
      if (c == '=') {
        op = i;
        break;
      }
    }
    if (op < 0) {
      String name = attributeName(body, whole);
      return new Attr(name, "", "", false);
    }
    String operator = "=";
    int nameEnd = op;
    if (op > 0 && "~|^$*".indexOf(body.charAt(op - 1)) >= 0) {
      operator = body.charAt(op - 1) + "=";
      nameEnd = op - 1;
    }
    String name = attributeName(body.substring(0, nameEnd), whole);
    String rest = body.substring(op + 1).strip();
    boolean ignoreCase = false;
    String value;
    if (!rest.isEmpty() && (rest.charAt(0) == '"' || rest.charAt(0) == '\'')) {
      char quote = rest.charAt(0);
      int close = rest.indexOf(quote, 1);
      while (close > 0 && rest.charAt(close - 1) == '\\') {
        close = rest.indexOf(quote, close + 1);
      }
      if (close < 0) {
        throw new SyntaxException(whole);
      }
      value = unescape(rest.substring(1, close));
      rest = rest.substring(close + 1).strip();
    } else {
      int space = rest.indexOf(' ');
      value = unescape(space < 0 ? rest : rest.substring(0, space));
      rest = space < 0 ? "" : rest.substring(space + 1).strip();
    }
    if (rest.equalsIgnoreCase("i")) {
      ignoreCase = true;
    } else if (!rest.isEmpty() && !rest.equalsIgnoreCase("s")) {
      throw new SyntaxException(whole);
    }
    return new Attr(name, operator, value, ignoreCase);
  }

  private static String attributeName(String text, String whole) {
    String name = text.strip();
    int bar = name.indexOf('|');
    if (bar >= 0) {
      name = name.substring(bar + 1);
    }
    if (name.isEmpty()) {
      throw new SyntaxException(whole);
    }
    return unescape(name).toLowerCase(Locale.ROOT);
  }

  private static int closing(String text, int open, char opener, char closer, String whole) {
    int depth = 0;
    char quote = 0;
    for (int i = open; i < text.length(); i++) {
      char c = text.charAt(i);
      if (quote != 0) {
        if (c == '\\') {
          i++;
        } else if (c == quote) {
          quote = 0;
        }
      } else if (c == '"' || c == '\'') {
        quote = c;
      } else if (c == opener) {
        depth++;
      } else if (c == closer && --depth == 0) {
        return i;
      }
    }
    throw new SyntaxException(whole);
  }

  private static int nameEnd(String text, int start) {
    int i = start;
    while (i < text.length()) {
      char c = text.charAt(i);
      if (c == '\\' && i + 1 < text.length()) {
        i += 2;
      } else if (isNameChar(c)) {
        i++;
      } else {
        break;
      }
    }
    return i;
  }

  /** Resolves CSS escapes such as {@code \:} and {@code \31 }. */
  private static String unescape(String text) {
    if (text.indexOf('\\') < 0) {
      return text;
    }
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c != '\\' || i + 1 >= text.length()) {
        out.append(c);
        continue;
      }
      int end = i + 1;
      while (end < text.length() && end - i <= 6 && Character.digit(text.charAt(end), 16) >= 0) {
        end++;
      }
      if (end > i + 1) {
        out.appendCodePoint(Integer.parseInt(text.substring(i + 1, end), 16));
        if (end < text.length() && text.charAt(end) == ' ') {
          end++;
        }
        i = end - 1;
      } else {
        out.append(text.charAt(i + 1));
        i++;
      }
    }
    return out.toString();
  }

  private static String unquote(String text) {
    return text.length() >= 2
            && (text.startsWith("\"") && text.endsWith("\"")
                || text.startsWith("'") && text.endsWith("'"))
        ? text.substring(1, text.length() - 1)
        : text;
  }

  private static boolean isNameChar(char c) {
    return Character.isLetterOrDigit(c) || c == '-' || c == '_' || c > 0x7f;
  }
}
