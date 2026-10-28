package kr.soyuo.serox.ai.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import kr.soyuo.serox.ai.api.AiFailed;
import org.junit.jupiter.api.Test;

class FriendlyAiMessageTest {
  @Test
  void everyFailureIsExplainedInKoreanWithoutRawEnglishOrCodes() {
    for (String code :
        List.of(
            "unauthorized", "bad_request", "rate_limited", "timeout", "network", "incomplete",
            "invalid_response", "too_large", "unavailable", "provider_error", "too_long", "anything_else")) {
      String message = AiSearchService.friendlyMessage(new AiFailed(code, "The AI API key was rejected."));

      assertTrue(message.matches(".*[가-힣].*"), code);
      assertFalse(message.contains("rejected"), code);
      assertFalse(message.contains(code), code);
    }
  }

  @Test
  void theHttpStatusIsShownSoAWrongModelCanBeToldApartFromAWrongKey() {
    String message =
        AiSearchService.friendlyMessage(
            new AiFailed("bad_request", "The AI service rejected the request (HTTP 404)."));

    assertTrue(message.endsWith("(HTTP 404)"));
  }

  @Test
  void aRejectedKeyPointsToTheProviderSettings() {
    String message = AiSearchService.friendlyMessage(new AiFailed("unauthorized", "x"));

    assertTrue(message.contains("API 키"));
    assertTrue(message.contains("설정"));
  }
}
