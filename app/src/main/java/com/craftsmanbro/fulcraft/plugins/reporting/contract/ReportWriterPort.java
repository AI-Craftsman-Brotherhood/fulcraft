package com.craftsmanbro.fulcraft.plugins.reporting.contract;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.plugins.reporting.model.ReportData;

/**
 * Port interface for writing pipeline execution reports.
 *
 * <p>This is the primary entry point for the <strong>REPORT phase</strong> of the FUL pipeline.
 * This port is invoked as the <strong>final stage</strong> after all analysis, selection,
 * generation, and run phases have completed.
 *
 * <h2>Pipeline Context</h2>
 *
 * <pre>
 *   ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌─────────────┐   ┌──────────────────┐
 *   │ Analyze  │ → │  Select  │ → │ Generate │ → │    Run      │ → │      Report      │
 *   │  Phase   │   │  Phase   │   │  Phase   │   │   Phase     │   │      Phase       │
 *   └──────────┘   └──────────┘   └──────────┘   └─────────────┘   └──────────────────┘
 *                                                                           │
 *                                                                           ▼
 *                                                              ┌────────────────────────┐
 *                                                              │ ReportWriterPort       │
 *                                                              │ (this interface)       │
 *                                                              └────────────────────────┘
 *                                                                           │
 *                               ┌───────────────────────────────────────────┼───────────┐
 *                               │                                           │           │
 *                               ▼                                           ▼           ▼
 *                       ┌──────────────┐                           ┌──────────┐  ┌──────────┐
 *                       │ JSON Report  │                           │ Markdown │  │   HTML   │
 *                       │ (summary.json)│                          │  Report  │  │  Report  │
 *                       └──────────────┘                           └──────────┘  └──────────┘
 * </pre>
 *
 * <h2>Responsibilities</h2>
 *
 * <p>The REPORT phase is responsible for aggregating and persisting all pipeline outputs:
 *
 * <ul>
 *   <li><strong>Test Generation Results</strong> - Success/failure counts, per-method outcomes
 *   <li><strong>Coverage Metrics</strong> - Line and branch coverage from JaCoCo integration
 *   <li><strong>Plugin Extensions</strong> - Framework-specific details supplied via extensions
 *   <li><strong>Trend Analysis</strong> - Historical comparison with previous runs
 * </ul>
 *
 * <h2>Output Formats</h2>
 *
 * <p>Implementations may produce reports in multiple formats based on configuration:
 *
 * <ul>
 *   <li><strong>JSON</strong> - Machine-readable format for CI/CD integration
 *   <li><strong>Markdown</strong> - Human-readable summary for pull requests
 *   <li><strong>HTML</strong> - Rich format with charts and detailed breakdowns
 * </ul>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * ReportWriterPort writer = ...; // obtained via dependency injection
 *
 * // Build report data from pipeline context
 * GenerationSummary summary = context.getMetadata("generation.summary", GenerationSummary.class).orElse(null);
 * ReportData data = ReportData.builder()
 *         .summary(summary)
 *         .taskResults(summary != null ? summary.getDetails() : List.of())
 *         .extensions(pluginExtensions)
 *         .lineCoverage(0.85)
 *         .branchCoverage(0.72)
 *         .build();
 *
 * // Write the report
 * writer.writeReport(data, config);
 * }</pre>
 *
 * <h2>Configuration</h2>
 *
 * <p>Report behavior is controlled via {@link Config.OutputConfig}, which specifies:
 *
 * <ul>
 *   <li>Output directory for generated reports
 *   <li>Desired output formats (JSON, Markdown, HTML)
 *   <li>Whether to include detailed per-task breakdowns
 *   <li>Console output verbosity
 * </ul>
 *
 * <h2>Thread Safety</h2>
 *
 * <p>Implementations should be thread-safe. Multiple reports may be written concurrently in
 * parallel pipeline execution scenarios.
 *
 * @see ReportData
 * @see Config
 * @see com.craftsmanbro.fulcraft.plugins.reporting.flow.ReportFlow
 */
public interface ReportWriterPort {

  /**
   * Returns the report format produced by this writer.
   *
   * @return the report format
   */
  default com.craftsmanbro.fulcraft.plugins.reporting.model.ReportFormat getFormat() {
    return com.craftsmanbro.fulcraft.plugins.reporting.model.ReportFormat.MARKDOWN;
  }

  /**
   * Writes a report containing test generation results and analysis data.
   *
   * <p>This method persists the aggregated pipeline results to files in the configured output
   * format(s) and location. It is the main entry point for the REPORT phase, called after all
   * generation and run work is complete.
   *
   * <p><strong>Preconditions:</strong>
   *
   * <ul>
   *   <li>{@code data} must not be null and must contain a valid {@link ReportData#getSummary()
   *       summary}
   *   <li>{@code config} must not be null and should contain valid output configuration
   *   <li>The output directory (from config) must be writable
   * </ul>
   *
   * <p><strong>Postconditions:</strong>
   *
   * <ul>
   *   <li>Report files are written to the configured output directory
   *   <li>If multiple formats are configured, all formats are generated
   *   <li>Any I/O errors result in a {@link ReportWriteException}
   * </ul>
   *
   * <p><strong>Output Files:</strong><br>
   * Depending on configuration, this method may produce:
   *
   * <ul>
   *   <li>{@code summary.json} - Machine-readable summary
   *   <li>{@code report.md} - Human-readable Markdown report
   *   <li>{@code report.html} - Rich HTML report with visualizations
   * </ul>
   *
   * @param data The aggregated report data containing all pipeline results. Must not be null.
   * @param config The configuration containing output settings (directory, format, etc.). Must not
   *     be null.
   * @throws ReportWriteException if report generation or file writing fails
   * @throws NullPointerException if data or config is null
   */
  void writeReport(ReportData data, Config config) throws ReportWriteException;
}
