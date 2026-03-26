package com.craftsmanbro.fulcraft.ui.banner;

import com.craftsmanbro.fulcraft.Main;
import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.config.ConfigLoaderPort;
import com.craftsmanbro.fulcraft.config.ConfigPathResolver;
import com.craftsmanbro.fulcraft.infrastructure.config.impl.ConfigLoaderImpl;
import com.craftsmanbro.fulcraft.infrastructure.version.contract.VersionInfoResolverPort;
import com.craftsmanbro.fulcraft.infrastructure.version.impl.DefaultVersionInfoResolver;
import java.nio.file.Path;
import java.util.Objects;
import picocli.CommandLine.Command;

/** Resolves startup banner metadata (application and config-derived values). */
public final class StartupBannerSupport {

  private static final String DEFAULT_APP_NAME = "ful";

  private static final String UNKNOWN_VERSION = "unknown";

  private static final String UNKNOWN_MODEL = "unknown";

  private static final VersionInfoResolverPort VERSION_INFO_RESOLVER =
      new DefaultVersionInfoResolver();

  private StartupBannerSupport() {}

  public static String resolveApplicationName() {
    final Command command = Main.class.getAnnotation(Command.class);
    if (command == null || command.name() == null || command.name().isBlank()) {
      return DEFAULT_APP_NAME;
    }
    return command.name().trim();
  }

  public static String resolveApplicationName(final Path projectRoot) {
    return resolveApplicationName(projectRoot, new ConfigLoaderImpl());
  }

  public static String resolveApplicationName(
      final Path projectRoot, final ConfigLoaderPort configLoader) {
    final Config config = loadConfig(projectRoot, configLoader);
    if (config != null) {
      final String configured = config.getAppName();
      if (configured != null && !configured.isBlank()) {
        return configured.trim();
      }
    }
    return resolveApplicationName();
  }

  public static String resolveApplicationVersion() {
    final String version = VERSION_INFO_RESOLVER.resolveApplicationVersion();
    if (version == null || version.isBlank()) {
      return UNKNOWN_VERSION;
    }
    return version.trim();
  }

  public static String resolveApplicationVersion(final Path projectRoot) {
    return resolveApplicationVersion(projectRoot, new ConfigLoaderImpl());
  }

  public static String resolveApplicationVersion(
      final Path projectRoot, final ConfigLoaderPort configLoader) {
    final Config config = loadConfig(projectRoot, configLoader);
    if (config != null) {
      final String configured = config.getVersion();
      if (configured != null && !configured.isBlank()) {
        return configured.trim();
      }
    }
    return resolveApplicationVersion();
  }

  public static String resolveModelName(final Path projectRoot) {
    return resolveModelName(projectRoot, new ConfigLoaderImpl());
  }

  public static String resolveModelName(
      final Path projectRoot, final ConfigLoaderPort configLoader) {
    final Config config = loadConfig(projectRoot, configLoader);
    if (config == null || config.getLlm() == null) {
      return UNKNOWN_MODEL;
    }
    final String model = config.getLlm().getModelName();
    if (model == null || model.isBlank()) {
      return UNKNOWN_MODEL;
    }
    return model.trim();
  }

  public static String formatDirectory(final Path root) {
    return formatPath(root);
  }

  public static String formatPath(final Path path) {
    final Path absoluteRoot = (path != null ? path : Path.of(".")).toAbsolutePath().normalize();
    final String renderedPath = absoluteRoot.toString().replace('\\', '/');
    final String home = System.getProperty("user.home");
    if (home != null && !home.isBlank()) {
      final Path homePath = Path.of(home).toAbsolutePath().normalize();
      if (absoluteRoot.equals(homePath)) {
        return "~";
      }
      if (absoluteRoot.startsWith(homePath)) {
        final String relative = homePath.relativize(absoluteRoot).toString().replace('\\', '/');
        return "~/" + relative;
      }
    }
    return renderedPath;
  }

  private static Config loadConfig(final Path projectRoot, final ConfigLoaderPort configLoader) {
    Objects.requireNonNull(configLoader, "configLoader must not be null");
    final Path configPath = ConfigPathResolver.resolveFromProjectRoot(projectRoot);
    if (configPath == null) {
      return Config.createDefault();
    }
    try {
      return configLoader.load(configPath);
    } catch (RuntimeException e) {
      return Config.createDefault();
    }
  }
}
