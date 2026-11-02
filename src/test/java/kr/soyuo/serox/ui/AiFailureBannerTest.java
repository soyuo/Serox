package kr.soyuo.serox.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AiFailureBannerTest {
  @Test
  void rejectedKeyAndQuotaGetTheirOwnTitles() {
    assertEquals("AI 연결을 다시 확인해 주세요", SearchResultsView.failureTitle("unauthorized"));
    assertEquals("사용 한도에 도달했어요", SearchResultsView.failureTitle("rate_limited"));
  }

  @Test
  void unknownFailuresKeepTheGeneralTitle() {
    assertEquals("요약하지 못했어요", SearchResultsView.failureTitle(""));
    assertEquals("요약하지 못했어요", SearchResultsView.failureTitle("provider_error"));
  }
}
