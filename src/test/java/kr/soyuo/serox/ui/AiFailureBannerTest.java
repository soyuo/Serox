package kr.soyuo.serox.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import kr.soyuo.serox.core.event.AiAnswerChanged;
import org.junit.jupiter.api.Test;

class AiFailureBannerTest {
  @Test
  void rejectedKeyAndQuotaGetTheirOwnTitles() {
    assertEquals("로그인이 만료됐어요", SearchResultsView.failureTitle("unauthorized"));
    assertEquals("사용 한도에 도달했어요", SearchResultsView.failureTitle("rate_limited"));
  }

  @Test
  void unknownFailuresKeepTheGeneralTitle() {
    assertEquals("요약하지 못했어요", SearchResultsView.failureTitle(""));
    assertEquals("요약하지 못했어요", SearchResultsView.failureTitle("provider_error"));
  }

  @Test
  void eachStateOffersTheDesignsSettingsButton() {
    assertEquals("AI 연결하기", SearchResultsView.settingsAction(AiAnswerChanged.Phase.UNAVAILABLE, ""));
    assertEquals(
        "다시 로그인하기",
        SearchResultsView.settingsAction(AiAnswerChanged.Phase.FAILED, "unauthorized"));
    assertEquals(
        "다른 AI로 요약", SearchResultsView.settingsAction(AiAnswerChanged.Phase.FAILED, "rate_limited"));
    assertEquals("", SearchResultsView.settingsAction(AiAnswerChanged.Phase.FAILED, "timeout"));
  }
}
