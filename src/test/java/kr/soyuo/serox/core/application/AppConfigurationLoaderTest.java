package kr.soyuo.serox.core.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AppConfigurationLoaderTest {
  @Test
  void loadsConfiguredSecretsAndKeepsTheirStringRepresentationRedacted() {
    AppConfiguration configuration =
        AppConfigurationLoader.fromEnvironment(
            Map.of(
                AppConfigurationLoader.BRAVE_SEARCH_API_KEY,
                "brave-secret",
                AppConfigurationLoader.ANTHROPIC_API_KEY,
                "anthropic-secret"));

    assertTrue(configuration.hasBraveSearchApiKey());
    assertTrue(configuration.hasAnthropicApiKey());
    assertEquals("[REDACTED]", configuration.braveSearchApiKey().orElseThrow().toString());
    assertEquals(
        "brave-secret", configuration.braveSearchApiKey().orElseThrow().revealForRequest());
  }

  @Test
  void treatsMissingAndBlankSecretsAsUnconfigured() {
    AppConfiguration configuration =
        AppConfigurationLoader.fromEnvironment(
            Map.of(AppConfigurationLoader.BRAVE_SEARCH_API_KEY, "  "));

    assertFalse(configuration.hasBraveSearchApiKey());
    assertFalse(configuration.hasAnthropicApiKey());
  }
}
