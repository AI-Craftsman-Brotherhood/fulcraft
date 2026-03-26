package com.craftsmanbro.fulcraft.infrastructure.buildtool.impl.classpath;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.buildtool.contract.ClasspathResolverPort;
import com.craftsmanbro.fulcraft.infrastructure.buildtool.impl.BuildToolDetector;
import com.craftsmanbro.fulcraft.infrastructure.buildtool.model.BuildToolType;
import com.craftsmanbro.fulcraft.infrastructure.buildtool.model.ClasspathResolutionResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ClasspathResolver {

  private static final ClasspathResolverPort PORT = ClasspathResolver::resolveCompileClasspath;

  private ClasspathResolver() {}

  private static final String TOOL_MAVEN = "maven";

  private static final String TOOL_GRADLE = "gradle";

  private static final String TOOL_UNKNOWN = "unknown";

  public static ClasspathResolverPort port() {
    return PORT;
  }

  public static ClasspathResolutionResult resolveCompileClasspath(
      final Path projectRoot, final Config config) {
    final BuildToolType detectedTool = BuildToolDetector.detect(projectRoot, config);
    // Unknown detections intentionally keep the existing Gradle-first fallback order.
    final boolean preferMaven = detectedTool == BuildToolType.MAVEN;
    final String primaryToolName = preferMaven ? TOOL_MAVEN : TOOL_GRADLE;
    final String fallbackToolName = preferMaven ? TOOL_GRADLE : TOOL_MAVEN;
    final List<ClasspathResolutionResult.Attempt> resolutionAttempts = new ArrayList<>();

    final ClasspathAttemptResult primaryAttemptResult =
        preferMaven
            ? MavenClasspathResolver.resolveCompileClasspathAttempt(projectRoot)
            : GradleClasspathResolver.resolveCompileClasspathAttempt(projectRoot);
    resolutionAttempts.add(primaryAttemptResult.toAttempt());
    if (primaryAttemptResult.success()) {
      return new ClasspathResolutionResult(
          primaryAttemptResult.safeEntries(), primaryToolName, resolutionAttempts);
    }

    final ClasspathAttemptResult fallbackAttemptResult =
        preferMaven
            ? GradleClasspathResolver.resolveCompileClasspathAttempt(projectRoot)
            : MavenClasspathResolver.resolveCompileClasspathAttempt(projectRoot);
    resolutionAttempts.add(fallbackAttemptResult.toAttempt());
    if (fallbackAttemptResult.success()) {
      return new ClasspathResolutionResult(
          fallbackAttemptResult.safeEntries(), fallbackToolName, resolutionAttempts);
    }

    final String reportedToolName =
        switch (detectedTool) {
          case MAVEN -> TOOL_MAVEN;
          case GRADLE -> TOOL_GRADLE;
          default -> TOOL_UNKNOWN;
        };
    return new ClasspathResolutionResult(List.of(), reportedToolName, resolutionAttempts);
  }
}
