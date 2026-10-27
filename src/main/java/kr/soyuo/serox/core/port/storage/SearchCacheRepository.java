package kr.soyuo.serox.core.port.storage;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import kr.soyuo.serox.core.model.SearchCacheEntry;

/** Persistence port for expiring, non-sensitive provider search responses. */
public interface SearchCacheRepository {
  CompletionStage<Void> save(SearchCacheEntry entry);

  CompletionStage<Optional<String>> find(String cacheKey, Instant now);

  CompletionStage<Integer> removeExpired(Instant now);
}
