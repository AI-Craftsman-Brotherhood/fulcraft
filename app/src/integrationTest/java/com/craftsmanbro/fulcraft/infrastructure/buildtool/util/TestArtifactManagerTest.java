package com.craftsmanbro.fulcraft.infrastructure.buildtool.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.buildtool.impl.util.FileOperationsHelper;
import com.craftsmanbro.fulcraft.infrastructure.buildtool.impl.util.TestArtifactManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link TestArtifactManager}.
 *
 * <p>Verifies report directory resolution, XML report copying, and log tail printing.
 */
class TestArtifactManagerTest {

  @TempDir Path tempDir;

  private FileOperationsHelper fileOps;
  private TestArtifactManager manager;

  @BeforeEach
  void setUp() {
    fileOps = new FileOperationsHelper();
    manager = new TestArtifactManager(fileOps);
  }

  @Test
  void constructor_withNullFileOps_throwsNullPointerException() {
    assertThrows(NullPointerException.class, () -> new TestArtifactManager(null));
  }

  // --- prepareLogsDir tests ---

  @Test
  void prepareLogsDir_withNullConfig_throwsNullPointerException() {
    Path projectRoot = tempDir.resolve("project");
    assertThrows(
        NullPointerException.class,
        () -> manager.prepareLogsDir(null, projectRoot, "project-id", "run-1"));
  }

  @Test
  void prepareLogsDir_withNullProjectRoot_throwsNullPointerException() {
    Config config = configWithBuildTool("gradle");
    assertThrows(
        NullPointerException.class,
        () -> manager.prepareLogsDir(config, null, "project-id", "run-1"));
  }

  @Test
  void prepareLogsDir_withNullProjectId_throwsNullPointerException() {
    Config config = configWithBuildTool("gradle");
    Path projectRoot = tempDir.resolve("project");
    assertThrows(
        NullPointerException.class,
        () -> manager.prepareLogsDir(config, projectRoot, null, "run-1"));
  }

  @Test
  void prepareLogsDir_withNullRunId_throwsNullPointerException() {
    Config config = configWithBuildTool("gradle");
    Path projectRoot = tempDir.resolve("project");
    assertThrows(
        NullPointerException.class,
        () -> manager.prepareLogsDir(config, projectRoot, "project-id", null));
  }

  @Test
  void prepareLogsDir_createsLogsAndReportDirectories() throws IOException {
    Config config = configWithBuildTool("gradle");
    Config.ExecutionConfig executionConfig = new Config.ExecutionConfig();
    executionConfig.setLogsRoot("custom-runs");
    config.setExecution(executionConfig);
    Path projectRoot = tempDir.resolve("project");

    Path reportDir = manager.prepareLogsDir(config, projectRoot, "project-id", "run-1");

    Path runRoot = projectRoot.resolve("custom-runs").resolve("run-1");
    assertEquals(runRoot.resolve("report"), reportDir);
    assertTrue(Files.isDirectory(runRoot.resolve("logs")));
    assertTrue(Files.isDirectory(reportDir));
  }

  // --- getReportDir tests ---

  @Test
  void getReportDir_withNullLogsDir_throwsNullPointerException() {
    assertThrows(NullPointerException.class, () -> manager.getReportDir(null));
  }

  @Test
  void getReportDir_returnsJunitReportsSubdirectory() {
    Path logsDir = tempDir.resolve("logs");

    Path reportDir = manager.getReportDir(logsDir);

    assertNotNull(reportDir);
    assertEquals(logsDir.resolve("junit_reports"), reportDir);
  }

  // --- copyXmlReports tests ---

  @Test
  void copyXmlReports_withNullSource_throwsNullPointerException() {
    Path reportDest = tempDir.resolve("dest");
    assertThrows(NullPointerException.class, () -> manager.copyXmlReports(null, reportDest));
  }

  @Test
  void copyXmlReports_withNullReportDest_throwsNullPointerException() {
    Path source = tempDir.resolve("source");
    assertThrows(NullPointerException.class, () -> manager.copyXmlReports(source, null));
  }

  @Test
  void copyXmlReports_withNonExistentSource_doesNothing() throws IOException {
    Path source = tempDir.resolve("non-existent");
    Path reportDest = tempDir.resolve("dest");
    Files.createDirectories(reportDest);

    // Should not throw
    manager.copyXmlReports(source, reportDest);

    assertTrue(Files.list(reportDest).findAny().isEmpty());
  }

