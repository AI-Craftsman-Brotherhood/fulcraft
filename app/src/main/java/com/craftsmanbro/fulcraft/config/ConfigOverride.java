package com.craftsmanbro.fulcraft.config;

/**
 * Functional interface for applying configuration overrides.
 *
 * <p>Implementations can modify the Config object after it has been loaded from the configuration
 * file. This is typically used to apply CLI options that should take precedence over file-based
 * configuration.
 */
@FunctionalInterface
public interface ConfigOverride {
  /**
   * Applies this override to the given configuration.
   *
   * @param config the configuration to modify
   */
  void apply(Config config);
}
