package kr.soyuo.serox.core.port.storage;

import java.util.concurrent.CompletionStage;
import kr.soyuo.serox.core.model.BrowserSettings;

/** Persistence port for non-secret browser preferences. */
public interface SettingsRepository {
  CompletionStage<BrowserSettings> load();

  CompletionStage<Void> save(BrowserSettings settings);
}
