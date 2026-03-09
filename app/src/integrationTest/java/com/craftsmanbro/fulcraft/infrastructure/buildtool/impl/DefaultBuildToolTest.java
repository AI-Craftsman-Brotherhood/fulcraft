package com.craftsmanbro.fulcraft.infrastructure.buildtool.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.buildtool.impl.util.FileOperationsHelper;
import com.craftsmanbro.fulcraft.infrastructure.buildtool.impl.util.TestArtifactManager;
import com.craftsmanbro.fulcraft.infrastructure.buildtool.impl.util.TestRunFailedException;
import com.craftsmanbro.fulcraft.infrastructure.fs.model.RunPaths;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link DefaultBuildTool}.
 *
 * <p>Tests input validation and isAvailable logic. Process execution tests are omitted as they
 * require external build tools.
 */
class DefaultBuildToolTest {

  @TempDir Path tempDir;

  private Tracer tracer;
  private DefaultBuildTool buildTool;

  @BeforeEach
  void setUp() {
    tracer = TracerProvider.noop().get("test");
    buildTool = new DefaultBuildTool(tracer);
  }

  // --- Constructor tests ---

  @Test
  void constructor_withTracer_createsInstance() {
    DefaultBuildTool tool = new DefaultBuildTool(tracer);
    assertNotNull(tool);
  }

  @Test
  void constructor_withNullTracer_throwsNullPointerException() {
    assertThrows(NullPointerException.class, () -> new DefaultBuildTool(null));
  }

  @Test
  void constructor_withAllDependencies_createsInstance() {
    DefaultBuildTool tool =
        new DefaultBuildTool(
            tracer,
            Clock.systemDefaultZone(),
            new DefaultBuildTool.DefaultProcessRunner(),
            new FileOperationsHelper());
    assertNotNull(tool);
  }

  @Test
  void constructor_withNullClock_throwsNullPointerException() {
    assertThrows(
        NullPointerException.class,
        () ->
            new DefaultBuildTool(
                tracer,
                null,
                new DefaultBuildTool.DefaultProcessRunner(),
                new FileOperationsHelper()));
  }

  @Test
  void constructor_withNullProcessRunner_throwsNullPointerException() {
    assertThrows(
        NullPointerException.class,
        () ->
            new DefaultBuildTool(
                tracer, Clock.systemDefaultZone(), null, new FileOperationsHelper()));
  }

  @Test
  void constructor_withNullFileOperationsHelper_throwsNullPointerException() {
    assertThrows(
        NullPointerException.class,
        () ->
            new DefaultBuildTool(
                tracer,
                Clock.systemDefaultZone(),
                new DefaultBuildTool.DefaultProcessRunner(),
                null));
  }

  // --- isAvailable tests ---

  @Test
  void isAvailable_withNullPath_returnsFalse() {
    assertFalse(buildTool.isAvailable(null));
  }

  @Test
  void isAvailable_withNonExistentPath_returnsFalse() {
    Path nonExistent = tempDir.resolve("does-not-exist");
    assertFalse(buildTool.isAvailable(nonExistent));
  }

  @Test
  void isAvailable_withFile_returnsFalse() throws IOException {
    Path file = tempDir.resolve("file.txt");
    Files.writeString(file, "content");

    assertFalse(buildTool.isAvailable(file));
  }

  @Test
  void isAvailable_withEmptyDirectory_returnsFalse() {
    assertFalse(buildTool.isAvailable(tempDir));
  }

  @Test
  void isAvailable_withPomXml_returnsTrue() throws IOException {
    Files.writeString(tempDir.resolve("pom.xml"), "<project/>");

    assertTrue(buildTool.isAvailable(tempDir));
  }

  @Test
  void isAvailable_withBuildGradle_returnsTrue() throws IOException {
    Files.writeString(tempDir.resolve("build.gradle"), "plugins {}");

    assertTrue(buildTool.isAvailable(tempDir));
  }

