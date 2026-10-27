package kr.soyuo.serox.core.port.storage;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletionStage;
import kr.soyuo.serox.core.model.HistoryEntry;

/** Persistence port for non-secret visit metadata. */
public interface HistoryRepository {
  CompletionStage<Void> add(HistoryEntry entry);

  CompletionStage<List<HistoryEntry>> recent(int limit);

  /** Deletes entries older than the cutoff and any beyond the newest {@code maxEntries}. */
  CompletionStage<Void> prune(Instant olderThan, int maxEntries);

  /** Removes every visit. */
  CompletionStage<Void> clear();
}
