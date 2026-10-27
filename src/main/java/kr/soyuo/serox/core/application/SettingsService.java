package kr.soyuo.serox.core.application;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import kr.soyuo.serox.core.event.BrowserEventBus;
import kr.soyuo.serox.core.event.SettingsChanged;
import kr.soyuo.serox.core.model.BrowserSettings;
import kr.soyuo.serox.core.port.storage.SettingsRepository;

/** Holds the saved settings, persists changes when storage exists, and announces them. */
public final class SettingsService {
  private static final Logger LOGGER = Logger.getLogger(SettingsService.class.getName());

  private final Optional<SettingsRepository> repository;
  private final BrowserEventBus eventBus;
  private volatile BrowserSettings current = BrowserSettings.defaults();

  /** Without a repository, settings still apply for the current run. */
  public SettingsService(Optional<SettingsRepository> repository, BrowserEventBus eventBus) {
    this.repository = Objects.requireNonNull(repository, "repository");
    this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
  }

  /** Reads stored settings once at startup; any failure keeps the defaults. */
  public BrowserSettings load() {
    if (repository.isPresent()) {
      try {
        current = repository.orElseThrow().load().toCompletableFuture().get(3, TimeUnit.SECONDS);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
      } catch (Exception exception) {
        LOGGER.log(Level.WARNING, "Stored settings could not be read; using defaults.");
      }
    }
    return current;
  }

  /** The settings currently in effect. */
  public BrowserSettings current() {
    return current;
  }

  /** Makes the settings effective immediately and stores them; storage failure is not fatal. */
  public CompletionStage<Void> save(BrowserSettings settings) {
    Objects.requireNonNull(settings, "settings");
    current = settings;
    eventBus.publish(new SettingsChanged(Instant.now(), settings));
    if (repository.isEmpty()) {
      return CompletableFuture.completedFuture(null);
    }
    try {
      return repository
          .orElseThrow()
          .save(settings)
          .exceptionally(
              failure -> {
                LOGGER.log(Level.WARNING, "Settings could not be saved.");
                return null;
              });
    } catch (RuntimeException exception) {
      LOGGER.log(Level.WARNING, "Settings could not be saved.");
      return CompletableFuture.completedFuture(null);
    }
  }
}
