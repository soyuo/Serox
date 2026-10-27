package kr.soyuo.serox.ai.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import kr.soyuo.serox.ai.api.AiCompleted;
import kr.soyuo.serox.ai.api.AiEvent;
import kr.soyuo.serox.ai.api.AiFailed;
import kr.soyuo.serox.ai.api.AiRequest;
import kr.soyuo.serox.ai.api.AiTextDelta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StreamingAiProviderTest {
  private HttpServer server;
  private final AtomicReference<String> authorization = new AtomicReference<>();
  private final AtomicReference<String> googleKey = new AtomicReference<>();
  private final AtomicReference<String> requestBody = new AtomicReference<>();
  private int status = 200;
  private String response = "";

  @BeforeEach
  void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/summary",
        exchange -> {
          authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
          googleKey.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
          requestBody.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
          exchange.sendResponseHeaders(status, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  private StreamingAiProvider provider(StreamingAiProvider.Protocol protocol) {
    return new StreamingAiProvider(
        HttpClient.newHttpClient(),
        URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/summary"),
        "test-model",
        "secret-key",
        protocol);
  }

  private List<AiEvent> run(StreamingAiProvider.Protocol protocol) throws Exception {
    List<AiEvent> events = new ArrayList<>();
    CompletableFuture<Void> done = new CompletableFuture<>();
    provider(protocol)
        .analyze(new AiRequest("Summarize this", List.of()))
        .subscribe(
            new Flow.Subscriber<>() {
              @Override
              public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
              }

              @Override
              public void onNext(AiEvent item) {
                events.add(item);
              }

              @Override
              public void onError(Throwable error) {
                done.completeExceptionally(error);
              }

              @Override
              public void onComplete() {
                done.complete(null);
              }
            });
    done.get(10, TimeUnit.SECONDS);
    return events;
  }

  @Test
  void responsesProtocolStreamsGroundedTextAndDisablesStorage() throws Exception {
    response =
        "data: {\"type\":\"response.output_text.delta\",\"delta\":\"Answer [S1]\"}\n\n"
            + "data: {\"type\":\"response.completed\"}\n\n";
    var events = run(StreamingAiProvider.Protocol.RESPONSES);
    assertEquals("Answer [S1]", assertInstanceOf(AiTextDelta.class, events.getFirst()).text());
    assertInstanceOf(AiCompleted.class, events.getLast());
    assertEquals(2, events.size());
    assertEquals("Bearer secret-key", authorization.get());
    var body = JsonParser.parseString(requestBody.get()).getAsJsonObject();
    assertFalse(body.get("store").getAsBoolean());
    assertTrue(body.get("stream").getAsBoolean());
    assertEquals("test-model", body.get("model").getAsString());
    assertTrue(body.get("instructions").getAsString().contains("untrusted"));
    assertFalse(requestBody.get().contains("secret-key"));
  }

  @Test
  void geminiProtocolExcludesThoughtsAndCompletesAtStop() throws Exception {
    response =
        "data: {\"candidates\":[{\"content\":{\"parts\":[{\"thought\":true,\"text\":\"hidden\"},"
            + "{\"text\":\"Answer [S1]\"}]},\"finishReason\":\"STOP\"}]}\n\n";
    var events = run(StreamingAiProvider.Protocol.GEMINI);
    assertEquals("Answer [S1]", assertInstanceOf(AiTextDelta.class, events.getFirst()).text());
    assertInstanceOf(AiCompleted.class, events.getLast());
    assertEquals(2, events.size());
    assertEquals("secret-key", googleKey.get());
    assertEquals(null, authorization.get());
    assertTrue(requestBody.get().contains("systemInstruction"));
    assertFalse(requestBody.get().contains("secret-key"));
  }

  @Test
  void rejectsIncompleteBlockedMalformedAndOversizedResponses() throws Exception {
    for (String text :
        List.of(
            "data: {\"type\":\"response.output_text.delta\",\"delta\":\"partial\"}\n\n",
            "data: {\"type\":\"response.failed\"}\n\n",
            "data: invalid-json\n\n",
            "data: " + "x".repeat(70_000) + "\n\n")) {
      response = text;
      assertInstanceOf(AiFailed.class, run(StreamingAiProvider.Protocol.RESPONSES).getLast());
    }
    response = "data: {\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}\n\n";
    assertInstanceOf(AiFailed.class, run(StreamingAiProvider.Protocol.GEMINI).getLast());
    response = "data: {\"candidates\":[{\"finishReason\":\"MAX_TOKENS\"}]}\n\n";
    assertInstanceOf(AiFailed.class, run(StreamingAiProvider.Protocol.GEMINI).getLast());
  }

  @Test
  void rejectsCredentialsWithoutLeakingTheErrorBody() throws Exception {
    status = 401;
    response = "secret-key cookie=private";
    for (var protocol : StreamingAiProvider.Protocol.values()) {
      var events = run(protocol);
      var failure = assertInstanceOf(AiFailed.class, events.getFirst());
      assertEquals("unauthorized", failure.code());
      assertFalse(failure.safeMessage().contains("secret-key"));
      assertFalse(failure.safeMessage().contains("private"));
      assertEquals(1, events.size());
    }
  }

  @Test
  void cancellationBeforeDemandDoesNotSendCredentials() {
    provider(StreamingAiProvider.Protocol.RESPONSES)
        .analyze(new AiRequest("test", List.of()))
        .subscribe(
            new Flow.Subscriber<>() {
              @Override
              public void onSubscribe(Flow.Subscription subscription) {
                subscription.cancel();
                subscription.request(1);
              }

              @Override
              public void onNext(AiEvent event) {
                throw new AssertionError("Cancelled stream emitted an event");
              }

              @Override
              public void onError(Throwable error) {
                throw new AssertionError(error);
              }

              @Override
              public void onComplete() {
                throw new AssertionError("Cancelled stream completed");
              }
            });
    assertEquals(null, requestBody.get());
  }
}
