package com.craftsmanbro.fulcraft.infrastructure.validation.impl;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.common.SourcePathResolver;
import com.craftsmanbro.fulcraft.infrastructure.validation.contract.PreFlightCheckPort;
import com.craftsmanbro.fulcraft.infrastructure.validation.model.PreFlightCheckInput;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Checks prerequisites before executing commands. Verifies project structure and dependencies. */
public class PreFlightChecker implements PreFlightCheckPort {
  private static final int MIN_SUPPORTED_JAVA_VERSION = 21;
  private static final String[] BUILD_DEFINITION_FILES = {
    "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts", "pom.xml"
  };
  private static final String LLM_HEALTH_CHECK_FAILED_MESSAGE =
      "LLM Client health check failed. Please verify your configuration (API Key, Ollama availability, etc).";

  private final SourcePathResolver sourcePathResolver;

  public PreFlightChecker() {
    this(new SourcePathResolver());
  }

  // Allow injection for testing.
  public PreFlightChecker(final SourcePathResolver sourcePathResolver) {
    this.sourcePathResolver = Objects.requireNonNull(sourcePathResolver);
  }

  @Override
  public void check(final PreFlightCheckInput input) {
    Objects.requireNonNull(
        input, MessageSource.getMessage("infra.common.error.argument_null", "input"));
    final Path projectRoot = input.normalizedProjectRoot();
    final Config config = input.config();
    final BooleanSupplier llmHealthCheck = input.llmHealthCheck();
    validateProjectRoot(projectRoot);
    validateBuildDefinition(projectRoot);
    validateSourceLayout(projectRoot, config);
    checkJavaVersion();
    if (llmHealthCheck != null) {
      checkLlmHealth(llmHealthCheck);
    }
  }

  private void validateProjectRoot(final Path projectRoot) {
    if (!Files.exists(projectRoot) || !Files.isDirectory(projectRoot)) {
      throw new IllegalStateException(
          MessageSource.getMessage(
              "infra.common.error.message",
              "Project root does not exist or is not a directory: " + projectRoot));
    }
  }

  private void validateBuildDefinition(final Path projectRoot) {
    if (!hasBuildDefinition(projectRoot)) {
      throw new IllegalStateException(
          "Project root ["
              + projectRoot
              + "] does not contain build definition (pom.xml, build.gradle, or settings.gradle). This tool requires a Maven or Gradle project.");
    }
  }

  private boolean hasBuildDefinition(final Path projectRoot) {
    return hasAnyFile(projectRoot, BUILD_DEFINITION_FILES);
  }

  private boolean hasAnyFile(final Path projectRoot, final String[] fileNames) {
    for (final String fileName : fileNames) {
      if (Files.exists(projectRoot.resolve(fileName))) {
        return true;
      }
    }
    return false;
  }

  // Align with Analyzer/SpoonAnalyzer source resolution behavior.
  private void validateSourceLayout(final Path projectRoot, final Config config) {
    final var sourceDirectories = sourcePathResolver.resolve(projectRoot, config);
    if (sourceDirectories.mainSource().isEmpty()) {
      throw new IllegalStateException(
          "Project root ["
              + projectRoot
              + "] does not contain a recognizable main source directory.\n"
              + "Tried: src/main/java, app/src/main/java, src (non-test), project root (top-level .java)");
    }
  }

  private void checkJavaVersion() {
    final int currentJavaVersion = Runtime.version().feature();
    if (currentJavaVersion < MIN_SUPPORTED_JAVA_VERSION) {
      throw new IllegalStateException(
          "Current Java version is "
              + currentJavaVersion
              + ". This tool requires Java "
              + MIN_SUPPORTED_JAVA_VERSION
              + " or higher.");
    }
  }

  private void checkLlmHealth(final BooleanSupplier llmHealthCheck) {
    final boolean isHealthy;
    try {
      isHealthy = llmHealthCheck.getAsBoolean();
    } catch (final RuntimeException e) {
      throw llmHealthCheckFailed(e);
    }
    if (!isHealthy) {
      throw llmHealthCheckFailed();
    }
  }

  private IllegalStateException llmHealthCheckFailed() {
    return new IllegalStateException(LLM_HEALTH_CHECK_FAILED_MESSAGE);
  }

  private IllegalStateException llmHealthCheckFailed(final RuntimeException cause) {
    return new IllegalStateException(LLM_HEALTH_CHECK_FAILED_MESSAGE, cause);
  }
}
