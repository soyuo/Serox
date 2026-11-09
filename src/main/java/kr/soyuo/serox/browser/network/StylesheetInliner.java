package kr.soyuo.serox.browser.network;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.soyuo.serox.browser.dom.Document;
import kr.soyuo.serox.browser.dom.Element;
import kr.soyuo.serox.browser.dom.Node;
import kr.soyuo.serox.browser.dom.TextNode;
import kr.soyuo.serox.browser.script.ScriptFetcher;

/**
 * Replaces {@code <link rel="stylesheet">} elements with {@code <style>} elements holding the
 * loaded text, in the same place, so the cascade order of the page is kept. Sheets are fetched in
 * parallel, and {@code @import} rules inside them are loaded one level deep. Sheets for print,
 * alternate sheets, a wrong type, a non-web address, or past the limits are left out.
 */
public final class StylesheetInliner {
  private static final int MAX_SHEETS = 48;
  private static final int MAX_IMPORTS = 16;
  private static final int MAX_CHARS = 1_500_000;
  private static final int MAX_TOTAL_CHARS = 6_000_000;
  private static final long WAIT_SECONDS = 15;

  /** {@code @import url("a.css") screen;} or {@code @import "a.css";} at the start of a sheet. */
  private static final Pattern IMPORT =
      Pattern.compile(
          "@import\\s+(?:url\\(\\s*)?[\"']?([^\"')\\s;]+)[\"']?\\s*\\)?\\s*([^;]*);",
          Pattern.CASE_INSENSITIVE);

  private final ScriptFetcher fetcher;

  /** {@code fetcher} loads the text at an address, or nothing when it cannot. */
  public StylesheetInliner(ScriptFetcher fetcher) {
    this.fetcher = Objects.requireNonNull(fetcher, "fetcher");
  }

  /** Loads and inlines the page's outside style sheets; returns how many were inlined. */
  public int inline(Document document) {
    List<Element> links = new ArrayList<>();
    List<URI> addresses = new ArrayList<>();
    for (Element link : document.getElementsByTagName("link")) {
      if (links.size() >= MAX_SHEETS) {
        break;
      }
      if (!isScreenStylesheet(link) || link.parent().isEmpty()) {
        continue;
      }
      Optional<URI> address = resolve(document.documentUrl(), link.attribute("href").orElse(""));
      if (address.isPresent()) {
        links.add(link);
        addresses.add(address.get());
      }
    }
    if (links.isEmpty()) {
      return 0;
    }
    List<Optional<String>> texts = fetchAll(addresses);
    int inlined = 0;
    long total = 0;
    for (int i = 0; i < links.size(); i++) {
      Optional<String> text = texts.get(i);
      if (text.isEmpty() || total + text.get().length() > MAX_TOTAL_CHARS) {
        continue;
      }
      total += text.get().length();
      Element style = new Element("style");
      style.appendChild(new TextNode(text.get()));
      Element link = links.get(i);
      link.parent().orElseThrow().replaceChild(link, style);
      inlined++;
    }
    return inlined;
  }

  /** Fetches every sheet at once, then resolves each sheet's imports. */
  private List<Optional<String>> fetchAll(List<URI> addresses) {
    try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<Optional<String>>> pending = new ArrayList<>();
      for (URI address : addresses) {
        pending.add(pool.submit(() -> withImports(address, fetchSheet(address), pool)));
      }
      List<Optional<String>> texts = new ArrayList<>();
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
      for (Future<Optional<String>> future : pending) {
        try {
          long left = Math.max(1, deadline - System.nanoTime());
          texts.add(future.get(left, TimeUnit.NANOSECONDS));
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          texts.add(Optional.empty());
        } catch (Exception failed) {
          future.cancel(true);
          texts.add(Optional.empty());
        }
      }
      pool.shutdownNow();
      return texts;
    }
  }

  private Optional<String> fetchSheet(URI address) {
    try {
      return fetcher.fetch(address).filter(text -> text.length() <= MAX_CHARS);
    } catch (RuntimeException failed) {
      return Optional.empty();
    }
  }

  /** Puts the text of each {@code @import}ed sheet in place of its rule. */
  private Optional<String> withImports(URI base, Optional<String> sheet, ExecutorService pool)
      throws Exception {
    if (sheet.isEmpty() || !sheet.get().contains("@import")) {
      return sheet;
    }
    String text = sheet.get();
    Matcher matcher = IMPORT.matcher(text);
    List<int[]> spans = new ArrayList<>();
    List<String> media = new ArrayList<>();
    List<Future<Optional<String>>> loads = new ArrayList<>();
    while (matcher.find() && loads.size() < MAX_IMPORTS) {
      String condition = matcher.group(2).strip().toLowerCase(Locale.ROOT);
      Optional<URI> address = resolve(base, matcher.group(1));
      spans.add(new int[] {matcher.start(), matcher.end()});
      media.add(condition);
      if (address.isEmpty() || !screenMedia(condition)) {
        loads.add(null);
      } else {
        URI target = address.get();
        loads.add(pool.submit(() -> fetchSheet(target)));
      }
    }
    StringBuilder out = new StringBuilder();
    int last = 0;
    for (int i = 0; i < spans.size(); i++) {
      out.append(text, last, spans.get(i)[0]);
      Future<Optional<String>> load = loads.get(i);
      Optional<String> imported = load == null ? Optional.empty() : load.get(10, TimeUnit.SECONDS);
      if (imported.isPresent()) {
        String condition = media.get(i);
        boolean wrap = !condition.isEmpty() && !condition.equals("all");
        out.append(wrap ? "@media " + condition + " {" : "")
            .append(imported.get())
            .append(wrap ? "}" : "")
            .append('\n');
      }
      last = spans.get(i)[1];
    }
    out.append(text, last, text.length());
    return Optional.of(out.toString());
  }

  private static boolean isScreenStylesheet(Element link) {
    String rel = link.attribute("rel").orElse("").toLowerCase(Locale.ROOT);
    List<String> kinds = List.of(rel.strip().split("\\s+"));
    if (!kinds.contains("stylesheet") || kinds.contains("alternate")) {
      return false;
    }
    if (link.attribute("disabled").isPresent()) {
      return false;
    }
    String type = link.attribute("type").orElse("").toLowerCase(Locale.ROOT).strip();
    if (!type.isEmpty() && !type.equals("text/css")) {
      return false;
    }
    return screenMedia(link.attribute("media").orElse("").toLowerCase(Locale.ROOT).strip());
  }

  /** Media queries the screen can meet; width conditions are left for the CSS parser. */
  private static boolean screenMedia(String media) {
    return media.isEmpty()
        || media.contains("screen")
        || media.equals("all")
        || media.startsWith("(")
        || media.startsWith("only screen")
        || media.startsWith("all and");
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
