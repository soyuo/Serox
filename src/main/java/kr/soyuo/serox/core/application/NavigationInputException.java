package kr.soyuo.serox.core.application;

import java.util.Objects;
import kr.soyuo.serox.core.error.ApplicationError;

/** Safe classification failure for invalid addresses or unsupported schemes. */
public final class NavigationInputException extends IllegalArgumentException {
  private final ApplicationError error;

  public NavigationInputException(ApplicationError error) {
    super(Objects.requireNonNull(error, "error").safeMessage());
    this.error = error;
  }

  public ApplicationError error() {
    return error;
  }
}
