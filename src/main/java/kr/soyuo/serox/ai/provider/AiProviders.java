package kr.soyuo.serox.ai.provider;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;
import kr.soyuo.serox.ai.api.AiProvider;
import kr.soyuo.serox.core.application.AppConfiguration;
import kr.soyuo.serox.core.model.AiProviderId;
import kr.soyuo.serox.core.model.SecretValue;

/** Constructs only the provider explicitly selected by the user. */
public final class AiProviders {
  private AiProviders() {}

  public static Optional<AiProvider> fromConfiguration(AppConfiguration configuration) {
    return configuration
        .selectedAiKey()
        .map(key -> create(configuration.aiProvider(), configuration.aiModel(), key));
  }

  public static AiProvider create(AiProviderId id, String model, SecretValue key) {
    if (!model.matches("[A-Za-z0-9._:-]{1,128}")) {
      throw new IllegalArgumentException("Invalid AI model identifier");
    }
    HttpClient client =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    String secret = key.revealForRequest();
    return switch (id) {
      case CLAUDE -> new ClaudeProvider(client, ClaudeProvider.DEFAULT_ENDPOINT, model, secret);
      case GEMINI ->
          new StreamingAiProvider(
              client,
              URI.create(
                  "https://generativelanguage.googleapis.com/v1beta/models/"
                      + model
                      + ":streamGenerateContent?alt=sse"),
              model,
              secret,
              StreamingAiProvider.Protocol.GEMINI);
      case CODEX ->
          new StreamingAiProvider(
              client,
              URI.create("https://api.openai.com/v1/responses"),
              model,
              secret,
              StreamingAiProvider.Protocol.RESPONSES);
      case GROK ->
          new StreamingAiProvider(
              client,
              URI.create("https://api.x.ai/v1/responses"),
              model,
              secret,
              StreamingAiProvider.Protocol.RESPONSES);
    };
  }
}
