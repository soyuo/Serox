package kr.soyuo.serox.search.provider;

/** Provider failure with a safe message suitable for showing in the search UI. */
public final class SearchProviderException extends RuntimeException {
  /** Creates a provider failure without including request credentials or response bodies. */
  public SearchProviderException(String message) {
    super(message);
  }

  /** Creates a safe provider failure and retains the cause for diagnostic handling. */
  public SearchProviderException(String message, Throwable cause) {
    super(message, cause);
  }
}
