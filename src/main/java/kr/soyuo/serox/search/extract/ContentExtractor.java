package kr.soyuo.serox.search.extract;

import java.util.Locale;
import java.util.Set;
import kr.soyuo.serox.browser.dom.Document;
import kr.soyuo.serox.browser.dom.Element;
import kr.soyuo.serox.browser.dom.Node;
import kr.soyuo.serox.browser.dom.TextNode;
import kr.soyuo.serox.core.model.UntrustedText;

/** Turns a parsed DOM into bounded plain text, skipping scripts, styles and other non-content. */
public final class ContentExtractor {
  private static final Set<String> SKIPPED_ELEMENTS =
      Set.of("script", "style", "noscript", "template", "svg", "head", "iframe", "object");
  private static final Set<String> BLOCK_ELEMENTS =
      Set.of(
          "p", "div", "br", "li", "ul", "ol", "h1", "h2", "h3", "h4", "h5", "h6", "section",
          "article", "tr", "table", "blockquote", "pre", "header", "footer", "main");

  private final int maxTextChars;

  /** Creates an extractor that never returns more than {@code maxTextChars} characters of text. */
  public ContentExtractor(int maxTextChars) {
    if (maxTextChars < 1) {
      throw new IllegalArgumentException("maxTextChars must be positive");
    }
    this.maxTextChars = maxTextChars;
  }

  /** Extracts the title and visible text of the document. */
  public ExtractedContent extract(Document document) {
    String title =
        document.getElementsByTagName("title").stream()
            .findFirst()
            .map(element -> UntrustedText.plain(element.textContent(), 200))
            .orElse("");
    StringBuilder text = new StringBuilder();
    document.body().ifPresent(body -> collect(body, text));
    String collapsed = UntrustedText.plain(text.toString(), Integer.MAX_VALUE - 2);
    if (collapsed.length() > maxTextChars) {
      collapsed = collapsed.substring(0, maxTextChars);
    }
    return new ExtractedContent(title, collapsed);
  }

  private void collect(Node node, StringBuilder out) {
    if (out.length() > maxTextChars * 2) {
      return;
    }
    if (node instanceof TextNode textNode) {
      out.append(textNode.data());
      return;
    }
    if (node instanceof Element element) {
      String tag = element.tagName().toLowerCase(Locale.ROOT);
      if (SKIPPED_ELEMENTS.contains(tag)) {
        return;
      }
      boolean block = BLOCK_ELEMENTS.contains(tag);
      if (block) {
        out.append(' ');
      }
      for (Node child : element.children()) {
        collect(child, out);
      }
      if (block) {
        out.append(' ');
      }
      return;
    }
    for (Node child : node.children()) {
      collect(child, out);
    }
  }
}
