package com.craftsmanbro.fulcraft.kernel.plugin.api;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;

/** Plugin-scoped configuration wrapper. */
public final class PluginConfig {

  private static final Config EMPTY_CONFIG = ConfigProviderResolver.instance().getBuilder().build();

  private final String pluginId;

  private final Path configPath;

  private final Path schemaPath;

  private final Config config;

  private final boolean configExists;

  public static PluginConfig empty() {
    return new PluginConfig(null, null, null, EMPTY_CONFIG, false);
  }

  public PluginConfig(
      final String pluginId,
      final Path configPath,
      final Path schemaPath,
      final Config config,
      final boolean configExists) {
    this.pluginId = pluginId;
    this.configPath = configPath;
    this.schemaPath = schemaPath;
    this.config =
        Objects.requireNonNull(
            config, MessageSource.getMessage("kernel.common.error.argument_null", "config"));
    this.configExists = configExists;
  }

  public Optional<String> getPluginId() {
    return Optional.ofNullable(pluginId);
  }

  public Path getConfigPath() {
    return configPath;
  }

  public Path getSchemaPath() {
    return schemaPath;
  }

  public boolean configExists() {
    return configExists;
  }

  public Config getConfig() {
    return config;
  }

  public Iterable<String> getPropertyNames() {
    return config.getPropertyNames();
  }

  public <T> Optional<T> getOptionalValue(final String key, final Class<T> type) {
    if (key == null || key.isBlank()) {
      return Optional.empty();
    }
    return config.getOptionalValue(key, type);
  }

  public Optional<String> getOptionalString(final String key) {
    return getOptionalValue(key, String.class);
  }
}
