package kr.soyuo.serox.ai.auth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.Base64;

/** Strict RS256 OpenID Connect verification before accepting a browser sign-in. */
final class OpenAiIdentity {
  static JsonObject verify(
      String token, String clientId, String nonce, JsonObject jwks, Instant now)
      throws IOException {
    try {
      if (token.length() > 32 * 1024) {
        throw new IllegalArgumentException();
      }
      String[] pieces = token.split("\\.", -1);
      if (pieces.length != 3) {
        throw new IllegalArgumentException();
      }
      var decoder = Base64.getUrlDecoder();
      JsonObject header =
          JsonParser.parseString(new String(decoder.decode(pieces[0]), StandardCharsets.UTF_8))
              .getAsJsonObject();
      if (!"RS256".equals(header.get("alg").getAsString())) {
        throw new IllegalArgumentException();
      }
      String kid = header.get("kid").getAsString();
      JsonObject selected = null;
      for (var value : jwks.getAsJsonArray("keys")) {
        JsonObject key = value.getAsJsonObject();
        if (key.has("kid")
            && kid.equals(key.get("kid").getAsString())
            && "RSA".equals(key.get("kty").getAsString())
            && (!key.has("use") || "sig".equals(key.get("use").getAsString()))
            && (!key.has("alg") || "RS256".equals(key.get("alg").getAsString()))) {
          selected = key;
          break;
        }
      }
      if (selected == null) {
        throw new IllegalArgumentException();
      }
      var publicKey =
          KeyFactory.getInstance("RSA")
              .generatePublic(
                  new RSAPublicKeySpec(
                      new BigInteger(1, decoder.decode(selected.get("n").getAsString())),
                      new BigInteger(1, decoder.decode(selected.get("e").getAsString()))));
      Signature signature = Signature.getInstance("SHA256withRSA");
      signature.initVerify(publicKey);
      signature.update((pieces[0] + "." + pieces[1]).getBytes(StandardCharsets.US_ASCII));
      if (!signature.verify(decoder.decode(pieces[2]))) {
        throw new IllegalArgumentException();
      }
      JsonObject claims =
          JsonParser.parseString(new String(decoder.decode(pieces[1]), StandardCharsets.UTF_8))
              .getAsJsonObject();
      boolean audience =
          claims.get("aud").isJsonArray()
              ? claims.getAsJsonArray("aud").asList().stream()
                  .anyMatch(value -> clientId.equals(value.getAsString()))
              : clientId.equals(claims.get("aud").getAsString());
      if (!"https://auth.openai.com".equals(claims.get("iss").getAsString())
          || !audience
          || claims.get("exp").getAsLong() <= now.getEpochSecond()
          || claims.get("iat").getAsLong() > now.plusSeconds(60).getEpochSecond()
          || !nonce.equals(claims.get("nonce").getAsString())
          || claims.get("sub").getAsString().isBlank()
          || claims.has("azp") && !clientId.equals(claims.get("azp").getAsString())
          || claims.get("aud").isJsonArray()
              && claims.getAsJsonArray("aud").size() > 1
              && !claims.has("azp")) {
        throw new IllegalArgumentException();
      }
      return claims;
    } catch (Exception exception) {
      throw new IOException("The sign-in identity could not be verified");
    }
  }
}
