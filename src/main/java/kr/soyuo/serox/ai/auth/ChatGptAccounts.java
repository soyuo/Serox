package kr.soyuo.serox.ai.auth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Serox's own dynamically registered ChatGPT OAuth clients, never another app's credentials. */
public final class ChatGptAccounts implements AutoCloseable {
  private static final URI TOKEN = URI.create("https://auth.openai.com/api/accounts/oauth/token");
  private static final String RESOURCE = "https://api.openai.com/v1";
  private static final String PLAN_SCOPE = "chatgpt.tokens.use.direct";
  private final ProtectedLocalStore store;
  private final Path directory;
  private final OAuthHttp http = new OAuthHttp();
  private CompletableFuture<Account> pending;
  private HttpServer listener;
  private Thread loginThread;
  private volatile boolean closed;

  /** A safe view; no tokens are exposed to the UI. */
  public record Account(String id, String label, boolean planEnabled) {}

  /** Model names come from the signed-in account, preserving the server order. */
  public record Model(String id, String label) {
    @Override
    public String toString() {
      return label;
    }
  }

  public ChatGptAccounts(Path directory) {
    this.directory = directory;
    store = new ProtectedLocalStore(directory.resolve("chatgpt.dpapi"));
  }

  public synchronized List<Account> accounts() throws IOException {
    List<Account> accounts = new ArrayList<>();
    JsonObject data = data();
    for (var entry : data.getAsJsonObject("accounts").entrySet()) {
      JsonObject account = entry.getValue().getAsJsonObject();
      accounts.add(view(entry.getKey(), account));
    }
    return List.copyOf(accounts);
  }

  /** The saved registration can remain after sign-out, but cannot make requests without tokens. */
  public synchronized String selectedId() throws IOException {
    return string(data(), "selected");
  }

  public synchronized void select(String id) throws IOException {
    JsonObject data = data();
    JsonObject account = account(data, id);
    if (!account.has("access_token")) {
      throw new IOException("Sign in to this ChatGPT account again");
    }
    data.addProperty("selected", id);
    store.write(data.toString());
  }

