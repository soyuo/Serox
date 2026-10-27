package kr.soyuo.serox.core.model;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

/** Connection facts about the page shown in a tab, derived from its address. */
public record SiteInfo(Level level, String host, String summary) {
  /** Connection security classification. */
  public enum Level {
    /** No page is loaded, for example the home or search screens. */
    NONE,
    SECURE,
    INSECURE
  }

  public SiteInfo {
    Objects.requireNonNull(level, "level");
    Objects.requireNonNull(host, "host");
    Objects.requireNonNull(summary, "summary");
  }

  /** Describes the connection used for the given page address. */
  public static SiteInfo of(Optional<URI> pageAddress) {
    if (pageAddress.isEmpty()) {
      return new SiteInfo(Level.NONE, "", "Serox 화면이라 외부 사이트 연결이 없어요.");
    }
    URI uri = pageAddress.orElseThrow();
    String host = uri.getHost() == null ? "" : uri.getHost();
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(java.util.Locale.ROOT);
    return switch (scheme) {
      case "https" -> new SiteInfo(Level.SECURE, host, "보안 연결(HTTPS)로 연결되어 있어요.");
      case "http" ->
          new SiteInfo(Level.INSECURE, host, "보안되지 않은 연결(HTTP)이에요. 입력한 정보가 노출될 수 있어요.");
      default -> new SiteInfo(Level.NONE, host, "지원하지 않는 주소 형식이에요.");
    };
  }
}
