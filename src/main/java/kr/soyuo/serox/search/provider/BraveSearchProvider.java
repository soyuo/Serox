package kr.soyuo.serox.search.provider;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import kr.soyuo.serox.browser.network.HttpRequestDispatcher;
import kr.soyuo.serox.core.model.UntrustedText;
import kr.soyuo.serox.search.api.SearchProvider;
import kr.soyuo.serox.search.api.SearchRequest;
import kr.soyuo.serox.search.api.SearchResponse;
import kr.soyuo.serox.search.api.SearchResult;

/** Brave Web Search API adapter returning provider-independent search results. */
public final class BraveSearchProvider implements SearchProvider {
  private static final URI ENDPOINT = URI.create("https://api.search.brave.com/res/v1/web/search");
  private static final int MAX_RESPONSE_BYTES = 1024 * 1024;
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(12);
  private static final int MAX_RESULTS = 20;

  private final HttpClient client;
  private final URI endpoint;
  private final String apiKey;
  private final Duration requestTimeout;

  /** Creates a provider using the Brave endpoint and a client that does not follow redirects. */
  public BraveSearchProvider(String apiKey) {
    this(
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
        ENDPOINT,
        apiKey,
        REQUEST_TIMEOUT);
  }

  /** Creates an injectable provider for deterministic transport tests. */
  BraveSearchProvider(HttpClient client, URI endpoint, String apiKey, Duration requestTimeout) {
    this.client = Objects.requireNonNull(client, "client");
    this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
    this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
    this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
    if (apiKey.isBlank()) {
      throw new IllegalArgumentException("apiKey must not be blank");
    }
    if (endpoint.getHost() == null
        || !("https".equalsIgnoreCase(endpoint.getScheme())
            || ("http".equalsIgnoreCase(endpoint.getScheme()) && isLoopback(endpoint.getHost())))) {
      throw new IllegalArgumentException("endpoint must use HTTPS");
    }
    if (requestTimeout.isZero() || requestTimeout.isNegative()) {
      throw new IllegalArgumentException("requestTimeout must be positive");
    }
  }

  @Override
  public CompletionStage<SearchResponse> search(SearchRequest request) {
    Objects.requireNonNull(request, "request");
    HttpRequest httpRequest =
        HttpRequest.newBuilder(withQuery(request))
            .timeout(requestTimeout)
            .header("Accept", "application/json")
            .header("X-Subscription-Token", apiKey)
            .GET()
            .build();
    return client
        .sendAsync(
            httpRequest,
            ignored -> new HttpRequestDispatcher.LimitedBodySubscriber(MAX_RESPONSE_BYTES))
        .thenApply(
            response -> {
              if (response.statusCode() != 200) {
                throw failureForStatus(response.statusCode());
              }
              return parseResponse(response.body(), request.resultLimit());
            });
  }

  private URI withQuery(SearchRequest request) {
    String query =
        "q="
            + encode(request.query())
            + "&country=KR&search_lang=ko&ui_lang=ko-KR&safesearch="
            + request.safeSearch()
            + "&count="
            + Math.min(request.resultLimit(), MAX_RESULTS);
    return URI.create(endpoint + "?" + query);
  }

  private SearchResponse parseResponse(byte[] bytes, int resultLimit) {
    try {
      JsonObject root =
          JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
      JsonElement web = root.get("web");
      if (web == null || web.isJsonNull()) {
        return new SearchResponse("brave", java.util.List.of());
      }
      JsonElement resultsElement = web.getAsJsonObject().get("results");
      if (resultsElement == null || resultsElement.isJsonNull()) {
        return new SearchResponse("brave", java.util.List.of());
      }
      JsonArray results = resultsElement.getAsJsonArray();
      LinkedHashMap<String, SearchResult> unique = new LinkedHashMap<>();
      for (JsonElement resultElement : results) {
        parseResult(resultElement, unique);
        if (unique.size() >= resultLimit) {
          break;
        }
      }
      return new SearchResponse("brave", java.util.List.copyOf(unique.values()));
    } catch (JsonParseException
        | IllegalStateException
        | UnsupportedOperationException
        | IllegalArgumentException exception) {
      throw new SearchProviderException("Brave Search returned an unreadable response.", exception);
    }
  }

  private void parseResult(JsonElement resultElement, LinkedHashMap<String, SearchResult> unique) {
    if (!resultElement.isJsonObject()) {
      return;
    }
    JsonObject result = resultElement.getAsJsonObject();
    String title = UntrustedText.fromMarkup(stringValue(result, "title"), 200);
    String url = stringValue(result, "url");
    if (title.isBlank() || url.isBlank()) {
      return;
    }
    try {
      URI uri = URI.create(url);
      if (!isWebUri(uri)) {
        return;
      }
      String canonical = canonicalUrl(uri);
      unique.putIfAbsent(
          canonical,
          new SearchResult(
              canonical,
              uri,
              title,
              UntrustedText.fromMarkup(stringValue(result, "description"), 500),
              unique.size() + 1));
    } catch (IllegalArgumentException ignored) {
      // A malformed individual result is ignored while valid results remain usable.
    }
  }

  private String stringValue(JsonObject object, String name) {
    JsonElement value = object.get(name);
    return value == null || value.isJsonNull() ? "" : value.getAsString();
  }

  private boolean isWebUri(URI uri) {
    String scheme = uri.getScheme();
    return uri.isAbsolute()
        && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
        && uri.getHost() != null
        && !uri.getHost().isBlank()
        && uri.getUserInfo() == null
        && !UntrustedText.hasHiddenCharacters(uri.toString());
  }

  private String canonicalUrl(URI uri) {
    String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
    String host = uri.getHost().toLowerCase(Locale.ROOT);
    int port = uri.getPort();
    boolean defaultPort =
        port == -1
            || (scheme.equals("http") && port == 80)
            || (scheme.equals("https") && port == 443);
    String path = uri.normalize().getRawPath();
    if (path == null || path.isBlank()) {
      path = "/";
    }
    if (path.length() > 1 && path.endsWith("/")) {
      path = path.substring(0, path.length() - 1);
    }
    return scheme
        + "://"
        + host
        + (defaultPort ? "" : ":" + port)
        + path
        + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
  }

  private String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private boolean isLoopback(String host) {
    return host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1") || host.equals("[::1]");
  }

  private SearchProviderException failureForStatus(int statusCode) {
    if (statusCode == 401 || statusCode == 403) {
      return new SearchProviderException(
          "Brave Search rejected the API key. Check SEROX_BRAVE_SEARCH_API_KEY.");
    }
    if (statusCode == 429) {
      return new SearchProviderException("Brave Search rate limit reached. Try again shortly.");
    }
    if (statusCode >= 500) {
      return new SearchProviderException("Brave Search is temporarily unavailable.");
    }
    return new SearchProviderException("Brave Search could not complete this request.");
  }
}
