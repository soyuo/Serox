package kr.soyuo.serox.ai.auth;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChatGptAccountsTest {
  @TempDir Path directory;

  @Test
  void browserAttemptUsesCodexClientPkceAndCallbackPort() throws Exception {
    try (var accounts = new ChatGptAccounts(directory)) {
      CompletableFuture<URI> opened = new CompletableFuture<>();
      var result = accounts.signIn("", opened::complete);
      URI url = opened.get(5, TimeUnit.SECONDS);
      var parameters = ChatGptAccounts.query(url.getRawQuery());
      assertEquals("https", url.getScheme());
      assertEquals("auth.openai.com", url.getHost());
      assertEquals("/oauth/authorize", url.getPath());
      assertEquals(ChatGptAccounts.CLIENT_ID, parameters.get("client_id"));
      assertEquals("true", parameters.get("codex_cli_simplified_flow"));
      assertEquals("codex_cli_rs", parameters.get("originator"));
      assertEquals("S256", parameters.get("code_challenge_method"));
      assertEquals(43, parameters.get("code_challenge").length());
      assertEquals("openid profile email offline_access", parameters.get("scope"));
      URI callback = URI.create(parameters.get("redirect_uri"));
      assertEquals("127.0.0.1", callback.getHost());
      assertEquals("/auth/callback", callback.getPath());
      assertTrue(callback.getPort() == 1455 || callback.getPort() == 1457);
      var client = HttpClient.newHttpClient();
      URI wrong = URI.create(callback + "?state=wrong&code=unused");
      assertEquals(
          400,
          client
              .send(
                  HttpRequest.newBuilder(wrong).GET().build(),
                  HttpResponse.BodyHandlers.discarding())
              .statusCode());
      assertFalse(result.isDone());
      URI duplicate =
          URI.create(callback + "?state=" + parameters.get("state") + "&state=other&code=unused");
      assertEquals(
          400,
          client
              .send(
                  HttpRequest.newBuilder(duplicate).GET().build(),
                  HttpResponse.BodyHandlers.discarding())
              .statusCode());
      accounts.cancelSignIn();
      assertTrue(result.isCancelled());
      CompletableFuture<URI> second = new CompletableFuture<>();
      var retried = accounts.signIn("", second::complete);
      var retry = ChatGptAccounts.query(second.get(5, TimeUnit.SECONDS).getRawQuery());
      assertNotEquals(parameters.get("state"), retry.get("state"));
      assertNotEquals(parameters.get("nonce"), retry.get("nonce"));
      URI denied =
          URI.create(
              retry.get("redirect_uri") + "?state=" + retry.get("state") + "&error=access_denied");
      client.send(
          HttpRequest.newBuilder(denied).GET().build(), HttpResponse.BodyHandlers.discarding());
      assertThrows(ExecutionException.class, () -> retried.get(5, TimeUnit.SECONDS));
      assertTrue(accounts.accounts().isEmpty());
    }
  }

  @Test
  void readsWorkspaceAndOnboardingState() throws Exception {
    var claims =
        com.google.gson.JsonParser.parseString(
                "{\"https://api.openai.com/auth\":{\"chatgpt_account_id\":\"ws-1\"}}")
            .getAsJsonObject();
    assertEquals("ws-1", ChatGptAccounts.chatGptAccountId(claims));
    assertEquals("", ChatGptAccounts.chatGptAccountId(new com.google.gson.JsonObject()));
    assertEquals(
        "abc", ChatGptAccounts.callbackState("abc.onboarding_entrypoint=life_sciences"));
    assertEquals("abc", ChatGptAccounts.callbackState("abc"));
    assertThrows(IllegalArgumentException.class, () -> ChatGptAccounts.query("state=a&state=b"));
  }
}
