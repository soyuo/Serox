package kr.soyuo.serox.browser.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.net.http.HttpHeaders;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import kr.soyuo.serox.core.error.ErrorCode;
import org.junit.jupiter.api.Test;

class HttpResponseHandlerTest {
  private final HttpResponseHandler handler = new HttpResponseHandler();

  @Test
  void decodesDeclaredCharsetAndHonorsBom() {
    byte[] latin1 = new byte[] {'c', 'a', 'f', (byte) 0xe9};
    final var declared = handle(200, "text/html; charset=ISO-8859-1", latin1);
    byte[] utf8WithBom = "<p>ok</p>".getBytes(StandardCharsets.UTF_8);
    byte[] bomBody = new byte[utf8WithBom.length + 3];
    bomBody[0] = (byte) 0xef;
    bomBody[1] = (byte) 0xbb;
    bomBody[2] = (byte) 0xbf;
    System.arraycopy(utf8WithBom, 0, bomBody, 3, utf8WithBom.length);
    var bom = handle(200, "text/html; charset=ISO-8859-1", bomBody);

    assertEquals("caf" + (char) 0xe9, declared.html());
    assertEquals("<p>ok</p>", bom.html());
  }

  @Test
  void rejectsUnsupportedMediaStatusAndCharset() {
    BrowserResponseException media =
        assertThrows(
            BrowserResponseException.class,
            () -> handle(200, "application/json", "{}".getBytes(StandardCharsets.UTF_8)));
    BrowserResponseException status =
        assertThrows(
            BrowserResponseException.class,
            () -> handle(503, "text/html", "down".getBytes(StandardCharsets.UTF_8)));
    BrowserResponseException charset =
        assertThrows(
            BrowserResponseException.class,
            () -> handle(200, "text/html; charset=no-such-charset", new byte[0]));

    assertEquals(ErrorCode.UNSUPPORTED_CONTENT, media.error().code());
    assertEquals(ErrorCode.NETWORK_FAILURE, status.error().code());
    assertEquals(ErrorCode.INVALID_RESPONSE, charset.error().code());
  }

  private DecodedHtmlResponse handle(int status, String contentType, byte[] body) {
    HttpHeaders headers =
        HttpHeaders.of(Map.of("Content-Type", List.of(contentType)), (name, value) -> true);
    return handler.handle(
        new HttpResponseData(URI.create("https://example.test"), status, headers, body));
  }
}
