package kr.soyuo.serox.browser.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpHeaders;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import kr.soyuo.serox.browser.api.PageLoadRequest;
import org.junit.jupiter.api.Test;

class GuardedTransportTest {
  private static final PublicAddressPolicy POLICY =
      new PublicAddressPolicy(
          host -> {
            try {
              return List.of(InetAddress.getByName(host.equals("public.test") ? "93.184.216.34" : host));
            } catch (java.net.UnknownHostException exception) {
              return List.of();
            }
          });

  private final List<URI> requested = new ArrayList<>();

  private static HttpResponseData response(URI uri, int status, String location) {
    Map<String, List<String>> headers =
        location == null ? Map.of() : Map.of("Location", List.of(location));
    return new HttpResponseData(uri, status, HttpHeaders.of(headers, (a, b) -> true), new byte[0]);
  }

  private GuardedTransport transport(Map<String, String> redirects) {
    return new GuardedTransport(
        request -> {
          requested.add(request.uri());
          String location = redirects.get(request.uri().toString());
          return CompletableFuture.completedFuture(
              response(request.uri(), location == null ? 200 : 302, location));
        },
        POLICY);
  }

  private HttpResponseData fetch(GuardedTransport transport, String uri) throws Exception {
    return transport
        .dispatch(new PageLoadRequest(URI.create(uri)))
        .toCompletableFuture()
        .get(5, TimeUnit.SECONDS);
  }

  private static void assertBlocked(GuardedTransport transport, String uri) {
    var failure =
        assertThrows(
            CompletionException.class,
            () ->
                transport
                    .dispatch(new PageLoadRequest(URI.create(uri)))
                    .toCompletableFuture()
                    .join());
    assertInstanceOf(SecurityException.class, failure.getCause());
  }

  @Test
  void publicAddressesAreFetchedAndRedirectsFollowed() throws Exception {
    var transport = transport(Map.of("https://public.test/a", "/b"));

    assertEquals(200, fetch(transport, "https://public.test/a").statusCode());
    assertEquals(
        List.of(URI.create("https://public.test/a"), URI.create("https://public.test/b")), requested);
  }

  @Test
  void localAndInternalAddressesAreNeverContacted() {
    var transport = transport(Map.of());
    for (String uri :
        List.of(
            "http://localhost/",
            "http://127.0.0.1:8080/",
            "http://10.0.0.5/",
            "http://192.168.1.1/",
            "http://172.16.0.1/",
            "http://169.254.169.254/latest/meta-data/",
            "http://[::1]/",
            "http://[fd00::1]/",
            "http://100.64.0.1/",
            "http://printer.local/",
            "http://0.0.0.0/")) {
      assertBlocked(transport, uri);
    }
    assertTrue(requested.isEmpty(), "blocked addresses must not be requested");
  }

  @Test
  void redirectsToInternalTargetsOrDowngradesAreRefused() {
    var transport =
        transport(
            Map.of(
                "https://public.test/to-local", "http://127.0.0.1/admin",
                "https://public.test/downgrade", "http://public.test/plain",
                "https://public.test/other-scheme", "file:///etc/passwd"));

    assertBlocked(transport, "https://public.test/to-local");
    assertBlocked(transport, "https://public.test/downgrade");
    assertBlocked(transport, "https://public.test/other-scheme");
    assertFalse(requested.contains(URI.create("http://127.0.0.1/admin")));
  }

  @Test
  void redirectLoopsStop() {
    var transport = transport(Map.of("https://public.test/loop", "https://public.test/loop"));

    assertBlocked(transport, "https://public.test/loop");
    assertEquals(GuardedTransport.MAX_REDIRECTS + 1, requested.size());
  }
}
