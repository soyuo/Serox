package kr.soyuo.serox.browser.network;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import kr.soyuo.serox.browser.dom.Document;
import kr.soyuo.serox.browser.dom.Element;
import kr.soyuo.serox.browser.dom.Node;
import kr.soyuo.serox.browser.dom.TextNode;
import kr.soyuo.serox.browser.script.ScriptFetcher;

/**
 * Replaces {@code <link rel="stylesheet">} elements with {@code <style>} elements holding the
 * loaded text, in the same place, so the cascade order of the page is kept. Sheets for print, a
 * wrong type, a non-web address, or too many sheets are left out.
 */
public final class StylesheetInliner {
  private static final int MAX_SHEETS = 8;
  private static final int MAX_CHARS = 500_000;

  private final ScriptFetcher fetcher;

  /** {@code fetcher} loads the text at an address, or nothing when it cannot. */
  public StylesheetInliner(ScriptFetcher fetcher) {
    this.fetcher = Objects.requireNonNull(fetcher, "fetcher");
  }

  /** Loads and inlines the page's outside style sheets; returns how many were inlined. */
  public int inline(Document document) {
    int inlined = 0;
    List<Element> links = document.getElementsByTagName("link");
    for (Element link : links) {
      if (inlined >= MAX_SHEETS) {
        break;
      }
      if (!isScreenStylesheet(link)) {
        continue;
      }
      Optional<URI> address = resolve(document.documentUrl(), link.attribute("href").orElse(""));
      Optional<Node> parent = link.parent();
      if (address.isEmpty() || parent.isEmpty()) {
        continue;
      }
      Optional<String> text = fetcher.fetch(address.get());
      if (text.isEmpty() || text.get().length() > MAX_CHARS) {
        continue;
      }
      Element style = new Element("style");
      style.appendChild(new TextNode(text.get()));
      parent.get().replaceChild(link, style);
      inlined++;
    }
    return inlined;
  }

  private static boolean isScreenStylesheet(Element link) {
    String rel = link.attribute("rel").orElse("").toLowerCase(Locale.ROOT);
    if (!List.of(rel.split("\\s+")).contains("stylesheet")) {
      return false;
    }
    String type = link.attribute("type").orElse("").toLowerCase(Locale.ROOT).strip();
    if (!type.isEmpty() && !type.equals("text/css")) {
      return false;
    }
    String media = link.attribute("media").orElse("").toLowerCase(Locale.ROOT).strip();
    return media.isEmpty() || media.contains("screen") || media.equals("all");
  }

  private static Optional<URI> resolve(URI base, String href) {
    try {
      URI resolved = base.resolve(href.strip());
      String scheme = resolved.getScheme();
      boolean web = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
      return web && resolved.getHost() != null ? Optional.of(resolved) : Optional.empty();
    } catch (IllegalArgumentException malformed) {
      return Optional.empty();
    }
  }
}
