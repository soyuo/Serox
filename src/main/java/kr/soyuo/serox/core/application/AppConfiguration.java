package kr.soyuo.serox.core.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import kr.soyuo.serox.core.model.AiProviderId;
import kr.soyuo.serox.core.model.SecretValue;

/** Process configuration; secrets are optional and never persisted in preferences. */
public record AppConfiguration(
    Optional<SecretValue> braveSearchApiKey,
    Optional<SecretValue> anthropicApiKey,
    Map<AiProviderId, SecretValue> aiApiKeys,
    AiProviderId aiProvider,
    String aiModel) {
  /** Preserves the original Claude-only configuration entry point. */
  public AppConfiguration(
      Optional<SecretValue> braveSearchApiKey, Optional<SecretValue> anthropicApiKey) {
    this(
        braveSearchApiKey,
        anthropicApiKey,
        anthropicApiKey.map(key -> Map.of(AiProviderId.CLAUDE, key)).orElseGet(Map::of),
        AiProviderId.CLAUDE,
        AiProviderId.CLAUDE.defaultModel());
  }

  /** Requires non-null optional holders; absent secrets remain unconfigured. */
  public AppConfiguration {
    Objects.requireNonNull(braveSearchApiKey, "braveSearchApiKey");
    Objects.requireNonNull(anthropicApiKey, "anthropicApiKey");
    aiApiKeys = Map.copyOf(aiApiKeys);
    Objects.requireNonNull(aiProvider, "aiProvider");
    Objects.requireNonNull(aiModel, "aiModel");
    if (!aiModel.matches("[A-Za-z0-9._:-]{1,128}")) {
      throw new IllegalArgumentException("Invalid AI model identifier");
    }
  }

  public boolean hasBraveSearchApiKey() {
    return braveSearchApiKey.isPresent();
  }

  public boolean hasAnthropicApiKey() {
    return anthropicApiKey.isPresent();
  }

  public Optional<SecretValue> selectedAiKey() {
    return Optional.ofNullable(aiApiKeys.get(aiProvider));
  }

  public boolean hasAiApiKey() {
    return selectedAiKey().isPresent();
  }
}
