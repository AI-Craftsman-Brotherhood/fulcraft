package com.craftsmanbro.fulcraft.infrastructure.buildtool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.buildtool.impl.BuildCommandHelper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link BuildCommandHelper}.
 *
 * <p>Verifies build command resolution, shell command building, and report path resolution.
 */
class BuildCommandHelperTest {

  @TempDir Path tempDir;

  // --- resolveBuildCommand tests ---

  @Test
  void resolveBuildCommand_withNullConfig_returnsMavenDefault() {
    String command = BuildCommandHelper.resolveBuildCommand(null);
    assertEquals("mvn -q test", command);
  }

  @Test
  void resolveBuildCommand_withExplicitCommand_returnsConfiguredCommand() {
    Config config = new Config();
    Config.ProjectConfig projectConfig = new Config.ProjectConfig();
    projectConfig.setBuildCommand("./gradlew clean test");
    config.setProject(projectConfig);

    String command = BuildCommandHelper.resolveBuildCommand(config);
    assertEquals("./gradlew clean test", command);
  }

  @Test
  void resolveBuildCommand_withGradleBuildTool_returnsGradleCommand() {
    Config config = new Config();
    Config.ProjectConfig projectConfig = new Config.ProjectConfig();
    projectConfig.setBuildTool("gradle");
    config.setProject(projectConfig);

    String command = BuildCommandHelper.resolveBuildCommand(config);
    assertEquals("./gradlew test", command);
  }

  @Test
  void resolveBuildCommand_withMavenBuildTool_returnsMavenCommand() {
    Config config = new Config();
    Config.ProjectConfig projectConfig = new Config.ProjectConfig();
    projectConfig.setBuildTool("maven");
    config.setProject(projectConfig);

    String command = BuildCommandHelper.resolveBuildCommand(config);
    assertEquals("mvn -q test", command);
  }

  // --- resolveIsolatedCommand tests ---

  @Test
  void resolveIsolatedCommand_withGradle_returnsGradleTestSelector() {
    Config config = new Config();
    Config.ProjectConfig projectConfig = new Config.ProjectConfig();
    projectConfig.setBuildTool("gradle");
    config.setProject(projectConfig);

    String command = BuildCommandHelper.resolveIsolatedCommand(config, "com.example", "MyTest");

    assertTrue(command.contains("./gradlew test"));
    assertTrue(command.contains("--tests"));
    assertTrue(command.contains("com.example.MyTest"));
  }

  @Test
  void resolveIsolatedCommand_withMaven_returnsMavenTestSelector() {
    Config config = new Config();
    Config.ProjectConfig projectConfig = new Config.ProjectConfig();
    projectConfig.setBuildTool("maven");
    config.setProject(projectConfig);

    String command = BuildCommandHelper.resolveIsolatedCommand(config, "com.example", "MyTest");

    assertTrue(command.contains("mvn"));
    assertTrue(command.contains("-Dtest="));
    assertTrue(command.contains("com.example.MyTest"));
  }

  @Test
  void resolveIsolatedCommand_withEmptyPackage_usesClassNameOnly() {
    Config config = new Config();
    Config.ProjectConfig projectConfig = new Config.ProjectConfig();
    projectConfig.setBuildTool("gradle");
    config.setProject(projectConfig);

    String command = BuildCommandHelper.resolveIsolatedCommand(config, "", "MyTest");

    assertTrue(command.contains("MyTest"));
  }

  @Test
  void resolveIsolatedCommand_withCustomCommandAndPlaceholder_appliesTestSelector() {
    Config config = new Config();
    Config.ProjectConfig projectConfig = new Config.ProjectConfig();
    projectConfig.setBuildCommand("./gradlew test --tests \"{test}\"");
    config.setProject(projectConfig);

    String command = BuildCommandHelper.resolveIsolatedCommand(config, "com.example", "MyTest");

    assertEquals("./gradlew test --tests \"com.example.MyTest\"", command);
  }

  @Test
  void resolveIsolatedCommand_withCustomCommandWithoutPlaceholder_returnsConfiguredCommand() {
    Config config = new Config();
    Config.ProjectConfig projectConfig = new Config.ProjectConfig();
    projectConfig.setBuildCommand("./gradlew test");
    config.setProject(projectConfig);

    String command = BuildCommandHelper.resolveIsolatedCommand(config, "com.example", "MyTest");

    assertEquals("./gradlew test", command);
  }

  @Test
  void resolveIsolatedCommand_withNoHints_fallsBackToMavenDefaultCommand() {
    Config config = new Config();
    config.setProject(new Config.ProjectConfig());

    String command =
        BuildCommandHelper.resolveIsolatedCommand(config, tempDir, "com.example", "MyTest");

    assertEquals("mvn -q test", command);
  }

  // --- resolveSingleTestCommand tests ---

  @Test
  void resolveSingleTestCommand_withGradleAndMethod_usesDotsForMethodSeparator() {
    Config config = new Config();
    Config.ProjectConfig projectConfig = new Config.ProjectConfig();
    projectConfig.setBuildTool("gradle");
    config.setProject(projectConfig);

    String command = BuildCommandHelper.resolveSingleTestCommand(config, "MyTest", "testMethod");

    assertTrue(command.contains("MyTest.testMethod"));
  }

