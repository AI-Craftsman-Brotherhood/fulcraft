package com.craftsmanbro.fulcraft.infrastructure.version.impl;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.json.impl.JsonMapperFactory;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.infrastructure.version.contract.VersionInfoResolverPort;
import com.craftsmanbro.fulcraft.infrastructure.version.model.VersionInfo;
import com.google.common.hash.Hashing;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Default implementation for loading {@link VersionInfo} from config and lockfile sources. */
public final class DefaultVersionInfoResolver implements VersionInfoResolverPort {

  private static final String VERSION_PROPERTIES = "/version.properties";

  private static final String UNKNOWN_VERSION = "unknown";

  private static final String LOG_MESSAGE_KEY = "infra.common.log.message";
  private static final String VERSION_PROPERTY_KEY = "version";
  private static final String ENV_VERSION_KEY = "FUL_VERSION";
  private static final String ARGUMENT_NULL_MESSAGE_KEY = "infra.common.error.argument_null";

  private static final String[] PREFERRED_LOCKFILE_PATHS = {
    "gradle.lockfile", "buildSrc/gradle.lockfile", ".gradle/caches/modules-2/modules-2.lock"
  };
  private static final String GRADLE_DEPENDENCY_LOCKS_DIR = "gradle/dependency-locks";
  private static final String GRADLE_LOCKFILE_GLOB = "*.lockfile";
  private static final String MAVEN_POM_LOCKFILE = "pom.xml.lock";

  @Override
  public VersionInfo fromContent(final String configContent) {
    return fromContent(configContent, null);
  }

  @Override
  public VersionInfo fromContent(final String configContent, final String lockfileContent) {
    final String configHash = computeHash(configContent != null ? configContent : "");
    final String lockHash = computeHashOrNull(lockfileContent);
    return new VersionInfo(resolveApplicationVersion(), configHash, lockHash);
  }

  @Override
  public VersionInfo fromProject(
      final Path projectRoot, final Path configPath, final boolean includeLockfile) {
    final Path resolvedProjectRoot = resolveProjectRoot(projectRoot);
    final Path resolvedConfigPath = resolveConfigPath(resolvedProjectRoot, configPath);
    final String configHash = computeHash(readFileContent(resolvedConfigPath));
    final String lockHash = resolveProjectLockfileHash(resolvedProjectRoot, includeLockfile);
    return new VersionInfo(resolveApplicationVersion(), configHash, lockHash);
  }

  @Override
  public VersionInfo fromConfig(
      final Config config, final Path projectRoot, final boolean includeLockfile) {
    final Path resolvedProjectRoot = resolveProjectRoot(projectRoot);
    final String configHash = computeHash(serializeConfig(config));
    final String lockHash = resolveConfigLockfileHash(resolvedProjectRoot, includeLockfile);
    return new VersionInfo(resolveApplicationVersion(), configHash, lockHash);
  }

  @Override
  public VersionInfo empty() {
    return VersionInfo.empty();
  }

  @Override
  public String resolveApplicationVersion() {
    return loadApplicationVersion();
  }

  private static String loadApplicationVersion() {
    final String versionFromProperties = readVersionFromPropertiesFile();
    if (versionFromProperties != null) {
      return versionFromProperties;
    }
    final String versionFromPackage = readVersionFromPackageMetadata();
    if (versionFromPackage != null) {
      return versionFromPackage;
    }
    final String versionFromEnvironment = trimToNull(System.getenv(ENV_VERSION_KEY));
    if (versionFromEnvironment != null) {
      return versionFromEnvironment;
    }
    return UNKNOWN_VERSION;
  }

  private static String readVersionFromPropertiesFile() {
    try (InputStream versionPropertiesStream =
        DefaultVersionInfoResolver.class.getResourceAsStream(VERSION_PROPERTIES)) {
      if (versionPropertiesStream == null) {
        return null;
      }
      final Properties versionProperties = new Properties();
      versionProperties.load(versionPropertiesStream);
      return trimToNull(versionProperties.getProperty(VERSION_PROPERTY_KEY));
    } catch (IOException e) {
      logDebug("Could not load version.properties: " + e.getMessage());
      return null;
    }
  }

  private static String readVersionFromPackageMetadata() {
    final Package packageMetadata = DefaultVersionInfoResolver.class.getPackage();
    return packageMetadata != null ? packageMetadata.getImplementationVersion() : null;
  }

  private static String readProjectLockfileContent(final Path projectRoot) {
    final String preferredLockfileContent = readPreferredLockfileContent(projectRoot);
    if (preferredLockfileContent != null) {
      return preferredLockfileContent;
    }
    final String gradleLockfileContent = readGradleDependencyLockfileContents(projectRoot);
    if (gradleLockfileContent != null) {
      return gradleLockfileContent;
    }
    final String mavenLockfileContent = readMavenLockfileContent(projectRoot);
    if (mavenLockfileContent != null) {
      return mavenLockfileContent;
    }
    logDebug("No dependency lockfile found in project");
    return null;
  }

