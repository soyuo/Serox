package kr.soyuo.serox.core.model;

/** Built-in providers available for grounded search summaries. */
public enum AiProviderId {
  CLAUDE("Claude", "SEROX_ANTHROPIC_API_KEY", "claude-sonnet-5-5"),
  GEMINI("Gemini", "SEROX_GEMINI_API_KEY", "gemini-3.8-flash"),
  CODEX("Codex", "SEROX_OPENAI_API_KEY", "gpt-6-luna"),
  GROK("Grok", "SEROX_XAI_API_KEY", "grok-4.7");

  private final String label;
  private final String environmentKey;
  private final String defaultModel;

  AiProviderId(String label, String environmentKey, String defaultModel) {
    this.label = label;
    this.environmentKey = environmentKey;
    this.defaultModel = defaultModel;
  }

  public String label() {
    return label;
  }

  public String environmentKey() {
    return environmentKey;
  }

  public String defaultModel() {
    return defaultModel;
  }
}
