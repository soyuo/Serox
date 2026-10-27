package kr.soyuo.serox.browser.network;

import java.net.URI;
import java.net.http.HttpHeaders;
import java.util.Objects;

/** Bounded HTTP response metadata and bytes returned by the transport. */
public record HttpResponseData(URI uri, int statusCode, HttpHeaders headers, byte[] body) {
  /** Copies the response body to keep the snapshot immutable. */
  public HttpResponseData {
    Objects.requireNonNull(uri, "uri");
    Objects.requireNonNull(headers, "headers");
    body = Objects.requireNonNull(body, "body").clone();
  }

  @Override
  public byte[] body() {
    return body.clone();
  }
}
