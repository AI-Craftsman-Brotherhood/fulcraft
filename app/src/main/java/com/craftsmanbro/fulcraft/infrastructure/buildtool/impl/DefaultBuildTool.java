package com.craftsmanbro.fulcraft.infrastructure.buildtool.impl;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.buildtool.contract.BuildToolPort;
import com.craftsmanbro.fulcraft.infrastructure.buildtool.failure.FailureAnalysisService;
import com.craftsmanbro.fulcraft.infrastructure.buildtool.impl.util.FileOperationsHelper;
import com.craftsmanbro.fulcraft.infrastructure.buildtool.impl.util.TestArtifactManager;
import com.craftsmanbro.fulcraft.infrastructure.buildtool.impl.util.TestRunFailedException;
import com.craftsmanbro.fulcraft.infrastructure.fs.model.RunPaths;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.xml.JUnitXmlReportParser;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

/** Implementation of BuildToolPort that orchestrates test execution. */
public class DefaultBuildTool implements BuildToolPort {

  private static final DateTimeFormatter RUN_ID_FORMAT =
      DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

  private static final String POM_XML = "pom.xml";

  private static final String BUILD_GRADLE = "build.gradle";

  private static final String BUILD_GRADLE_KTS = "build.gradle.kts";

  private static final String ISOLATION_LOG = "isolation_build.log";

  private static final String BUILD_LOG = "build_and_test.log";

  private static final String PROJECT_ROOT = "projectRoot";

  private final Tracer tracer;

  private final Clock clock;

  private final ProcessRunner processRunner;

  private final FileOperationsHelper fileOperationsHelper;

  private final TestArtifactManager artifactManager;

  private final FailureAnalysisService failureAnalysisService;

  /**
   * Default constructor that uses system defaults.
   *
   * @param tracer the OpenTelemetry tracer
   */
  public DefaultBuildTool(final Tracer tracer) {
    this(tracer, Clock.systemDefaultZone(), new DefaultProcessRunner(), new FileOperationsHelper());
  }

  /**
   * Constructor for partial dependency injection (TestArtifactManager created internally).
   *
   * @param tracer the OpenTelemetry tracer
   * @param clock the clock for time operations
   * @param processRunner the runner for executing processes
   * @param fileOperationsHelper the helper for file operations
   */
  public DefaultBuildTool(
      final Tracer tracer,
      final Clock clock,
      final ProcessRunner processRunner,
      final FileOperationsHelper fileOperationsHelper) {
    this(
        tracer,
        clock,
        processRunner,
        fileOperationsHelper,
        new TestArtifactManager(fileOperationsHelper));
  }

  /**
   * Constructor for full dependency injection (without FailureAnalysisService).
   *
   * @param tracer the OpenTelemetry tracer
   * @param clock the clock for time operations
   * @param processRunner the runner for executing processes
   * @param fileOperationsHelper the helper for file operations
   * @param artifactManager the manager for test artifacts
   */
  public DefaultBuildTool(
      final Tracer tracer,
      final Clock clock,
      final ProcessRunner processRunner,
      final FileOperationsHelper fileOperationsHelper,
      final TestArtifactManager artifactManager) {
    this(
        tracer,
        clock,
        processRunner,
        fileOperationsHelper,
        artifactManager,
        new FailureAnalysisService());
  }

  /**
   * Constructor for full dependency injection.
   *
   * @param tracer the OpenTelemetry tracer
   * @param clock the clock for time operations
   * @param processRunner the runner for executing processes
   * @param fileOperationsHelper the helper for file operations
   * @param artifactManager the manager for test artifacts
   * @param failureAnalysisService the service for analyzing test failures
   */
  public DefaultBuildTool(
      final Tracer tracer,
      final Clock clock,
      final ProcessRunner processRunner,
      final FileOperationsHelper fileOperationsHelper,
      final TestArtifactManager artifactManager,
      final FailureAnalysisService failureAnalysisService) {
    this.tracer = Objects.requireNonNull(tracer);
    this.clock = Objects.requireNonNull(clock);
    this.processRunner = Objects.requireNonNull(processRunner);
    this.fileOperationsHelper = Objects.requireNonNull(fileOperationsHelper);
    this.artifactManager = Objects.requireNonNull(artifactManager);
    this.failureAnalysisService = Objects.requireNonNull(failureAnalysisService);
  }

  @Override
  public boolean isAvailable(final Path projectRoot) {
    if (projectRoot == null || !Files.exists(projectRoot) || !Files.isDirectory(projectRoot)) {
      return false;
    }
    // Basic check for build files
    return Files.exists(projectRoot.resolve(POM_XML))
        || Files.exists(projectRoot.resolve(BUILD_GRADLE))
        || Files.exists(projectRoot.resolve(BUILD_GRADLE_KTS));
  }