  @Test
  void isAvailable_withBuildGradleKts_returnsTrue() throws IOException {
    Files.writeString(tempDir.resolve("build.gradle.kts"), "plugins {}");

    assertTrue(buildTool.isAvailable(tempDir));
  }

  @Test
  void isAvailable_withBothBuildFiles_returnsTrue() throws IOException {
    Files.writeString(tempDir.resolve("pom.xml"), "<project/>");
    Files.writeString(tempDir.resolve("build.gradle"), "plugins {}");

    assertTrue(buildTool.isAvailable(tempDir));
  }

  // --- DefaultProcessRunner tests ---

  @Test
  void defaultProcessRunner_createsInstance() {
    DefaultBuildTool.DefaultProcessRunner runner = new DefaultBuildTool.DefaultProcessRunner();
    assertNotNull(runner);
  }

  // --- runSingleTest tests ---

  @Test
  void runSingleTest_whenExecutionSucceeds_returnsPassedResult() throws IOException {
    Files.writeString(tempDir.resolve("pom.xml"), "<project/>");
    Config config = createConfig("maven");
    AtomicReference<List<String>> capturedCommand = new AtomicReference<>();
    DefaultBuildTool tool =
        buildToolWithRunner(
            (command, workingDir, logFile) -> {
              capturedCommand.set(command);
              return 0;
            });

    var result = tool.runSingleTest(config, tempDir, "MyTest", null);

    assertTrue(result.success());
    assertEquals(0, result.exitCode());
    assertNotNull(capturedCommand.get());
    assertTrue(capturedCommand.get().get(2).contains("-Dtest=MyTest"));
  }

  @Test
  void runSingleTest_whenExecutionFails_filtersJvmWarningFromOutput() throws IOException {
    Files.writeString(tempDir.resolve("pom.xml"), "<project/>");
    Config config = createConfig("maven");
    DefaultBuildTool tool =
        buildToolWithRunner(
            (command, workingDir, logFile) -> {
              Files.writeString(
                  logFile.toPath(),
                  "OpenJDK 64-Bit Server VM warning: ignored\nuseful output\nanother line");
              return 2;
            });

    var result = tool.runSingleTest(config, tempDir, "MyTest", "fails");

    assertFalse(result.success());
    assertEquals(2, result.exitCode());
    assertEquals("Test execution failed with exit code 2", result.errorMessage());
    assertNotNull(result.output());
    assertTrue(result.output().contains("useful output"));
    assertFalse(result.output().contains("OpenJDK 64-Bit Server VM warning"));
  }

  @Test
  void runSingleTest_whenInterrupted_returnsInterruptedResultAndPreservesInterruptFlag()
      throws IOException {
    Files.writeString(tempDir.resolve("pom.xml"), "<project/>");
    Config config = createConfig("maven");
    DefaultBuildTool tool =
        buildToolWithRunner(
            (command, workingDir, logFile) -> {
              throw new InterruptedException("stop");
            });

    var result = tool.runSingleTest(config, tempDir, "MyTest", null);

    assertFalse(result.success());
    assertEquals(-1, result.exitCode());
    assertEquals("Test execution interrupted", result.errorMessage());
    assertTrue(Thread.currentThread().isInterrupted());
    Thread.interrupted();
  }

  @Test
  void runSingleTest_whenRunnerThrowsException_returnsFailureResult() throws IOException {
    Files.writeString(tempDir.resolve("pom.xml"), "<project/>");
    Config config = createConfig("maven");
    DefaultBuildTool tool =
        buildToolWithRunner(
            (command, workingDir, logFile) -> {
              throw new IOException("boom");
            });

    var result = tool.runSingleTest(config, tempDir, "MyTest", null);

    assertFalse(result.success());
    assertEquals(-1, result.exitCode());
    assertEquals("Test execution failed: boom", result.errorMessage());
  }

