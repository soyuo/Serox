package kr.soyuo.serox.ai.auth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

/** Small, bounded OAuth JSON requests; errors never include remote bodies or credentials. */
final class OAuthHttp {
  private static final java.util.concurrent.ScheduledExecutorService DEADLINES =
      java.util.concurrent.Executors.newSingleThreadScheduledExecutor(
          runnable -> {
            Thread thread = new Thread(runnable, "oauth-deadlines");
            thread.setDaemon(true);
            return thread;
          });
  private final HttpClient client =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(10))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  JsonObject get(URI uri) throws IOException {
    return send(HttpRequest.newBuilder(uri).GET());
  }

  JsonObject get(URI uri, String accessToken) throws IOException {
    return send(HttpRequest.newBuilder(uri).header("Authorization", "Bearer " + accessToken).GET());
  }

  JsonObject get(URI uri, Map<String, String> headers) throws IOException {
    HttpRequest.Builder builder = HttpRequest.newBuilder(uri).GET();
    headers.forEach(builder::header);
    return send(builder);
  }

  JsonObject post(URI uri, Map<String, String> values) throws IOException {
    return send(
        HttpRequest.newBuilder(uri)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form(values))));
  }

  private JsonObject send(HttpRequest.Builder builder) throws IOException {
    try {
      HttpResponse<java.io.InputStream> response =
          client.send(
              builder.timeout(Duration.ofSeconds(20)).build(),
              HttpResponse.BodyHandlers.ofInputStream());
      try (var body = response.body()) {
        var deadline =
            DEADLINES.schedule(
                () -> {
                  try {
                    body.close();
                  } catch (IOException ignored) {
                    // Closing an already completed response is harmless.
                  }
                },
                20,
                java.util.concurrent.TimeUnit.SECONDS);
        try {
          if (response.statusCode() != 200) {
            throw new IOException(
                "Authentication request failed (HTTP " + response.statusCode() + ")");
          }
          byte[] bytes = body.readNBytes(256 * 1024 + 1);
          if (bytes.length > 256 * 1024) {
            throw new IOException("Authentication response is too large");
          }
          String text = new String(bytes, StandardCharsets.UTF_8);
          return text.isBlank() ? new JsonObject() : JsonParser.parseString(text).getAsJsonObject();
        } finally {
          deadline.cancel(false);
        }
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IOException("Authentication cancelled");
    } catch (RuntimeException exception) {
      throw new IOException("Invalid authentication response");
    }
  }

  static String form(Map<String, String> values) {
    return values.entrySet().stream()
        .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
        .collect(Collectors.joining("&"));
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
