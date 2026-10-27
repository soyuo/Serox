package kr.soyuo.serox.storage.sqlite;

import java.net.URI;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import kr.soyuo.serox.core.model.NavigationStatus;
import kr.soyuo.serox.core.model.SessionSnapshot;
import kr.soyuo.serox.core.model.TabId;
import kr.soyuo.serox.core.model.TabSnapshot;
import kr.soyuo.serox.core.port.storage.SessionRepository;

/** SQLite-backed last-session repository. */
public final class SqliteSessionRepository implements SessionRepository {
  private final SqliteDatabase database;

  public SqliteSessionRepository(SqliteDatabase database) {
    this.database = Objects.requireNonNull(database, "database");
  }

  @Override
  public CompletionStage<Void> save(SessionSnapshot session) {
    Objects.requireNonNull(session, "session");
    return database.submit(
        connection -> {
          long now = System.currentTimeMillis();
          connection.setAutoCommit(false);
          try {
            try (PreparedStatement statement =
                connection.prepareStatement(
                    "INSERT INTO sessions(id, profile_id, created_at, updated_at) "
                        + "VALUES (?, 'default', ?, ?) "
                        + "ON CONFLICT(id) DO UPDATE SET updated_at=excluded.updated_at")) {
              statement.setString(1, session.id().toString());
              statement.setLong(2, now);
              statement.setLong(3, now);
              statement.executeUpdate();
            }
            try (PreparedStatement statement =
                connection.prepareStatement("DELETE FROM tabs WHERE session_id = ?")) {
              statement.setString(1, session.id().toString());
              statement.executeUpdate();
            }
            try (PreparedStatement statement =
                connection.prepareStatement(
                    "INSERT INTO tabs(id, session_id, url, position, active) "
                        + "VALUES (?, ?, ?, ?, ?)")) {
              for (int position = 0; position < session.tabs().size(); position++) {
                TabSnapshot tab = session.tabs().get(position);
                statement.setString(1, tab.id().value().toString());
                statement.setString(2, session.id().toString());
                if (tab.currentUri().isPresent()) {
                  statement.setString(3, tab.currentUri().orElseThrow().toString());
                } else {
                  statement.setNull(3, java.sql.Types.VARCHAR);
                }
                statement.setInt(4, position);
                statement.setInt(
                    5, session.activeTabId().filter(tab.id()::equals).isPresent() ? 1 : 0);
                statement.addBatch();
              }
              statement.executeBatch();
            }
            connection.commit();
          } catch (Exception exception) {
            connection.rollback();
            throw exception;
          } finally {
            connection.setAutoCommit(true);
          }
          return null;
        });
  }

  @Override
  public CompletionStage<Optional<SessionSnapshot>> latest() {
    return database.submit(
        connection -> {
          String sessionId;
          try (Statement statement = connection.createStatement();
              ResultSet result =
                  statement.executeQuery(
                      "SELECT id FROM sessions ORDER BY updated_at DESC LIMIT 1")) {
            if (!result.next()) {
              return Optional.empty();
            }
            sessionId = result.getString(1);
          }
          UUID id = UUID.fromString(sessionId);
          List<TabSnapshot> tabs = new ArrayList<>();
          Optional<TabId> activeTab = Optional.empty();
          try (PreparedStatement statement =
              connection.prepareStatement(
                  "SELECT id, url, active FROM tabs WHERE session_id = ? ORDER BY position")) {
            statement.setString(1, sessionId);
            try (ResultSet result = statement.executeQuery()) {
              while (result.next()) {
                TabId tabId = new TabId(UUID.fromString(result.getString("id")));
                String url = result.getString("url");
                Optional<URI> uri = url == null ? Optional.empty() : Optional.of(URI.create(url));
                tabs.add(new TabSnapshot(tabId, uri, "", NavigationStatus.IDLE, false, false));
                if (result.getInt("active") == 1) {
                  activeTab = Optional.of(tabId);
                }
              }
            }
          }
          return Optional.of(new SessionSnapshot(id, tabs, activeTab));
        });
  }
}
