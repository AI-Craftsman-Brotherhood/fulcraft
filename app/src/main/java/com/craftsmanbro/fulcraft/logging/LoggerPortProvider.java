package com.craftsmanbro.fulcraft.logging;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Process-wide entry point for application logging.
 *
 * <p>A concrete {@link LoggerPortFactory} is configured during bootstrap. {@link #getLogger}
 * returns a lightweight delegating logger so callers can keep static logger fields without taking a
 * direct dependency on the infrastructure implementation.
 */
public final class LoggerPortProvider {

  private static final AtomicReference<LoggerPortFactory> FACTORY = new AtomicReference<>();

  private LoggerPortProvider() {}

  /** Registers the factory used to create application loggers. */
  public static void setFactory(final LoggerPortFactory factory) {
    Objects.requireNonNull(factory, "factory must not be null");
    FACTORY.set(factory);
  }

  /**
   * Returns a delegating logger for the given type.
   *
   * <p>The returned logger resolves the configured factory when a logging method is invoked, so
   * logger fields can be created before bootstrap completes.
   */
  public static LoggerPort getLogger(final Class<?> type) {
    return new DelegatingLoggerPort(type);
  }

  private static final class DelegatingLoggerPort implements LoggerPort {

    private final Class<?> type;

    private DelegatingLoggerPort(final Class<?> type) {
      this.type = type;
    }

    @Override
    public void debug(final String message, final Object... args) {
      logger().debug(message, args);
    }

    @Override
    public void info(final String message, final Object... args) {
      logger().info(message, args);
    }

    @Override
    public void warn(final String message, final Object... args) {
      logger().warn(message, args);
    }

    @Override
    public void warn(final String message, final Throwable t) {
      logger().warn(message, t);
    }

    @Override
    public void error(final String message, final Object... args) {
      logger().error(message, args);
    }

    @Override
    public void error(final String message, final Throwable t) {
      logger().error(message, t);
    }

    private LoggerPort logger() {
      return factory().getLogger(type);
    }

    private LoggerPortFactory factory() {
      final LoggerPortFactory configuredFactory = FACTORY.get();
      if (configuredFactory == null) {
        throw new IllegalStateException(
            "LoggerPortFactory is not configured. "
                + "Call LoggerPortProvider.setFactory(...) before use.");
      }
      return configuredFactory;
    }
  }
}
