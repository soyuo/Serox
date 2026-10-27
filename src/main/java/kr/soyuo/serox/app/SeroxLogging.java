package kr.soyuo.serox.app;

import java.util.logging.Level;
import java.util.logging.Logger;

/** Logging bootstrap using the JDK logging implementation. */
public final class SeroxLogging {
  private static final String LOGGER_NAMESPACE = "kr.soyuo.serox";

  /** Prevents instantiation of this logging utility. */
  private SeroxLogging() {}

  /** Sets the application logger namespace to informational output. */
  public static void configure() {
    Logger root = Logger.getLogger(LOGGER_NAMESPACE);
    root.setLevel(Level.INFO);
    root.setUseParentHandlers(true);
  }

  public static Logger loggerFor(Class<?> owner) {
    return Logger.getLogger(owner.getName());
  }
}
