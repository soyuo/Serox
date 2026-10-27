package kr.soyuo.serox.browser.network;

import java.util.Objects;
import kr.soyuo.serox.core.error.ApplicationError;

/** Safe, user-presentable failure while validating or decoding an HTTP response. */
public final class BrowserResponseException extends RuntimeException {
  private final ApplicationError error;

  /** Creates a response exception with a sanitized application error. */
  public BrowserResponseException(ApplicationError error) {
    super(Objects.requireNonNull(error, "error").safeMessage());
    this.error = error;
  }

  /** Returns the safe error associated with this response failure. */
  public ApplicationError error() {
    return error;
  }
}
