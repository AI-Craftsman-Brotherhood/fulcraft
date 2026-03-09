package com.craftsmanbro.fulcraft.ui.cli.command.support;

import com.craftsmanbro.fulcraft.plugins.reporting.adapter.HtmlReportWriter;
import com.craftsmanbro.fulcraft.plugins.reporting.adapter.JsonReportWriter;
import com.craftsmanbro.fulcraft.plugins.reporting.adapter.MarkdownReportWriter;
import com.craftsmanbro.fulcraft.plugins.reporting.adapter.YamlReportWriter;
import com.craftsmanbro.fulcraft.plugins.reporting.contract.ReportWriterPort;
import com.craftsmanbro.fulcraft.plugins.reporting.model.ReportFormat;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Objects;

/** Shared writer construction for analysis report commands. */
public final class AnalysisReportWriters {

  private AnalysisReportWriters() {}

  public static EnumMap<ReportFormat, ReportWriterPort> create(
      final Path outputDirectory, final ReportFormat primaryFormat, final String filenameOverride) {
    Objects.requireNonNull(outputDirectory, "outputDirectory must not be null");
    Objects.requireNonNull(primaryFormat, "primaryFormat must not be null");
    final EnumMap<ReportFormat, ReportWriterPort> writers = new EnumMap<>(ReportFormat.class);
    writers.put(
        ReportFormat.MARKDOWN,
        new MarkdownReportWriter(
            outputDirectory,
            resolveFilenameOverride(primaryFormat, ReportFormat.MARKDOWN, filenameOverride)));
    writers.put(
        ReportFormat.JSON,
        new JsonReportWriter(
            outputDirectory,
            resolveFilenameOverride(primaryFormat, ReportFormat.JSON, filenameOverride),
            true));
    writers.put(
        ReportFormat.HTML,
        new HtmlReportWriter(
            outputDirectory,
            resolveFilenameOverride(primaryFormat, ReportFormat.HTML, filenameOverride),
            null));
    writers.put(
        ReportFormat.YAML,
        new YamlReportWriter(
            outputDirectory,
            resolveFilenameOverride(primaryFormat, ReportFormat.YAML, filenameOverride),
            true));
    return writers;
  }

  private static String resolveFilenameOverride(
      final ReportFormat primaryFormat,
      final ReportFormat candidate,
      final String overrideFilename) {
    if (overrideFilename == null) {
      return null;
    }
    return primaryFormat == candidate ? overrideFilename : null;
  }
}
