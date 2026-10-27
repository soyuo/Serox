package kr.soyuo.serox.browser.network;

/** Indicates that a response exceeded the configured in-memory body limit. */
public final class ResponseTooLargeException extends RuntimeException {
  public ResponseTooLargeException(int maxBytes) {
    super("HTTP response exceeded the configured limit of " + maxBytes + " bytes");
  }
}
