package kr.soyuo.serox.storage.sqlite;

/** Safe unchecked wrapper for local storage failures. */
public final class StorageException extends RuntimeException {
  public StorageException(String message, Throwable cause) {
    super(message, cause);
  }
}
