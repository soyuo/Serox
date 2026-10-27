package kr.soyuo.serox.core.port.storage;

import java.util.Optional;
import java.util.concurrent.CompletionStage;
import kr.soyuo.serox.core.model.SessionSnapshot;

/** Persistence port for resumable, non-private browser session metadata. */
public interface SessionRepository {
  CompletionStage<Void> save(SessionSnapshot session);

  CompletionStage<Optional<SessionSnapshot>> latest();
}
