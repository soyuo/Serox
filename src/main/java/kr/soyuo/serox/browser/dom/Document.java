package kr.soyuo.serox.browser.dom;

import java.net.URI;
import java.util.Objects;

/** Root of a parsed HTML document. */
public final class Document extends Node {
  private final URI documentUrl;

  public Document(URI documentUrl) {
    this.documentUrl = Objects.requireNonNull(documentUrl, "documentUrl");
  }

  public URI documentUrl() {
    return documentUrl;
  }
}
