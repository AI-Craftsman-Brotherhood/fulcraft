package com.craftsmanbro.fulcraft.logging;

/**
 * Application-level logging port.
 *
 * <p>Callers depend on this interface instead of a concrete logging framework so core code stays
 * decoupled from infrastructure-specific adapters.
 */
public interface LoggerPort {

  void debug(String message, Object... args);

  void info(String message, Object... args);

  void warn(String message, Object... args);

  void warn(String message, Throwable t);

  void error(String message, Object... args);

  void error(String message, Throwable t);
}
