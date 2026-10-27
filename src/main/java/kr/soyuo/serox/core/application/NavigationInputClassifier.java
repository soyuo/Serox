package kr.soyuo.serox.core.application;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Pattern;
import kr.soyuo.serox.browser.api.PageLoadRequest;
import kr.soyuo.serox.core.error.ApplicationError;
import kr.soyuo.serox.core.error.ErrorCode;

/** Distinguishes web addresses from search text and rejects unsafe destinations. */
public final class NavigationInputClassifier {
  private static final Pattern SCHEME = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*:");
  private static final Pattern HOST_LIKE =
      Pattern.compile("^(localhost|(?:[A-Za-z0-9-]+\\.)+[A-Za-z]{2,})(?::\\d+)?(?:[/#?].*)?$");

  public NavigationTarget classify(String input) {
    if (input == null || input.isBlank()) {
      throw invalidAddress();
    }

    String value = input.trim();
    if (value.contains(" ") || value.contains("\t") || value.contains("\n")) {
      return new SearchQuery(value);
    }

    boolean hostLike = HOST_LIKE.matcher(value).matches();
    boolean explicitScheme = hasExplicitScheme(value);
    if (explicitScheme && !isWebScheme(value) && !hostLike) {
      throw unsupportedScheme();
    }
    if (!explicitScheme && !hostLike) {
      return new SearchQuery(value);
    }

    try {
      URI uri = new URI(hostLike ? "https://" + value : value);
      if (uri.getPort() == 0 || uri.getPort() > 65535) {
        throw invalidAddress();
      }
      new PageLoadRequest(uri);
      return new DirectAddress(uri);
    } catch (URISyntaxException | IllegalArgumentException exception) {
      if (hasExplicitScheme(value) && !isWebScheme(value)) {
        throw unsupportedScheme();
      }
      throw invalidAddress();
    }
  }

  private boolean hasExplicitScheme(String value) {
    if (!SCHEME.matcher(value).find()) {
      return false;
    }
    int colonIndex = value.indexOf(':');
    int slashIndex = value.indexOf('/');
    return slashIndex < 0 || colonIndex < slashIndex;
  }

  private boolean isWebScheme(String value) {
    String scheme = value.substring(0, value.indexOf(':')).toLowerCase(Locale.ROOT);
    return scheme.equals("http") || scheme.equals("https");
  }

  private NavigationInputException invalidAddress() {
    return new NavigationInputException(
        new ApplicationError(
            ErrorCode.INVALID_ADDRESS, "Enter a valid web address or search query.", false));
  }

  private NavigationInputException unsupportedScheme() {
    return new NavigationInputException(
        new ApplicationError(
            ErrorCode.UNSUPPORTED_SCHEME, "Only HTTP and HTTPS addresses are supported.", false));
  }
}
