package kr.soyuo.serox.core.port.storage;

import java.util.List;
import java.util.concurrent.CompletionStage;
import kr.soyuo.serox.core.model.RecentSearch;

/** Persistence port for non-private recent search queries. */
public interface RecentSearchRepository {
  CompletionStage<Void> record(RecentSearch recentSearch);

  CompletionStage<List<RecentSearch>> recent(int limit);

  /** Removes one query; nothing happens if it is not stored. */
  CompletionStage<Void> delete(String query);

  /** Removes every stored query. */
  CompletionStage<Void> clear();
}
