package com.craftsmanbro.fulcraft.plugins.analysis.core.service.execution;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.plugins.analysis.contract.AnalysisPort;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Service responsible for executing static analysis.
 *
 * <p>This service wraps the AnalysisPort to provide:
 *
 * <ul>
 *   <li>Static analysis execution
 *   <li>Result validation
 *   <li>Analysis statistics logging
 * </ul>
 */
public class AnalysisExecutionService {

  private final AnalysisPort analysisPort;

  /**
   * Creates an AnalysisExecutionService with the specified analysis port.
   *
   * @param analysisPort the port to use for analysis
   */
  public AnalysisExecutionService(final AnalysisPort analysisPort) {
    this.analysisPort =
        Objects.requireNonNull(
            analysisPort,
            MessageSource.getMessage(
                "analysis.common.error.argument_null", "analysisPort must not be null"));
  }

  /**
   * Executes static analysis on the project.
   *
   * @param projectRoot the project root directory
   * @param config the configuration
   * @return the analysis result
   * @throws IOException if an I/O error occurs during analysis
   */
  public AnalysisResult analyze(final Path projectRoot, final Config config) throws IOException {
    Objects.requireNonNull(
        projectRoot,
        MessageSource.getMessage(
            "analysis.common.error.argument_null", "projectRoot must not be null"));
    Objects.requireNonNull(
        config,
        MessageSource.getMessage(
            "analysis.common.error.argument_null", "config must not be null"));
    Logger.debug(MessageSource.getMessage("analysis.execution.log.start", projectRoot));
    return analysisPort.analyze(projectRoot, config);
  }

  /**
   * Validates the analysis result.
   *
   * @param result the analysis result to validate
   * @return validation result containing any issues found
   */
  public ValidationResult validate(final AnalysisResult result) {
    if (result == null) {
      return new ValidationResult(
          false, MessageSource.getMessage("analysis.execution.validation.null_result"));
    }
    final StringBuilder warnings = new StringBuilder();
    final int errorCount = result.getAnalysisErrors().size();
    if (errorCount > 0) {
      warnings.append(MessageSource.getMessage("analysis.execution.validation.errors", errorCount));
    }
    if (result.getClasses().isEmpty()) {
      if (!warnings.isEmpty()) {
        warnings.append("; ");
      }
      warnings.append(MessageSource.getMessage("analysis.execution.validation.no_classes"));
    }
    if (!warnings.isEmpty()) {
      return new ValidationResult(true, warnings.toString());
    }
    return new ValidationResult(true, null);
  }

  /**
   * Logs analysis statistics.
   *
   * @param result the analysis result
   */
  public void logStats(final AnalysisResult result) {
    if (result == null) {
      Logger.warn(MessageSource.getMessage("analysis.execution.log.null_stats"));
      return;
    }
    final var classes = result.getClasses();
    long classCount = 0;
    long methodCount = 0;
    for (final var clazz : classes) {
      if (clazz == null) {
        continue;
      }
      classCount++;
      methodCount += clazz.getMethodCount();
    }
    Logger.info(
        MessageSource.getMessage("analysis.execution.log.complete", classCount, methodCount));
  }

  /**
   * Gets summary statistics from the analysis result.
   *
   * @param result the analysis result
   * @return the statistics
   */
  public AnalysisStats getStats(final AnalysisResult result) {
    if (result == null) {
      return new AnalysisStats(0, 0, 0);
    }
    final var classes = result.getClasses();
    int classCount = 0;
    int methodCount = 0;
    final int errorCount = result.getAnalysisErrors().size();
    for (final var clazz : classes) {
      if (clazz == null) {
        continue;
      }
      classCount++;
      methodCount += clazz.getMethodCount();
    }
    return new AnalysisStats(classCount, methodCount, errorCount);
  }

  /** Represents the result of validation. */
  public record ValidationResult(boolean valid, String warning) {

    public boolean hasWarning() {
      return warning != null && !warning.isBlank();
    }
  }

  /** Represents analysis statistics. */
  public record AnalysisStats(int classCount, int methodCount, int errorCount) {}
}
