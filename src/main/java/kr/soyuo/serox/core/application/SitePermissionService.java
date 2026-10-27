package kr.soyuo.serox.core.application;

import java.net.URI;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import kr.soyuo.serox.core.model.PermissionChoice;
import kr.soyuo.serox.core.model.SitePermission;
import kr.soyuo.serox.core.port.storage.SitePermissionRepository;

/**
 * Holds what each site may do. Choices are kept in memory for fast checks and written to storage
 * when there is some; a failed write is logged and the choice still holds for this run.
 */
public final class SitePermissionService {
  private static final Logger LOGGER = Logger.getLogger(SitePermissionService.class.getName());
  private static final int MAX_HOST_LENGTH = 253;

  private final Optional<SitePermissionRepository> repository;
  private final Map<String, Map<SitePermission, PermissionChoice>> choices =
      new ConcurrentHashMap<>();

  public SitePermissionService(Optional<SitePermissionRepository> repository) {
    this.repository = Objects.requireNonNull(repository, "repository");
  }

  /** Reads the stored choices once at start-up; a failure leaves the defaults. */
  public void load() {
    if (repository.isEmpty()) {
      return;
    }
    try {
      repository
          .orElseThrow()
          .loadAll()
          .toCompletableFuture()
          .get(3, TimeUnit.SECONDS)
          .forEach((host, byPermission) -> choices.put(host, new EnumMap<>(byPermission)));
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    } catch (Exception failure) {
      LOGGER.log(Level.WARNING, "Site permissions could not be read; using the defaults.");
    }
  }

  /** What the site may do now. Permissions that cannot be changed are always blocked. */
  public PermissionChoice choice(String host, SitePermission permission) {
    Objects.requireNonNull(permission, "permission");
    if (!permission.changeable()) {
      return PermissionChoice.BLOCK;
    }
    Map<SitePermission, PermissionChoice> byPermission = choices.get(normalize(host));
    PermissionChoice stored = byPermission == null ? null : byPermission.get(permission);
    return stored == null ? permission.defaultChoice() : stored;
  }

  /** Whether scripts may run on a page with this address. */
  public boolean allowsScripts(URI page) {
    return choice(page.getHost(), SitePermission.JAVASCRIPT) == PermissionChoice.ALLOW;
  }

  /** Changes one choice for a site; only changeable permissions accept a choice. */
  public synchronized void set(String host, SitePermission permission, PermissionChoice choice) {
    Objects.requireNonNull(permission, "permission");
    Objects.requireNonNull(choice, "choice");
    String key = normalize(host);
    if (key.isEmpty()) {
      throw new IllegalArgumentException("A site is needed");
    }
    if (!permission.changeable()) {
      throw new IllegalArgumentException(permission.label() + " cannot be changed");
    }
    Map<SitePermission, PermissionChoice> copy = new EnumMap<>(SitePermission.class);
    copy.putAll(choices.getOrDefault(key, Map.of()));
    copy.put(permission, choice);
    choices.put(key, copy);
    repository.ifPresent(
        store ->
            store
                .save(key, permission, choice)
                .exceptionally(
                    failure -> {
                      LOGGER.log(Level.WARNING, "A site permission could not be saved.");
                      return null;
                    }));
  }

  /** Forgets every choice made for a site, so its defaults apply again. */
  public synchronized void reset(String host) {
    String key = normalize(host);
    choices.remove(key);
    repository.ifPresent(
        store ->
            store
                .reset(key)
                .exceptionally(
                    failure -> {
                      LOGGER.log(Level.WARNING, "Site permissions could not be reset.");
                      return null;
                    }));
  }

  private static String normalize(String host) {
    if (host == null) {
      return "";
    }
    String value = host.strip().toLowerCase(Locale.ROOT);
    return value.length() > MAX_HOST_LENGTH ? value.substring(0, MAX_HOST_LENGTH) : value;
  }
}
