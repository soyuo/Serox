package kr.soyuo.serox.core.application;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import kr.soyuo.serox.core.event.ActiveTabChanged;
import kr.soyuo.serox.core.event.BrowserEventBus;
import kr.soyuo.serox.core.event.EventSubscription;
import kr.soyuo.serox.core.event.TabClosed;
import kr.soyuo.serox.core.event.TabStateChanged;
import kr.soyuo.serox.core.model.BrowserSettings;
import kr.soyuo.serox.core.model.SessionSnapshot;
import kr.soyuo.serox.core.model.StartupMode;
import kr.soyuo.serox.core.model.TabId;
import kr.soyuo.serox.core.model.TabSnapshot;
import kr.soyuo.serox.core.port.storage.SessionRepository;

/** Persists open tabs as they change and restores the last session at startup. */
public final class SessionKeeper implements AutoCloseable {
  static final int MAX_RESTORED_TABS = 10;
  private static final long LOAD_TIMEOUT_SECONDS = 3;
  private static final Logger LOGGER = Logger.getLogger(SessionKeeper.class.getName());

  private final SessionRepository sessionRepository;
  private final UUID sessionId = UUID.randomUUID();
  private final Map<TabId, TabSnapshot> tabs = new LinkedHashMap<>();
  private final List<EventSubscription> subscriptions = new ArrayList<>();
  private TabId activeTabId;
  private boolean recording;

  /** Subscribes to tab events; nothing is written until {@link #restoreOrStart} completes. */
  public SessionKeeper(BrowserEventBus eventBus, SessionRepository sessionRepository) {
    Objects.requireNonNull(eventBus, "eventBus");
    this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository");
    subscriptions.add(eventBus.subscribe(TabStateChanged.class, this::onTabState));
    subscriptions.add(eventBus.subscribe(TabClosed.class, this::onTabClosed));
    subscriptions.add(eventBus.subscribe(ActiveTabChanged.class, this::onActiveTab));
  }

  /**
   * Reopens the last session's web pages, or starts one blank tab when there is nothing usable to
   * restore. Storage and restore failures fall back to the blank tab.
   */
  public void restoreOrStart(BrowserCommandService commands) {
    restoreOrStart(commands, StartupMode.RESTORE_TABS, "");
  }

  /**
   * Starts the way the user chose: reopen the last session's pages, open the start page, or begin
   * with one blank tab. A start page that cannot be used falls back to the blank tab.
   */
  public void restoreOrStart(
      BrowserCommandService commands, StartupMode mode, String startPageUrl) {
    Objects.requireNonNull(commands, "commands");
    Objects.requireNonNull(mode, "mode");
    if (mode == StartupMode.RESTORE_TABS) {
      try {
        restore(commands);
      } catch (RuntimeException exception) {
        LOGGER.log(Level.WARNING, "Previous session could not be restored.");
      }
    }
    try {
      if (commands.listTabs().toCompletableFuture().join().isEmpty()) {
        TabSnapshot first = commands.createTab().toCompletableFuture().join();
        if (mode == StartupMode.SPECIFIC_PAGE && BrowserSettings.isWebAddress(startPageUrl)) {
          commands.navigate(first.id(), startPageUrl);
        }
      }
    } catch (RuntimeException exception) {
      LOGGER.log(Level.WARNING, "Default tab could not be created.");
    }
    synchronized (this) {
      recording = true;
    }
    save();
  }

  private void restore(BrowserCommandService commands) {
    Optional<SessionSnapshot> stored = loadLatest();
    if (stored.isEmpty()) {
      return;
    }
    List<TabSnapshot> restorable =
        stored.orElseThrow().tabs().stream()
            .filter(tab -> tab.currentUri().filter(SessionKeeper::isWebAddress).isPresent())
            .limit(MAX_RESTORED_TABS)
            .toList();
    Optional<TabId> storedActive = stored.orElseThrow().activeTabId();
    TabId activeRestored = null;
    for (TabSnapshot tab : restorable) {
      TabSnapshot created = commands.createTab().toCompletableFuture().join();
      commands.navigate(created.id(), tab.currentUri().orElseThrow().toString());
      if (storedActive.filter(tab.id()::equals).isPresent()) {
        activeRestored = created.id();
      }
    }
    if (activeRestored != null) {
      commands.activateTab(activeRestored).toCompletableFuture().join();
    }
  }

  private Optional<SessionSnapshot> loadLatest() {
    try {
      return sessionRepository
          .latest()
          .toCompletableFuture()
          .get(LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return Optional.empty();
    } catch (Exception exception) {
      LOGGER.log(Level.WARNING, "Stored session could not be read; starting fresh.");
      return Optional.empty();
    }
  }

  private static boolean isWebAddress(URI uri) {
    String scheme = uri.getScheme();
    return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
  }

  private void onTabState(TabStateChanged event) {
    synchronized (this) {
      tabs.put(event.snapshot().id(), event.snapshot());
    }
    save();
  }

  private void onTabClosed(TabClosed event) {
    synchronized (this) {
      tabs.remove(event.tabId());
    }
    save();
  }

  private void onActiveTab(ActiveTabChanged event) {
    synchronized (this) {
      activeTabId = event.tabId().orElse(null);
    }
    save();
  }

  private void save() {
    SessionSnapshot snapshot;
    synchronized (this) {
      if (!recording) {
        return;
      }
      List<TabSnapshot> current = List.copyOf(tabs.values());
      Optional<TabId> active = Optional.ofNullable(activeTabId).filter(tabs::containsKey);
      snapshot = new SessionSnapshot(sessionId, current, active);
    }
    try {
      sessionRepository
          .save(snapshot)
          .exceptionally(
              failure -> {
                LOGGER.log(Level.WARNING, "Session could not be saved.");
                return null;
              });
    } catch (RuntimeException exception) {
      LOGGER.log(Level.WARNING, "Session could not be saved.");
    }
  }

  @Override
  public void close() {
    subscriptions.forEach(EventSubscription::close);
  }
}
