package kr.soyuo.serox.browser.render;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import kr.soyuo.serox.browser.api.PageLoadRequest;
import kr.soyuo.serox.browser.network.GuardedTransport;
import kr.soyuo.serox.browser.network.HttpRequestDispatcher;
import kr.soyuo.serox.browser.network.HttpResponseData;
import kr.soyuo.serox.browser.network.HttpTransport;
import kr.soyuo.serox.browser.network.PublicAddressPolicy;

/** Loads a bounded, public web image without blocking the JavaFX thread. */
final class PageImageLoader {
  static final int MAX_IMAGES = 16;
  static final int MAX_BYTES = 2 * 1024 * 1024;
  static final long MAX_PIXELS = 4_000_000L;
  private final HttpTransport transport;

  PageImageLoader(HttpTransport transport) {
    this.transport = Objects.requireNonNull(transport, "transport");
  }

  static PageImageLoader createDefault() {
    HttpClient client =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    var dispatcher = new HttpRequestDispatcher(client, Duration.ofSeconds(10), MAX_BYTES);
    return new PageImageLoader(new GuardedTransport(dispatcher, new PublicAddressPolicy()));
  }

  static Optional<URI> resolve(URI base, String source) {
    if (source == null || source.isBlank()) {
      return Optional.empty();
    }
    try {
      URI uri = base.resolve(source.strip());
      new PageLoadRequest(uri);
      return Optional.of(uri);
    } catch (IllegalArgumentException exception) {
      return Optional.empty();
    }
  }

  CompletableFuture<BufferedImage> load(URI uri) {
    var exchange = transport.dispatch(new PageLoadRequest(uri)).toCompletableFuture();
    var result = exchange.thenApplyAsync(PageImageLoader::decode);
    result.whenComplete(
        (image, error) -> {
          if (result.isCancelled()) {
            exchange.cancel(true);
          }
        });
    return result;
  }

  private static BufferedImage decode(HttpResponseData response) {
    String contentType =
        response
            .headers()
            .firstValue("Content-Type")
            .orElse("")
            .split(";", 2)[0]
            .strip()
            .toLowerCase(Locale.ROOT);
    byte[] bytes = response.body();
    if (response.statusCode() < 200
        || response.statusCode() >= 300
        || !java.util.Set.of("image/png", "image/jpeg", "image/gif").contains(contentType)
        || bytes.length == 0
        || bytes.length > MAX_BYTES) {
      throw new IllegalArgumentException("Unsupported or oversized image response");
    }
    try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
      var readers = ImageIO.getImageReaders(stream);
      if (!readers.hasNext()) {
        throw new IllegalArgumentException("Unrecognized image data");
      }
      ImageReader reader = readers.next();
      try {
        String format = reader.getFormatName().toLowerCase(Locale.ROOT);
        if (!(contentType.equals("image/png") && format.equals("png")
            || contentType.equals("image/jpeg") && (format.equals("jpeg") || format.equals("jpg"))
            || contentType.equals("image/gif") && format.equals("gif"))) {
          throw new IllegalArgumentException("Image type does not match its contents");
        }
        reader.setInput(stream, true, true);
        int width = reader.getWidth(0);
        int height = reader.getHeight(0);
        if (width < 1 || height < 1 || (long) width * height > MAX_PIXELS) {
          throw new IllegalArgumentException("Image dimensions exceed the limit");
        }
        BufferedImage image = reader.read(0);
        if (image == null) {
          throw new IllegalArgumentException("Image could not be decoded");
        }
        return image;
      } finally {
        reader.dispose();
      }
    } catch (IOException exception) {
      throw new IllegalArgumentException("Image could not be decoded", exception);
    }
  }
}
