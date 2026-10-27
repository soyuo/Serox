package kr.soyuo.serox.core.port.storage;

import java.util.Map;
import java.util.concurrent.CompletionStage;
import kr.soyuo.serox.core.model.PermissionChoice;
import kr.soyuo.serox.core.model.SitePermission;

/** Persistence port for the permission choices made per site. */
public interface SitePermissionRepository {
  /** Every stored choice, by host. */
  CompletionStage<Map<String, Map<SitePermission, PermissionChoice>>> loadAll();

  CompletionStage<Void> save(String host, SitePermission permission, PermissionChoice choice);

  /** Forgets all choices for one host. */
  CompletionStage<Void> reset(String host);
}
