package kr.soyuo.serox.ai.auth;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import kr.soyuo.serox.core.model.AiProviderId;

/**
 * Checks credentials using model metadata, without sending page text or generating billable text.
 */
public final class ProviderKeyTest {
  private ProviderKeyTest() {}

  public static void check(AiProviderId provider, String key) throws IOException {
    if (key.isBlank() || key.length() > 16 * 1024 || key.contains("\r") || key.contains("\n")) {
      throw new IOException("Invalid API key");
    }
    OAuthHttp http = new OAuthHttp();
    switch (provider) {
      case CLAUDE ->
          http.get(
              URI.create("https://api.anthropic.com/v1/models"),
              Map.of("x-api-key", key, "anthropic-version", "2023-06-01"));
      case GEMINI ->
          http.get(
              URI.create("https://generativelanguage.googleapis.com/v1beta/models"),
              Map.of("x-goog-api-key", key));
      case CODEX -> http.get(URI.create("https://api.openai.com/v1/models"), key);
      case GROK -> http.get(URI.create("https://api.x.ai/v1/models"), key);
    }
  }
}
