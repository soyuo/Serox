package kr.soyuo.serox.core.port.storage;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import kr.soyuo.serox.core.model.Bookmark;

/** Persistence port for user-saved page references. */
public interface BookmarkRepository {
  CompletionStage<Void> save(Bookmark bookmark);

  CompletionStage<Void> remove(UUID bookmarkId);

  CompletionStage<List<Bookmark>> all();
}
