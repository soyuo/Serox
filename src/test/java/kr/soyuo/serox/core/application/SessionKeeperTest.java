package kr.soyuo.serox.core.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import kr.soyuo.serox.core.event.ActiveTabChanged;
import kr.soyuo.serox.core.event.BrowserEventBus;
import kr.soyuo.serox.core.event.InProcessBrowserEventBus;
import kr.soyuo.serox.core.event.TabStateChanged;
import kr.soyuo.serox.core.model.NavigationStatus;
import kr.soyuo.serox.core.model.SessionSnapshot;
import kr.soyuo.serox.core.model.StartupMode;
import kr.soyuo.serox.core.model.TabId;
import kr.soyuo.serox.core.model.TabSnapshot;
import kr.soyuo.serox.core.port.storage.SessionRepository;
import kr.soyuo.serox.storage.sqlite.SqliteStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionKeeperTest {
  @TempDir Path tempDir;

  @Test
  void restoresWebTabsAndActiveSelectionFromThePreviousRun() {
    Path file = tempDir.resolve("serox.db");
    try (var storage = new SqliteStorage(file);
        var bus = new InProcessBrowserEventBus();
        var keeper = new SessionKeeper(bus, storage.sessions())) {
      var commands = new StubCommands(bus);
      keeper.restoreOrStart(commands);
      TabSnapshot second = commands.createTab().toCompletableFuture().join();
      commands.navigate(commands.tabs.getFirst().id(), "https://one.test/");
      commands.navigate(second.id(), "https://two.test/");
      commands.activateTab(commands.tabs.getFirst().id());
    }

    try (var storage = new SqliteStorage(file);
        var bus = new InProcessBrowserEventBus();
        var keeper = new SessionKeeper(bus, storage.sessions())) {
      var commands = new StubCommands(bus);
      keeper.restoreOrStart(commands);

      assertEquals(
          List.of("https://one.test/", "https://two.test/"), commands.navigations);
      assertEquals(commands.tabs.getFirst().id(), commands.activated.getLast());
    }
  }

  @Test
  void startsOneBlankTabWhenNothingCanBeRestored() {
    try (var storage = new SqliteStorage(tempDir.resolve("serox.db"));
        var bus = new InProcessBrowserEventBus();
        var keeper = new SessionKeeper(bus, storage.sessions())) {
      var commands = new StubCommands(bus);
      keeper.restoreOrStart(commands);

      assertEquals(1, commands.tabs.size());
      assertTrue(commands.navigations.isEmpty());
    }
  }

  private void rememberTwoPages(Path file) {
    try (var storage = new SqliteStorage(file);
        var bus = new InProcessBrowserEventBus();
        var keeper = new SessionKeeper(bus, storage.sessions())) {
      var commands = new StubCommands(bus);
      keeper.restoreOrStart(commands);
      TabSnapshot second = commands.createTab().toCompletableFuture().join();
      commands.navigate(commands.tabs.getFirst().id(), "https://one.test/");
      commands.navigate(second.id(), "https://two.test/");
    }
  }

  @Test
  void homeStartupIgnoresThePreviousSession() {
    Path file = tempDir.resolve("serox.db");
    rememberTwoPages(file);

    try (var storage = new SqliteStorage(file);
        var bus = new InProcessBrowserEventBus();
        var keeper = new SessionKeeper(bus, storage.sessions())) {
      var commands = new StubCommands(bus);
      keeper.restoreOrStart(commands, StartupMode.HOME, "https://start.test/");

      assertEquals(1, commands.tabs.size());
      assertTrue(commands.navigations.isEmpty());
    }
  }

  @Test
  void specificPageStartupOpensOnlyTheStartPage() {
    Path file = tempDir.resolve("serox.db");
    rememberTwoPages(file);

    try (var storage = new SqliteStorage(file);
        var bus = new InProcessBrowserEventBus();
        var keeper = new SessionKeeper(bus, storage.sessions())) {
      var commands = new StubCommands(bus);
      keeper.restoreOrStart(commands, StartupMode.SPECIFIC_PAGE, "https://start.test/");

      assertEquals(1, commands.tabs.size());
      assertEquals(List.of("https://start.test/"), commands.navigations);
    }
  }

  @Test
  void specificPageStartupWithoutAUsableAddressStartsBlank() {
    try (var storage = new SqliteStorage(tempDir.resolve("serox.db"));
        var bus = new InProcessBrowserEventBus();
        var keeper = new SessionKeeper(bus, storage.sessions())) {
      var commands = new StubCommands(bus);
      keeper.restoreOrStart(commands, StartupMode.SPECIFIC_PAGE, "");

      assertEquals(1, commands.tabs.size());
      assertTrue(commands.navigations.isEmpty());
    }
  }

  @Test
  void ignoresNonWebAddressesAndStorageFailures() {
    SessionRepository failing =
        new SessionRepository() {
          @Override
          public CompletionStage<Void> save(SessionSnapshot session) {
            return CompletableFuture.failedFuture(new IllegalStateException("locked"));
          }

          @Override
          public CompletionStage<Optional<SessionSnapshot>> latest() {
            return CompletableFuture.failedFuture(new IllegalStateException("corrupt"));
          }
        };
    try (var bus = new InProcessBrowserEventBus();
        var keeper = new SessionKeeper(bus, failing)) {
      var commands = new StubCommands(bus);
      keeper.restoreOrStart(commands);

      assertEquals(1, commands.tabs.size());
    }
  }

  private static final class StubCommands implements BrowserCommandService {
    final BrowserEventBus bus;
    final List<TabSnapshot> tabs = new ArrayList<>();
    final List<String> navigations = new ArrayList<>();
    final List<TabId> activated = new ArrayList<>();

    StubCommands(BrowserEventBus bus) {
      this.bus = bus;
    }

    @Override
    public CompletionStage<TabSnapshot> createTab() {
      TabSnapshot tab = snapshot(TabId.create(), Optional.empty());
      tabs.add(tab);
      bus.publish(new TabStateChanged(Instant.now(), tab));
      bus.publish(new ActiveTabChanged(Instant.now(), Optional.of(tab.id())));
      return CompletableFuture.completedFuture(tab);
    }

    @Override
    public CompletionStage<TabSnapshot> navigate(TabId tabId, String addressOrQuery) {
      navigations.add(addressOrQuery);
      TabSnapshot tab = snapshot(tabId, Optional.of(URI.create(addressOrQuery)));
      tabs.replaceAll(existing -> existing.id().equals(tabId) ? tab : existing);
      bus.publish(new TabStateChanged(Instant.now(), tab));
      return CompletableFuture.completedFuture(tab);
    }

    @Override
    public CompletionStage<Void> activateTab(TabId tabId) {
      activated.add(tabId);
      bus.publish(new ActiveTabChanged(Instant.now(), Optional.of(tabId)));
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<List<TabSnapshot>> listTabs() {
      return CompletableFuture.completedFuture(List.copyOf(tabs));
    }

    @Override
    public CompletionStage<TabSnapshot> goBack(TabId tabId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public CompletionStage<TabSnapshot> goForward(TabId tabId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public CompletionStage<TabSnapshot> reload(TabId tabId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public CompletionStage<Void> cancelNavigation(TabId tabId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public CompletionStage<Void> closeTab(TabId tabId) {
      throw new UnsupportedOperationException();
    }

    private static TabSnapshot snapshot(TabId id, Optional<URI> uri) {
      return new TabSnapshot(id, uri, "", NavigationStatus.IDLE, false, false);
    }
  }
}
