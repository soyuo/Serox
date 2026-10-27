package kr.soyuo.serox.core.application;

import java.util.Objects;
import java.util.Optional;
import kr.soyuo.serox.core.model.SecretValue;

/** Process configuration; secrets are optional and never persisted in preferences. */
public record AppConfiguration(
    Optional<SecretValue> braveSearchApiKey, Optional<SecretValue> anthropicApiKey) {
  /** Requires non-null optional holders; absent secrets remain unconfigured. */
  public AppConfiguration {
    Objects.requireNonNull(braveSearchApiKey, "braveSearchApiKey");
    Objects.requireNonNull(anthropicApiKey, "anthropicApiKey");
  }

  public boolean hasBraveSearchApiKey() {
    return braveSearchApiKey.isPresent();
  }

  public boolean hasAnthropicApiKey() {
    return anthropicApiKey.isPresent();
  }
}
