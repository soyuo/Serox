package kr.soyuo.serox.search.provider;

import java.net.URI;
import java.util.Locale;
import kr.soyuo.serox.core.model.UntrustedText;

/** Address checks and normalization shared by the search adapters. */
final class ResultUrls {
  /** Prevents instantiation of this helper. */
  private ResultUrls() {}

  /** Whether the address is a plain absolute http or https address a result may point to. */
  static boolean isWebUri(URI uri) {
    String scheme = uri.getScheme();
    return uri.isAbsolute()
        && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
        && uri.getHost() != null
        && !uri.getHost().isBlank()
        && uri.getUserInfo() == null
        && !UntrustedText.hasHiddenCharacters(uri.toString());
  }

  /** Lower-cases scheme and host and drops default ports, fragments and a trailing slash. */
  static String canonical(URI uri) {
    String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
    String host = uri.getHost().toLowerCase(Locale.ROOT);
    int port = uri.getPort();
    boolean defaultPort =
        port == -1
            || (scheme.equals("http") && port == 80)
            || (scheme.equals("https") && port == 443);
    String path = uri.normalize().getRawPath();
    if (path == null || path.isBlank()) {
      path = "/";
    }
    if (path.length() > 1 && path.endsWith("/")) {
      path = path.substring(0, path.length() - 1);
    }
    return scheme
        + "://"
        + host
        + (defaultPort ? "" : ":" + port)
        + path
        + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
  }
}
