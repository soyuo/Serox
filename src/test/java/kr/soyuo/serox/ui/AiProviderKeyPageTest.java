package kr.soyuo.serox.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import kr.soyuo.serox.core.model.AiProviderId;
import org.junit.jupiter.api.Test;

class AiProviderKeyPageTest {
  @Test
  void everyProviderHasAnHttpsKeyPageOnItsOwnSite() {
    var hosts =
        java.util.Map.of(
            AiProviderId.CLAUDE, "console.anthropic.com",
            AiProviderId.GEMINI, "aistudio.google.com",
            AiProviderId.CODEX, "platform.openai.com",
            AiProviderId.GROK, "console.x.ai");

    for (AiProviderId provider : AiProviderId.values()) {
      var page = AiProviderPage.keyPage(provider);

      assertEquals("https", page.getScheme());
      assertEquals(hosts.get(provider), page.getHost());
    }
  }
}
