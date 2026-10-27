package kr.soyuo.serox.browser.network;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

/**
 * Decides whether a web address points at the public internet. Used before fetching pages on
 * behalf of the AI and MCP features, so a search result cannot make Serox read local or internal
 * resources.
 */
public final class PublicAddressPolicy {
  private static final List<String> INTERNAL_SUFFIXES =
      List.of(".localhost", ".local", ".internal", ".lan", ".home", ".corp", ".intranet");

  private final Function<String, List<InetAddress>> resolver;

  /** Uses the system resolver. */
  public PublicAddressPolicy() {
    this(PublicAddressPolicy::resolve);
  }

  /** Uses the supplied resolver, which makes the policy testable without DNS. */
  public PublicAddressPolicy(Function<String, List<InetAddress>> resolver) {
    this.resolver = Objects.requireNonNull(resolver, "resolver");
  }

  /** True when the address is http(s) and every address of its host is public. */
  public boolean isPublic(URI uri) {
    String scheme = uri.getScheme();
    String host = uri.getHost();
    if (scheme == null
        || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
        || host == null
        || host.isBlank()
        || uri.getUserInfo() != null) {
      return false;
    }
    String lowered = host.toLowerCase(Locale.ROOT);
    if (lowered.equals("localhost") || INTERNAL_SUFFIXES.stream().anyMatch(lowered::endsWith)) {
      return false;
    }
    List<InetAddress> addresses = resolver.apply(host);
    return !addresses.isEmpty() && addresses.stream().noneMatch(PublicAddressPolicy::isPrivate);
  }

  static boolean isPrivate(InetAddress address) {
    if (address.isLoopbackAddress()
        || address.isAnyLocalAddress()
        || address.isLinkLocalAddress()
        || address.isSiteLocalAddress()
        || address.isMulticastAddress()) {
      return true;
    }
    byte[] bytes = address.getAddress();
    if (bytes.length == 4) {
      int first = bytes[0] & 0xff;
      int second = bytes[1] & 0xff;
      return first == 0 || (first == 100 && second >= 64 && second <= 127) || first >= 240;
    }
    return (bytes[0] & 0xfe) == 0xfc;
  }

  private static List<InetAddress> resolve(String host) {
    try {
      return List.of(InetAddress.getAllByName(host));
    } catch (UnknownHostException exception) {
      return List.of();
    }
  }
}
