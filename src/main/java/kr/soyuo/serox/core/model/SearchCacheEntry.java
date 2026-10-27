package kr.soyuo.serox.core.model;

import java.time.Instant;
import java.util.Objects;

/** A provider response cached until its configured expiration time. */
public record SearchCacheEntry(
    String cacheKey, String response, Instant createdAt, Instant expiresAt) {
  public SearchCacheEntry {
    Objects.requireNonNull(cacheKey, "cacheKey");
    Objects.requireNonNull(response, "response");
    Objects.requireNonNull(createdAt, "createdAt");
    Objects.requireNonNull(expiresAt, "expiresAt");
    if (cacheKey.isBlank()) {
      throw new IllegalArgumentException("cacheKey must not be blank");
    }
    if (expiresAt.isBefore(createdAt)) {
      throw new IllegalArgumentException("expiresAt must not precede createdAt");
    }
  }
}
