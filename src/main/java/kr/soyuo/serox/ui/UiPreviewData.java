package kr.soyuo.serox.ui;

import java.util.List;

/** Static sample values for UI states before live services are connected. */
record UiPreviewData(
    List<String> recentSearches, ServicePreview ai, ServicePreview mcp, String mcpDescription) {

  UiPreviewData {
    recentSearches = List.copyOf(recentSearches);
  }

  static UiPreviewData sample() {
    return new UiPreviewData(
        List.of("자바 가상 스레드 장점", "Maven 의존성 충돌 해결", "JavaFX WebView 쿠키 설정"),
        new ServicePreview("AI 요약", "Claude", true),
        new ServicePreview("MCP 서버", "search_and_summarize", true),
        "다른 AI 에이전트가 search_and_summarize 도구로 Serox를 호출할 수 있어요.");
  }

  /** Initial values shown until each service reports its real state. */
  static UiPreviewData pending() {
    return new UiPreviewData(
        List.of(),
        new ServicePreview("AI 요약", "확인 중", false),
        new ServicePreview("MCP 서버", "확인 중", false),
        "다른 AI 에이전트가 search_and_summarize 도구로 Serox를 호출할 수 있어요.");
  }

  UiPreviewData withAi(ServicePreview ai) {
    return new UiPreviewData(recentSearches, ai, mcp, mcpDescription);
  }

  UiPreviewData withMcp(ServicePreview mcp) {
    return new UiPreviewData(recentSearches, ai, mcp, mcpDescription);
  }

  record ServicePreview(String name, String detail, boolean enabled) {
    String statusText() {
      return name + (enabled ? " 켜짐" : " 꺼짐");
    }
  }
}
