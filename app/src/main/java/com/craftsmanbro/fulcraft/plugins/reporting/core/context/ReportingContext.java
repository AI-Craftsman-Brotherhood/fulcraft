package com.craftsmanbro.fulcraft.plugins.reporting.core.context;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.fs.model.RunPaths;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.plugins.reporting.io.contract.CoverageReader;
import java.nio.file.Path;
import java.util.Objects;

/** Holds runtime dependencies and configuration for report generation. */
public final class ReportingContext {

  private final RunContext runContext;

  private final Config config;

  private final Path outputDirectory;

  private final CoverageReader coverageReader;

  public ReportingContext(
      final RunContext runContext, final Config config, final CoverageReader coverageReader) {
    this(runContext, config, null, coverageReader);
  }

  public ReportingContext(
      final RunContext runContext,
      final Config config,
      final Path outputDirectory,
      final CoverageReader coverageReader) {
    this.runContext =
        Objects.requireNonNull(
            runContext,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "report.common.error.argument_null", "runContext must not be null"));
    this.config =
        Objects.requireNonNull(
            config,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "report.common.error.argument_null", "config must not be null"));
    this.outputDirectory = outputDirectory;
    this.coverageReader =
        Objects.requireNonNull(
            coverageReader,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "report.common.error.argument_null", "coverageReader must not be null"));
  }

  public RunContext getRunContext() {
    return runContext;
  }

  public Config getConfig() {
    return config;
  }

  public CoverageReader getCoverageReader() {
    return coverageReader;
  }

  public Path resolveOutputDirectory() {
    if (outputDirectory != null) {
      return outputDirectory;
    }
    return RunPaths.from(runContext.getConfig(), runContext.getProjectRoot(), runContext.getRunId())
        .reportDir();
  }
}
