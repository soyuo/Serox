package kr.soyuo.serox.search.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import kr.soyuo.serox.core.event.InProcessBrowserEventBus;
import kr.soyuo.serox.core.model.TabId;
import kr.soyuo.serox.search.api.SearchProvider;
import kr.soyuo.serox.search.api.SearchResponse;
import org.junit.jupiter.api.Test;

class SearchEngineChoiceTest {
  private static SearchProvider named(String id) {
    return request -> CompletableFuture.completedFuture(new SearchResponse(id, List.of()));
  }

  private static String used(Optional<SearchProvider> keyed, String choice) {
    var service = new SearchService(keyed, new InProcessBrowserEventBus());
    service.setFallback(named("duckduckgo"));
    service.setEngine(() -> choice);
    AtomicReference<String> seen = new AtomicReference<>();
    service.search(TabId.create(), "q").whenComplete((response, failure) -> {
      if (response != null) {
        seen.set(response.providerId());
      }
    });
    return seen.get();
  }

  @Test
  void autoPrefersTheKeyedEngineAndOtherwiseDuckDuckGo() {
    assertEquals("brave", used(Optional.of(named("brave")), "auto"));
    assertEquals("duckduckgo", used(Optional.empty(), "auto"));
  }

  @Test
  void aChosenEngineIsUsedEvenWhenAnotherIsAvailable() {
    assertEquals("duckduckgo", used(Optional.of(named("brave")), "duckduckgo"));
    assertEquals("brave", used(Optional.of(named("brave")), "brave"));
  }

  @Test
  void braveChosenWithoutAKeyReportsThatSearchIsNotConfigured() {
    assertEquals(null, used(Optional.empty(), "brave"));
  }
}
