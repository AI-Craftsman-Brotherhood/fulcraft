package com.craftsmanbro.fulcraft.plugins.reporting.adapter;

import com.craftsmanbro.fulcraft.kernel.pipeline.model.ReportTaskResult;
import com.craftsmanbro.fulcraft.plugins.reporting.contract.ReportFileAccessor;
import com.craftsmanbro.fulcraft.plugins.reporting.io.ReportFileFinder;
import com.craftsmanbro.fulcraft.plugins.reporting.io.ReportParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Default implementation of {@link ReportFileAccessor}. */
public class DefaultReportFileAccessor implements ReportFileAccessor {

  private final ReportFileFinder reportFileFinder;

  private final ReportParser reportParser;

  public DefaultReportFileAccessor() {
    this(new ReportFileFinder(), new ReportParser());
  }

  public DefaultReportFileAccessor(
      final ReportFileFinder reportFileFinder, final ReportParser reportParser) {
    this.reportFileFinder =
        Objects.requireNonNull(
            reportFileFinder,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "report.common.error.argument_null", "reportFileFinder must not be null"));
    this.reportParser =
        Objects.requireNonNull(
            reportParser,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "report.common.error.argument_null", "reportParser must not be null"));
  }

  @Override
  public Path resolveReportDir(final Path projectRoot) {
    return reportFileFinder.resolveReportDir(projectRoot);
  }

  @Override
  public boolean isReportsDirectory(final Path reportDir) {
    return reportDir != null && Files.exists(reportDir) && Files.isDirectory(reportDir);
  }

  @Override
  public boolean hasAnyReportFile(final Path reportDir) {
    return reportFileFinder.hasAnyReportFile(reportDir);
  }

  @Override
  public Optional<Path> findReportFile(
      final Path reportDir, final String baseTestName, final String testClassName) {
    return reportFileFinder.findReportFile(reportDir, baseTestName, testClassName);
  }

  @Override
  public boolean reportFileExists(final Path reportFile) {
    return reportFile != null && Files.exists(reportFile);
  }

  @Override
  public boolean parseReport(final Path reportFile, final ReportTaskResult result) {
    return reportParser.parseReport(reportFile, result);
  }
}
