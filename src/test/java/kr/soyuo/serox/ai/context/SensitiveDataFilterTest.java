package kr.soyuo.serox.ai.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SensitiveDataFilterTest {
  @Test
  void redactsCredentialsAndIdentifiers() {
    String text =
        "Contact me@example.com or 010-1234-5678. Key sk-abcdefghijklmnop1234. "
            + "Authorization: Bearer abcdefghijklmnop12345 password=hunter2 "
            + "card 4111 1111 1111 1111 done";

    String redacted = SensitiveDataFilter.redact(text);

    assertFalse(redacted.contains("me@example.com"));
    assertFalse(redacted.contains("010-1234-5678"));
    assertFalse(redacted.contains("sk-abcdefghijklmnop1234"));
    assertFalse(redacted.contains("abcdefghijklmnop12345"));
    assertFalse(redacted.contains("hunter2"));
    assertFalse(redacted.contains("4111"));
    assertTrue(redacted.contains("Contact"));
    assertTrue(redacted.endsWith("done"));
  }

  @Test
  void leavesOrdinaryProseUntouched() {
    String text = "Java 25 was released in September 2025 with 18 JEPs.";

    assertEquals(text, SensitiveDataFilter.redact(text));
  }
}
