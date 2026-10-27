package kr.soyuo.serox.core.port.storage;

import java.util.concurrent.CompletionStage;
import kr.soyuo.serox.core.model.McpSettings;

/** Persistence port for how the MCP HTTP server runs. */
public interface McpSettingsRepository {
  /** The saved settings, or the defaults when nothing valid is stored. */
  CompletionStage<McpSettings> load();

  CompletionStage<Void> save(McpSettings settings);
}