  @Test
  void resolveSingleTestCommand_withMavenAndMethod_usesHashForMethodSeparator() {
    Config config = new Config();
    Config.ProjectConfig projectConfig = new Config.ProjectConfig();
    projectConfig.setBuildTool("maven");
    config.setProject(projectConfig);

    String command = BuildCommandHelper.resolveSingleTestCommand(config, "MyTest", "testMethod");

    assertTrue(command.contains("MyTest#testMethod"));
  }

  @Test
  void resolveSingleTestCommand_withNullMethod_usesClassOnly() {
    Config config = new Config();
    Config.ProjectConfig projectConfig = new Config.ProjectConfig();
    projectConfig.setBuildTool("gradle");
    config.setProject(projectConfig);

    String command = BuildCommandHelper.resolveSingleTestCommand(config, "MyTest", null);

    assertTrue(command.contains("MyTest"));
  }

  @Test
  void resolveSingleTestCommand_withPlaceholderAndGradleCommand_usesDotMethodSeparator() {
    Config config = new Config();
    Config.ProjectConfig projectConfig = new Config.ProjectConfig();
    projectConfig.setBuildCommand("./gradlew test --tests \"{test}\"");
    config.setProject(projectConfig);

    String command = BuildCommandHelper.resolveSingleTestCommand(config, "MyTest", "testMethod");

    assertEquals("./gradlew test --tests \"MyTest.testMethod\"", command);
  }

  @Test
  void resolveSingleTestCommand_withPlaceholderAndMavenCommand_usesHashMethodSeparator() {
    Config config = new Config();
    Config.ProjectConfig projectConfig = new Config.ProjectConfig();
    projectConfig.setBuildCommand("mvn -q test -Dtest={test}");
    config.setProject(projectConfig);

    String command = BuildCommandHelper.resolveSingleTestCommand(config, "MyTest", "testMethod");

    assertEquals("mvn -q test -Dtest=MyTest#testMethod", command);
  }

  @Test
  void resolveSingleTestCommand_withGradleProjectRootAndMethod_usesDotsForMethodSeparator()
      throws IOException {
    Files.writeString(tempDir.resolve("build.gradle"), "plugins {}");
    Config config = new Config();
    config.setProject(new Config.ProjectConfig());

    String command =
        BuildCommandHelper.resolveSingleTestCommand(config, tempDir, "MyTest", "testMethod");

    assertEquals("./gradlew test --tests \"MyTest.testMethod\"", command);
  }

  @Test
  void resolveSingleTestCommand_withMavenProjectRootAndMethod_usesHashMethodSeparator()
      throws IOException {
    Files.writeString(tempDir.resolve("pom.xml"), "<project/>");
    Config config = new Config();
    config.setProject(new Config.ProjectConfig());

    String command =
        BuildCommandHelper.resolveSingleTestCommand(config, tempDir, "MyTest", "testMethod");

    assertEquals("mvn -q test -Dtest=MyTest#testMethod", command);
  }

  // --- buildShellCommand tests ---

  @Test
  void buildShellCommand_onLinux_usesShC() {
    List<String> command = invokeBuildShellCommand("./gradlew test", "Linux");

    assertEquals(3, command.size());
    assertEquals("sh", command.get(0));
    assertEquals("-c", command.get(1));
    assertEquals("./gradlew test", command.get(2));
  }

  @Test
  void buildShellCommand_onMacOS_usesShC() {
    List<String> command = invokeBuildShellCommand("mvn test", "Mac OS X");

    assertEquals("sh", command.get(0));
    assertEquals("-c", command.get(1));
  }

  @Test
  void buildShellCommand_onWindows_usesCmdC() {
    List<String> command = invokeBuildShellCommand("gradlew.bat test", "Windows 10");

    assertEquals(3, command.size());
    assertEquals("cmd", command.get(0));
    assertEquals("/c", command.get(1));
    assertEquals("gradlew.bat test", command.get(2));
  }

  @Test
  void buildShellCommand_withNullOs_usesShC() {
    List<String> command = invokeBuildShellCommand("./gradlew test", null);

    assertEquals("sh", command.get(0));
  }

  // --- getReportSources tests ---

  @Test
  void getReportSources_withGradle_returnsGradlePath() {
    Config config = new Config();
    Config.ProjectConfig projectConfig = new Config.ProjectConfig();
    projectConfig.setBuildTool("gradle");
    config.setProject(projectConfig);

    List<Path> sources = BuildCommandHelper.getReportSources(config, tempDir);

    assertEquals(1, sources.size());
    assertTrue(sources.get(0).toString().contains("build/test-results/test"));
  }

