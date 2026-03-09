package com.craftsmanbro.fulcraft.infrastructure.fs.model;

import com.craftsmanbro.fulcraft.config.Config;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Resolves run-scoped filesystem paths for run artifacts. */
public final class RunPaths {

  public static final String DEFAULT_RUNS_DIR = Config.ExecutionConfig.DEFAULT_LOGS_ROOT;

  public static final String LOGS_DIR = "logs";

  public static final String ANALYSIS_DIR = "analysis";

  public static final String PLAN_DIR = "plan";

  public static final String GENERATION_DIR = "generation";

  public static final String REPORT_DIR = "report";

  public static final String JUNIT_REPORTS_DIR = "junit_reports";

  public static final String DEFAULT_LOG_FILE = "ful.log";

  public static final String LLM_LOG_FILE = "llm.log";

  private static final String ARGUMENT_NULL_MESSAGE_KEY = "infra.common.error.argument_null";

  private final Path runsRoot;

  private final Path runRoot;

  private RunPaths(final Path runsRoot, final Path runRoot) {
    this.runsRoot = runsRoot;
    this.runRoot = runRoot;
  }

  public static RunPaths from(final Config config, final Path projectRoot, final String runId) {
    final String requiredRunId = requireRunId(runId);
    final Path resolvedRunsRoot = resolveRunsRoot(config, projectRoot);
    return new RunPaths(resolvedRunsRoot, resolveRunRoot(resolvedRunsRoot, requiredRunId));
  }

  public static Path resolveRunsRoot(final Config config, final Path projectRoot) {
    final Path base = Path.of(resolveConfiguredLogsRoot(config));
    if (base.isAbsolute()) {
      return base.normalize();
    }
    if (projectRoot != null) {
      return projectRoot.resolve(base).normalize();
    }
    return base.toAbsolutePath().normalize();
  }

  public static Path resolveRunRoot(
      final Config config, final Path projectRoot, final String runId) {
    return resolveRunRoot(resolveRunsRoot(config, projectRoot), requireRunId(runId));
  }

  public Path runsRoot() {
    return runsRoot;
  }

  public Path runRoot() {
    return runRoot;
  }

  public Path logsDir() {
    return runRoot.resolve(LOGS_DIR);
  }

  public Path analysisDir() {
    return runRoot.resolve(ANALYSIS_DIR);
  }

  public Path planDir() {
    return runRoot.resolve(PLAN_DIR);
  }

  public Path generationDir() {
    return runRoot.resolve(GENERATION_DIR);
  }

  public Path reportDir() {
    return runRoot.resolve(REPORT_DIR);
  }

  public Path junitReportsDir() {
    return reportDir().resolve(JUNIT_REPORTS_DIR);
  }

  public Path logFile(final Path configuredFilePath) {
    final String logFileName = resolveConfiguredLogFileName(configuredFilePath);
    return logsDir().resolve(logFileName);
  }

  public Path llmLogFile() {
    return logsDir().resolve(LLM_LOG_FILE);
  }

  public void ensureDirectories() throws IOException {
    Files.createDirectories(logsDir());
    Files.createDirectories(analysisDir());
    Files.createDirectories(planDir());
    Files.createDirectories(generationDir());
    Files.createDirectories(reportDir());
  }

  private static String resolveConfiguredLogFileName(final Path configuredFilePath) {
    if (configuredFilePath == null) {
      return DEFAULT_LOG_FILE;
    }
    final Path fileName = configuredFilePath.getFileName();
    if (fileName == null || fileName.toString().isBlank()) {
      return DEFAULT_LOG_FILE;
    }
    return fileName.toString();
  }

  private static String requireRunId(final String runId) {
    return Objects.requireNonNull(runId, argumentNullMessage("runId must not be null"));
  }

  private static Path resolveRunRoot(final Path runsRoot, final String runId) {
    return runsRoot.resolve(runId);
  }

  private static String resolveConfiguredLogsRoot(final Config config) {
    if (config == null || config.getExecution() == null) {
      return DEFAULT_RUNS_DIR;
    }
    return config.getExecution().getEffectiveLogsRoot();
  }

  private static String argumentNullMessage(final String argumentDescription) {
    return com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
        ARGUMENT_NULL_MESSAGE_KEY, argumentDescription);
  }
}
