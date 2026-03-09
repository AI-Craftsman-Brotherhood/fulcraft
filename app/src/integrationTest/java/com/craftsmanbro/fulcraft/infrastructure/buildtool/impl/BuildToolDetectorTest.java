package com.craftsmanbro.fulcraft.infrastructure.buildtool.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.buildtool.model.BuildToolType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link BuildToolDetector}.
 *
 * <p>Verifies detection logic from both configuration and file system.
 */
class BuildToolDetectorTest {

  @TempDir Path tempDir;

  // --- Config-based detection tests ---

  @Test
  void detect_withNullConfig_returnsUnknown() {
    assertEquals(BuildToolType.UNKNOWN, BuildToolDetector.detect(tempDir, null));
  }

  @Test
  void detect_withNullProjectConfig_returnsUnknown() {
    Config config = new Config();
    assertEquals(BuildToolType.UNKNOWN, BuildToolDetector.detect(tempDir, config));
  }

  @Test
  void detect_withBlankBuildTool_returnsUnknown() {
    Config config = new Config();
    Config.ProjectConfig projectConfig = new Config.ProjectConfig();
    projectConfig.setBuildTool("  ");
    config.setProject(projectConfig);

    assertEquals(BuildToolType.UNKNOWN, BuildToolDetector.detect(tempDir, config));
  }

  @Test
  void detect_withGradleBuildToolConfig_returnsGradle() {
    Config config = new Config();
    Config.ProjectConfig projectConfig = new Config.ProjectConfig();
    projectConfig.setBuildTool("gradle");
    config.setProject(projectConfig);

    assertEquals(BuildToolType.GRADLE, BuildToolDetector.detect(tempDir, config));
  }

  @Test
  void detect_withGradleBuildToolConfigUpperCase_returnsGradle() {
    Config config = new Config();
    Config.ProjectConfig projectConfig = new Config.ProjectConfig();
    projectConfig.setBuildTool("GRADLE");
    config.setProject(projectConfig);

    assertEquals(BuildToolType.GRADLE, BuildToolDetector.detect(tempDir, config));
  }

  @Test
  void detect_withBuildToolContainingGradleSubstring_returnsGradle() {
    Config config = new Config();
    Config.ProjectConfig projectConfig = new Config.ProjectConfig();
    projectConfig.setBuildTool("use-gradle-wrapper");
    config.setProject(projectConfig);

    assertEquals(BuildToolType.GRADLE, BuildToolDetector.detect(tempDir, config));
  }

  @Test
  void detect_withGradleBuildCommandConfig_returnsGradle() {
    Config config = new Config();
    Config.ProjectConfig projectConfig = new Config.ProjectConfig();
    projectConfig.setBuildCommand("./gradlew clean test");
    config.setProject(projectConfig);

    assertEquals(BuildToolType.GRADLE, BuildToolDetector.detect(tempDir, config));
  }

  @Test
  void detect_withMavenBuildToolConfig_returnsMaven() {
    Config config = new Config();
    Config.ProjectConfig projectConfig = new Config.ProjectConfig();
    projectConfig.setBuildTool("maven");
    config.setProject(projectConfig);

    assertEquals(BuildToolType.MAVEN, BuildToolDetector.detect(tempDir, config));
  }

  @Test
  void detect_withMvnBuildToolConfig_returnsMaven() {
    Config config = new Config();
    Config.ProjectConfig projectConfig = new Config.ProjectConfig();
    projectConfig.setBuildTool("mvn");
    config.setProject(projectConfig);

    assertEquals(BuildToolType.MAVEN, BuildToolDetector.detect(tempDir, config));
  }

  @Test
  void detect_withBuildToolContainingMvnSubstring_returnsMaven() {
    Config config = new Config();
    Config.ProjectConfig projectConfig = new Config.ProjectConfig();
    projectConfig.setBuildTool("apache-mvn-wrapper");
    config.setProject(projectConfig);

    assertEquals(BuildToolType.MAVEN, BuildToolDetector.detect(tempDir, config));
  }

  @Test
  void detect_withMavenBuildCommandConfig_returnsMaven() {
    Config config = new Config();
    Config.ProjectConfig projectConfig = new Config.ProjectConfig();
    projectConfig.setBuildCommand("mvn -q test");
    config.setProject(projectConfig);

    assertEquals(BuildToolType.MAVEN, BuildToolDetector.detect(tempDir, config));
  }

  @Test
  void detect_withUnknownBuildToolConfig_fallsBackToFiles() throws IOException {
    Config config = new Config();
    Config.ProjectConfig projectConfig = new Config.ProjectConfig();
    projectConfig.setBuildTool("ant");
    config.setProject(projectConfig);
    // Create build.gradle to test fallback
    Files.writeString(tempDir.resolve("build.gradle"), "");

    // Unknown tool from config returns UNKNOWN from fromConfig, then fromFiles
    // finds build.gradle
    assertEquals(BuildToolType.GRADLE, BuildToolDetector.detect(tempDir, config));
  }

  // --- File-based detection tests ---

  @Test
  void detect_withNullProjectRoot_returnsUnknown() {
    assertEquals(BuildToolType.UNKNOWN, BuildToolDetector.detect(null, null));
  }

  @Test
  void detect_withBuildGradle_returnsGradle() throws IOException {
    Files.writeString(tempDir.resolve("build.gradle"), "plugins {}");

    assertEquals(BuildToolType.GRADLE, BuildToolDetector.detect(tempDir, null));
  }

  @Test
  void detect_withBuildGradleKts_returnsGradle() throws IOException {
    Files.writeString(tempDir.resolve("build.gradle.kts"), "plugins {}");

    assertEquals(BuildToolType.GRADLE, BuildToolDetector.detect(tempDir, null));
  }

  @Test
  void detect_withPomXml_returnsMaven() throws IOException {
    Files.writeString(tempDir.resolve("pom.xml"), "<project></project>");

    assertEquals(BuildToolType.MAVEN, BuildToolDetector.detect(tempDir, null));
  }

  @Test
  void detect_withNoBuildFiles_returnsUnknown() {
    // Empty directory
    assertEquals(BuildToolType.UNKNOWN, BuildToolDetector.detect(tempDir, null));
  }

  @Test
  void detect_prefersGradleOverMaven() throws IOException {
    // Both build files exist
    Files.writeString(tempDir.resolve("build.gradle"), "plugins {}");
    Files.writeString(tempDir.resolve("pom.xml"), "<project></project>");

    // Gradle should be preferred (checked first)
    assertEquals(BuildToolType.GRADLE, BuildToolDetector.detect(tempDir, null));
  }

  @Test
  void detect_configTakesPrecedenceOverFiles() throws IOException {
    Config config = new Config();
    Config.ProjectConfig projectConfig = new Config.ProjectConfig();
    projectConfig.setBuildTool("maven");
    config.setProject(projectConfig);
    // Create build.gradle (Gradle), but config says maven
    Files.writeString(tempDir.resolve("build.gradle"), "");

    assertEquals(BuildToolType.MAVEN, BuildToolDetector.detect(tempDir, config));
  }
}
