package kr.soyuo.serox.ai.application;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import kr.soyuo.serox.ai.auth.ChatGptAccounts;
import kr.soyuo.serox.ai.auth.ChatGptProvider;
import kr.soyuo.serox.ai.auth.ProtectedLocalStore;
import kr.soyuo.serox.ai.auth.ProviderKeyTest;
import kr.soyuo.serox.ai.provider.AiProviders;
import kr.soyuo.serox.core.application.AppConfiguration;
import kr.soyuo.serox.core.event.BrowserEventBus;
import kr.soyuo.serox.core.event.ConnectionStatusChanged;
import kr.soyuo.serox.core.event.ServiceId;
import kr.soyuo.serox.core.event.ServiceStatus;
import kr.soyuo.serox.core.model.AiProviderId;
import kr.soyuo.serox.core.model.SecretValue;

/** Owns provider selection, protected user keys, browser accounts, and the live AI connection. */
public final class AiConnectionService implements AutoCloseable {
  /** Non-secret settings state, safe to hand to the JavaFX view. */
  public record Snapshot(
      AiProviderId provider,
      String model,
      boolean browser,
      String accountId,
      Set<AiProviderId> configured,
      List<ChatGptAccounts.Account> accounts,
      List<ChatGptAccounts.Model> models,
      String message) {}

  private final AiSearchService ai;
  private final BrowserEventBus events;
  private final ProtectedLocalStore store;
  private final ChatGptAccounts accounts;
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
  private final Map<AiProviderId, SecretValue> keys = new EnumMap<>(AiProviderId.class);
  private final Map<AiProviderId, SecretValue> savedKeys = new EnumMap<>(AiProviderId.class);
  private final FileChannel lockChannel;
  private final FileLock lock;
  private volatile Snapshot current;
  private boolean closed;
  private volatile boolean welcomeShown;

