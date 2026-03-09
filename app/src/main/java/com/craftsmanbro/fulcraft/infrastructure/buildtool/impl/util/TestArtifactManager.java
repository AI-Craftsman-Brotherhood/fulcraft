package com.craftsmanbro.fulcraft.infrastructure.buildtool.impl.util;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.buildtool.impl.BuildCommandHelper;
import com.craftsmanbro.fulcraft.infrastructure.fs.model.RunPaths;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.Objects;

/** Manager for test artifacts (logs, reports). */
public class TestArtifactManager {

  private static final String XML_EXTENSION = ".xml";

  private static final String LOG_TAIL_HEADER = "--- Log Tail ---";

  private static final String LOG_TAIL_FOOTER = "----------------";

  private final FileOperationsHelper fileOps;

  public TestArtifactManager(final FileOperationsHelper fileOps) {
    this.fileOps =
        Objects.requireNonNull(
            fileOps,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "infra.common.error.argument_null", "fileOps must not be null"));
  }

  /** Prepares the logs directory for a test run. */
  public Path prepareLogsDir(
      final Config config, final Path projectRoot, final String projectId, final String runId)
      throws IOException {
    Objects.requireNonNull(
        config,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "config must not be null"));
    Objects.requireNonNull(
        projectRoot,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "projectRoot must not be null"));
    Objects.requireNonNull(
        projectId,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "projectId must not be null"));
    Objects.requireNonNull(
        runId,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "runId must not be null"));
    final RunPaths runPaths = RunPaths.from(config, projectRoot, runId);
    fileOps.createDirectories(runPaths.logsDir());
    fileOps.createDirectories(runPaths.reportDir());
    return runPaths.reportDir();
  }

  /** Collects test reports from standard locations to the logs directory. */
  public void collectReports(final Path projectRoot, final Path logsDir, final Config config) {
    Objects.requireNonNull(
        projectRoot,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "projectRoot must not be null"));
    Objects.requireNonNull(
        logsDir,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "logsDir must not be null"));
    Objects.requireNonNull(
        config,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "config must not be null"));
    final var reportDirectory = getReportDir(logsDir);
    try {
      fileOps.createDirectories(reportDirectory);
    } catch (IOException e) {
      Logger.error(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message", "Failed to create report dir: " + e.getMessage()),
          e);
      return;
    }
    final var reportSourceDirectories = BuildCommandHelper.getReportSources(config, projectRoot);
    for (final var reportSource : reportSourceDirectories) {
      copyXmlReports(reportSource, reportDirectory);
    }
    Logger.info(
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.log.message",
            "Collected JUnit reports to: " + reportDirectory.toAbsolutePath()));
  }

  public Path getReportDir(final Path logsDir) {
    Objects.requireNonNull(
        logsDir,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "logsDir must not be null"));
    return logsDir.resolve(RunPaths.JUNIT_REPORTS_DIR);
  }

  /** Copies XML reports from a source directory to a destination. */
  public void copyXmlReports(final Path source, final Path reportDest) {
    Objects.requireNonNull(
        source,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "source must not be null"));
    Objects.requireNonNull(
        reportDest,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "reportDest must not be null"));
    if (!Files.exists(source) || !Files.isDirectory(source)) {
      return;
    }
    try (var reportPaths = Files.walk(source)) {
      reportPaths
          .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(XML_EXTENSION))
          .sorted(Comparator.comparing(Path::toString))
          .forEach(reportFile -> copyReportFileToDestination(reportFile, reportDest));
    } catch (IOException e) {
      Logger.warn(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message",
              "Failed to walk report source: " + source + " -> " + e.getMessage()));
    }
  }

  private void copyReportFileToDestination(final Path source, final Path reportDest) {
    try {
      final Path destinationFile = reportDest.resolve(source.getFileName());
      Files.copy(source, destinationFile, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      Logger.warn(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message",
              "Failed to copy report: " + source + " -> " + e.getMessage()));
    }
  }

  /** Prints the tail of a log file (last N lines). */
  public void printLogTail(final Path file, final int lines) {
    Objects.requireNonNull(
        file,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "file must not be null"));
    try (var reader = Files.newBufferedReader(file)) {
      final var recentLines = new LinkedList<String>();
      String currentLine;
      while ((currentLine = reader.readLine()) != null) {
        // Keep only the requested tail while streaming the file.
        recentLines.add(currentLine);
        if (recentLines.size() > lines) {
          recentLines.removeFirst();
        }
      }
      Logger.info(LOG_TAIL_HEADER);
      for (final var logLine : recentLines) {
        Logger.info(logLine);
      }
      Logger.info(LOG_TAIL_FOOTER);
    } catch (IOException e) {
      Logger.warn(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message", "Failed to read log file."));
    }
  }
}
