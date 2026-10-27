package kr.soyuo.serox.storage.sqlite;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Owns SQLite connections, schema migrations, and serialized storage I/O. */
public final class SqliteDatabase implements AutoCloseable {
  private static final int SCHEMA_VERSION = 1;
  private static final int DEFAULT_BUSY_TIMEOUT_MILLIS = 5_000;
  private static final Logger LOGGER = Logger.getLogger(SqliteDatabase.class.getName());
  private final Path file;
  private final String jdbcUrl;
  private final int busyTimeoutMillis;
  private final ExecutorService executor =
      Executors.newSingleThreadExecutor(
          Thread.ofPlatform().daemon().name("serox-storage-", 0).factory());
  private volatile boolean closed;

  public SqliteDatabase(Path file) {
    this(file, DEFAULT_BUSY_TIMEOUT_MILLIS);
  }

  SqliteDatabase(Path file, int busyTimeoutMillis) {
    this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
    this.busyTimeoutMillis = busyTimeoutMillis;
    jdbcUrl = "jdbc:sqlite:" + this.file;
    try {
      Path parent = this.file.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      try {
        migrate();
      } catch (NewerSchemaException exception) {
        throw exception;
      } catch (SQLException exception) {
        if (!Files.exists(this.file) || isLocked(exception)) {
          throw exception;
        }
        quarantineDamagedFile();
        migrate();
      }
    } catch (IOException | SQLException exception) {
      executor.shutdownNow();
      throw new StorageException("Could not initialize the local database.", exception);
    }
  }

  private static boolean isLocked(SQLException exception) {
    String message = String.valueOf(exception.getMessage());
    return message.contains("SQLITE_BUSY") || message.contains("SQLITE_LOCKED");
  }

  /** Moves an unreadable database aside so a fresh one can be created without losing evidence. */
  private void quarantineDamagedFile() throws IOException {
    String stamp = Long.toString(System.currentTimeMillis());
    Path damaged = file.resolveSibling(file.getFileName() + ".corrupt-" + stamp);
    LOGGER.log(Level.WARNING, "Local database is unreadable; starting a fresh one.");
    Files.move(file, damaged);
    for (String suffix : new String[] {"-wal", "-shm"}) {
      Files.deleteIfExists(file.resolveSibling(file.getFileName() + suffix));
    }
  }

  Path file() {
    return file;
  }

  <T> CompletionStage<T> submit(SqlWork<T> work) {
    Objects.requireNonNull(work, "work");
    if (closed) {
      return CompletableFuture.failedFuture(
          new IllegalStateException("SQLite database is closed."));
    }
    return CompletableFuture.supplyAsync(
        () -> {
          try (Connection connection = openConnection()) {
            return work.apply(connection);
          } catch (Exception exception) {
            if (exception instanceof StorageException storageException) {
              throw storageException;
            }
            throw new CompletionException(
                new StorageException("Local database operation failed.", exception));
          }
        },
        executor);
  }

  Connection openConnection() throws SQLException {
    Connection connection = DriverManager.getConnection(jdbcUrl);
    try (Statement statement = connection.createStatement()) {
      statement.execute("PRAGMA foreign_keys = ON");
      statement.execute("PRAGMA busy_timeout = " + busyTimeoutMillis);
      statement.execute("PRAGMA synchronous = NORMAL");
    } catch (SQLException exception) {
      connection.close();
      throw exception;
    }
    return connection;
  }

  private void migrate() throws SQLException, IOException {
    try (Connection connection = DriverManager.getConnection(jdbcUrl);
        Statement statement = connection.createStatement()) {
      statement.execute("PRAGMA journal_mode = WAL");
      statement.execute("PRAGMA foreign_keys = ON");
      statement.execute("PRAGMA busy_timeout = " + busyTimeoutMillis);
      int version;
      try (ResultSet result = statement.executeQuery("PRAGMA user_version")) {
        version = result.next() ? result.getInt(1) : 0;
      }
      if (version > SCHEMA_VERSION) {
        throw new NewerSchemaException();
      }
      for (int nextVersion = version + 1; nextVersion <= SCHEMA_VERSION; nextVersion++) {
        applyMigration(connection, nextVersion);
      }
    }
  }

  private void applyMigration(Connection connection, int version) throws SQLException, IOException {
    String resource =
        "/kr/soyuo/serox/storage/sqlite/migration/V" + version + "__initial_storage.sql";
    String sql;
    try (InputStream input = SqliteDatabase.class.getResourceAsStream(resource)) {
      if (input == null) {
        throw new IOException("Missing database migration resource: " + resource);
      }
      sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
    StringBuilder executableSql = new StringBuilder();
    sql.lines()
        .filter(line -> !line.stripLeading().startsWith("--"))
        .forEach(line -> executableSql.append(line).append('\n'));
    connection.setAutoCommit(false);
    try (Statement statement = connection.createStatement()) {
      for (String command : executableSql.toString().split(";")) {
        if (!command.isBlank()) {
          statement.execute(command);
        }
      }
      statement.execute("PRAGMA user_version = " + version);
      connection.commit();
    } catch (SQLException exception) {
      connection.rollback();
      throw exception;
    } finally {
      connection.setAutoCommit(true);
    }
  }

  @Override
  public void close() {
    if (!closed) {
      closed = true;
      executor.shutdown();
      try {
        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
          executor.shutdownNow();
        }
      } catch (InterruptedException exception) {
        executor.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }
  }

  private static final class NewerSchemaException extends SQLException {
    NewerSchemaException() {
      super("Database schema version is newer than this application.");
    }
  }

  @FunctionalInterface
  interface SqlWork<T> {
    T apply(Connection connection) throws Exception;
  }
}
