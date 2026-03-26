package com.craftsmanbro.fulcraft.plugins.analysis.flow;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.plugins.analysis.contract.AnalysisPort;
import com.craftsmanbro.fulcraft.plugins.analysis.core.service.execution.AnalysisExecutionService;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Flow responsible for executing static analysis.
 *
 * <p>This flow delegates to {@link AnalysisExecutionService} and keeps the pipeline wiring stable.
 */
public class AnalysisFlow {

  private final AnalysisExecutionService analysisExecutionService;

  /**
   * Creates an AnalysisFlow with the specified analysis port.
   *
   * @param analysisPort the port to use for analysis
   */
  public AnalysisFlow(final AnalysisPort analysisPort) {
    Objects.requireNonNull(
        analysisPort,
        MessageSource.getMessage(
            "analysis.common.error.argument_null", "analysisPort must not be null"));
    this.analysisExecutionService = new AnalysisExecutionService(analysisPort);
  }

  /** Constructor for testing with a custom service. */
  AnalysisFlow(final AnalysisExecutionService analysisExecutionService) {
    this.analysisExecutionService = Objects.requireNonNull(analysisExecutionService);
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
    return analysisExecutionService.analyze(projectRoot, config);
  }

  /**
   * Validates the analysis result.
   *
   * @param result the analysis result to validate
   * @return validation result containing any issues found
   */
  public ValidationResult validate(final AnalysisResult result) {
    final AnalysisExecutionService.ValidationResult validation =
        analysisExecutionService.validate(result);
    return new ValidationResult(validation.valid(), validation.warning());
  }

  /**
   * Logs analysis statistics.
   *
   * @param result the analysis result
   */
  public void logStats(final AnalysisResult result) {
    analysisExecutionService.logStats(result);
  }

  /**
   * Gets summary statistics from the analysis result.
   *
   * @param result the analysis result
   * @return the statistics
   */
  public AnalysisStats getStats(final AnalysisResult result) {
    final AnalysisExecutionService.AnalysisStats stats = analysisExecutionService.getStats(result);
    return new AnalysisStats(stats.classCount(), stats.methodCount(), stats.errorCount());
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
