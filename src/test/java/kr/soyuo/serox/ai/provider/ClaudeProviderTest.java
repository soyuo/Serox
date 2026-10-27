package kr.soyuo.serox.ai.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
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
import kr.soyuo.serox.ai.api.EvidenceDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClaudeProviderTest {
  private HttpServer server;
  private final AtomicReference<String> seenKey = new AtomicReference<>();
  private final AtomicReference<String> seenBody = new AtomicReference<>();
  private volatile int status = 200;
  private volatile String response = "";

  @BeforeEach
  void startServer() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/v1/messages",
        exchange -> {
          seenKey.set(exchange.getRequestHeaders().getFirst("x-api-key"));
          seenBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(status, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  private ClaudeProvider provider() {
    return new ClaudeProvider(
        HttpClient.newHttpClient(),
        URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/messages"),
        "test-model",
        "secret-key");
  }

  private static AiRequest request() {
    return new AiRequest(
        "What is Serox?",
        List.of(
            new EvidenceDocument(
                "S1", URI.create("https://example.test/"), "Serox", "A browser.", Instant.now())));
  }

  private List<AiEvent> run() throws Exception {
    List<AiEvent> events = new ArrayList<>();
    CompletableFuture<Void> done = new CompletableFuture<>();
    provider()
        .analyze(request())
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
              public void onError(Throwable throwable) {
                done.completeExceptionally(throwable);
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
  void streamsTextDeltasThenCompletes() throws Exception {
    response =
        "event: content_block_delta\n"
            + "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"Hello \"}}\n\n"
            + "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"world [S1]\"}}\n\n"
            + "data: {\"type\":\"message_stop\"}\n\n";

    List<AiEvent> events = run();

    assertEquals(3, events.size());
    assertEquals("Hello ", assertInstanceOf(AiTextDelta.class, events.get(0)).text());
    assertEquals("world [S1]", assertInstanceOf(AiTextDelta.class, events.get(1)).text());
    assertInstanceOf(AiCompleted.class, events.get(2));
    assertEquals("secret-key", seenKey.get());
    assertTrue(seenBody.get().contains("\"stream\":true"));
    assertTrue(seenBody.get().contains("<document id=\\\"S1\\\""));
    assertFalse(seenBody.get().contains("secret-key"));
  }

  @Test
  void mapsHttpFailuresToSafeFailureEvents() throws Exception {
    status = 401;
    response = "{\"error\":{\"message\":\"invalid x-api-key secret-key\"}}";

    List<AiEvent> events = run();

    AiFailed failed = assertInstanceOf(AiFailed.class, events.getFirst());
    assertEquals("unauthorized", failed.code());
    assertFalse(failed.safeMessage().contains("secret-key"));
  }

  @Test
  void truncatedStreamReportsAnIncompleteAnswer() throws Exception {
    response =
        "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"Par\"}}\n\n";

    List<AiEvent> events = run();

    assertEquals("incomplete", assertInstanceOf(AiFailed.class, events.getLast()).code());
  }
}
