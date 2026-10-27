package kr.soyuo.serox.ai.citation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import kr.soyuo.serox.ai.api.EvidenceDocument;
import org.junit.jupiter.api.Test;

class CitationManagerTest {
  private static EvidenceDocument doc(String id, String uri) {
    return new EvidenceDocument(id, URI.create(uri), "Title " + id, "text", Instant.EPOCH);
  }

  @Test
  void linksValidMarkersAndDropsInventedOnes() {
    var evidence = List.of(doc("S1", "https://a.test/"), doc("S2", "https://b.test/"));

    CitedAnswer answer =
        CitationManager.resolve(
            "Serox is a browser [S1]. It uses Java [S2][S9]. See https://evil.test/.", evidence);

    assertEquals(List.of("S1", "S2"), answer.citations().stream().map(Citation::sourceId).toList());
    assertEquals("Serox is a browser", answer.citations().getFirst().referencedText());
    assertEquals(List.of("S9"), answer.invalidSourceIds());
    assertFalse(answer.text().contains("[S9]"));
    assertTrue(answer.text().contains("[S2]"));
    assertFalse(answer.fullyGrounded());
  }

  @Test
  void answersWithoutMarkersHaveNoCitations() {
    CitedAnswer answer =
        CitationManager.resolve("No sources here.", List.of(doc("S1", "https://a.test/")));

    assertTrue(answer.citations().isEmpty());
    assertFalse(answer.fullyGrounded());
  }

  @Test
  void documentsWithNonWebAddressesCannotBeCited() {
    CitedAnswer answer =
        CitationManager.resolve("Claim [S1].", List.of(doc("S1", "file:///etc/passwd")));

    assertTrue(answer.citations().isEmpty());
    assertEquals(List.of("S1"), answer.invalidSourceIds());
  }
}
