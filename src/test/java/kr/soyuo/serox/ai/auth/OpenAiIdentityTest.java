package kr.soyuo.serox.ai.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OpenAiIdentityTest {
  private static final Instant NOW = Instant.parse("2026-10-27T00:00:00Z");
  private static KeyPair pair;
  private static JsonObject jwks;

  @BeforeAll
  static void keys() throws Exception {
    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    pair = generator.generateKeyPair();
    RSAPublicKey key = (RSAPublicKey) pair.getPublic();
    JsonObject jwk = new JsonObject();
    jwk.addProperty("kid", "test-key");
    jwk.addProperty("kty", "RSA");
    jwk.addProperty("use", "sig");
    jwk.addProperty("alg", "RS256");
    jwk.addProperty("n", encode(key.getModulus().toByteArray()));
    jwk.addProperty("e", encode(key.getPublicExponent().toByteArray()));
    JsonArray keys = new JsonArray();
    keys.add(jwk);
    jwks = new JsonObject();
    jwks.add("keys", keys);
  }

  private static JsonObject claims() {
    JsonObject claims = new JsonObject();
    claims.addProperty("iss", "https://auth.openai.com");
    claims.addProperty("aud", "oaiapp_test");
    claims.addProperty("sub", "account-one");
    claims.addProperty("nonce", "nonce-one");
    claims.addProperty("exp", NOW.plusSeconds(3600).getEpochSecond());
    claims.addProperty("iat", NOW.minusSeconds(10).getEpochSecond());
    return claims;
  }

  private static String token(JsonObject claims) throws Exception {
    String unsigned =
        encode("{\"alg\":\"RS256\",\"kid\":\"test-key\"}".getBytes(StandardCharsets.UTF_8))
            + "."
            + encode(claims.toString().getBytes(StandardCharsets.UTF_8));
    Signature signature = Signature.getInstance("SHA256withRSA");
    signature.initSign(pair.getPrivate());
    signature.update(unsigned.getBytes(StandardCharsets.US_ASCII));
    return unsigned + "." + encode(signature.sign());
  }

  private static String encode(byte[] value) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
  }

  @Test
  void acceptsOnlySignedIdentityBoundToClientAndAttempt() throws Exception {
    assertEquals(
        "account-one",
        OpenAiIdentity.verify(token(claims()), "oaiapp_test", "nonce-one", jwks, NOW)
            .get("sub")
            .getAsString());
    for (String name : new String[] {"iss", "aud", "nonce", "sub", "azp"}) {
      JsonObject changed = claims();
      changed.addProperty(name, name.equals("sub") ? "" : "attacker");
      String encoded = token(changed);
      assertThrows(
          IOException.class,
          () -> OpenAiIdentity.verify(encoded, "oaiapp_test", "nonce-one", jwks, NOW),
          name);
    }
    JsonObject expired = claims();
    expired.addProperty("exp", NOW.getEpochSecond());
    String expiredToken = token(expired);
    assertThrows(
        IOException.class,
        () -> OpenAiIdentity.verify(expiredToken, "oaiapp_test", "nonce-one", jwks, NOW));
    JsonObject future = claims();
    future.addProperty("iat", NOW.plusSeconds(120).getEpochSecond());
    String futureToken = token(future);
    assertThrows(
        IOException.class,
        () -> OpenAiIdentity.verify(futureToken, "oaiapp_test", "nonce-one", jwks, NOW));
  }

  @Test
  void rejectsTamperingUnknownKeysAndUnsignedTokens() throws Exception {
    String valid = token(claims());
    String[] parts = valid.split("\\.");
    JsonObject changed = claims();
    changed.addProperty("sub", "attacker");
    String tampered =
        parts[0]
            + "."
            + encode(changed.toString().getBytes(StandardCharsets.UTF_8))
            + "."
            + parts[2];
    assertThrows(
        IOException.class,
        () -> OpenAiIdentity.verify(tampered, "oaiapp_test", "nonce-one", jwks, NOW));
    JsonObject empty = new JsonObject();
    empty.add("keys", new JsonArray());
    assertThrows(
        IOException.class,
        () -> OpenAiIdentity.verify(valid, "oaiapp_test", "nonce-one", empty, NOW));
    String none =
        encode("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8)) + "." + parts[1] + ".";
    assertThrows(
        IOException.class,
        () -> OpenAiIdentity.verify(none, "oaiapp_test", "nonce-one", jwks, NOW));
  }
}
