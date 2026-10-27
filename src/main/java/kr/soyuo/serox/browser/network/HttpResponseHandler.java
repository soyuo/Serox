package kr.soyuo.serox.browser.network;

import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.soyuo.serox.core.error.ApplicationError;
import kr.soyuo.serox.core.error.ErrorCode;

/** Validates HTTP status and media type, then decodes HTML using charset metadata. */
public final class HttpResponseHandler {
  private static final Pattern CHARSET_PARAMETER =
      Pattern.compile(
          "(?:^|;)\\s*charset\\s*=\\s*(?:\"([^\"]+)\"|'([^']+)'|([^;\\s]+))",
          Pattern.CASE_INSENSITIVE);

  /** Validates and decodes a bounded transport response as HTML. */
  public DecodedHtmlResponse handle(HttpResponseData response) {
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw error(ErrorCode.NETWORK_FAILURE, "The server returned an unsuccessful response.");
    }

    String contentType = response.headers().firstValue("Content-Type").orElse("");
    String mediaType = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    if (!mediaType.equals("text/html") && !mediaType.equals("application/xhtml+xml")) {
      throw error(ErrorCode.UNSUPPORTED_CONTENT, "This response is not an HTML document.");
    }

    byte[] body = response.body();
    Bom bom = Bom.detect(body);
    Charset charset = bom.charset().orElseGet(() -> declaredCharset(contentType));
    int offset = bom.length();
    String html = new String(body, offset, body.length - offset, charset);
    return new DecodedHtmlResponse(response.uri(), html);
  }

  private Charset declaredCharset(String contentType) {
    Matcher matcher = CHARSET_PARAMETER.matcher(contentType);
    if (!matcher.find()) {
      return StandardCharsets.UTF_8;
    }
    String name = firstNonNull(matcher.group(1), matcher.group(2), matcher.group(3));
    try {
      return Charset.forName(name);
    } catch (IllegalCharsetNameException | UnsupportedCharsetException exception) {
      throw error(
          ErrorCode.INVALID_RESPONSE, "The document uses an unsupported character encoding.");
    }
  }

  private String firstNonNull(String... values) {
    for (String value : values) {
      if (value != null) {
        return value;
      }
    }
    return "";
  }

  private BrowserResponseException error(ErrorCode code, String message) {
    return new BrowserResponseException(new ApplicationError(code, message, false));
  }

  private enum Bom {
    UTF_8(StandardCharsets.UTF_8, new byte[] {(byte) 0xef, (byte) 0xbb, (byte) 0xbf}),
    UTF_16_LE(StandardCharsets.UTF_16LE, new byte[] {(byte) 0xff, (byte) 0xfe}),
    UTF_16_BE(StandardCharsets.UTF_16BE, new byte[] {(byte) 0xfe, (byte) 0xff}),
    NONE(null, new byte[0]);

    private final Charset charset;
    private final byte[] marker;

    Bom(Charset charset, byte[] marker) {
      this.charset = charset;
      this.marker = marker;
    }

    static Bom detect(byte[] bytes) {
      for (Bom candidate : values()) {
        if (candidate == NONE || bytes.length < candidate.marker.length) {
          continue;
        }
        boolean matches = true;
        for (int index = 0; index < candidate.marker.length; index++) {
          if (bytes[index] != candidate.marker[index]) {
            matches = false;
            break;
          }
        }
        if (matches) {
          return candidate;
        }
      }
      return NONE;
    }

    Optional<Charset> charset() {
      return Optional.ofNullable(charset);
    }

    int length() {
      return marker.length;
    }
  }
}
