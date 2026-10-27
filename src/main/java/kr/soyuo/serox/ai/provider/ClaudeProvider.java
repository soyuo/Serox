package kr.soyuo.serox.ai.provider;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import kr.soyuo.serox.ai.api.AiCompleted;
import kr.soyuo.serox.ai.api.AiEvent;
import kr.soyuo.serox.ai.api.AiFailed;
import kr.soyuo.serox.ai.api.AiProvider;
import kr.soyuo.serox.ai.api.AiRequest;
import kr.soyuo.serox.ai.api.AiTextDelta;
import kr.soyuo.serox.ai.context.PromptBuilder;

/** Streams grounded answers from the Anthropic Messages API. */
public final class ClaudeProvider implements AiProvider {
  public static final URI DEFAULT_ENDPOINT = URI.create("https://api.anthropic.com/v1/messages");
  public static final String DEFAULT_MODEL = "claude-sonnet-5-5";
  private static final Logger LOGGER = Logger.getLogger(ClaudeProvider.class.getName());
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
  private static final int MAX_OUTPUT_TOKENS = 1_024;

  private final HttpClient client;
  private final URI endpoint;
  private final String model;
  private final String apiKey;

  /** The key is only placed in the request header and never logged. */
  public ClaudeProvider(HttpClient client, URI endpoint, String model, String apiKey) {
    this.client = Objects.requireNonNull(client, "client");
    this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
    this.model = Objects.requireNonNull(model, "model");
    this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
  }

  /** Creates a provider for the public Anthropic endpoint. */
  public static ClaudeProvider create(String apiKey) {
    return new ClaudeProvider(
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
        DEFAULT_ENDPOINT,
        DEFAULT_MODEL,
        apiKey);
  }

  @Override
  public Flow.Publisher<AiEvent> analyze(AiRequest request) {
    Objects.requireNonNull(request, "request");
    return subscriber -> subscriber.onSubscribe(new StreamedExchange(request, subscriber));
  }

  private String requestBody(AiRequest request) {
    JsonObject message = new JsonObject();
    message.addProperty("role", "user");
    message.addProperty("content", PromptBuilder.userMessage(request));
    JsonObject body = new JsonObject();
    body.addProperty("model", model);
    body.addProperty("max_tokens", MAX_OUTPUT_TOKENS);
    body.addProperty("stream", true);
    body.addProperty("system", PromptBuilder.systemPrompt());
    com.google.gson.JsonArray messages = new com.google.gson.JsonArray();
    messages.add(message);
    body.add("messages", messages);
    return body.toString();
  }

  /** One streamed exchange; ends with exactly one completion or failure event. */
  private final class StreamedExchange implements Flow.Subscription {
    private final AiRequest request;
    private final Flow.Subscriber<? super AiEvent> subscriber;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean finished = new AtomicBoolean();
    private volatile CompletableFuture<?> exchange;

    StreamedExchange(AiRequest request, Flow.Subscriber<? super AiEvent> subscriber) {
      this.request = request;
      this.subscriber = subscriber;
    }

    @Override
    public void request(long n) {
      if (n <= 0) {
        finish(new AiFailed("bad_request", "Invalid stream demand."));
        return;
      }
      if (!started.compareAndSet(false, true)) {
        return;
      }
      HttpRequest httpRequest =
          HttpRequest.newBuilder(endpoint)
              .timeout(REQUEST_TIMEOUT)
              .header("content-type", "application/json")
              .header("x-api-key", apiKey)
              .header("anthropic-version", "2023-06-01")
              .POST(
                  HttpRequest.BodyPublishers.ofString(requestBody(request), StandardCharsets.UTF_8))
              .build();
      exchange =
          client
              .sendAsync(httpRequest, HttpResponse.BodyHandlers.ofLines())
              .thenAccept(this::consume)
              .exceptionally(
                  failure -> {
                    finish(failureFor(failure));
                    return null;
                  });
    }

    @Override
    public void cancel() {
      finished.set(true);
      CompletableFuture<?> current = exchange;
      if (current != null) {
        current.cancel(true);
      }
    }

    private void consume(HttpResponse<Stream<String>> response) {
      try (Stream<String> lines = response.body()) {
        if (response.statusCode() != 200) {
          finish(statusFailure(response.statusCode()));
          return;
        }
        var iterator = lines.iterator();
        while (iterator.hasNext() && !finished.get()) {
          String line = iterator.next();
          if (!line.startsWith("data:")) {
            continue;
          }
          if (handleEvent(line.substring(5).strip())) {
            return;
          }
        }
        finish(new AiFailed("incomplete", "The AI response ended unexpectedly."));
      } catch (RuntimeException exception) {
        finish(failureFor(exception));
      }
    }

    /** Returns true once a terminal event has been delivered. */
    private boolean handleEvent(String json) {
      JsonObject event;
      try {
        event = JsonParser.parseString(json).getAsJsonObject();
      } catch (RuntimeException exception) {
        return false;
      }
      String type = event.has("type") ? event.get("type").getAsString() : "";
      switch (type) {
        case "content_block_delta" -> {
          JsonObject delta = event.has("delta") ? event.getAsJsonObject("delta") : null;
          if (delta != null && delta.has("text")) {
            emit(new AiTextDelta(delta.get("text").getAsString()));
          }
          return false;
        }
        case "message_stop" -> {
          finish(new AiCompleted(List.of()));
          return true;
        }
        case "error" -> {
          finish(new AiFailed("provider_error", "The AI service reported an error."));
          return true;
        }
        default -> {
          return false;
        }
      }
    }

    private void emit(AiEvent event) {
      if (!finished.get()) {
        subscriber.onNext(event);
      }
    }

    private void finish(AiEvent terminal) {
      if (finished.compareAndSet(false, true)) {
        subscriber.onNext(terminal);
        subscriber.onComplete();
      }
    }
  }

  private static AiFailed statusFailure(int status) {
    return switch (status) {
      case 401, 403 -> new AiFailed("unauthorized", "The AI API key was rejected.");
      case 429 -> new AiFailed("rate_limited", "The AI service is busy. Try again shortly.");
      case 400, 404, 413, 422 ->
          new AiFailed("bad_request", "The AI service rejected the request.");
      default -> new AiFailed("unavailable", "The AI service is unavailable right now.");
    };
  }

  private static AiFailed failureFor(Throwable failure) {
    Throwable cause = failure;
    while (cause.getCause() != null) {
      cause = cause.getCause();
    }
    if (cause instanceof java.net.http.HttpTimeoutException
        || cause instanceof java.util.concurrent.TimeoutException) {
      return new AiFailed("timeout", "The AI service took too long to respond.");
    }
    if (cause instanceof IOException) {
      return new AiFailed("network", "The AI service could not be reached.");
    }
    LOGGER.log(Level.WARNING, "AI request failed: {0}", cause.getClass().getSimpleName());
    return new AiFailed("unavailable", "The AI service is unavailable right now.");
  }
}
