package kr.soyuo.serox.ai.auth;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChatGptAccountsTest {
  @TempDir Path directory;

  @Test
  void browserAttemptUsesOwnRegistrationPkceAndStableHost() throws Exception {
    try (var accounts = new ChatGptAccounts(directory)) {
      CompletableFuture<URI> opened = new CompletableFuture<>();
      var result = accounts.signIn("", opened::complete);
      URI url = opened.get(5, TimeUnit.SECONDS);
      var parameters = ChatGptAccounts.query(url.getRawQuery());
      assertEquals("https", url.getScheme());
      assertEquals("auth.openai.com", url.getHost());
      assertEquals("dynamic_agent_client", parameters.get("client_id"));
      assertEquals("Serox", parameters.get("agent_name_hint"));
      assertEquals("S256", parameters.get("code_challenge_method"));
      assertEquals(43, parameters.get("code_challenge").length());
      assertTrue(parameters.get("scope").contains("chatgpt.tokens.use.direct"));
      URI callback = URI.create(parameters.get("redirect_uri"));
      assertEquals("127.0.0.1", callback.getHost());
      assertEquals("/auth/callback", callback.getPath());
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
      assertEquals(parameters.get("ext_agent_host_id"), retry.get("ext_agent_host_id"));
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
  void rejectsMissingChangedOrBorrowedClientRegistration() throws Exception {
    assertEquals("oaiapp_new", ChatGptAccounts.issuedClient(Map.of("client_id", "oaiapp_new"), ""));
    assertEquals("oaiapp_old", ChatGptAccounts.issuedClient(Map.of(), "oaiapp_old"));
    assertThrows(java.io.IOException.class, () -> ChatGptAccounts.issuedClient(Map.of(), ""));
    assertThrows(
        java.io.IOException.class,
        () -> ChatGptAccounts.issuedClient(Map.of("client_id", "dynamic_agent_client"), ""));
    assertThrows(
        java.io.IOException.class,
        () -> ChatGptAccounts.issuedClient(Map.of("client_id", "oaiapp_changed"), "oaiapp_old"));
    assertThrows(IllegalArgumentException.class, () -> ChatGptAccounts.query("state=a&state=b"));
  }
}