  @Test
  void runSingleTest_withoutBuildFile_throwsIllegalStateException() {
    Config config = createConfig("maven");
    DefaultBuildTool tool = buildToolWithRunner((command, workingDir, logFile) -> 0);

    assertThrows(
        IllegalStateException.class, () -> tool.runSingleTest(config, tempDir, "MyTest", null));
  }

  // --- runTests tests ---

  @Test
  void runTests_whenExecutionSucceeds_returnsRunId() throws IOException {
    Files.writeString(tempDir.resolve("pom.xml"), "<project/>");
    Config config = createConfig("maven");
    DefaultBuildTool tool = buildToolWithRunner((command, workingDir, logFile) -> 0);

    String runId = tool.runTests(config, tempDir, "run-success");

    assertEquals("run-success", runId);
  }

  @Test
  void runTests_whenExecutionFails_throwsTestRunFailedExceptionWithDirectories()
      throws IOException {
    Files.writeString(tempDir.resolve("pom.xml"), "<project/>");
    Config config = createConfig("maven");
    DefaultBuildTool tool = buildToolWithRunner((command, workingDir, logFile) -> 5);

    String runId = "run-failure";
    TestRunFailedException thrown =
        assertThrows(TestRunFailedException.class, () -> tool.runTests(config, tempDir, runId));
    RunPaths expected = RunPaths.from(config, tempDir, runId);

    assertEquals(runId, thrown.getRunId());
    assertEquals(expected.logsDir(), thrown.getLogsDir());
    assertEquals(expected.reportDir(), thrown.getReportDir());
    assertTrue(thrown.getMessage().contains("exit code 5"));
  }

  @Test
  void runTests_whenInterrupted_throwsIoExceptionAndPreservesInterruptFlag() throws IOException {
    Files.writeString(tempDir.resolve("pom.xml"), "<project/>");
    Config config = createConfig("maven");
    DefaultBuildTool tool =
        buildToolWithRunner(
            (command, workingDir, logFile) -> {
              throw new InterruptedException("interrupted");
            });

    IOException thrown =
        assertThrows(IOException.class, () -> tool.runTests(config, tempDir, "run-interrupted"));

    assertEquals("Test execution interrupted", thrown.getMessage());
    assertTrue(Thread.currentThread().isInterrupted());
    Thread.interrupted();
  }

  @Test
  void runTests_whenRunnerThrowsRuntimeException_wrapsAsIoException() throws IOException {
    Files.writeString(tempDir.resolve("pom.xml"), "<project/>");
    Config config = createConfig("maven");
    DefaultBuildTool tool =
        buildToolWithRunner(
            (command, workingDir, logFile) -> {
              throw new RuntimeException("boom");
            });

    IOException thrown =
        assertThrows(IOException.class, () -> tool.runTests(config, tempDir, "run-runtime"));

    assertEquals("Test execution failed", thrown.getMessage());
    assertNotNull(thrown.getCause());
    assertEquals("boom", thrown.getCause().getMessage());
  }

  @Test
  void runTests_withBlankProjectId_throwsIllegalArgumentException() {
    Config config = new Config();
    Config.ProjectConfig project = new Config.ProjectConfig();
    project.setId("   ");
    project.setBuildTool("maven");
    config.setProject(project);

    assertThrows(
        IllegalArgumentException.class, () -> buildTool.runTests(config, tempDir, "run-id"));
  }

  private Config createConfig(String buildTool) {
    Config config = new Config();
    Config.ProjectConfig project = new Config.ProjectConfig();
    project.setId("project-id");
    project.setBuildTool(buildTool);
    config.setProject(project);
    return config;
  }

  private DefaultBuildTool buildToolWithRunner(DefaultBuildTool.ProcessRunner processRunner) {
    return new DefaultBuildTool(
        tracer,
        Clock.systemUTC(),
        processRunner,
        new FileOperationsHelper(),
        new TestArtifactManager(new FileOperationsHelper()));
  }
}
