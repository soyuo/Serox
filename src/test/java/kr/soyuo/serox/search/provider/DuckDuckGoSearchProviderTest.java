package kr.soyuo.serox.search.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpClient;
import java.time.Duration;
import kr.soyuo.serox.search.api.SearchRequest;
import org.junit.jupiter.api.Test;

class DuckDuckGoSearchProviderTest {
  private static final String PAGE =
      """
      <div class="result">
        <h2><a rel="nofollow" class="result__a"
          href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Fa%3Fx%3D1&amp;rut=abc">First <b>hit</b></a></h2>
        <a class="result__snippet" href="//duckduckgo.com/l/?uddg=x">The &amp; snippet</a>
      </div>
      <div class="result">
        <a class="result__a" href="javascript:alert(1)">Bad</a>
      </div>
      <div class="result">
        <a class="result__a" href="https://example.org/b/">Second</a>
      </div>
      <div class="result">
        <a class="result__a" href="https://example.com/a?x=1">Duplicate</a>
      </div>
      """;

  private final DuckDuckGoSearchProvider provider =
      new DuckDuckGoSearchProvider(
          HttpClient.newHttpClient(),
          java.net.URI.create("https://html.duckduckgo.com/html/"),
          Duration.ofSeconds(1));

  @Test
  void readsTitlesAddressesAndSnippets() {
    var response = provider.parse(PAGE, new SearchRequest("q", 10));

    assertEquals("duckduckgo", response.providerId());
    assertEquals(2, response.results().size());
    var first = response.results().get(0);
    assertEquals("First hit", first.title());
    assertEquals("https://example.com/a?x=1", first.uri().toString());
    assertTrue(first.snippet().startsWith("The & snippet"));
    assertEquals("https://example.org/b", response.results().get(1).id());
  }

  @Test
  void stopsAtTheResultLimit() {
    assertEquals(1, provider.parse(PAGE, new SearchRequest("q", 1)).results().size());
  }

  @Test
  void pageWithoutResultsGivesAnEmptyList() {
    assertTrue(provider.parse("<html></html>", new SearchRequest("q", 5)).results().isEmpty());
  }
}
