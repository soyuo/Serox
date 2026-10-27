package kr.soyuo.serox.app;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Log calls stay constant-text: no exception messages, stack traces, bodies or credentials. */
class LogHygieneTest {
  private static final Pattern LOG_CALL =
      Pattern.compile("LOGGER\\.log\\((.*?)\\);", Pattern.DOTALL);
  private static final Pattern RISKY =
      Pattern.compile("(?i)getMessage|body|header|cookie|apikey|api_key|token");
  private static final Pattern THROWABLE_ARGUMENT =
      Pattern.compile(",\\s*\\w*(ex|exception|cause|failure|error)\\s*$", Pattern.CASE_INSENSITIVE);

  @Test
  void logCallsNeverCarryThrowablesBodiesOrCredentials() throws IOException {
    List<Path> sources;
    try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
      sources = files.filter(path -> path.toString().endsWith(".java")).toList();
    }
    assertTrue(sources.size() > 50);
    for (Path source : sources) {
      String text = Files.readString(source);
      assertFalse(text.contains("printStackTrace"), source.toString());
      assertFalse(text.contains("System.err"), source.toString());
      var matcher = LOG_CALL.matcher(text);
      while (matcher.find()) {
        String call = matcher.group(1);
        assertFalse(RISKY.matcher(call).find(), source + ": " + call);
        assertFalse(THROWABLE_ARGUMENT.matcher(call).find(), source + ": " + call);
      }
    }
  }
}
