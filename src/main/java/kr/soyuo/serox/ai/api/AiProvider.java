package kr.soyuo.serox.ai.api;

import java.util.concurrent.Flow;

/** Provider-independent streaming AI contract. */
public interface AiProvider {
  Flow.Publisher<AiEvent> analyze(AiRequest request);
}
