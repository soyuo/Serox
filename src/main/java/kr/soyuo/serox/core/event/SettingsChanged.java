package kr.soyuo.serox.core.event;

import java.time.Instant;
import java.util.Objects;
import kr.soyuo.serox.core.model.BrowserSettings;

/** Announces the settings that are now in effect for the whole application. */
public record SettingsChanged(Instant occurredAt, BrowserSettings settings)
    implements ApplicationEvent {
  public SettingsChanged {
    Objects.requireNonNull(occurredAt, "occurredAt");
    Objects.requireNonNull(settings, "settings");
  }
}
