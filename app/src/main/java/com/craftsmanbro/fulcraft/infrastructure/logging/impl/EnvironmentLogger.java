package com.craftsmanbro.fulcraft.infrastructure.logging.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Logs environment details at startup for diagnostics. */
public final class EnvironmentLogger {

  private static final AtomicBoolean LOGGED = new AtomicBoolean(false);

  private static final Pattern GRADLE_VERSION_PATTERN = Pattern.compile("gradle-([0-9.]+)");

  private static final Pattern TOOLCHAIN_PATTERN =
      Pattern.compile("JavaLanguageVersion\\.of\\(\\s*(\\d+)\\s*\\)");

  private static final Pattern JAVA_VERSION_PATTERN =
      Pattern.compile("JavaVersion\\.VERSION_(\\d+)(?:_(\\d+))?");

  private static final Pattern COMPATIBILITY_PATTERN =
      Pattern.compile(
          "(?:sourceCompatibility|targetCompatibility)\\s*=\\s*['\\\"]?(\\d+)(?:\\.(\\d+))?");

  private static final String UNKNOWN = "unknown";

  private EnvironmentLogger() {}

  public static void logStartupEnvironment() {
    if (!LOGGED.compareAndSet(false, true)) {
      return;
    }
    final String javaVersion = System.getProperty("java.version");
    final RootInfo rootInfo = detectProjectRoot();
    final String gradleVersion = detectGradleVersion(rootInfo.root()).orElse(UNKNOWN);
    final String toolchainVersion = detectToolchainVersion(rootInfo.root()).orElse(UNKNOWN);
    final String cwd = Path.of("").toAbsolutePath().toString();
    Logger.debug(
        "[Environment] java="
            + valueOrUnknown(javaVersion)
            + ", gradle="
            + gradleVersion
            + ", toolchain="
            + toolchainVersion
            + ", root="
            + rootInfo.root()
            + ", rootSource="
            + rootInfo.source()
            + ", cwd="
            + cwd);
  }

  private static Optional<String> detectGradleVersion(final Path root) {
    final Path wrapper = root.resolve("gradle/wrapper/gradle-wrapper.properties");
    if (!Files.exists(wrapper)) {
      return Optional.empty();
    }
    try {
      final String text = Files.readString(wrapper);
      final Matcher matcher = GRADLE_VERSION_PATTERN.matcher(text);
      if (matcher.find()) {
        return Optional.of(matcher.group(1));
      }
    } catch (IOException e) {
      Logger.debug(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message",
              "[Environment] Failed to read gradle-wrapper.properties: " + e.getMessage()));
    }
    return Optional.empty();
  }

  private static Optional<String> detectToolchainVersion(final Path root) {
    final Optional<String> fromApp = extractJavaVersion(root.resolve("app/build.gradle.kts"));
    if (fromApp.isPresent()) {
      return fromApp;
    }
    final Optional<String> fromRoot = extractJavaVersion(root.resolve("build.gradle.kts"));
    if (fromRoot.isPresent()) {
      return fromRoot;
    }
    final Optional<String> fromAppGroovy = extractJavaVersion(root.resolve("app/build.gradle"));
    if (fromAppGroovy.isPresent()) {
      return fromAppGroovy;
    }
    return extractJavaVersion(root.resolve("build.gradle"));
  }

  private static Optional<String> extractJavaVersion(final Path buildFile) {
    if (!Files.exists(buildFile)) {
      return Optional.empty();
    }
    try {
      final String text = Files.readString(buildFile);
      final Matcher matcher = TOOLCHAIN_PATTERN.matcher(text);
      if (matcher.find()) {
        return Optional.of(matcher.group(1));
      }
      final Matcher javaVersionMatcher = JAVA_VERSION_PATTERN.matcher(text);
      if (javaVersionMatcher.find()) {
        return Optional.of(
            normalizeJavaVersion(javaVersionMatcher.group(1), javaVersionMatcher.group(2)));
      }
      final Matcher compatibilityMatcher = COMPATIBILITY_PATTERN.matcher(text);
      if (compatibilityMatcher.find()) {
        return Optional.of(
            normalizeJavaVersion(compatibilityMatcher.group(1), compatibilityMatcher.group(2)));
      }
    } catch (IOException e) {
      Logger.debug(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message",
              "[Environment] Failed to read " + buildFile + ": " + e.getMessage()));
    }
    return Optional.empty();
  }

  private static RootInfo detectProjectRoot() {
    final Optional<Path> fromCodeSource = findRootFromCodeSource();
    if (fromCodeSource.isPresent()) {
      return new RootInfo(fromCodeSource.get(), "codeSource");
    }
    final Optional<Path> fromWorkingDir = findRootFromWorkingDir();
    if (fromWorkingDir.isPresent()) {
      return new RootInfo(fromWorkingDir.get(), "cwd");
    }
    return new RootInfo(Path.of("").toAbsolutePath(), "cwd");
  }

  private static Optional<Path> findRootFromCodeSource() {
    try {
      final var codeSource = EnvironmentLogger.class.getProtectionDomain().getCodeSource();
      if (codeSource == null || codeSource.getLocation() == null) {
        return Optional.empty();
      }
      final Path location = Path.of(codeSource.getLocation().toURI());
      final Path start = Files.isRegularFile(location) ? location.getParent() : location;
      return findGradleRoot(start);
    } catch (Exception e) {
      Logger.debug(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message",
              "[Environment] Failed to resolve code source: " + e.getMessage()));
      return Optional.empty();
    }
  }

  private static Optional<Path> findRootFromWorkingDir() {
    final Path current = Path.of("").toAbsolutePath();
    return findGradleRoot(current);
  }

  private static Optional<Path> findGradleRoot(final Path start) {
    Path cursor = start;
    while (cursor != null) {
      if (Files.exists(cursor.resolve("settings.gradle.kts"))
          || Files.exists(cursor.resolve("settings.gradle"))) {
        return Optional.of(cursor);
      }
      cursor = cursor.getParent();
    }
    return Optional.empty();
  }

  private static String normalizeJavaVersion(final String major, final String minor) {
    if ("1".equals(major) && minor != null && !minor.isBlank()) {
      return minor;
    }
    return major;
  }

  private static String valueOrUnknown(final String value) {
    return value == null || value.isBlank() ? UNKNOWN : value;
  }

  private record RootInfo(Path root, String source) {}
}
