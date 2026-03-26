package com.craftsmanbro.fulcraft.ui.cli.command.support;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.config.ConfigLoaderPort;
import com.craftsmanbro.fulcraft.config.ConfigOverride;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Support for loading Config with CLI-derived overrides. */
public final class CliConfigSupport {

  private CliConfigSupport() {}

  public static Config loadConfig(
      final ConfigLoaderPort configLoader,
      final Path resolvedConfigPath,
      final List<ConfigOverride> overrides) {
    Objects.requireNonNull(configLoader, "configLoader must not be null");
    final ConfigOverride[] overrideArray =
        overrides == null ? new ConfigOverride[0] : overrides.toArray(ConfigOverride[]::new);
    return configLoader.load(resolvedConfigPath, overrideArray);
  }
}
