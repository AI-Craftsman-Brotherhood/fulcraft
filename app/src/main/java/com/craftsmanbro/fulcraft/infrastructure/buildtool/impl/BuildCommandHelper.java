package com.craftsmanbro.fulcraft.infrastructure.buildtool.impl;

import com.craftsmanbro.fulcraft.config.Config;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.apache.commons.lang3.StringUtils;

/** Helper class for resolving build commands and related configuration. */
public final class BuildCommandHelper {

  private static final String GRADLE = "gradle";

  private static final String MAVEN = "maven";

  private static final String MVN = "mvn";

  private static final String TEST_PLACEHOLDER = "{test}";

  private static final String POM_XML = "pom.xml";

  private static final String BUILD_GRADLE = "build.gradle";

  private static final String BUILD_GRADLE_KTS = "build.gradle.kts";

  private static final String GRADLEW = "gradlew";

  private static final String MVNW = "mvnw";

  // Commands
  private static final String MVN_TEST_CMD = "mvn -q test";

  private static final String GRADLE_TEST_CMD = "./gradlew test";

  // Paths
  private static final String GRADLE_REPORT_PATH = "build/test-results/test";

  private static final String MAVEN_SUREFIRE_PATH = "target/surefire-reports";

  private static final String MAVEN_FAILSAFE_PATH = "target/failsafe-reports";

  private static final String GRADLE_LOGS_PATH = "build/logs";

  private static final String MAVEN_LOGS_PATH = "target/logs";

  private static final String DEFAULT_LOGS_PATH = "logs";

  private BuildCommandHelper() {
    // Utility class
  }

  /** Resolves the build command to use for running tests. */
  public static String resolveBuildCommand(final Config config) {
    return resolveBuildCommand(config, null);
  }

  /**
   * Resolves the build command to use for running tests with a project root hint.
   */
  public static String resolveBuildCommand(final Config config, final Path projectRoot) {
    final String configuredBuildCommand = getConfiguredBuildCommand(config);
    if (StringUtils.isNotEmpty(configuredBuildCommand)) {
      return configuredBuildCommand;
    }
    return getDefaultBuildCommand(config, projectRoot);
  }

  /** Resolves the command to run a specific test in isolation. */
  public static String resolveIsolatedCommand(
      final Config config, final String packageName, final String testClassName) {
    return resolveIsolatedCommand(config, null, packageName, testClassName);
  }

  /**
   * Resolves the command to run a specific test in isolation with a project root
   * hint.
   */
  public static String resolveIsolatedCommand(
      final Config config,
      final Path projectRoot,
      final String packageName,
      final String testClassName) {
    final String configuredBuildCommand = getConfiguredBuildCommand(config);
    final String buildToolName = getBuildToolLower(config, projectRoot);
    final String testSelector = resolveTestSelector(buildToolName, packageName, testClassName, null);
    if (StringUtils.isNotEmpty(configuredBuildCommand)) {
      return resolveConfiguredBuildCommand(configuredBuildCommand, testSelector);
    }
    return buildSingleTestCommand(buildToolName, testSelector);
  }

  /** Resolves the command to run a specific test class/method in the project. */
  public static String resolveSingleTestCommand(
      final Config config, final String testClassName, final String testMethodName) {
    return resolveSingleTestCommand(config, null, testClassName, testMethodName);
  }

  /** Resolves the command to run a specific test class/method in the project. */
  public static String resolveSingleTestCommand(
      final Config config,
      final Path projectRoot,
      final String testClassName,
      final String testMethodName) {
    final String configuredBuildCommand = getConfiguredBuildCommand(config);
    final String buildToolName = getBuildToolLower(config, projectRoot);
    final String testSelector = resolveTestSelector(buildToolName, null, testClassName, testMethodName);
    if (StringUtils.isNotEmpty(configuredBuildCommand)) {
      return resolveConfiguredBuildCommand(configuredBuildCommand, testSelector);
    }
    return buildSingleTestCommand(buildToolName, testSelector);
  }

  /** Builds a shell command appropriate for the current OS. */
  public static List<String> buildShellCommand(final String command) {
    return buildShellCommand(command, System.getProperty("os.name"));
  }

  /**
   * Builds a shell command appropriate for the specified OS. Visible for testing.
   */
  static List<String> buildShellCommand(final String command, final String osName) {
    if (osName != null && osName.toLowerCase(Locale.ROOT).contains("win")) {
      return List.of("cmd", "/c", command);
    }
    return List.of("sh", "-c", command);
  }

