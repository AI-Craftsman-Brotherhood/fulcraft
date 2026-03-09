package com.craftsmanbro.fulcraft.plugins.reporting.io;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.plugins.reporting.io.contract.CoverageReader;
import com.craftsmanbro.fulcraft.plugins.reporting.model.CoverageSummary;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Loads JaCoCo coverage reports for reporting. */
public class CoverageReportLoader {

  private static final String JACOCO_REPORT_PATH = "build/reports/jacoco/test/jacocoTestReport.xml";

  public CoverageSummary loadCoverage(
      final RunContext context, final Config config, final CoverageReader coverageReader) {
    Objects.requireNonNull(
        context,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "report.common.error.argument_null", "context must not be null"));
    Objects.requireNonNull(
        config,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "report.common.error.argument_null", "config must not be null"));
    Objects.requireNonNull(
        coverageReader,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "report.common.error.argument_null", "coverageReader must not be null"));
    final Path reportPath = resolveCoverageReportPath(context.getProjectRoot(), config);
    if (!Files.exists(reportPath)) {
      Logger.debug(MessageSource.getMessage("report.coverage.not_found", reportPath));
      return null;
    }
    try {
      return coverageReader.readCoverage(reportPath);
    } catch (IOException e) {
      Logger.warn(MessageSource.getMessage("report.coverage.read_failed", e.getMessage()));
      return null;
    }
  }

  private Path resolveCoverageReportPath(final Path projectRoot, final Config config) {
    Path coveragePath = null;
    if (config.getQualityGate() != null) {
      final String configuredPath = config.getQualityGate().getCoverageReportPath();
      if (configuredPath != null && !configuredPath.isBlank()) {
        coveragePath = Path.of(configuredPath);
      }
    }
    if (coveragePath != null) {
      return coveragePath.isAbsolute() ? coveragePath : projectRoot.resolve(coveragePath);
    }
    return projectRoot.resolve(JACOCO_REPORT_PATH);
  }
}