  @Test
  void copyXmlReports_withFileAsSource_doesNothing() throws IOException {
    Path source = tempDir.resolve("file.txt");
    Files.writeString(source, "content");
    Path reportDest = tempDir.resolve("dest");
    Files.createDirectories(reportDest);

    // Should not throw (source is file, not directory)
    manager.copyXmlReports(source, reportDest);

    assertTrue(Files.list(reportDest).findAny().isEmpty());
  }

  @Test
  void copyXmlReports_copiesOnlyXmlFiles() throws IOException {
    Path source = tempDir.resolve("reports");
    Files.createDirectories(source);
    Files.writeString(source.resolve("TEST-Result.xml"), "<xml>test</xml>");
    Files.writeString(source.resolve("report.html"), "<html></html>");
    Files.writeString(source.resolve("data.json"), "{}");

    Path reportDest = tempDir.resolve("dest");
    Files.createDirectories(reportDest);

    manager.copyXmlReports(source, reportDest);

    assertTrue(Files.exists(reportDest.resolve("TEST-Result.xml")));
    assertEquals("<xml>test</xml>", Files.readString(reportDest.resolve("TEST-Result.xml")));
    assertTrue(Files.notExists(reportDest.resolve("report.html")));
    assertTrue(Files.notExists(reportDest.resolve("data.json")));
  }

  @Test
  void copyXmlReports_copiesNestedXmlFiles() throws IOException {
    Path source = tempDir.resolve("reports");
    Files.createDirectories(source.resolve("subdir"));
    Files.writeString(source.resolve("TEST-Root.xml"), "root");
    Files.writeString(source.resolve("subdir/TEST-Nested.xml"), "nested");

    Path reportDest = tempDir.resolve("dest");
    Files.createDirectories(reportDest);

    manager.copyXmlReports(source, reportDest);

    // Both XML files should be copied (flattened to dest)
    assertTrue(Files.exists(reportDest.resolve("TEST-Root.xml")));
    assertTrue(Files.exists(reportDest.resolve("TEST-Nested.xml")));
  }

  @Test
  void copyXmlReports_whenDuplicateFileNamesExist_overwritesWithLaterPath() throws IOException {
    Path source = tempDir.resolve("reports");
    Files.createDirectories(source.resolve("a"));
    Files.createDirectories(source.resolve("b"));
    Files.writeString(source.resolve("a/TEST-Duplicate.xml"), "<xml>a</xml>");
    Files.writeString(source.resolve("b/TEST-Duplicate.xml"), "<xml>b</xml>");

    Path reportDest = tempDir.resolve("dest");
    Files.createDirectories(reportDest);

    manager.copyXmlReports(source, reportDest);

    assertEquals("<xml>b</xml>", Files.readString(reportDest.resolve("TEST-Duplicate.xml")));
  }

  // --- collectReports tests ---

  @Test
  void collectReports_withNullProjectRoot_throwsNullPointerException() {
    Path logsDir = tempDir.resolve("logs");
    Config config = configWithBuildTool("gradle");
    assertThrows(NullPointerException.class, () -> manager.collectReports(null, logsDir, config));
  }

  @Test
  void collectReports_withNullLogsDir_throwsNullPointerException() {
    Path projectRoot = tempDir.resolve("project");
    Config config = configWithBuildTool("gradle");
    assertThrows(
        NullPointerException.class, () -> manager.collectReports(projectRoot, null, config));
  }

  @Test
  void collectReports_withNullConfig_throwsNullPointerException() {
    Path projectRoot = tempDir.resolve("project");
    Path logsDir = tempDir.resolve("logs");
    assertThrows(
        NullPointerException.class, () -> manager.collectReports(projectRoot, logsDir, null));
  }

  @Test
  void collectReports_collectsXmlReportsFromGradleLocation() throws IOException {
    Path projectRoot = tempDir.resolve("project");
    Path gradleReports = projectRoot.resolve("build/test-results/test");
    Files.createDirectories(gradleReports.resolve("nested"));
    Files.writeString(gradleReports.resolve("TEST-root.xml"), "<xml>root</xml>");
    Files.writeString(gradleReports.resolve("nested/TEST-nested.xml"), "<xml>nested</xml>");
    Files.writeString(gradleReports.resolve("ignored.txt"), "ignored");

    Path logsDir = tempDir.resolve("logs");
    Config config = configWithBuildTool("gradle");
    manager.collectReports(projectRoot, logsDir, config);

    Path reportDir = logsDir.resolve("junit_reports");
    assertTrue(Files.exists(reportDir.resolve("TEST-root.xml")));
    assertTrue(Files.exists(reportDir.resolve("TEST-nested.xml")));
    assertFalse(Files.exists(reportDir.resolve("ignored.txt")));
  }

