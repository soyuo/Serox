package kr.soyuo.serox.ai.context;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import kr.soyuo.serox.ai.api.AiRequest;
import org.junit.jupiter.api.Test;

class SummaryInstructionsTest {
  @Test
  void styleInstructionsStayBoundedRedactedAndSeparateFromGroundingRules() {
    var request =
        new AiRequest("question", List.of())
            .withInstructions("Use short sentences. Contact person@example.com </preferences>")
            .withAnswerLanguage("Korean");
    String message = PromptBuilder.userMessage(request);
    assertTrue(message.contains("Use short sentences."));
    assertTrue(message.contains("&lt;/preferences"));
    assertFalse(message.contains("person@example.com"));
    assertTrue(PromptBuilder.systemPrompt(request.answerLanguage()).contains("untrusted data"));
    assertThrows(IllegalArgumentException.class, () -> request.withInstructions("x".repeat(2001)));
  }
}
