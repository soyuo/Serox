package kr.soyuo.serox.search.provider;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.soyuo.serox.browser.network.HttpRequestDispatcher;
import kr.soyuo.serox.core.model.UntrustedText;
import kr.soyuo.serox.search.api.SearchProvider;
import kr.soyuo.serox.search.api.SearchRequest;
import kr.soyuo.serox.search.api.SearchResponse;
import kr.soyuo.serox.search.api.SearchResult;

/** Search adapter that needs no account: it reads the public DuckDuckGo HTML result page. */
public final class DuckDuckGoSearchProvider implements SearchProvider {
  private static final URI ENDPOINT = URI.create("https://html.duckduckgo.com/html/");
  private static final int MAX_RESPONSE_BYTES = 1024 * 1024;
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(12);
  private static final Pattern ANCHOR =
      Pattern.compile("<a\\s[^>]*result__a[^>]*>(.*?)</a>", Pattern.DOTALL);
  private static final Pattern SNIPPET =
      Pattern.compile("<a\\s[^>]*result__snippet[^>]*>(.*?)</a>", Pattern.DOTALL);
  private static final Pattern HREF = Pattern.compile("href=\"([^\"]*)\"");

  private final HttpClient client;
  private final URI endpoint;
  private final Duration requestTimeout;

  /** Creates a provider that talks to the public endpoint without following redirects. */
  public DuckDuckGoSearchProvider() {
    this(
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
        ENDPOINT,
        REQUEST_TIMEOUT);
  }

  /** Creates an injectable provider for deterministic transport tests. */
  DuckDuckGoSearchProvider(HttpClient client, URI endpoint, Duration requestTimeout) {
    this.client = Objects.requireNonNull(client, "client");
    this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
    this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
  }

  @Override
  public CompletionStage<SearchResponse> search(SearchRequest request) {
    Objects.requireNonNull(request, "request");
    HttpRequest httpRequest =
        HttpRequest.newBuilder(withQuery(request))
            .timeout(requestTimeout)
            .header("Accept", "text/html")
            .header("User-Agent", "Mozilla/5.0 (compatible; Serox)")
            .GET()
            .build();
    return client
        .sendAsync(
            httpRequest,
            ignored -> new HttpRequestDispatcher.LimitedBodySubscriber(MAX_RESPONSE_BYTES))
        .thenApply(
            response -> {
              if (response.statusCode() == 429 || response.statusCode() == 202) {
                throw new SearchProviderException(
                    "DuckDuckGo rate limit reached. Try again shortly.");
              }
              if (response.statusCode() != 200) {
                throw new SearchProviderException("DuckDuckGo could not complete this request.");
              }
              return parse(new String(response.body(), StandardCharsets.UTF_8), request);
            });
  }

  private URI withQuery(SearchRequest request) {
    String safe =
        switch (request.safeSearch()) {
          case "strict" -> "1";
          case "off" -> "-2";
          default -> "-1";
        };
    return URI.create(
        endpoint
            + "?q="
            + URLEncoder.encode(request.query(), StandardCharsets.UTF_8)
            + "&kl=kr-kr&kp="
            + safe);
  }

  /** Reads result titles, addresses and snippets from the result page. */
  SearchResponse parse(String html, SearchRequest request) {
    LinkedHashMap<String, SearchResult> unique = new LinkedHashMap<>();
    Matcher anchors = ANCHOR.matcher(html);
    while (anchors.find() && unique.size() < request.resultLimit()) {
      String tag = anchors.group();
      Matcher href = HREF.matcher(tag);
      if (!href.find()) {
        continue;
      }
      String title = UntrustedText.fromMarkup(anchors.group(1), 200);
      URI target = target(href.group(1));
      if (title.isBlank() || target == null || !ResultUrls.isWebUri(target)) {
        continue;
      }
      int end = anchors.end();
      int nextAnchor = html.indexOf("result__a", end);
      Matcher snippet = SNIPPET.matcher(html);
      snippet.region(end, nextAnchor < 0 ? html.length() : nextAnchor);
      String description = snippet.find() ? UntrustedText.fromMarkup(snippet.group(1), 500) : "";
      String canonical = ResultUrls.canonical(target);
      unique.putIfAbsent(
          canonical, new SearchResult(canonical, target, title, description, unique.size() + 1));
    }
    return new SearchResponse("duckduckgo", java.util.List.copyOf(unique.values()));
  }

  /** Unwraps DuckDuckGo's redirect link; direct links are used as they are. */
  private static URI target(String rawHref) {
    try {
      String href = rawHref.replace("&amp;", "&");
      if (href.startsWith("//")) {
        href = "https:" + href;
      }
      URI link = URI.create(href);
      String query = link.getRawQuery();
      if (query != null && "duckduckgo.com".equalsIgnoreCase(link.getHost())) {
        for (String pair : query.split("&")) {
          if (pair.startsWith("uddg=")) {
            return URI.create(URLDecoder.decode(pair.substring(5), StandardCharsets.UTF_8));
          }
        }
        return null;
      }
      return link;
    } catch (IllegalArgumentException malformed) {
      return null;
    }
  }
}