  /** Gets the paths where test reports are located based on the build tool. */
  public static List<Path> getReportSources(final Config config, final Path projectRoot) {
    final var buildTool = getBuildToolLower(config, projectRoot);
    if (buildTool.contains(GRADLE)) {
      return List.of(projectRoot.resolve(GRADLE_REPORT_PATH));
    }
    if (buildTool.contains(MAVEN) || buildTool.contains(MVN)) {
      return List.of(
          projectRoot.resolve(MAVEN_SUREFIRE_PATH), projectRoot.resolve(MAVEN_FAILSAFE_PATH));
    }
    return List.of();
  }

  /** Gets the default logs root directory based on the build tool. */
  public static String getDefaultLogsRoot(final Config config) {
    return getDefaultLogsRoot(config, null);
  }

  /**
   * Gets the default logs root directory based on the build tool with a project
   * root hint.
   */
  public static String getDefaultLogsRoot(final Config config, final Path projectRoot) {
    final String buildToolName = getBuildToolLower(config, projectRoot);
    if (buildToolName.contains(GRADLE)) {
      return GRADLE_LOGS_PATH;
    }
    if (buildToolName.contains(MAVEN) || buildToolName.contains(MVN)) {
      return MAVEN_LOGS_PATH;
    }
    return DEFAULT_LOGS_PATH;
  }

  private static String getDefaultBuildCommand(final Config config, final Path projectRoot) {
    final var buildTool = getBuildToolLower(config, projectRoot);
    if (buildTool.contains(GRADLE)) {
      return GRADLE_TEST_CMD;
    }
    return MVN_TEST_CMD;
  }

  private static String getConfiguredBuildCommand(final Config config) {
    return config != null && config.getProject() != null
        ? config.getProject().getBuildCommand()
        : null;
  }

  private static String getBuildToolLower(final Config config, final Path projectRoot) {
    final String buildTool = config != null && config.getProject() != null ? config.getProject().getBuildTool() : null;
    if (buildTool != null && StringUtils.isNotBlank(buildTool)) {
      return buildTool.toLowerCase(Locale.ROOT);
    }
    final String configuredBuildCommand = getConfiguredBuildCommand(config);
    if (StringUtils.isNotBlank(configuredBuildCommand)) {
      final String normalizedBuildCommand = configuredBuildCommand.toLowerCase(Locale.ROOT);
      if (normalizedBuildCommand.contains(GRADLE) || normalizedBuildCommand.contains(GRADLEW)) {
        return GRADLE;
      }
      if (normalizedBuildCommand.contains(MVN) || normalizedBuildCommand.contains(MAVEN)) {
        return MAVEN;
      }
    }
    return detectBuildToolFromProjectRoot(projectRoot);
  }

  private static String detectBuildToolFromProjectRoot(final Path projectRoot) {
    if (projectRoot == null) {
      return "";
    }
    if (Files.exists(projectRoot.resolve(BUILD_GRADLE))
        || Files.exists(projectRoot.resolve(BUILD_GRADLE_KTS))
        || Files.exists(projectRoot.resolve(GRADLEW))) {
      return GRADLE;
    }
    if (Files.exists(projectRoot.resolve(POM_XML)) || Files.exists(projectRoot.resolve(MVNW))) {
      return MAVEN;
    }
    return "";
  }

  private static String resolveTestSelector(
      final String buildToolName,
      final String packageName,
      final String testClassName,
      final String testMethodName) {
    final String qualifiedTestClassName = StringUtils.isEmpty(packageName) ? testClassName
        : packageName + "." + testClassName;
    if (StringUtils.isBlank(testMethodName)) {
      return qualifiedTestClassName;
    }
    if (buildToolName.contains(GRADLE)) {
      return qualifiedTestClassName + "." + testMethodName;
    }
    return qualifiedTestClassName + "#" + testMethodName;
  }

  private static String resolveConfiguredBuildCommand(
      final String configuredBuildCommand, final String testSelector) {
    if (hasTestPlaceholder(configuredBuildCommand)) {
      return applyTestSelector(configuredBuildCommand, testSelector);
    }
    return configuredBuildCommand;
  }

  private static String buildSingleTestCommand(
      final String buildToolName, final String testSelector) {
    if (buildToolName.contains(GRADLE)) {
      return GRADLE_TEST_CMD + " --tests \"" + testSelector + "\"";
    }
    if (buildToolName.contains(MAVEN) || buildToolName.contains(MVN)) {
      return MVN_TEST_CMD + " -Dtest=" + testSelector;
    }
    // Unknown tools fall back to the Maven default used elsewhere in this helper.
    return MVN_TEST_CMD;
  }

  private static boolean hasTestPlaceholder(final String buildCommand) {
    return buildCommand.contains(TEST_PLACEHOLDER);
  }

  private static String applyTestSelector(final String buildCommand, final String testSelector) {
    return buildCommand.replace(TEST_PLACEHOLDER, testSelector);
  }
}