  private static String readPreferredLockfileContent(final Path projectRoot) {
    for (final String preferredLockfilePath : PREFERRED_LOCKFILE_PATHS) {
      final Path lockPath = projectRoot.resolve(preferredLockfilePath);
      if (!Files.isRegularFile(lockPath)) {
        continue;
      }
      final String content = readFileContent(lockPath);
      if (content != null && !content.isBlank()) {
        logDebug("Using lockfile: " + lockPath);
        return content;
      }
    }
    return null;
  }

  private static String readGradleDependencyLockfileContents(final Path projectRoot) {
    final Path dependencyLocksDirectory = projectRoot.resolve(GRADLE_DEPENDENCY_LOCKS_DIR);
    if (!Files.isDirectory(dependencyLocksDirectory)) {
      return null;
    }
    try (DirectoryStream<Path> dependencyLockfileStream =
        Files.newDirectoryStream(dependencyLocksDirectory, GRADLE_LOCKFILE_GLOB)) {
      final List<Path> dependencyLockfiles = new ArrayList<>();
      for (final Path dependencyLockfile : dependencyLockfileStream) {
        if (Files.isRegularFile(dependencyLockfile)) {
          dependencyLockfiles.add(dependencyLockfile);
        }
      }
      if (dependencyLockfiles.isEmpty()) {
        return null;
      }
      dependencyLockfiles.sort(Comparator.comparing(Path::toString));
      final String combinedLockfileContent = combineLockfileContents(dependencyLockfiles);
      if (combinedLockfileContent.isEmpty()) {
        return null;
      }

      logDebug("Using Gradle dependency lockfiles in: " + dependencyLocksDirectory);
      return combinedLockfileContent;
    } catch (IOException e) {
      logDebug("Could not read dependency lockfiles: " + e.getMessage());
    }

    return null;
  }

  private static String combineLockfileContents(final List<Path> lockfiles) {
    final StringBuilder combinedLockfileContent = new StringBuilder();
    for (final Path lockfile : lockfiles) {
      final String content = readFileContent(lockfile);
      if (content != null && !content.isBlank()) {
        // Prefix each section with source filename so hash input remains unambiguous.
        combinedLockfileContent.append("file:").append(lockfile.getFileName()).append("\n");
        combinedLockfileContent.append(content).append("\n");
      }
    }
    return combinedLockfileContent.toString();
  }

  private static String readMavenLockfileContent(final Path projectRoot) {
    final Path mavenLockfilePath = projectRoot.resolve(MAVEN_POM_LOCKFILE);
    if (!Files.isRegularFile(mavenLockfilePath)) {
      return null;
    }
    logDebug("Using Maven lockfile: " + mavenLockfilePath);
    return readFileContent(mavenLockfilePath);
  }

  private static String readFileContent(final Path path) {
    if (path == null || !Files.isRegularFile(path)) {
      return "";
    }
    try {
      return Files.readString(path, StandardCharsets.UTF_8);
    } catch (IOException e) {
      logDebug("Could not read file " + path + ": " + e.getMessage());
      return "";
    }
  }

  private static Path resolveProjectRoot(final Path projectRoot) {
    return projectRoot != null ? projectRoot : Path.of(".");
  }

  private static Path resolveConfigPath(final Path projectRoot, final Path configPath) {
    return configPath != null && !configPath.isAbsolute()
        ? projectRoot.resolve(configPath)
        : configPath;
  }

  private static String resolveProjectLockfileHash(
      final Path projectRoot, final boolean includeLockfile) {
    if (!includeLockfile) {
      return null;
    }
    final String lockfileContent = readProjectLockfileContent(projectRoot);
    return computeHashOrNull(lockfileContent);
  }

  private static String resolveConfigLockfileHash(
      final Path projectRoot, final boolean includeLockfile) {
    if (!includeLockfile) {
      return null;
    }
    final String lockfileContent = readProjectLockfileContent(projectRoot);
    // Keep existing behavior: config-based versioning ignores blank lockfile content.
    return lockfileContent != null && !lockfileContent.isBlank()
        ? computeHash(lockfileContent)
        : null;
  }

  private static String serializeConfig(final Config config) {
    if (config == null) {
      return "";
    }
    final ObjectMapper mapper = JsonMapperFactory.create();

    try {
      return mapper.writeValueAsString(config);
    } catch (JacksonException e) {
      logDebug("Could not serialize config for version hash: " + e.getMessage());
      return "";
    }
  }

  private static String computeHashOrNull(final String content) {
    if (content == null) {
      return null;
    }
    return computeHash(content);
  }

  private static String computeHash(final String content) {
    final String safeContent =
        Objects.requireNonNull(
            content,
            MessageSource.getMessage(
                ARGUMENT_NULL_MESSAGE_KEY, "content must not be null"));
    return Hashing.sha256()
        .hashString(safeContent, Objects.requireNonNull(StandardCharsets.UTF_8))
        .toString();
  }

  private static String trimToNull(final String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }

  private static void logDebug(final String message) {
    Logger.debug(MessageSource.getMessage(LOG_MESSAGE_KEY, message));
  }
}