  /** Opens a system-browser authorization attempt, with a fresh local callback and PKCE values. */
  public synchronized CompletableFuture<Account> signIn(
      String existingId, Consumer<URI> openBrowser) {
    if (closed) {
      return CompletableFuture.failedFuture(new IOException("Authentication is closed"));
    }
    if (pending != null && !pending.isDone()) {
      return pending;
    }
    CompletableFuture<Account> result = new CompletableFuture<>();
    pending = result;
    loginThread =
        Thread.ofVirtual()
            .name("chatgpt-sign-in")
            .unstarted(
                () -> {
                  HttpServer server = null;
                  try {
                    JsonObject previous;
                    String host;
                    synchronized (this) {
                      previous =
                          existingId.isEmpty() ? null : account(data(), existingId).deepCopy();
                      host = hostId();
                    }
                    String state = random();
                    String nonce = random();
                    String verifier = random();
                    server =
                        HttpServer.create(
                            new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 4);
                    int port = server.getAddress().getPort();
                    String callback = "http://127.0.0.1:" + port + "/auth/callback";
                    CompletableFuture<Map<String, String>> returned = new CompletableFuture<>();
                    server.createContext(
                        "/auth/callback", exchange -> callback(exchange, state, port, returned));
                    server.setExecutor(
                        java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
                    synchronized (this) {
                      if (result.isCancelled() || closed) {
                        throw new IOException("Authentication cancelled");
                      }
                      listener = server;
                      server.start();
                    }
                    Map<String, String> parameters = new HashMap<>();
                    parameters.put(
                        "client_id",
                        previous == null ? "dynamic_agent_client" : string(previous, "client_id"));
                    if (previous == null) {
                      parameters.put("agent_name_hint", "Serox");
                    } else if (previous.has("id_token")) {
                      parameters.put("id_token_hint", string(previous, "id_token"));
                    }
                    parameters.put("ext_agent_host_id", host);
                    parameters.put("response_type", "code");
                    parameters.put("redirect_uri", callback);
                    parameters.put(
                        "scope",
                        "openid profile email offline_access resource.invoke " + PLAN_SCOPE);
                    parameters.put("resource", RESOURCE);
                    parameters.put("state", state);
                    parameters.put("nonce", nonce);
                    parameters.put("code_challenge_method", "S256");
                    parameters.put(
                        "code_challenge",
                        Base64.getUrlEncoder()
                            .withoutPadding()
                            .encodeToString(
                                MessageDigest.getInstance("SHA-256")
                                    .digest(verifier.getBytes(StandardCharsets.US_ASCII))));
                    openBrowser.accept(
                        URI.create(
                            "https://auth.openai.com/api/accounts/authorize?"
                                + OAuthHttp.form(parameters)));
                    Map<String, String> response = returned.get(3, TimeUnit.MINUTES);
                    String clientId =
                        issuedClient(
                            response, previous == null ? "" : string(previous, "client_id"));
                    // Retain a newly issued registration even if the one-use code later expires.
                    String registrationId =
                        previous == null ? UUID.randomUUID().toString() : existingId;
                    if (previous == null) {
                      synchronized (this) {
                        if (closed || result.isCancelled()) {
                          throw new IOException("Authentication cancelled");
                        }
                        JsonObject data = data();
                        JsonObject registration = new JsonObject();
                        registration.addProperty("client_id", clientId);
                        registration.addProperty("label", "인증 미완료");
                        registration.addProperty("subject", "");
                        data.getAsJsonObject("accounts").add(registrationId, registration);
                        store.write(data.toString());
                      }
                    }
                    JsonObject tokens =
                        http.post(
                            TOKEN,
                            Map.of(
                                "grant_type",
                                "authorization_code",
                                "client_id",
                                clientId,
                                "code",
                                response.get("code"),
                                "code_verifier",
                                verifier,
                                "redirect_uri",
                                callback,
                                "resource",
                                RESOURCE));
                    JsonObject claims =
                        OpenAiIdentity.verify(
                            string(tokens, "id_token"),
                            clientId,
                            nonce,
                            http.get(URI.create("https://auth.openai.com/.well-known/jwks.json")),
                            Instant.now());
                    if (previous != null
                        && !string(previous, "subject").isEmpty()
                        && !string(previous, "subject").equals(string(claims, "sub"))) {
                      throw new IOException("The selected ChatGPT account does not match");
                    }
                    JsonObject account = new JsonObject();
                    account.addProperty("client_id", clientId);
                    account.addProperty("subject", string(claims, "sub"));
                    account.addProperty(
                        "label",
                        claims.has("email") ? string(claims, "email") : string(claims, "sub"));
                    updateTokens(account, tokens, true);
                    synchronized (this) {
                      if (closed || result.isCancelled()) {
                        throw new IOException("Authentication cancelled");
                      }
                      JsonObject data = data();
                      String id = registrationId;
                      data.getAsJsonObject("accounts").add(id, account);
                      data.addProperty("selected", id);
                      store.write(data.toString());
                      result.complete(view(id, account));
                    }
                  } catch (Exception exception) {
                    result.completeExceptionally(
                        new IOException(
                            "ChatGPT sign-in failed or was cancelled. Please try again."));
                  } finally {
                    if (server != null) {
                      server.stop(0);
                    }
                    synchronized (this) {
                      if (listener == server) {
                        listener = null;
                      }
                    }
                  }
                });
    loginThread.start();
    result.whenComplete(
        (account, failure) -> {
          synchronized (this) {
            if (pending == result && listener != null) {
              listener.stop(0);
              listener = null;
            }
          }
        });
    return result;
  }

  public synchronized void cancelSignIn() {
    if (pending != null) {
      pending.cancel(true);
    }
    if (loginThread != null) {
      loginThread.interrupt();
    }
  }

  /** Serializes refresh and atomically persists rotating refresh tokens before returning access. */
  public synchronized String accessToken(String id) throws IOException {
    if (closed) {
      throw new IOException("Authentication is closed");
    }
    JsonObject data = data();
    JsonObject account = account(data, id);
    if (!enabled(account)) {
      throw new IOException("ChatGPT plan permission is required");
    }
    if (account.get("expires_at").getAsLong() <= Instant.now().plusSeconds(60).getEpochSecond()) {
      JsonObject tokens =
          http.post(
              TOKEN,
              Map.of(
                  "grant_type",
                  "refresh_token",
                  "client_id",
                  string(account, "client_id"),
                  "refresh_token",
                  string(account, "refresh_token"),
                  "resource",
                  RESOURCE));
      updateTokens(account, tokens, false);
      store.write(data.toString());
    }
    return string(account, "access_token");
  }

  public List<Model> models(String id) throws IOException {
    JsonObject response = http.get(URI.create(RESOURCE + "/models"), accessToken(id));
    List<Model> models = new ArrayList<>();
    for (var element : response.getAsJsonArray("models")) {
      JsonObject model = element.getAsJsonObject();
      String slug = string(model, "slug");
      if ("list".equals(string(model, "visibility")) && slug.matches("[A-Za-z0-9._:-]{1,128}")) {
        models.add(new Model(slug, string(model, "display_name")));
      }
    }
    return List.copyOf(models);
  }

  /** Always clears local tokens; false means remote revocation needs the user's attention. */
  public synchronized boolean signOut(String id) throws IOException {
    JsonObject data = data();
    JsonObject account = account(data, id);
    boolean revoked = !account.has("refresh_token");
    if (!revoked) {
      try {
        http.post(
            URI.create("https://auth.openai.com/api/accounts/oauth/revoke"),
            Map.of(
                "token",
                string(account, "refresh_token"),
                "token_type_hint",
                "refresh_token",
                "client_id",
                string(account, "client_id")));
        revoked = true;
      } catch (IOException ignored) {
        // Local sign-out must still finish when the remote service is unavailable.
      }
    }
    for (String key : List.of("access_token", "refresh_token", "id_token", "scope", "expires_at")) {
      account.remove(key);
    }
    if (id.equals(string(data, "selected"))) {
      data.remove("selected");
    }
    store.write(data.toString());
    return revoked;
  }

  private synchronized JsonObject data() throws IOException {
    try {
      JsonObject data = JsonParser.parseString(store.read()).getAsJsonObject();
      if (!data.has("accounts")) {
        data.add("accounts", new JsonObject());
      }
      return data;
    } catch (RuntimeException exception) {
      throw new IOException("The saved ChatGPT accounts could not be read");
    }
  }

  private String hostId() throws IOException {
    Path file = directory.resolve("chatgpt-host.txt");
    Files.createDirectories(directory);
    if (!Files.exists(file)) {
      try {
        Files.writeString(
            file,
            "urn:uuid:" + UUID.randomUUID(),
            StandardCharsets.US_ASCII,
            java.nio.file.StandardOpenOption.CREATE_NEW);
      } catch (java.nio.file.FileAlreadyExistsException ignored) {
        // Another Serox process created this installation's stable host identifier.
      }
    }
    String host = Files.readString(file, StandardCharsets.US_ASCII).strip();
    if (!host.matches("urn:uuid:[0-9a-f-]{36}")) {
      throw new IOException("Invalid ChatGPT host identifier");
    }
    return host;
  }

  static String issuedClient(Map<String, String> response, String existing) throws IOException {
    String issued = response.getOrDefault("client_id", existing);
    if (!issued.matches("oaiapp_[A-Za-z0-9_-]{1,200}")
        || !existing.isEmpty() && !existing.equals(issued)) {
      throw new IOException("Invalid client registration");
    }
    return issued;
  }

  private static void callback(
      HttpExchange exchange, String state, int port, CompletableFuture<Map<String, String>> result)
      throws IOException {
    int status = 400;
    Map<String, String> accepted = null;
    boolean denied = false;
    try {
      if (!"GET".equals(exchange.getRequestMethod())
          || !"/auth/callback".equals(exchange.getRequestURI().getRawPath())
          || !("127.0.0.1:" + port).equals(exchange.getRequestHeaders().getFirst("Host"))) {
        return;
      }
      Map<String, String> query = query(exchange.getRequestURI().getRawQuery());
      if (!MessageDigest.isEqual(
          state.getBytes(StandardCharsets.UTF_8),
          query.getOrDefault("state", "").getBytes(StandardCharsets.UTF_8))) {
        return;
      }
      if (query.containsKey("error")) {
        denied = true;
        status = 200;
      } else if (query.containsKey("code") && !query.get("code").isBlank()) {
        accepted = query;
        status = 200;
      }
    } catch (IllegalArgumentException ignored) {
      // Malformed or duplicate parameters cannot authenticate an attempt.
    } finally {
      byte[] page =
          (status == 200
                  ? "Return to Serox to check your connection."
                  : "Invalid sign-in callback.")
              .getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
      exchange.getResponseHeaders().set("Cache-Control", "no-store");
      exchange.getResponseHeaders().set("Referrer-Policy", "no-referrer");
      exchange.sendResponseHeaders(status, page.length);
      try (var output = exchange.getResponseBody()) {
        output.write(page);
      }
      exchange.close();
      // Finish the browser response before waking a thread that can stop this listener.
      if (denied) {
        result.completeExceptionally(new IOException("Sign-in was declined"));
      } else if (accepted != null) {
        result.complete(accepted);
      }
    }
  }

  static Map<String, String> query(String raw) {
    if (raw == null || raw.length() > 16 * 1024) {
      throw new IllegalArgumentException();
    }
    Map<String, String> result = new HashMap<>();
    for (String pair : raw.split("&")) {
      String[] parts = pair.split("=", 2);
      String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
      String value = parts.length == 2 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
      if (result.putIfAbsent(key, value) != null) {
        throw new IllegalArgumentException();
      }
    }
    return result;
  }

  private static void updateTokens(JsonObject account, JsonObject tokens, boolean initial)
      throws IOException {
    if (!"Bearer".equalsIgnoreCase(string(tokens, "token_type"))
        || string(tokens, "access_token").isEmpty()
        || initial && string(tokens, "refresh_token").isEmpty()) {
      throw new IOException("Incomplete authentication response");
    }
    for (String name : List.of("access_token", "refresh_token", "id_token", "scope")) {
      if (tokens.has(name)) {
        account.add(name, tokens.get(name));
      }
    }
    long lifetime = tokens.get("expires_in").getAsLong();
    if (lifetime <= 0 || lifetime > 30 * 24 * 3600) {
      throw new IOException("Invalid token expiration");
    }
    account.addProperty("expires_at", Instant.now().plusSeconds(lifetime).getEpochSecond());
  }

  private static boolean enabled(JsonObject account) {
    return account.has("access_token")
        && List.of(string(account, "scope").split(" ")).contains(PLAN_SCOPE);
  }

  private static Account view(String id, JsonObject account) {
    String label = string(account, "label");
    return new Account(
        id,
        label.substring(0, Math.min(label.length(), 200)) + " · " + id.substring(0, 8),
        enabled(account));
  }

  private static JsonObject account(JsonObject data, String id) throws IOException {
    JsonObject accounts = data.getAsJsonObject("accounts");
    if (!accounts.has(id)) {
      throw new IOException("ChatGPT account not found");
    }
    return accounts.getAsJsonObject(id);
  }

  private static String string(JsonObject object, String key) {
    return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
  }

  private static String random() {
    byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  @Override
  public synchronized void close() {
    closed = true;
    cancelSignIn();
  }
}
