package kr.soyuo.serox.core.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import kr.soyuo.serox.core.model.AiProviderId;
import org.junit.jupiter.api.Test;

class AppConfigurationLoaderTest {
  @Test
  void selectsOnlyTheConfiguredProviderAndRedactsAllKeys() {
    for (AiProviderId id : AiProviderId.values()) {
      var configuration =
          AppConfigurationLoader.fromEnvironment(
              Map.of(
                  "SEROX_AI_PROVIDER",
                  id.name().toLowerCase(),
                  id.environmentKey(),
                  "provider-secret",
                  "SEROX_AI_MODEL",
                  "custom-model"));
      assertEquals(id, configuration.aiProvider());
      assertEquals("custom-model", configuration.aiModel());
      assertTrue(configuration.hasAiApiKey());
      assertFalse(configuration.toString().contains("provider-secret"));
    }
    var missing =
        AppConfigurationLoader.fromEnvironment(
            Map.of("SEROX_AI_PROVIDER", "gemini", "SEROX_ANTHROPIC_API_KEY", "claude-secret"));
    assertFalse(missing.hasAiApiKey());
  }

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
