package kr.soyuo.serox.browser.network;

import java.net.URI;
import java.util.Objects;

/** A successful HTML response decoded into Unicode text. */
public record DecodedHtmlResponse(URI uri, String html) {
  /** Validates the final response URI and decoded markup. */
  public DecodedHtmlResponse {
    Objects.requireNonNull(uri, "uri");
    Objects.requireNonNull(html, "html");
  }
}
