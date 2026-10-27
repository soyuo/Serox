package kr.soyuo.serox.core.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;
import kr.soyuo.serox.core.model.PermissionChoice;
import kr.soyuo.serox.core.model.SitePermission;
import kr.soyuo.serox.storage.sqlite.SqliteStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SitePermissionServiceTest {
  @TempDir Path tempDir;

  @Test
  void scriptsAreAllowedByDefaultAndEverythingElseIsBlocked() {
    var service = new SitePermissionService(Optional.empty());

    assertTrue(service.allowsScripts(URI.create("https://example.test/")));
    for (SitePermission permission : SitePermission.values()) {
      if (permission != SitePermission.JAVASCRIPT) {
        assertEquals(PermissionChoice.BLOCK, service.choice("example.test", permission));
      }
    }
  }

  @Test
  void aChoiceAppliesToThatSiteOnlyAndIgnoresCase() {
    var service = new SitePermissionService(Optional.empty());

    service.set("Example.Test", SitePermission.JAVASCRIPT, PermissionChoice.BLOCK);

    assertFalse(service.allowsScripts(URI.create("https://example.test/a")));
    assertTrue(service.allowsScripts(URI.create("https://other.test/")));
  }

  @Test
  void resetBringsTheDefaultsBack() {
    var service = new SitePermissionService(Optional.empty());
    service.set("example.test", SitePermission.JAVASCRIPT, PermissionChoice.BLOCK);

    service.reset("example.test");

    assertTrue(service.allowsScripts(URI.create("https://example.test/")));
  }

  @Test
  void permissionsSeroxCannotGiveCannotBeChanged() {
    var service = new SitePermissionService(Optional.empty());

    assertThrows(
        IllegalArgumentException.class,
        () -> service.set("example.test", SitePermission.CAMERA, PermissionChoice.ALLOW));
    assertEquals(PermissionChoice.BLOCK, service.choice("example.test", SitePermission.CAMERA));
  }

  @Test
  void aSiteIsRequired() {
    var service = new SitePermissionService(Optional.empty());

    assertThrows(
        IllegalArgumentException.class,
        () -> service.set(" ", SitePermission.JAVASCRIPT, PermissionChoice.BLOCK));
  }

  @Test
  void choicesSurviveARestart() throws Exception {
    try (var storage = new SqliteStorage(tempDir.resolve("p.db"))) {
      var service = new SitePermissionService(Optional.of(storage.sitePermissions()));
      service.set("a.example.test", SitePermission.JAVASCRIPT, PermissionChoice.BLOCK);
      storage.sitePermissions().reset("gone.test").toCompletableFuture().get();
    }
    try (var reopened = new SqliteStorage(tempDir.resolve("p.db"))) {
      var service = new SitePermissionService(Optional.of(reopened.sitePermissions()));
      service.load();

      assertFalse(service.allowsScripts(URI.create("https://a.example.test/")));
      assertTrue(service.allowsScripts(URI.create("https://example.test/")));
    }
  }

  @Test
  void resetIsKeptAcrossARestartToo() throws Exception {
    try (var storage = new SqliteStorage(tempDir.resolve("r.db"))) {
      var service = new SitePermissionService(Optional.of(storage.sitePermissions()));
      service.set("x.test", SitePermission.JAVASCRIPT, PermissionChoice.BLOCK);
      storage.sitePermissions().loadAll().toCompletableFuture().get();
      service.reset("x.test");
      storage.sitePermissions().loadAll().toCompletableFuture().get();
    }
    try (var reopened = new SqliteStorage(tempDir.resolve("r.db"))) {
      var service = new SitePermissionService(Optional.of(reopened.sitePermissions()));
      service.load();

      assertTrue(service.allowsScripts(URI.create("https://x.test/")));
    }
  }
}
