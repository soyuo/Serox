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
  private static final URI OPENAI_RESPONSES = URI.create("https://api.openai.com/v1/responses");

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
    HttpClient client = client();
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
              client, OPENAI_RESPONSES, model, secret, StreamingAiProvider.Protocol.RESPONSES);
      case GROK ->
          new StreamingAiProvider(
              client,
              URI.create("https://api.x.ai/v1/responses"),
              model,
              secret,
              StreamingAiProvider.Protocol.RESPONSES);
    };
  }

  /** Creates the ChatGPT plan-sharing Responses client, which has a narrower request contract. */
  /** The ChatGPT plan backend Codex CLI uses; it needs the workspace header with the token. */
  private static final URI CHATGPT_RESPONSES =
      URI.create("https://chatgpt.com/backend-api/codex/responses");

  public static AiProvider createChatGptPlan(
      String model, SecretValue accessToken, String chatGptAccountId) {
    if (!model.matches("[A-Za-z0-9._:-]{1,128}")) {
      throw new IllegalArgumentException("Invalid AI model identifier");
    }
    return new StreamingAiProvider(
        client(),
        CHATGPT_RESPONSES,
        model,
        accessToken.revealForRequest(),
        StreamingAiProvider.Protocol.CHATGPT_PLAN,
        chatGptAccountId.isEmpty()
            ? java.util.Map.of("originator", "codex_cli_rs")
            : java.util.Map.of(
                "originator", "codex_cli_rs", "chatgpt-account-id", chatGptAccountId));
  }

  private static HttpClient client() {
    return HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();
  }
}
