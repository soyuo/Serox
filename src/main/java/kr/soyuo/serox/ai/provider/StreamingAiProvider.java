package kr.soyuo.serox.ai.provider;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Flow;
import kr.soyuo.serox.ai.api.AiCompleted;
import kr.soyuo.serox.ai.api.AiEvent;
import kr.soyuo.serox.ai.api.AiFailed;
import kr.soyuo.serox.ai.api.AiProvider;
import kr.soyuo.serox.ai.api.AiRequest;
import kr.soyuo.serox.ai.api.AiTextDelta;
import kr.soyuo.serox.ai.context.PromptBuilder;

/** Grounded summary adapter for the Responses and Gemini content streaming protocols. */
public final class StreamingAiProvider implements AiProvider {
  /** The two supported wire formats; OpenAI and xAI share the Responses format. */
  public enum Protocol {
    RESPONSES,
    GEMINI
  }

  /** Thinking and reasoning tokens count against this, so it leaves room for the answer. */
  private static final int MAX_OUTPUT_TOKENS = 8192;

  private final HttpClient client;
  private final URI endpoint;
  private final String model;
  private final String apiKey;
  private final Protocol protocol;

  /** The explicit endpoint allows deterministic tests without using live credentials. */
  public StreamingAiProvider(
      HttpClient client, URI endpoint, String model, String apiKey, Protocol protocol) {
    this.client = Objects.requireNonNull(client, "client");
    this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
    this.model = Objects.requireNonNull(model, "model");
    this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
    this.protocol = Objects.requireNonNull(protocol, "protocol");
  }

  @Override
  public Flow.Publisher<AiEvent> analyze(AiRequest request) {
    Objects.requireNonNull(request, "request");
    return subscriber -> {
      HttpRequest httpRequest =
          HttpRequest.newBuilder(endpoint)
              .timeout(Duration.ofSeconds(60))
              .header("Content-Type", "application/json")
              .header("Accept", "text/event-stream")
              .header(
                  protocol == Protocol.GEMINI ? "x-goog-api-key" : "Authorization",
                  protocol == Protocol.GEMINI ? apiKey : "Bearer " + apiKey)
              .POST(HttpRequest.BodyPublishers.ofString(body(request).toString()))
              .build();
      subscriber.onSubscribe(
          new SseExchange(
              client,
              httpRequest,
              subscriber,
              protocol == Protocol.GEMINI
                  ? StreamingAiProvider::geminiEvents
                  : StreamingAiProvider::responseEvents));
    };
  }

  private JsonObject body(AiRequest request) {
    JsonObject body = new JsonObject();
    if (protocol == Protocol.RESPONSES) {
      body.addProperty("model", model);
      body.addProperty("instructions", PromptBuilder.systemPrompt(request.answerLanguage()));
      body.addProperty("input", PromptBuilder.userMessage(request));
      body.addProperty("stream", true);
      body.addProperty("store", false);
      body.addProperty("max_output_tokens", MAX_OUTPUT_TOKENS);
    } else {
      body.add("systemInstruction", content(PromptBuilder.systemPrompt(request.answerLanguage())));
      JsonArray contents = new JsonArray();
      JsonObject user = content(PromptBuilder.userMessage(request));
      user.addProperty("role", "user");
      contents.add(user);
      body.add("contents", contents);
      JsonObject generation = new JsonObject();
      generation.addProperty("maxOutputTokens", MAX_OUTPUT_TOKENS);
      JsonObject thinking = new JsonObject();
      thinking.addProperty("thinkingLevel", "LOW");
      generation.add("thinkingConfig", thinking);
      body.add("generationConfig", generation);
    }
    return body;
  }

  private static JsonObject content(String text) {
    JsonObject part = new JsonObject();
    part.addProperty("text", text);
    JsonArray parts = new JsonArray();
    parts.add(part);
    JsonObject content = new JsonObject();
    content.add("parts", parts);
    return content;
  }

  private static List<AiEvent> responseEvents(String json) {
    JsonObject event = JsonParser.parseString(json).getAsJsonObject();
    return switch (string(event, "type")) {
      case "response.output_text.delta" -> List.of(new AiTextDelta(string(event, "delta")));
      case "response.completed" -> List.of(new AiCompleted(List.of()));
      case "response.incomplete" ->
          List.of(new AiFailed("too_long", "The AI answer was cut off before it finished."));
      case "response.failed", "response.refusal.delta", "error" ->
          List.of(new AiFailed("provider_error", "The AI service could not complete the answer."));
      default -> List.of();
    };
  }

  private static List<AiEvent> geminiEvents(String json) {
    JsonObject event = JsonParser.parseString(json).getAsJsonObject();
    if (event.has("error")
        || event.has("promptFeedback")
            && event.getAsJsonObject("promptFeedback").has("blockReason")) {
      return List.of(
          new AiFailed("provider_error", "The AI service could not complete the answer."));
    }
    if (!event.has("candidates") || event.getAsJsonArray("candidates").isEmpty()) {
      return List.of();
    }
    JsonObject candidate = event.getAsJsonArray("candidates").get(0).getAsJsonObject();
    List<AiEvent> events = new ArrayList<>();
    if (candidate.has("content")) {
      JsonObject content = candidate.getAsJsonObject("content");
      if (content.has("parts")) {
        for (var value : content.getAsJsonArray("parts")) {
          JsonObject part = value.getAsJsonObject();
          if (part.has("text") && !(part.has("thought") && part.get("thought").getAsBoolean())) {
            events.add(new AiTextDelta(part.get("text").getAsString()));
          }
        }
      }
    }
    String finish = string(candidate, "finishReason");
    if (!finish.isEmpty()) {
      events.add(
          switch (finish) {
            case "STOP" -> new AiCompleted(List.of());
            case "MAX_TOKENS" ->
                new AiFailed("too_long", "The AI answer was cut off before it finished.");
            default ->
                new AiFailed("provider_error", "The AI service could not complete the answer.");
          });
    }
    return events;
  }

  private static String string(JsonObject object, String key) {
    return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
  }
}
