package kr.soyuo.serox.core.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.net.URI;
import org.junit.jupiter.api.Test;

class NavigationInputClassifierTest {
  private final NavigationInputClassifier classifier = new NavigationInputClassifier();

  @Test
  void opensIpv4Addresses() {
    assertEquals(
        URI.create("https://127.0.0.1:8080/status"),
        assertInstanceOf(DirectAddress.class, classifier.classify("127.0.0.1:8080/status")).uri());
    assertInstanceOf(DirectAddress.class, classifier.classify("192.168.0.1"));
  }

  @Test
  void keepsNumbersThatAreNotAddressesAsSearches() {
    assertInstanceOf(SearchQuery.class, classifier.classify("3.14"));
    assertInstanceOf(SearchQuery.class, classifier.classify("999.1.1.1"));
  }
}
