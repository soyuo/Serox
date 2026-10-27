package kr.soyuo.serox.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SiteInfoTest {
  @Test
  void classifiesByScheme() {
    assertEquals(
        SiteInfo.Level.SECURE, SiteInfo.of(Optional.of(URI.create("https://example.test/a"))).level());
    var insecure = SiteInfo.of(Optional.of(URI.create("http://example.test/")));
    assertEquals(SiteInfo.Level.INSECURE, insecure.level());
    assertEquals("example.test", insecure.host());
    assertEquals(SiteInfo.Level.NONE, SiteInfo.of(Optional.empty()).level());
    assertEquals(
        SiteInfo.Level.NONE, SiteInfo.of(Optional.of(URI.create("file:///tmp/x"))).level());
  }
}
