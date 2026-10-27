package kr.soyuo.serox.ui;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** The search page's credential operations; key material never enters ordinary preferences. */
public interface SearchActions {
  boolean configured();

  CompletionStage<Void> saveKey(String key);

  static SearchActions none() {
    return new SearchActions() {
      public boolean configured() {
        return false;
      }

      public CompletionStage<Void> saveKey(String key) {
        return CompletableFuture.failedFuture(
            new IllegalStateException("Search settings unavailable"));
      }
    };
  }
}
