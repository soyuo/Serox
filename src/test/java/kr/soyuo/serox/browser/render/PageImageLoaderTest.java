package kr.soyuo.serox.browser.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpHeaders;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import javax.imageio.ImageIO;
import kr.soyuo.serox.browser.network.HttpResponseData;
import org.junit.jupiter.api.Test;

class PageImageLoaderTest {
  private static final URI PAGE = URI.create("https://example.com/articles/page");
  private static final URI IMAGE = URI.create("https://example.com/images/test.png");

  @Test
  void resolvesRelativeWebUrlsAndRejectsOtherSchemes() {
    assertEquals(IMAGE, PageImageLoader.resolve(PAGE, "../images/test.png").orElseThrow());
    for (String source :
        List.of("", "file:///secret", "data:image/png;base64,AA", "http://x:bad")) {
      assertTrue(PageImageLoader.resolve(PAGE, source).isEmpty(), source);
    }
    assertTrue(PageImageLoader.resolve(PAGE, "https://user:password@example.com/a.png").isEmpty());
  }

  @Test
  void decodesSupportedImages() throws IOException {
    for (String format : List.of("png", "jpeg", "gif")) {
      String type = "image/" + format;
      byte[] bytes = encode(format, 3, 2);
      var loader = loader(response(200, type, bytes));
      BufferedImage image = loader.load(IMAGE).join();
      assertEquals(3, image.getWidth());
      assertEquals(2, image.getHeight());
    }
  }

  @Test
  void rejectsBadStatusTypeBytesAndExcessiveDimensions() throws IOException {
    byte[] png = encode("png", 3, 2);
    for (HttpResponseData response :
        List.of(
            response(404, "image/png", png),
            response(200, "text/html", png),
            response(200, "image/jpeg", png),
            response(200, "image/png", new byte[] {1, 2, 3}),
            response(200, "image/png", new byte[PageImageLoader.MAX_BYTES + 1]),
            response(200, "image/png", encode("png", 2001, 2000)))) {
      assertThrows(CompletionException.class, () -> loader(response).load(IMAGE).join());
    }
  }

  @Test
  void cancellingLoadCancelsTransport() {
    CompletableFuture<HttpResponseData> exchange = new CompletableFuture<>();
    var loader = new PageImageLoader(request -> exchange);
    var load = loader.load(IMAGE);
    assertTrue(load.cancel(true));
    assertTrue(exchange.isCancelled());
    assertFalse(load.isCompletedExceptionally() && !load.isCancelled());
  }

  private static PageImageLoader loader(HttpResponseData response) {
    return new PageImageLoader(request -> CompletableFuture.completedFuture(response));
  }

  private static HttpResponseData response(int status, String type, byte[] body) {
    return new HttpResponseData(
        IMAGE, status, HttpHeaders.of(Map.of("Content-Type", List.of(type)), (a, b) -> true), body);
  }

  private static byte[] encode(String format, int width, int height) throws IOException {
    var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    var output = new ByteArrayOutputStream();
    assertTrue(ImageIO.write(image, format, output));
    return output.toByteArray();
  }
}
