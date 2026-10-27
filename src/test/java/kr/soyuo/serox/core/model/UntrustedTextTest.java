package kr.soyuo.serox.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UntrustedTextTest {
  @Test
  void markupIsRemovedAndEntitiesDecoded() {
    assertEquals(
        "Best <deal> & more",
        UntrustedText.fromMarkup("<strong>Best</strong> &lt;deal&gt; &amp; <img src=x onerror=y>more", 100));
  }

  @Test
  void hiddenAndDirectionCharactersAreDropped() {
    String disguised = "pay‮exe.txt​\u0000 now";
    assertEquals("payexe.txt now", UntrustedText.plain(disguised, 100));
    assertTrue(UntrustedText.hasHiddenCharacters(disguised));
    assertFalse(UntrustedText.hasHiddenCharacters("plain text"));
  }

  @Test
  void lengthIsBoundedWithoutSplittingSurrogates() {
    String result = UntrustedText.plain("a".repeat(10) + "😀".repeat(10), 11);
    assertTrue(result.length() <= 11);
    assertTrue(result.endsWith("…"));
    assertFalse(Character.isHighSurrogate(result.charAt(result.length() - 2)));
  }
}