  @Override
  public String runTests(final Config config, final Path projectRoot, final String runId)
      throws IOException {
    validateConfig(config);
    Objects.requireNonNull(
        runId,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "runId must not be null"));
    final var safeProjectRoot = projectRoot != null ? projectRoot : Path.of(".");
    validateProjectRoot(safeProjectRoot);
    ensureBuildToolAvailable(safeProjectRoot);
    final var projectId =
        Objects.requireNonNull(
            config.getProject().getId(),
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "infra.common.error.argument_null", "config.project.id must not be null"));
    final Span span = tracer.spanBuilder("Runner.runTests").startSpan();
    try (Scope scope = span.makeCurrent()) {
      Objects.requireNonNull(
          scope,
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.argument_null", "telemetry scope"));
      return executeTests(config, safeProjectRoot, projectId, runId, span);
    } catch (IOException | RuntimeException | InterruptedException e) {
      recordError(span, e);
      throw translateRunTestsException(e);
    } finally {
      span.end();
    }
  }

  private IOException translateRunTestsException(final Exception exception) {
    if (exception instanceof InterruptedException interruptedException) {
      Thread.currentThread().interrupt();
      return new IOException("Test execution interrupted", interruptedException);
    }
    if (exception instanceof IOException ioException) {
      return ioException;
    }
    return new IOException("Test execution failed", exception);
  }

  @Override
  public com.craftsmanbro.fulcraft.infrastructure.buildtool.model.IsolationCheckResult
      runTestIsolated(
          final Config config,
          final Path projectRoot,
          final String testClassName,
          final String packageName,
          final String testCode) {
    validateConfig(config);
    Objects.requireNonNull(projectRoot, PROJECT_ROOT);
    validateProjectRoot(projectRoot);
    ensureBuildToolAvailable(projectRoot);
    final var runId = LocalDateTime.now(clock).format(RUN_ID_FORMAT);
    Path tempDir = null;
    try {
      tempDir = Files.createTempDirectory("ful_iso_" + testClassName + "_" + runId + "_");
      final File logFile = tempDir.resolve(ISOLATION_LOG).toFile();
      Logger.info(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message", "Preparing isolated workspace at: " + tempDir));
      copyProject(projectRoot, tempDir);
      writeTestFile(tempDir, packageName, testClassName, testCode);
      final var command = resolveIsolatedCommand(config, projectRoot, packageName, testClassName);
      Logger.info(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message", "Running isolated test: " + command));
      final int exitCode = runProcess(command, tempDir, logFile);
      final Path logsDir = tempDir.resolve("logs");
      artifactManager.collectReports(tempDir, logsDir, config);
      final JUnitFailureSummary junitSummary = parseJUnitFailures(logsDir);
      if (exitCode == 0) {
        Logger.info(
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "infra.common.log.message", "Isolated test passed!"));
        return com.craftsmanbro.fulcraft.infrastructure.buildtool.model.IsolationCheckResult
            .passed();
      } else {
        logTestFailure(exitCode, logFile);
        final String output = readAndCleanLogFile(logFile);
        return com.craftsmanbro.fulcraft.infrastructure.buildtool.model.IsolationCheckResult
            .failedWithDetails(
                exitCode,
                output,
                "Test execution failed with exit code " + exitCode,
                junitSummary != null ? junitSummary.assertionDetails() : null,
                junitSummary != null ? junitSummary.stackTrace() : null,
                junitSummary != null ? junitSummary.reportContent() : null);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      Logger.error(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message", "Isolated test execution interrupted"),
          e);
      return com.craftsmanbro.fulcraft.infrastructure.buildtool.model.IsolationCheckResult.failed(
          -1, null, "Isolated test execution interrupted");
    } catch (Exception e) {
      Logger.error(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message", "Isolated test execution failed: " + e.getMessage()),
          e);
      return com.craftsmanbro.fulcraft.infrastructure.buildtool.model.IsolationCheckResult.failed(
          -1, null, "Isolated test execution failed: " + e.getMessage());
    } finally {
      if (tempDir != null) {
        cleanupTempDir(tempDir);
      }
    }
  }

  @Override
  public com.craftsmanbro.fulcraft.infrastructure.buildtool.model.IsolationCheckResult
      runSingleTest(
          final Config config,
          final Path projectRoot,
          final String testClassName,
          final String testMethodName) {
    validateConfig(config);
    Objects.requireNonNull(projectRoot, PROJECT_ROOT);
    Objects.requireNonNull(
        testClassName,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "testClassName"));
    validateProjectRoot(projectRoot);
    ensureBuildToolAvailable(projectRoot);
    File logFile = null;
    try {
      logFile = Files.createTempFile("ful_rerun_", ".log").toFile();
      final String command =
          BuildCommandHelper.resolveSingleTestCommand(
              config, projectRoot, testClassName, testMethodName);
      Logger.info(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message", "Re-running test: " + command));
      final int exitCode = runProcess(command, projectRoot, logFile);
      if (exitCode == 0) {
        return com.craftsmanbro.fulcraft.infrastructure.buildtool.model.IsolationCheckResult
            .passed();
      }
      final String output = readAndCleanLogFile(logFile);
      return com.craftsmanbro.fulcraft.infrastructure.buildtool.model.IsolationCheckResult.failed(
          exitCode, output, "Test execution failed with exit code " + exitCode);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return com.craftsmanbro.fulcraft.infrastructure.buildtool.model.IsolationCheckResult.failed(
          -1, null, "Test execution interrupted");
    } catch (Exception e) {
      return com.craftsmanbro.fulcraft.infrastructure.buildtool.model.IsolationCheckResult.failed(
          -1, null, "Test execution failed: " + e.getMessage());
    } finally {
      if (logFile != null) {
        try {
          Files.deleteIfExists(logFile.toPath());
        } catch (IOException deleteEx) {
          // Cleanup failure is non-critical, just log
          Logger.debug(
              com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                  "infra.common.log.message",
                  "Failed to delete temp log file: " + deleteEx.getMessage()));
        }
      }
    }
  }

  private String readAndCleanLogFile(final File logFile) {
    try {
      final String content = Files.readString(logFile.toPath());
      return content
          .lines()
          .filter(line -> !line.contains("OpenJDK 64-Bit Server VM warning"))
          .collect(Collectors.joining("\n"));
    } catch (IOException e) {
      return "Failed to read log file: " + e.getMessage();
    }
  }

  private String executeTests(
      final Config config,
      final Path projectRoot,
      final String projectId,
      final String runId,
      final Span span)
      throws IOException, InterruptedException {
    Objects.requireNonNull(
        projectId,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "projectId must not be null"));
    Objects.requireNonNull(
        runId,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "runId must not be null"));
    setSpanAttributes(span, projectRoot, projectId);
    span.setAttribute("run.id", runId);
    final RunPaths runPaths = RunPaths.from(config, projectRoot, runId);
    final var reportDir = artifactManager.prepareLogsDir(config, projectRoot, projectId, runId);
    final var logFile = runPaths.logsDir().resolve(BUILD_LOG).toFile();
    final String command =
        Objects.requireNonNull(
            BuildCommandHelper.resolveBuildCommand(config, projectRoot), "Build command is null");
    logExecutionInfo(config, command, logFile);
    final int exitCode = runProcess(command, projectRoot, logFile);
    span.setAttribute("exit.code", exitCode);
    artifactManager.collectReports(projectRoot, reportDir, config);
    final JUnitFailureSummary junitSummary = parseJUnitFailures(reportDir);
    Logger.info(
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.log.message", "Test execution finished with exit code: " + exitCode));
    if (exitCode != 0) {
      if (junitSummary != null) {
        Logger.warn(
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "infra.common.log.message",
                "JUnit failures detected:\n" + junitSummary.assertionDetails()));
      }
      final String message =
          "Build/tests failed with exit code "
              + exitCode
              + ". See log: "
              + logFile.getAbsolutePath()
              + (junitSummary != null
                  ? System.lineSeparator() + junitSummary.assertionDetails()
                  : "");
      throw new TestRunFailedException(runId, runPaths.logsDir(), reportDir, message);
    }
    return runId;
  }

  private void validateConfig(final Config config) {
    Objects.requireNonNull(
        config,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "config"));
    Objects.requireNonNull(
        config.getProject(),
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "config.project"));
    if (StringUtils.isBlank(config.getProject().getId())) {
      throw new IllegalArgumentException(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.message", "config.project.id is required"));
    }
  }

  private void validateProjectRoot(final Path projectRoot) {
    Objects.requireNonNull(projectRoot, PROJECT_ROOT);
    if (!Files.exists(projectRoot) || !Files.isDirectory(projectRoot)) {
      throw new IllegalArgumentException(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.message", "projectRoot must be an existing directory"));
    }
  }

  private void ensureBuildToolAvailable(final Path projectRoot) {
    if (!isAvailable(projectRoot)) {
      throw new IllegalStateException(
          "No build tool configuration found in project root: " + projectRoot);
    }
  }

  private void setSpanAttributes(final Span span, final Path projectRoot, final String projectId) {
    if (projectRoot != null) {
      span.setAttribute("project.root", Objects.requireNonNull(projectRoot.toString()));
    }
    if (projectId != null) {
      span.setAttribute("project.id", projectId);
    }
  }

  private void logExecutionInfo(final Config config, final String command, final File logFile) {
    Logger.info(
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.log.message", "Running tests with command: " + command));
    Logger.info(
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.log.message", "Logs: " + logFile.getAbsolutePath()));
    if (config.getExecution() != null && config.getExecution().isPerTaskIsolation()) {
      Logger.info(
          "INFO: execution.per_task_isolation enabled. Individual tests were verified in isolation during generation.");
    }
  }

  private int runProcess(final String command, final Path workDir, final File logFile)
      throws IOException, InterruptedException {
    Objects.requireNonNull(
        command,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "command"));
    Objects.requireNonNull(
        workDir,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "workDir"));
    Objects.requireNonNull(
        logFile,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "logFile"));
    final var shellCommand = BuildCommandHelper.buildShellCommand(command);
    return processRunner.run(shellCommand, workDir.toFile(), logFile);
  }

  private void logTestFailure(final int exitCode, final File logFile) {
    Logger.warn(
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.log.message", "Isolated test failed (Exit Code: " + exitCode + ")"));
    Logger.warn(
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.log.message", "See logs: " + logFile.getAbsolutePath()));
    artifactManager.printLogTail(logFile.toPath(), 20);
  }

  private JUnitFailureSummary parseJUnitFailures(final Path logsDir) {
    final Path reportDir = artifactManager.getReportDir(logsDir);
    final JUnitXmlReportParser parser = new JUnitXmlReportParser();
    final FailureAnalysisService analyzer = this.failureAnalysisService;
    final List<JUnitXmlReportParser.TestFailure> failures = parser.parseTestResults(reportDir);
    if (failures.isEmpty()) {
      return null;
    }
    final StringBuilder assertionDetails = new StringBuilder();
    final int limit = Math.min(3, failures.size());
    for (int i = 0; i < limit; i++) {
      if (i > 0) {
        assertionDetails.append("\n");
      }
      final JUnitXmlReportParser.TestFailure f = failures.get(i);
      assertionDetails.append(
          analyzer.formatForExchange(
              f.testClass(), f.testMethod(), f.failureType(), f.failureMessage(), f.stackTrace()));
    }
    final JUnitXmlReportParser.TestFailure primary = failures.get(0);
    final String stackTrace = firstLines(primary.stackTrace(), 5);
    return new JUnitFailureSummary(
        assertionDetails.toString(), stackTrace, primary.reportContent());
  }

  private String firstLines(final String text, final int maxLines) {
    if (text == null || text.isBlank()) {
      return null;
    }
    final String[] lines = text.split("\n");
    final StringBuilder sb = new StringBuilder();
    for (int i = 0; i < Math.min(maxLines, lines.length); i++) {
      if (i > 0) {
        sb.append("\n");
      }
      sb.append(lines[i]);
    }
    return sb.toString();
  }

  private record JUnitFailureSummary(
      String assertionDetails, String stackTrace, String reportContent) {}

  private void recordError(final Span span, final Exception e) {
    Objects.requireNonNull(
        e,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "exception must not be null"));
    span.recordException(e);
    final String errorMessage = e.getMessage();
    span.setStatus(StatusCode.ERROR, errorMessage != null ? errorMessage : "error");
  }

  // Wrapper methods for static helpers to improve testability (Spying allowed)
  protected void copyProject(final Path source, final Path target) throws IOException {
    fileOperationsHelper.copyProject(source, target);
  }

  protected void writeTestFile(
      final Path dir, final String packageName, final String testClassName, final String testCode)
      throws IOException {
    fileOperationsHelper.writeTestFile(dir, packageName, testClassName, testCode);
  }

  protected String resolveIsolatedCommand(
      final Config config,
      final Path projectRoot,
      final String packageName,
      final String testClassName) {
    return BuildCommandHelper.resolveIsolatedCommand(
        config, projectRoot, packageName, testClassName);
  }

  protected void cleanupTempDir(final Path tempDir) {
    fileOperationsHelper.cleanupTempDir(tempDir);
  }

  /** Interface for process execution to facilitate testing. */
  @FunctionalInterface
  public interface ProcessRunner {

    int run(List<String> command, File workingDir, File logFile)
        throws IOException, InterruptedException;
  }

  /** Default implementation of ProcessRunner using ProcessBuilder. */
  public static class DefaultProcessRunner implements ProcessRunner {

    @Override
    public int run(final List<String> command, final File workingDir, final File logFile)
        throws IOException, InterruptedException {
      final var pb = new ProcessBuilder(command);
      pb.directory(workingDir);
      pb.redirectOutput(logFile);
      pb.redirectError(logFile);
      final Process process = pb.start();
      return process.waitFor();
    }
  }
}
