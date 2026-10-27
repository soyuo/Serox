package kr.soyuo.serox.core.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import kr.soyuo.serox.core.model.SecretValue;

/** Loads provider secrets from process environment without logging or persisting them. */
public final class AppConfigurationLoader {
  public static final String BRAVE_SEARCH_API_KEY = "SEROX_BRAVE_SEARCH_API_KEY";
  public static final String ANTHROPIC_API_KEY = "SEROX_ANTHROPIC_API_KEY";

  /** Prevents instantiation of this configuration utility. */
  private AppConfigurationLoader() {}

  /** Creates configuration from the supplied process environment values. */
  public static AppConfiguration fromEnvironment(Map<String, String> environment) {
    Objects.requireNonNull(environment, "environment");
    return new AppConfiguration(
        readSecret(environment, BRAVE_SEARCH_API_KEY), readSecret(environment, ANTHROPIC_API_KEY));
  }

  private static Optional<SecretValue> readSecret(Map<String, String> environment, String key) {
    String value = environment.get(key);
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }
    return Optional.of(new SecretValue(value));
  }
}
