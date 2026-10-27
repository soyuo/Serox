package kr.soyuo.serox.core.event;

import java.time.Instant;
import java.util.Objects;
import kr.soyuo.serox.core.model.TabId;

/** A page could not be opened in a tab; {@code reason} says which error page to show. */
public record NavigationFailed(Instant occurredAt, TabId tabId, String address, Reason reason)
    implements ApplicationEvent {
  /** Why the address could not be opened. */
  public enum Reason {
    /** The text is not a usable web address. */
    INVALID_ADDRESS,
    /** The host name does not resolve. */
    NOT_FOUND,
    /** The site did not answer in time. */
    TIMEOUT,
    /** Any other failure to reach the site. */
    UNREACHABLE
  }

  public NavigationFailed {
    Objects.requireNonNull(occurredAt, "occurredAt");
    Objects.requireNonNull(tabId, "tabId");
    Objects.requireNonNull(address, "address");
    Objects.requireNonNull(reason, "reason");
  }
}