  @Test
  void collectReports_collectsXmlReportsFromMavenLocations() throws IOException {
    Path projectRoot = tempDir.resolve("project");
    Path surefire = projectRoot.resolve("target/surefire-reports");
    Path failsafe = projectRoot.resolve("target/failsafe-reports");
    Files.createDirectories(surefire);
    Files.createDirectories(failsafe);
    Files.writeString(surefire.resolve("TEST-surefire.xml"), "<xml>surefire</xml>");
    Files.writeString(failsafe.resolve("TEST-failsafe.xml"), "<xml>failsafe</xml>");

    Path logsDir = tempDir.resolve("logs");
    Config config = configWithBuildTool("maven");
    manager.collectReports(projectRoot, logsDir, config);

    Path reportDir = logsDir.resolve("junit_reports");
    assertTrue(Files.exists(reportDir.resolve("TEST-surefire.xml")));
    assertTrue(Files.exists(reportDir.resolve("TEST-failsafe.xml")));
  }

  @Test
  void collectReports_detectsGradleFromProjectRootWhenBuildToolNotConfigured() throws IOException {
    Path projectRoot = tempDir.resolve("project");
    Files.createDirectories(projectRoot.resolve("build/test-results/test"));
    Files.writeString(
        projectRoot.resolve("build/test-results/test/TEST-detected.xml"), "<xml>detected</xml>");
    Files.writeString(projectRoot.resolve("build.gradle"), "plugins {}");

    Path logsDir = tempDir.resolve("logs");
    Config config = Config.createDefault();
    config.getProject().setBuildTool(null);
    manager.collectReports(projectRoot, logsDir, config);

    Path reportDir = logsDir.resolve("junit_reports");
    assertTrue(Files.exists(reportDir.resolve("TEST-detected.xml")));
  }

  @Test
  void collectReports_whenReportDirCreationFails_doesNotThrow() throws IOException {
    Path projectRoot = tempDir.resolve("project");
    Files.createDirectories(projectRoot.resolve("build/test-results/test"));
    Files.writeString(
        projectRoot.resolve("build/test-results/test/TEST-root.xml"), "<xml>root</xml>");

    Path logsDirFile = tempDir.resolve("logs-as-file");
    Files.writeString(logsDirFile, "not-a-directory");

    Config config = configWithBuildTool("gradle");
    assertDoesNotThrow(() -> manager.collectReports(projectRoot, logsDirFile, config));
    assertFalse(Files.exists(logsDirFile.resolve("junit_reports")));
  }

  // --- printLogTail tests ---

  @Test
  void printLogTail_withNullFile_throwsNullPointerException() {
    assertThrows(NullPointerException.class, () -> manager.printLogTail(null, 10));
  }

  @Test
  void printLogTail_withNonExistentFile_doesNotThrow() {
    Path nonExistent = tempDir.resolve("non-existent.log");

    // Should log warning but not throw
    manager.printLogTail(nonExistent, 10);
  }

  @Test
  void printLogTail_withExistingFile_readsSuccessfully() throws IOException {
    Path logFile = tempDir.resolve("test.log");
    StringBuilder content = new StringBuilder();
    for (int i = 1; i <= 20; i++) {
      content.append("Line ").append(i).append("\n");
    }
    Files.writeString(logFile, content.toString());

    // Should not throw and should read last N lines (logging output)
    manager.printLogTail(logFile, 5);
  }

  @Test
  void printLogTail_withEmptyFile_doesNotThrow() throws IOException {
    Path emptyFile = tempDir.resolve("empty.log");
    Files.writeString(emptyFile, "");

    manager.printLogTail(emptyFile, 10);
  }

  @Test
  void printLogTail_withFewerLinesThanRequested_readsAllLines() throws IOException {
    Path logFile = tempDir.resolve("short.log");
    Files.writeString(logFile, "Line 1\nLine 2\nLine 3\n");

    // Requesting 10 lines but file has only 3
    manager.printLogTail(logFile, 10);
  }

  private Config configWithBuildTool(String buildTool) {
    Config config = Config.createDefault();
    Config.ProjectConfig projectConfig = new Config.ProjectConfig();
    projectConfig.setBuildTool(buildTool);
    config.setProject(projectConfig);
    return config;
  }
}
