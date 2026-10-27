package kr.soyuo.serox.ai.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProtectedLocalStoreTest {
  @TempDir Path directory;

  @Test
  void windowsRoundTripNeverPersistsPlaintextAndRejectsCorruption() throws Exception {
    assumeTrue(System.getProperty("os.name").startsWith("Windows"));
    Path file = directory.resolve("keys.dpapi");
    var store = new ProtectedLocalStore(file);
    assertEquals("{}", store.read());
    String secret = "{\"key\":\"test-private-value-한국어\"}";
    store.write(secret);
    assertEquals(secret, store.read());
    assertFalse(
        new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
            .contains("test-private-value"));
    store.write("{\"next\":true}");
    assertEquals("{\"next\":true}", store.read());
    Files.write(file, new byte[] {1, 2, 3});
    assertThrows(java.io.IOException.class, store::read);
  }
}
