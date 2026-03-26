package com.craftsmanbro.fulcraft.config;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigPathResolver {

  private static final String FUL_DIRECTORY_NAME = ".ful";
  public static final String DEFAULT_CONFIG_FILE_NAME = "config.json";

  private static final Path DEFAULT_CONFIG_FILE = Path.of(DEFAULT_CONFIG_FILE_NAME);
  private static final Path FALLBACK_CONFIG_FILE =
      Path.of(FUL_DIRECTORY_NAME, DEFAULT_CONFIG_FILE_NAME);

  private ConfigPathResolver() {}

  public static Path resolve(final Path configuredPath) {
    final Path primary = configuredPath != null ? configuredPath : DEFAULT_CONFIG_FILE;
    if (Files.exists(primary)) {
      return primary;
    }
    if (Files.exists(FALLBACK_CONFIG_FILE)) {
      return FALLBACK_CONFIG_FILE;
    }
    return primary;
  }

  public static Path resolveFromProjectRoot(final Path projectRoot) {
    final Path root =
        (projectRoot != null ? projectRoot : Path.of(".")).toAbsolutePath().normalize();
    final Path primary = root.resolve(DEFAULT_CONFIG_FILE);
    if (Files.exists(primary)) {
      return primary;
    }
    final Path fallback = root.resolve(FUL_DIRECTORY_NAME).resolve(DEFAULT_CONFIG_FILE_NAME);
    if (Files.exists(fallback)) {
      return fallback;
    }
    return null;
  }
}