  @Test
  void getReportSources_withMaven_returnsSurefireAndFailsafePaths() {
    Config config = new Config();
    Config.ProjectConfig projectConfig = new Config.ProjectConfig();
    projectConfig.setBuildTool("maven");
    config.setProject(projectConfig);

    List<Path> sources = BuildCommandHelper.getReportSources(config, tempDir);

    assertEquals(2, sources.size());
    assertTrue(sources.stream().anyMatch(p -> p.toString().contains("surefire-reports")));
    assertTrue(sources.stream().anyMatch(p -> p.toString().contains("failsafe-reports")));
  }

  @Test
  void getReportSources_withUnknownTool_returnsEmptyList() {
    Config config = new Config();
    List<Path> sources = BuildCommandHelper.getReportSources(config, tempDir);

    assertTrue(sources.isEmpty());
  }

  // --- getDefaultLogsRoot tests ---

  @Test
  void getDefaultLogsRoot_withGradle_returnsBuildLogs() {
    Config config = new Config();
    Config.ProjectConfig projectConfig = new Config.ProjectConfig();
    projectConfig.setBuildTool("gradle");
    config.setProject(projectConfig);

    assertEquals("build/logs", BuildCommandHelper.getDefaultLogsRoot(config));
  }

  @Test
  void getDefaultLogsRoot_withMaven_returnsTargetLogs() {
    Config config = new Config();
    Config.ProjectConfig projectConfig = new Config.ProjectConfig();
    projectConfig.setBuildTool("maven");
    config.setProject(projectConfig);

    assertEquals("target/logs", BuildCommandHelper.getDefaultLogsRoot(config));
  }

  @Test
  void getDefaultLogsRoot_withUnknownTool_returnsDefaultLogs() {
    Config config = new Config();

    assertEquals("logs", BuildCommandHelper.getDefaultLogsRoot(config));
  }

  // --- ProjectRoot-based detection tests ---

  @Test
  void resolveBuildCommand_withProjectRootContainingGradle_returnsGradleCommand()
      throws IOException {
    Files.writeString(tempDir.resolve("build.gradle"), "plugins {}");
    Config config = new Config();
    config.setProject(new Config.ProjectConfig());

    String command = BuildCommandHelper.resolveBuildCommand(config, tempDir);

    assertEquals("./gradlew test", command);
  }

  @Test
  void resolveBuildCommand_withProjectRootContainingPom_returnsMavenCommand() throws IOException {
    Files.writeString(tempDir.resolve("pom.xml"), "<project/>");
    Config config = new Config();
    config.setProject(new Config.ProjectConfig());

    String command = BuildCommandHelper.resolveBuildCommand(config, tempDir);

    assertEquals("mvn -q test", command);
  }

  @Test
  void resolveBuildCommand_withProjectRootContainingGradleWrapper_returnsGradleCommand()
      throws IOException {
    Files.writeString(tempDir.resolve("gradlew"), "#!/bin/sh");
    Config config = new Config();
    config.setProject(new Config.ProjectConfig());

    String command = BuildCommandHelper.resolveBuildCommand(config, tempDir);

    assertEquals("./gradlew test", command);
  }

  @Test
  void getReportSources_withProjectRootContainingGradleWrapper_returnsGradleReportPath()
      throws IOException {
    Files.writeString(tempDir.resolve("gradlew"), "#!/bin/sh");
    Config config = new Config();
    config.setProject(new Config.ProjectConfig());

    List<Path> sources = BuildCommandHelper.getReportSources(config, tempDir);

    assertEquals(1, sources.size());
    assertTrue(sources.get(0).toString().contains("build/test-results/test"));
  }

  @Test
  void getReportSources_withProjectRootContainingMavenWrapper_returnsMavenReportPaths()
      throws IOException {
    Files.writeString(tempDir.resolve("mvnw"), "#!/bin/sh");
    Config config = new Config();
    config.setProject(new Config.ProjectConfig());

    List<Path> sources = BuildCommandHelper.getReportSources(config, tempDir);

    assertEquals(2, sources.size());
    assertTrue(sources.stream().anyMatch(p -> p.toString().contains("surefire-reports")));
    assertTrue(sources.stream().anyMatch(p -> p.toString().contains("failsafe-reports")));
  }

  @Test
  void getDefaultLogsRoot_withProjectRootContainingGradleWrapper_returnsBuildLogs()
      throws IOException {
    Files.writeString(tempDir.resolve("gradlew"), "#!/bin/sh");
    Config config = new Config();
    config.setProject(new Config.ProjectConfig());

    assertEquals("build/logs", BuildCommandHelper.getDefaultLogsRoot(config, tempDir));
  }

  @Test
  void getDefaultLogsRoot_withProjectRootContainingMavenWrapper_returnsTargetLogs()
      throws IOException {
    Files.writeString(tempDir.resolve("mvnw"), "#!/bin/sh");
    Config config = new Config();
    config.setProject(new Config.ProjectConfig());

    assertEquals("target/logs", BuildCommandHelper.getDefaultLogsRoot(config, tempDir));
  }

  private static List<String> invokeBuildShellCommand(String command, String osName) {
    try {
      var method =
          BuildCommandHelper.class.getDeclaredMethod(
              "buildShellCommand", String.class, String.class);
      method.setAccessible(true);
      return (List<String>) method.invoke(null, command, osName);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }
}
