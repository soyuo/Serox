package kr.soyuo.serox.ui;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** What the privacy page can do with stored data; each call completes even when storage fails. */
interface PrivacyActions {
  /** Whether anything can be stored on this machine at all. */
  boolean available();

  /** Stored recent searches, newest first. */
  CompletionStage<List<String>> recentQueries();

  CompletionStage<Void> deleteQuery(String query);

  CompletionStage<Void> clearQueries();

  CompletionStage<Void> clearVisits();

  /** For a shell without storage: nothing is stored, so there is nothing to clear. */
  static PrivacyActions none() {
    return new PrivacyActions() {
      @Override
      public boolean available() {
        return false;
      }

      @Override
      public CompletionStage<List<String>> recentQueries() {
        return CompletableFuture.completedFuture(List.of());
      }

      @Override
      public CompletionStage<Void> deleteQuery(String query) {
        return CompletableFuture.completedFuture(null);
      }

      @Override
      public CompletionStage<Void> clearQueries() {
        return CompletableFuture.completedFuture(null);
      }

      @Override
      public CompletionStage<Void> clearVisits() {
        return CompletableFuture.completedFuture(null);
      }
    };
  }
}
