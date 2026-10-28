package kr.soyuo.serox.browser.css;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tuple used to order matching CSS selectors in the cascade. */
public record Specificity(int ids, int classesAndAttributes, int elements)
    implements Comparable<Specificity> {
  private static final Pattern ID = Pattern.compile("#[\\w-]+");
  private static final Pattern CLASS_OR_ATTRIBUTE = Pattern.compile("\\.[\\w-]+|\\[[^]]+]|:[\\w-]+");
  private static final Pattern ELEMENT = Pattern.compile("(?:^|\\s|>)([a-zA-Z][\\w-]*)");

  /** Counts ID, class/attribute, and element selectors in a supported selector. */
  public static Specificity of(String selector) {
    return new Specificity(
        count(ID.matcher(selector)),
        count(CLASS_OR_ATTRIBUTE.matcher(selector)),
        count(ELEMENT.matcher(selector)));
  }

  @Override
  public int compareTo(Specificity other) {
    int idOrder = Integer.compare(ids, other.ids);
    if (idOrder != 0) {
      return idOrder;
    }
    int classOrder = Integer.compare(classesAndAttributes, other.classesAndAttributes);
    return classOrder != 0 ? classOrder : Integer.compare(elements, other.elements);
  }

  private static int count(Matcher matcher) {
    int count = 0;
    while (matcher.find()) {
      count++;
    }
    return count;
  }
}
