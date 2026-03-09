package com.craftsmanbro.fulcraft.infrastructure.buildtool.impl;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.buildtool.model.BuildToolType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.apache.commons.lang3.StringUtils;

public final class BuildToolDetector {

  private static final String GRADLE = "gradle";

  private static final String MAVEN = "maven";

  private static final String MVN = "mvn";

  private static final String BUILD_GRADLE = "build.gradle";

  private static final String BUILD_GRADLE_KTS = "build.gradle.kts";

  private static final String POM_XML = "pom.xml";

  private BuildToolDetector() {}

  public static BuildToolType detect(final Path projectRoot, final Config config) {
    final BuildToolType configuredBuildTool = fromConfig(config);
    if (configuredBuildTool != BuildToolType.UNKNOWN) {
      return configuredBuildTool;
    }
    return fromFiles(projectRoot);
  }

  private static BuildToolType fromConfig(final Config config) {
    if (config == null || config.getProject() == null) {
      return BuildToolType.UNKNOWN;
    }
    final BuildToolType configuredBuildTool =
        fromConfiguredValue(config.getProject().getBuildTool());
    if (configuredBuildTool != BuildToolType.UNKNOWN) {
      return configuredBuildTool;
    }
    return fromConfiguredValue(config.getProject().getBuildCommand());
  }

  private static BuildToolType fromConfiguredValue(final String configuredValue) {
    if (StringUtils.isBlank(configuredValue)) {
      return BuildToolType.UNKNOWN;
    }
    final String normalizedValue = configuredValue.toLowerCase(Locale.ROOT);
    if (normalizedValue.contains(GRADLE)) {
      return BuildToolType.GRADLE;
    }
    if (normalizedValue.contains(MAVEN) || normalizedValue.contains(MVN)) {
      return BuildToolType.MAVEN;
    }
    return BuildToolType.UNKNOWN;
  }

  private static BuildToolType fromFiles(final Path projectRoot) {
    if (projectRoot == null) {
      return BuildToolType.UNKNOWN;
    }
    if (Files.exists(projectRoot.resolve(BUILD_GRADLE))
        || Files.exists(projectRoot.resolve(BUILD_GRADLE_KTS))) {
      return BuildToolType.GRADLE;
    }
    if (Files.exists(projectRoot.resolve(POM_XML))) {
      return BuildToolType.MAVEN;
    }
    return BuildToolType.UNKNOWN;
  }
}
