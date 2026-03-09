package com.craftsmanbro.fulcraft.config;

import java.nio.file.Path;

/** Port for loading configuration files. */
public interface ConfigLoaderPort {

  /**
   * Loads configuration from the specified path.
   *
   * @param configFile path to the configuration file, or null to use default/fallback resolution
   * @return loaded configuration
   * @throws InvalidConfigurationException if the file cannot be read or parsed, or is invalid
   */
  Config load(Path configFile) throws InvalidConfigurationException;

  /**
   * Loads configuration from the specified path, then applies overrides.
   *
   * @param configFile path to the configuration file, or null to use default/fallback resolution
   * @param overrides configuration overrides to apply after loading
   * @return loaded configuration with overrides applied
   * @throws InvalidConfigurationException if the file cannot be read or parsed, or is invalid
   */
  Config load(Path configFile, ConfigOverride... overrides) throws InvalidConfigurationException;

  /**
   * Applies overrides to an existing configuration in place.
   *
   * @param config configuration to modify
   * @param overrides overrides to apply
   */
  void applyOverrides(Config config, ConfigOverride... overrides);
}
