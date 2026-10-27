package kr.soyuo.serox.core.application;

import java.net.URI;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;
import java.util.logging.Logger;
import kr.soyuo.serox.core.model.Bookmark;
import kr.soyuo.serox.core.model.HistoryEntry;
import kr.soyuo.serox.core.port.storage.BookmarkRepository;
import kr.soyuo.serox.core.port.storage.HistoryRepository;

/** Reads history and manages bookmarks; storage problems degrade to empty results. */
public final class LibraryService {
  private static final Logger LOGGER = Logger.getLogger(LibraryService.class.getName());
  private static final int HISTORY_LIMIT = 200;

  private final Optional<HistoryRepository> history;
  private final Optional<BookmarkRepository> bookmarks;
  private final Clock clock;

  public LibraryService(
      Optional<HistoryRepository> history, Optional<BookmarkRepository> bookmarks, Clock clock) {
    this.history = Objects.requireNonNull(history, "history");
    this.bookmarks = Objects.requireNonNull(bookmarks, "bookmarks");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /** True when history and bookmarks can be stored. */
  public boolean available() {
    return history.isPresent() && bookmarks.isPresent();
  }

  /** Newest visits first. */
  public CompletionStage<List<HistoryEntry>> recentHistory() {
    return guarded(history.map(repository -> repository.recent(HISTORY_LIMIT)), List.of());
  }

  /** Saved bookmarks, newest first. */
  public CompletionStage<List<Bookmark>> bookmarks() {
    return guarded(
        bookmarks.map(
            repository ->
                repository
                    .all()
                    .thenApply(
                        all ->
                            all.stream()
                                .sorted(
                                    java.util.Comparator.comparing(Bookmark::createdAt).reversed())
                                .toList())),
        List.of());
  }

  /** Adds a bookmark unless the address is already saved; completes with whether it was added. */
  public CompletionStage<Boolean> addBookmark(URI uri, String title) {
    Objects.requireNonNull(uri, "uri");
    if (bookmarks.isEmpty()) {
      return CompletableFuture.completedFuture(false);
    }
    BookmarkRepository repository = bookmarks.orElseThrow();
    try {
      return repository
          .all()
          .thenCompose(
              existing -> {
                if (existing.stream().anyMatch(bookmark -> bookmark.uri().equals(uri))) {
                  return CompletableFuture.completedFuture(false);
                }
                String name = title == null || title.isBlank() ? uri.toString() : title.strip();
                return repository
                    .save(new Bookmark(UUID.randomUUID(), uri, name, clock.instant()))
                    .thenApply(ignored -> true);
              })
          .exceptionally(
              failure -> {
                LOGGER.log(Level.WARNING, "Bookmark could not be saved.");
                return false;
              });
    } catch (RuntimeException exception) {
      return CompletableFuture.completedFuture(false);
    }
  }

  /** Removes a bookmark; failures are logged and ignored. */
  public CompletionStage<Void> removeBookmark(UUID id) {
    if (bookmarks.isEmpty()) {
      return CompletableFuture.completedFuture(null);
    }
    try {
      return bookmarks
          .orElseThrow()
          .remove(id)
          .exceptionally(
              failure -> {
                LOGGER.log(Level.WARNING, "Bookmark could not be removed.");
                return null;
              });
    } catch (RuntimeException exception) {
      return CompletableFuture.completedFuture(null);
    }
  }

  private static <T> CompletionStage<T> guarded(
      Optional<CompletionStage<T>> source, T fallback) {
    if (source.isEmpty()) {
      return CompletableFuture.completedFuture(fallback);
    }
    try {
      return source
          .orElseThrow()
          .exceptionally(
              failure -> {
                LOGGER.log(Level.WARNING, "Stored library data could not be read.");
                return fallback;
              });
    } catch (RuntimeException exception) {
      return CompletableFuture.completedFuture(fallback);
    }
  }
}
