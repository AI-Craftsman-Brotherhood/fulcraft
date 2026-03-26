package com.craftsmanbro.fulcraft.config.plugin;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves plugin configuration paths for both the nested layout and the legacy fallback layout.
 */
public final class PluginConfigPaths {

  private static final String PLUGINS_DIR = ".ful/plugins";

  private static final String CONFIG_DIR = "config";

  private static final String CONFIG_FILE = "config.json";

  private static final String SCHEMA_FILE = "schema.json";

  private PluginConfigPaths() {
    // Utility class
  }

  public record ResolvedPaths(
      Path pluginDirectory,
      Path configPath,
      Path schemaPath,
      boolean configExists,
      boolean schemaExists) {}

  public static Path pluginsRoot(final Path projectRoot) {
    if (projectRoot == null) {
      return null;
    }
    return projectRoot.resolve(PLUGINS_DIR);
  }

  public static ResolvedPaths resolve(final Path projectRoot, final String pluginId) {
    if (projectRoot == null || pluginId == null || pluginId.isBlank()) {
      return null;
    }
    return resolve(pluginsRoot(projectRoot).resolve(pluginId));
  }

  public static ResolvedPaths resolve(final Path pluginDirectory) {
    if (pluginDirectory == null) {
      return null;
    }
    final Path configDirectory = pluginDirectory.resolve(CONFIG_DIR);
    final Path configPath =
        resolvePreferredPath(
            configDirectory.resolve(CONFIG_FILE), pluginDirectory.resolve(CONFIG_FILE));
    final Path schemaPath =
        resolvePreferredPath(
            configDirectory.resolve(SCHEMA_FILE), pluginDirectory.resolve(SCHEMA_FILE));
    return new ResolvedPaths(
        pluginDirectory,
        configPath,
        schemaPath,
        Files.isRegularFile(configPath),
        Files.isRegularFile(schemaPath));
  }

  private static Path resolvePreferredPath(final Path nestedPath, final Path legacyPath) {
    if (nestedPath != null && Files.isRegularFile(nestedPath)) {
      return nestedPath;
    }
    if (legacyPath != null && Files.isRegularFile(legacyPath)) {
      return legacyPath;
    }
    return nestedPath;
  }
}
