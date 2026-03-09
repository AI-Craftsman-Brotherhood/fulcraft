package com.craftsmanbro.fulcraft.logging;

/**
 * Factory for creating {@link LoggerPort} instances.
 *
 * <p>Infrastructure code provides the concrete factory and registers it with {@link
 * LoggerPortProvider} during application bootstrap.
 */
public interface LoggerPortFactory {

  LoggerPort getLogger(Class<?> type);
}