  public AiConnectionService(
      AppConfiguration configuration, Path directory, AiSearchService ai, BrowserEventBus events) {
    this.ai = ai;
    this.events = events;
    keys.putAll(configuration.aiApiKeys());
    store = new ProtectedLocalStore(directory.resolve("ai-settings.dpapi"));
    accounts = new ChatGptAccounts(directory);
    FileChannel channel = null;
    FileLock acquired = null;
    String message = "";
    AiProviderId selected = configuration.aiProvider();
    String model = configuration.aiModel();
    boolean browser = false;
    String accountId = "";
    List<ChatGptAccounts.Account> savedAccounts = List.of();
    try {
      Files.createDirectories(directory);
      channel =
          FileChannel.open(
              directory.resolve("auth.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
      acquired = channel.tryLock();
      if (acquired == null) {
        throw new IOException("Another Serox instance owns these credentials");
      }
      JsonObject data = JsonParser.parseString(store.read()).getAsJsonObject();
      welcomeShown = data.has("welcomeShown") && data.get("welcomeShown").getAsBoolean();
      if (data.has("keys")) {
        for (AiProviderId id : AiProviderId.values()) {
          if (data.getAsJsonObject("keys").has(id.name())) {
            SecretValue key =
                new SecretValue(data.getAsJsonObject("keys").get(id.name()).getAsString());
            savedKeys.put(id, key);
            keys.put(id, key);
          }
        }
      }
      if (data.has("provider")) {
        selected = AiProviderId.valueOf(data.get("provider").getAsString());
        model = data.get("model").getAsString();
        if (!model.matches("[A-Za-z0-9._:-]{1,128}")) {
          throw new IOException("Invalid saved model");
        }
        browser = data.get("browser").getAsBoolean();
        accountId = data.get("account").getAsString();
      }
      savedAccounts = accounts.accounts();
    } catch (Exception exception) {
      savedKeys.clear();
      keys.clear();
      keys.putAll(configuration.aiApiKeys());
      message = "저장된 AI 연결을 읽지 못했어요. 환경 변수 연결을 사용하며, 새 키 저장이나 로그인은 잠금이 풀린 뒤 다시 시도하세요.";
      selected = configuration.aiProvider();
      model = configuration.aiModel();
      browser = false;
      accountId = "";
    }
    lockChannel = channel;
    lock = acquired;
    current =
        new Snapshot(
            selected,
            model,
            browser,
            accountId,
            Set.copyOf(keys.keySet()),
            savedAccounts,
            List.of(),
            message);
    install();
  }

  public Snapshot snapshot() {
    return current;
  }

  public boolean needsWelcome() {
    return current.browser() && !welcomeShown;
  }

  public CompletableFuture<Snapshot> acknowledgeWelcome() {
    return task(
        () -> {
          welcomeShown = true;
          save();
          return current;
        });
  }

  public void announce() {
    Snapshot value = current;
    boolean connected =
        value.browser()
            ? value.accounts().stream()
                .anyMatch(
                    account -> account.id().equals(value.accountId()) && account.planEnabled())
            : value.configured().contains(value.provider());
    events.publish(
        new ConnectionStatusChanged(
            Instant.now(),
            ServiceId.AI,
            connected ? ServiceStatus.CONNECTED : ServiceStatus.DISCONNECTED,
            connected
                ? "켜짐 ("
                    + (value.browser() ? "ChatGPT" : value.provider().label())
                    + " · "
                    + value.model()
                    + ")"
                : "연결 필요"));
  }

  /** Saves only after the provider accepts the supplied key. No source data is sent by the test. */
  public CompletableFuture<Snapshot> connectKey(AiProviderId provider, String model, String key) {
    return task(
        () -> {
          requireWritable();
          validateModel(model);
          ProviderKeyTest.check(provider, key);
          savedKeys.put(provider, new SecretValue(key));
          keys.put(provider, savedKeys.get(provider));
          current = copy(provider, model, false, "", "API 키 연결을 확인했어요.");
          save();
          install();
          return current;
        });
  }

  public CompletableFuture<Snapshot> selectKey(AiProviderId provider, String model) {
    return task(
        () -> {
          requireWritable();
          validateModel(model);
          current =
              copy(
                  provider,
                  model,
                  false,
                  "",
                  keys.containsKey(provider) ? "선택한 제공자를 사용해요." : "API 키를 연결하거나 브라우저에서 로그인하세요.");
          save();
          install();
          return current;
        });
  }

  public CompletableFuture<Snapshot> signIn(String existingAccount, Consumer<URI> openBrowser) {
    try {
      requireWritable();
    } catch (IOException exception) {
      return CompletableFuture.failedFuture(exception);
    }
    return accounts
        .signIn(existingAccount, openBrowser)
        .thenCompose(
            account -> {
              if (!account.planEnabled()) {
                return task(
                    () -> {
                      current =
                          new Snapshot(
                              current.provider(),
                              current.model(),
                              current.browser(),
                              current.accountId(),
                              Set.copyOf(keys.keySet()),
                              accounts.accounts(),
                              List.of(),
                              "로그인했지만 ChatGPT 플랜 사용 승인이 필요해요. 다시 로그인해 승인하세요.");
                      return current;
                    });
              }
              return useAccount(account.id());
            });
  }

  public void cancelSignIn() {
    accounts.cancelSignIn();
  }

  public CompletableFuture<Snapshot> useAccount(String id) {
    return task(
        () -> {
          requireWritable();
          List<ChatGptAccounts.Model> models = accounts.models(id);
          if (models.isEmpty()) {
            throw new IOException("No ChatGPT models are available");
          }
          accounts.select(id);
          current =
              new Snapshot(
                  AiProviderId.CODEX,
                  models.getFirst().id(),
                  true,
                  id,
                  Set.copyOf(keys.keySet()),
                  accounts.accounts(),
                  models,
                  "ChatGPT 계정을 연결했어요.");
          save();
          install();
          return current;
        });
  }

  public CompletableFuture<Snapshot> changeBrowserModel(String model) {
    return task(
        () -> {
          requireWritable();
          if (!current.browser()
              || current.models().stream().noneMatch(value -> value.id().equals(model))) {
            throw new IOException("Choose a model available to this ChatGPT account");
          }
          current = copy(AiProviderId.CODEX, model, true, current.accountId(), "모델을 바꿨어요.");
          save();
          install();
          return current;
        });
  }

  public CompletableFuture<Snapshot> signOut(String id) {
    return task(
        () -> {
          requireWritable();
          if (current.browser() && current.accountId().equals(id)) {
            ai.setProvider(Optional.empty());
          }
          boolean revoked = accounts.signOut(id);
          current =
              new Snapshot(
                  current.provider(),
                  current.model(),
                  current.browser(),
                  current.accountId(),
                  Set.copyOf(keys.keySet()),
                  accounts.accounts(),
                  List.of(),
                  revoked
                      ? "로그아웃했어요."
                      : "이 컴퓨터에서 로그아웃했어요. 원격 해제는 확인하지 못했어요. ChatGPT 설정에서 Serox 연결을 해제하세요.");
          install();
          return current;
        });
  }

  private Snapshot copy(
      AiProviderId provider, String model, boolean browser, String id, String message) {
    return new Snapshot(
        provider,
        model,
        browser,
        id,
        Set.copyOf(keys.keySet()),
        current.accounts(),
        current.models(),
        message);
  }

  private void install() {
    if (current.browser()) {
      boolean allowed =
          current.accounts().stream()
              .anyMatch(
                  account -> account.id().equals(current.accountId()) && account.planEnabled());
      ai.setProvider(
          allowed
              ? Optional.of(new ChatGptProvider(accounts, current.accountId(), current.model()))
              : Optional.empty());
    } else {
      ai.setProvider(
          Optional.ofNullable(keys.get(current.provider()))
              .map(key -> AiProviders.create(current.provider(), current.model(), key)));
    }
    announce();
  }

  private void save() throws IOException {
    JsonObject data = new JsonObject();
    data.addProperty("welcomeShown", welcomeShown);
    data.addProperty("provider", current.provider().name());
    data.addProperty("model", current.model());
    data.addProperty("browser", current.browser());
    data.addProperty("account", current.accountId());
    JsonObject stored = new JsonObject();
    savedKeys.forEach((id, key) -> stored.addProperty(id.name(), key.revealForRequest()));
    data.add("keys", stored);
    store.write(data.toString());
  }

  private synchronized void requireWritable() throws IOException {
    if (closed || lock == null || !lock.isValid()) {
      throw new IOException("AI credential storage is unavailable or already in use");
    }
  }

  private static void validateModel(String model) throws IOException {
    if (!model.matches("[A-Za-z0-9._:-]{1,128}")) {
      throw new IOException("Invalid model identifier");
    }
  }

  private CompletableFuture<Snapshot> task(Action action) {
    return CompletableFuture.supplyAsync(
        () -> {
          synchronized (this) {
            Snapshot before = current;
            Map<AiProviderId, SecretValue> previousKeys = new EnumMap<>(keys);
            Map<AiProviderId, SecretValue> previousSaved = new EnumMap<>(savedKeys);
            try {
              requireWritable();
              return action.run();
            } catch (Exception exception) {
              current = before;
              keys.clear();
              keys.putAll(previousKeys);
              savedKeys.clear();
              savedKeys.putAll(previousSaved);
              throw new java.util.concurrent.CompletionException(
                  new IOException("AI 연결을 완료하지 못했어요. 키, 모델, 네트워크와 저장소를 확인하세요."));
            }
          }
        },
        executor);
  }

  private interface Action {
    Snapshot run() throws IOException;
  }

  @Override
  public void close() {
    accounts.close();
    executor.shutdownNow();
    synchronized (this) {
      closed = true;
      try {
        if (lock != null) {
          lock.release();
        }
        if (lockChannel != null) {
          lockChannel.close();
        }
      } catch (IOException ignored) {
        // Process exit also releases the OS file lock.
      }
    }
  }
}
