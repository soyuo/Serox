package kr.soyuo.serox.browser.script;

import java.net.URI;
import java.util.Optional;

/** Loads the text of an external script; empty when it cannot be loaded or is too large. */
@FunctionalInterface
public interface ScriptFetcher {
  /** A fetcher that loads nothing, so only inline scripts run. */
  ScriptFetcher NONE = uri -> Optional.empty();

  Optional<String> fetch(URI uri);
}
