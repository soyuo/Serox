package kr.soyuo.serox.ai.provider;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import kr.soyuo.serox.ai.api.AiCompleted;
import kr.soyuo.serox.ai.api.AiEvent;
import kr.soyuo.serox.ai.api.AiFailed;
import kr.soyuo.serox.ai.api.AiTextDelta;

/** One bounded SSE exchange, owned and cancelled by one summary subscription. */
final class SseExchange implements Flow.Subscription {
  private static final int MAX_FRAME = 65_536;
  private static final int MAX_TEXT = 65_536;
  private final HttpClient client;
  private final HttpRequest request;
  private final Flow.Subscriber<? super AiEvent> subscriber;
  private final Function<String, List<AiEvent>> decoder;
  private final AtomicBoolean started = new AtomicBoolean();
  private volatile boolean finished;
  private volatile CompletableFuture<?> exchange;
  private volatile InputStream body;
  private int textLength;

  SseExchange(
      HttpClient client,
      HttpRequest request,
      Flow.Subscriber<? super AiEvent> subscriber,
      Function<String, List<AiEvent>> decoder) {
    this.client = client;
    this.request = request;
    this.subscriber = subscriber;
    this.decoder = decoder;
  }

  @Override
  public void request(long count) {
    if (count <= 0) {
      finish(new AiFailed("bad_request", "Invalid stream demand."));
      return;
    }
    if (finished || !started.compareAndSet(false, true)) {
      return;
    }
    CompletableFuture.delayedExecutor(60, TimeUnit.SECONDS)
        .execute(() -> finish(new AiFailed("timeout", "The AI service took too long to respond.")));
    var pending = client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());
    exchange = pending;
    pending.whenComplete(
        (response, failure) -> {
          if (failure != null) {
            finish(new AiFailed("network", "The AI service could not be reached."));
          } else {
            body = response.body();
            if (finished) {
              closeBody();
            } else {
              Thread.ofVirtual().start(() -> consume(response));
            }
          }
        });
    if (finished) {
      exchange.cancel(true);
    }
  }

  @Override
  public synchronized void cancel() {
    finished = true;
    closeBody();
    if (exchange != null) {
      exchange.cancel(true);
    }
  }

  private void consume(HttpResponse<InputStream> response) {
    try (var reader =
        new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
      if (response.statusCode() != 200) {
        finish(statusFailure(response.statusCode()));
        return;
      }
      StringBuilder data = new StringBuilder();
      String line;
      while (!finished && (line = readLine(reader)) != null) {
        if (line.isEmpty()) {
          if (!data.isEmpty()) {
            for (AiEvent event : decoder.apply(data.toString())) {
              emit(event);
            }
            data.setLength(0);
          }
        } else if (line.startsWith("data:")) {
          if (!data.isEmpty()) {
            data.append('\n');
          }
          data.append(line.substring(5).stripLeading());
          if (data.length() > MAX_FRAME) {
            throw new IOException("SSE frame exceeds the limit");
          }
        }
      }
      if (!finished && !data.isEmpty()) {
        for (AiEvent event : decoder.apply(data.toString())) {
          emit(event);
        }
      }
      finish(new AiFailed("incomplete", "The AI response ended unexpectedly."));
    } catch (IOException | RuntimeException failure) {
      finish(new AiFailed("invalid_response", "The AI response could not be read."));
    } finally {
      closeBody();
    }
  }

  private String readLine(BufferedReader reader) throws IOException {
    StringBuilder line = new StringBuilder();
    int next;
    while ((next = reader.read()) != -1) {
      if (next == '\n') {
        break;
      }
      if (next != '\r') {
        line.append((char) next);
      }
      if (line.length() > MAX_FRAME) {
        throw new IOException("SSE line exceeds the limit");
      }
    }
    return next == -1 && line.isEmpty() ? null : line.toString();
  }

  private synchronized void emit(AiEvent event) {
    if (finished) {
      return;
    }
    if (event instanceof AiFailed || event instanceof AiCompleted) {
      finish(event);
    } else {
      if (event instanceof AiTextDelta delta) {
        textLength += delta.text().length();
        if (textLength > MAX_TEXT) {
          finish(new AiFailed("too_large", "The AI answer exceeded the supported size."));
          return;
        }
      }
      subscriber.onNext(event);
    }
  }

  private synchronized void finish(AiEvent event) {
    if (!finished) {
      finished = true;
      subscriber.onNext(event);
      subscriber.onComplete();
      closeBody();
      if (exchange != null) {
        exchange.cancel(true);
      }
    }
  }

  private void closeBody() {
    InputStream current = body;
    if (current != null) {
      try {
        current.close();
      } catch (IOException ignored) {
        // The request is already finished; body content must never be logged.
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
}
