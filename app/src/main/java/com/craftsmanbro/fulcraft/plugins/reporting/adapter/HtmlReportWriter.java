package com.craftsmanbro.fulcraft.plugins.reporting.adapter;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodSemantics;
import com.craftsmanbro.fulcraft.plugins.document.adapter.HtmlReportingStyle;
import com.craftsmanbro.fulcraft.plugins.document.core.util.DocumentUtils;
import com.craftsmanbro.fulcraft.plugins.reporting.contract.ReportWriteException;
import com.craftsmanbro.fulcraft.plugins.reporting.contract.ReportWriterPort;
import com.craftsmanbro.fulcraft.plugins.reporting.model.GenerationSummary;
import com.craftsmanbro.fulcraft.plugins.reporting.model.GenerationTaskResult;
import com.craftsmanbro.fulcraft.plugins.reporting.model.ReportData;
import com.craftsmanbro.fulcraft.plugins.reporting.model.ReportFormat;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Report writer implementation that generates HTML format reports.
 *
 * <p>This writer produces rich HTML reports suitable for:
 *
 * <ul>
 *   <li>Dashboard displays with styled tables
 *   <li>Detailed reports with visual indicators
 *   <li>Email reports with embedded styling
 *   <li>Archive and audit purposes
 * </ul>
 *
 * <h2>Styling</h2>
 *
 * <p>The generated HTML includes embedded CSS for:
 *
 * <ul>
 *   <li>Clean, modern typography
 *   <li>Responsive table layout
 *   <li>Status color coding (success/failure/warning)
 *   <li>Collapsible sections for large datasets
 * </ul>
 *
 * @see ReportWriterPort
 * @see ReportFormat#HTML
 */
public class HtmlReportWriter implements ReportWriterPort {

  private static final String DEFAULT_OUTPUT_DIR = "build/reports/test-generation";

  private static final String DEFAULT_FILENAME = "report.html";

  private static final DateTimeFormatter DATE_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

  private static final String HTML_DIV_CLOSE_INDENTED = "  </div>\n";

  private static final String HTML_DIV_CLOSE = "</div>\n";

  private static final String HTML_SPAN_CLOSE = "</span>\n";

  private static final String HTML_H2_CLOSE = "</h2>\n";

  private static final String HTML_CARD_OPEN = "<div class=\"card\">\n";

  private static final String HTML_CARD_HEADER_OPEN = "  <div class=\"card-header\">\n";

  private static final String HTML_TABLE_CLOSE = "</table>\n";

  private static final String HTML_SECTION_CLOSE = "</section>\n";

  private static final String HTML_TH_SEPARATOR = "</th><th>";

  private static final String HTML_TD_DATA_SORT_OPEN = "<td data-sort=\"";

  private static final String HTML_TD_CLOSE = "</td>";

  private static final String HTML_TR_CLOSE = "</tr>\n";

  private static final String HTML_TD_CODE_OPEN = "<td><code>";

  private static final String HTML_CODE_TD_CLOSE = "</code></td>";

  private static final String CSS_CLASS_SUCCESS = "success";

  private static final String CSS_CLASS_WARNING = "warning";

  private static final String CSS_CLASS_FAILURE = "failure";

  private static final String STATUS_FAILURE = "FAILURE";

  private static final String STATUS_SUCCESS = "SUCCESS";

  private static final String STATUS_SKIPPED = "SKIPPED";

  private final Path outputDirectory;

  private final String filename;

  private final String customCss;

  /** Creates a writer with default output settings. */
  public HtmlReportWriter() {
    this(null, null, null);
  }

  /**
   * Creates a writer with custom output settings.
   *
   * @param outputDirectory the directory to write reports to (null for default)
   * @param filename the filename for the report (null for default)
   * @param customCss additional CSS to include (null for none)
   */
  public HtmlReportWriter(
      final Path outputDirectory, final String filename, final String customCss) {
    this.outputDirectory = outputDirectory;
    this.filename = filename != null ? filename : DEFAULT_FILENAME;
    this.customCss = customCss;
  }

  @Override
  public void writeReport(final ReportData data, final Config config) throws ReportWriteException {
    Objects.requireNonNull(
        data,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "report.common.error.argument_null", "data must not be null"));
    Objects.requireNonNull(
        config,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "report.common.error.argument_null", "config must not be null"));
    final Path outputPath = resolveOutputPath(config);
    try {
      final String content = generateHtml(data, config);
      ensureDirectoryExists(outputPath);
      Files.writeString(outputPath, content);
    } catch (IOException e) {
      throw new ReportWriteException(
          MessageSource.getMessage(
              "report.error.write_failed",
              MessageSource.getMessage("report.format.html"),
              outputPath),
          e);
    }
  }

  /**
   * Generates HTML content from the report data.
   *
   * @param data the report data
   * @return the HTML content as a string
   */
  public String generateHtml(final ReportData data) {
    return generateHtml(data, null);
  }

  /**
   * Generates HTML content from the report data with optional config context.
   *
   * @param data the report data
   * @param config the configuration (optional)
   * @return the HTML content as a string
   */
  public String generateHtml(final ReportData data, final Config config) {
    final StringBuilder html = new StringBuilder();
    appendDocumentStart(html, data);
    appendHeader(html, data);
    appendSectionNav(html, data);
    appendSummarySection(html, data);
    appendAnalysisSummarySection(html, data);
    appendCoverageSection(html, data);
    appendAnalysisDetailsSection(html, data, config);
    appendTaskDetailsSection(html, data);
    appendExecutionSection(html, data);
    appendDocumentEnd(html);
    return html.toString();
  }

  private void appendDocumentStart(final StringBuilder html, final ReportData data) {
    html.append("<!DOCTYPE html>\n");
    html.append("<html lang=\"").append(htmlLang()).append("\">\n");
    html.append("<head>\n");
    html.append("  <meta charset=\"UTF-8\">\n");
    html.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
    html.append("  <title>")
        .append(MessageSource.getMessage("report.title"))
        .append(" - ")
        .append(escape(data.getProjectId()))
        .append("</title>\n");
    appendStyles(html);
    html.append("</head>\n");
    html.append("<body>\n");
    html.append("<div class=\"container\">\n");
  }

  private void appendStyles(final StringBuilder html) {
    html.append("  <style>\n");
    html.append(HtmlReportingStyle.css());
    if (customCss != null && !customCss.isBlank()) {
      html.append("\n    ").append(customCss).append("\n");
    }
    html.append("  </style>\n");
  }

  private void appendHeader(final StringBuilder html, final ReportData data) {
    html.append("<header>\n");
    html.append("  <div class=\"header-top\">\n");
    html.append("    <h1>").append(MessageSource.getMessage("report.title")).append("</h1>\n");
    html.append("    <span class=\"badge badge-info\">")
        .append(MessageSource.getMessage("report.format.html"))
        .append(HTML_SPAN_CLOSE);
    html.append(HTML_DIV_CLOSE_INDENTED);
    html.append("  <div class=\"meta\">\n");
    appendMetaItem(
        html,
        MessageSource.getMessage("report.label.project"),
        fallbackText(data.getProjectId(), "report.value.unknown_project"),
        false);
    appendMetaItem(
        html,
        MessageSource.getMessage("report.label.run_id"),
        fallbackText(data.getRunId(), "report.value.unknown"),
        true);
    appendMetaItem(
        html,
        MessageSource.getMessage("report.label.generated"),
        formatTime(data.getTimestamp()),
        false);
    if (data.getDurationMs() > 0) {
      appendMetaItem(
          html,
          MessageSource.getMessage("report.label.duration"),
          formatDuration(data.getDurationMs()),
          false);
    }
    html.append(HTML_DIV_CLOSE_INDENTED);
    html.append("</header>\n");
  }

  private void appendMetaItem(
      final StringBuilder html, final String label, final String value, final boolean codeValue) {
    html.append("    <span class=\"meta-item\">\n");
    html.append("      <span class=\"meta-label\">").append(escape(label)).append(HTML_SPAN_CLOSE);
    html.append("      <span class=\"meta-value\">");
    if (codeValue) {
      html.append("<code>").append(escape(value)).append("</code>");
    } else {
      html.append(escape(value));
    }
    html.append(HTML_SPAN_CLOSE);
    html.append("    </span>\n");
  }

  private String fallbackText(final String value, final String fallbackMessageKey) {
    if (value == null || value.isBlank()) {
      return MessageSource.getMessage(fallbackMessageKey);
    }
    return value;
  }

  private void appendSectionNav(final StringBuilder html, final ReportData data) {
    html.append("<nav class=\"section-nav\" aria-label=\"Report sections\">\n");
    appendSectionNavLink(
        html, "section-summary", MessageSource.getMessage("report.section.summary"));
    if (data.getAnalysisHumanSummary() != null && !data.getAnalysisHumanSummary().isBlank()) {
      appendSectionNavLink(
          html,
          "section-analysis-summary",
          MessageSource.getMessage("report.section.analysis_human_summary"));
    }
    if (data.getLineCoverage() != null || data.getBranchCoverage() != null) {
      appendSectionNavLink(
          html, "section-coverage", MessageSource.getMessage("report.section.coverage"));
    }
    if (data.getAnalysisResult() != null && data.getAnalysisResult().getClasses() != null) {
      appendSectionNavLink(
          html, "section-classes", MessageSource.getMessage("report.section.analyzed_classes"));
    }
    if (!data.getTaskResults().isEmpty()) {
      appendSectionNavLink(
          html, "section-task-details", MessageSource.getMessage("report.section.task_details"));
    }
    if (data.hasErrors() || data.hasWarnings()) {
      appendSectionNavLink(
          html,
          "section-execution-notes",
          MessageSource.getMessage("report.section.execution_notes"));
    }
    html.append("</nav>\n");
  }

  private void appendSectionNavLink(final StringBuilder html, final String id, final String label) {
    html.append("  <a class=\"section-link\" href=\"#")
        .append(id)
        .append("\">")
        .append(escape(label))
        .append("</a>\n");
  }

  private void appendSummarySection(final StringBuilder html, final ReportData data) {
    html.append("<section class=\"report-section\" id=\"section-summary\">\n");
    html.append("<h2>")
        .append(MessageSource.getMessage("report.section.summary"))
        .append(HTML_H2_CLOSE);
    html.append("<div class=\"stats-grid\">\n");
    final GenerationSummary summary = data.getSummary();
    if (summary != null) {
      appendStatBox(
          html,
          String.valueOf(summary.getTotalTasks()),
          MessageSource.getMessage("report.metric.total_tasks"),
          null);
      appendStatBox(
          html,
          String.valueOf(summary.getSucceeded()),
          MessageSource.getMessage("report.metric.succeeded"),
          CSS_CLASS_SUCCESS);
      appendStatBox(
          html,
          String.valueOf(summary.getFailed()),
          MessageSource.getMessage("report.metric.failed"),
          CSS_CLASS_FAILURE);
      appendStatBox(
          html,
          String.valueOf(summary.getSkipped()),
          MessageSource.getMessage("report.metric.skipped"),
          null);
      if (summary.getSuccessRate() != null) {
        final String successRateClass = resolveSuccessRateClass(summary.getSuccessRate());
        appendStatBox(
            html,
            pct(summary.getSuccessRate()),
            MessageSource.getMessage("report.metric.success_rate"),
            successRateClass);
      }
    } else {
      appendStatBox(
          html,
          String.valueOf(data.getTotalClassesAnalyzed()),
          MessageSource.getMessage("report.metric.classes"),
          null);
      appendStatBox(
          html,
          String.valueOf(data.getTotalMethodsAnalyzed()),
          MessageSource.getMessage("report.metric.methods"),
          null);
      appendStatBox(
          html,
          String.valueOf(data.getSelectedTasks().size()),
          MessageSource.getMessage("report.metric.selected"),
          CSS_CLASS_SUCCESS);
      appendStatBox(
          html,
          String.valueOf(data.getExcludedTaskCount()),
          MessageSource.getMessage("report.metric.excluded"),
          null);
    }
    html.append(HTML_DIV_CLOSE);
    html.append(HTML_SECTION_CLOSE);
  }

  private void appendStatBox(
      final StringBuilder html, final String value, final String label, final String colorClass) {
    html.append("<div class=\"stat-card\">\n");
    html.append("  <div class=\"stat-label\">").append(label).append(HTML_DIV_CLOSE);
    html.append("  <div class=\"stat-value");
    if (colorClass != null) {
      html.append(" ").append(colorClass);
    }
    html.append("\">").append(value).append(HTML_DIV_CLOSE);
    html.append(HTML_DIV_CLOSE);
  }

  private void appendCoverageSection(final StringBuilder html, final ReportData data) {
    if (data.getLineCoverage() == null && data.getBranchCoverage() == null) {
      return;
    }
    html.append("<section class=\"report-section\" id=\"section-coverage\">\n");
    html.append("<h2>")
        .append(MessageSource.getMessage("report.section.coverage"))
        .append(HTML_H2_CLOSE);
    html.append(HTML_CARD_OPEN);
    html.append(HTML_CARD_HEADER_OPEN);
    html.append("    <span class=\"card-title\">")
        .append( // Using simpler title
            MessageSource.getMessage("report.metric.line_coverage"))
        .append(HTML_SPAN_CLOSE);
    html.append(HTML_DIV_CLOSE_INDENTED);
    html.append("<div class=\"table-wrap\">\n");
    html.append("<table>\n");
    html.append("<tr><th>")
        .append(MessageSource.getMessage("report.table.metric"))
        .append(HTML_TH_SEPARATOR)
        .append(MessageSource.getMessage("report.table.value"))
        .append("</th></tr>\n");
    if (data.getLineCoverage() != null) {
      html.append("<tr><td>")
          .append(MessageSource.getMessage("report.metric.line_coverage"))
          .append("</td><td>")
          .append(pct(data.getLineCoverage()))
          .append("</td></tr>\n");
    }
    if (data.getBranchCoverage() != null) {
      html.append("<tr><td>")
          .append(MessageSource.getMessage("report.metric.branch_coverage"))
          .append("</td><td>")
          .append(pct(data.getBranchCoverage()))
          .append("</td></tr>\n");
    }
    html.append(HTML_TABLE_CLOSE);
    html.append(HTML_DIV_CLOSE);
    html.append(HTML_DIV_CLOSE);
    html.append(HTML_SECTION_CLOSE);
  }

  private void appendAnalysisSummarySection(final StringBuilder html, final ReportData data) {
    final String summary = data.getAnalysisHumanSummary();
    if (summary == null || summary.isBlank()) {
      return;
    }
    html.append("<section class=\"report-section\" id=\"section-analysis-summary\">\n");
    html.append("<h2>")
        .append(MessageSource.getMessage("report.section.analysis_human_summary"))
        .append(HTML_H2_CLOSE);
    html.append(HTML_CARD_OPEN);
    html.append("  <div class=\"analysis-summary-text\">")
        .append(escape(summary.trim()))
        .append(HTML_DIV_CLOSE_INDENTED);
    html.append(HTML_DIV_CLOSE);
    html.append(HTML_SECTION_CLOSE);
  }

  private void appendAnalysisDetailsSection(
      final StringBuilder html, final ReportData data, final Config config) {
    if (data.getAnalysisResult() == null || data.getAnalysisResult().getClasses() == null) {
      return;
    }
    final var classes = data.getAnalysisResult().getClasses();
    final int complexityThreshold = resolveComplexityThreshold(config);
    html.append("<section class=\"report-section\" id=\"section-classes\">\n");
    html.append("<h2>")
        .append(MessageSource.getMessage("report.section.analyzed_classes"))
        .append(HTML_H2_CLOSE);
    if (classes.isEmpty()) {
      html.append("<p class=\"empty-state\">")
          .append(MessageSource.getMessage("report.html.msg.no_classes"))
          .append("</p>\n");
      html.append(HTML_SECTION_CLOSE);
      return;
    }
    html.append(HTML_CARD_OPEN);
    html.append("<div class=\"table-wrap\">\n");
    html.append("<table class=\"sortable\">\n");
    html.append("<thead><tr><th data-sort=\"class\" data-sort-type=\"text\">")
        .append(MessageSource.getMessage("report.table.class"))
        .append("</th><th data-sort=\"package\" data-sort-type=\"text\">")
        .append(MessageSource.getMessage("report.table.package"))
        .append("</th><th data-sort=\"methods\" data-sort-type=\"number\">")
        .append(MessageSource.getMessage("report.table.method"))
        .append("</th><th data-sort=\"complexity\" data-sort-type=\"number\">")
        .append(MessageSource.getMessage("report.table.complexity"))
        .append("</th></tr></thead>\n");
    html.append("<tbody>\n");
    for (final var classInfo : classes) {
      String simpleName = MessageSource.getMessage("report.value.unknown_word");
      String packageName = MessageSource.getMessage("report.value.unknown_word");
      if (classInfo.getFqn() != null) {
        final int lastDot = classInfo.getFqn().lastIndexOf('.');
        if (lastDot > 0) {
          simpleName = classInfo.getFqn().substring(lastDot + 1);
          packageName = classInfo.getFqn().substring(0, lastDot);
        } else {
          simpleName = classInfo.getFqn();
          packageName = "";
        }
      }
      final int methodCount =
          MethodSemantics.countMethodsExcludingImplicitDefaultConstructors(classInfo);
      final ClassComplexitySummary complexity = computeClassComplexitySummary(classInfo);
      final boolean highlight =
          complexity.count() > 0
              && complexityThreshold > 0
              && complexity.max() > complexityThreshold;
      final String link = DocumentUtils.generateSourceAlignedReportPath(classInfo, ".html");
      html.append("<tr");
      if (highlight) {
        html.append(" class=\"attention-row\"");
      }
      html.append(">");
      html.append(HTML_TD_DATA_SORT_OPEN)
          .append(escape(simpleName))
          .append("\"><a href=\"")
          .append(escape(link))
          .append("\"><code>")
          .append(escape(simpleName))
          .append("</code></a></td>");
      html.append(HTML_TD_DATA_SORT_OPEN)
          .append(escape(packageName))
          .append("\">")
          .append(escape(packageName))
          .append(HTML_TD_CLOSE);
      html.append(HTML_TD_DATA_SORT_OPEN)
          .append(methodCount)
          .append("\">")
          .append(methodCount)
          .append(HTML_TD_CLOSE);
      html.append(HTML_TD_DATA_SORT_OPEN)
          .append(complexity.max())
          .append("\">")
          .append(escape(formatClassComplexity(complexity)))
          .append(HTML_TD_CLOSE);
      html.append(HTML_TR_CLOSE);
    }
    html.append("</tbody>\n");
    html.append(HTML_TABLE_CLOSE);
    html.append(HTML_DIV_CLOSE);
    html.append(HTML_DIV_CLOSE);
    html.append(HTML_SECTION_CLOSE);
  }

  private ClassComplexitySummary computeClassComplexitySummary(final ClassInfo classInfo) {
    final List<MethodInfo> methods =
        MethodSemantics.methodsExcludingImplicitDefaultConstructors(classInfo);
    if (methods.isEmpty()) {
      return new ClassComplexitySummary(0, 0.0, 0);
    }
    int max = 0;
    int sum = 0;
    int count = 0;
    for (final var method : methods) {
      if (method == null) {
        continue;
      }
      final int complexity = method.getCyclomaticComplexity();
      max = Math.max(max, complexity);
      sum += complexity;
      count++;
    }
    if (count == 0) {
      return new ClassComplexitySummary(0, 0.0, 0);
    }
    final double avg = sum / (double) count;
    return new ClassComplexitySummary(max, avg, count);
  }

  private String formatClassComplexity(final ClassComplexitySummary summary) {
    if (summary == null || summary.count() == 0) {
      return "-";
    }
    return MessageSource.getMessage(
        "report.html.complexity_summary",
        summary.max(),
        formatDecimal(summary.avg()),
        summary.count());
  }

  private int resolveComplexityThreshold(final Config config) {
    if (config == null) {
      return 0;
    }
    final var selectionRules = config.getSelectionRules();
    if (selectionRules == null) {
      return 0;
    }
    final Integer maxCyclomatic = selectionRules.getComplexity().getMaxCyclomatic();
    return maxCyclomatic != null ? maxCyclomatic : 0;
  }

  private void appendTaskDetailsSection(final StringBuilder html, final ReportData data) {
    final List<GenerationTaskResult> results = data.getTaskResults();
    if (results.isEmpty()) {
      return;
    }
    final var failures =
        results.stream().filter(r -> STATUS_FAILURE.equals(r.getStatus())).toList();
    final var successes =
        results.stream().filter(r -> STATUS_SUCCESS.equals(r.getStatus())).toList();
    final var skipped = results.stream().filter(r -> STATUS_SKIPPED.equals(r.getStatus())).toList();
    html.append("<section class=\"report-section\" id=\"section-task-details\">\n");
    html.append("<h2>")
        .append(MessageSource.getMessage("report.section.task_details"))
        .append(HTML_H2_CLOSE);
    // Failures (always expanded)
    if (!failures.isEmpty()) {
      final String errorsLabel = MessageSource.getMessage("report.section.errors");
      html.append(HTML_CARD_OPEN);
      html.append(HTML_CARD_HEADER_OPEN);
      html.append("    <span class=\"card-title\"><span class=\"status-chip status-failure\">")
          .append(escape(errorsLabel))
          .append("</span>")
          .append(MessageSource.getMessage("report.html.details.failures", failures.size()))
          .append(HTML_SPAN_CLOSE);
      html.append(HTML_DIV_CLOSE_INDENTED);
      appendTaskTable(html, failures, true);
      html.append(HTML_DIV_CLOSE);
    }
    // Successes (collapsed)
    if (!successes.isEmpty()) {
      final String succeededLabel = MessageSource.getMessage("report.metric.succeeded");
      html.append("<details open>\n");
      html.append("<summary><span class=\"status-chip status-success\">")
          .append(escape(succeededLabel))
          .append("</span>")
          .append(MessageSource.getMessage("report.html.details.successes", successes.size()))
          .append("</summary>\n");
      html.append(HTML_CARD_OPEN);
      appendTaskTable(html, successes, false);
      html.append(HTML_DIV_CLOSE);
      html.append("</details>\n");
    }
    // Skipped (collapsed)
    if (!skipped.isEmpty()) {
      final String skippedLabel = MessageSource.getMessage("report.metric.skipped");
      html.append("<details>\n");
      html.append("<summary><span class=\"status-chip status-neutral\">")
          .append(escape(skippedLabel))
          .append("</span>")
          .append(MessageSource.getMessage("report.html.details.skipped", skipped.size()))
          .append("</summary>\n");
      html.append(HTML_CARD_OPEN);
      appendTaskTable(html, skipped, true);
      html.append(HTML_DIV_CLOSE);
      html.append("</details>\n");
    }
    html.append(HTML_SECTION_CLOSE);
  }

  private void appendTaskTable(
      final StringBuilder html, final List<GenerationTaskResult> tasks, final boolean showError) {
    html.append("<div class=\"table-wrap\">\n");
    html.append("<table>\n");
    html.append("<tr><th>")
        .append(MessageSource.getMessage("report.table.task_id"))
        .append(HTML_TH_SEPARATOR)
        .append(MessageSource.getMessage("report.table.class"))
        .append(HTML_TH_SEPARATOR)
        .append(MessageSource.getMessage("report.table.method"))
        .append(HTML_TH_SEPARATOR)
        .append(MessageSource.getMessage("report.table.model"))
        .append(HTML_TH_SEPARATOR)
        .append(MessageSource.getMessage("report.table.tokens"))
        .append("</th>");
    if (showError) {
      html.append("<th>").append(MessageSource.getMessage("report.table.error")).append("</th>");
    }
    html.append(HTML_TR_CLOSE);
    for (final var task : tasks) {
      html.append("<tr>");
      html.append(HTML_TD_CODE_OPEN).append(escape(task.getTaskId())).append(HTML_CODE_TD_CLOSE);
      html.append(HTML_TD_CODE_OPEN).append(escape(task.getClassFqn())).append(HTML_CODE_TD_CLOSE);
      html.append(HTML_TD_CODE_OPEN)
          .append(escape(task.getMethodName()))
          .append(HTML_CODE_TD_CLOSE);
      html.append("<td>").append(escape(formatLlmModel(task))).append(HTML_TD_CLOSE);
      html.append("<td>").append(escape(formatTokenUsage(task))).append(HTML_TD_CLOSE);
      if (showError) {
        html.append("<td>").append(escape(task.getErrorMessage())).append(HTML_TD_CLOSE);
      }
      html.append(HTML_TR_CLOSE);
    }
    html.append(HTML_TABLE_CLOSE);
    html.append(HTML_DIV_CLOSE);
  }

  private String formatLlmModel(final GenerationTaskResult result) {
    if (result == null || result.getGenerationResult() == null) {
      return "";
    }
    return result.getGenerationResult().getLlmModelUsed().orElse("");
  }

  private String formatTokenUsage(final GenerationTaskResult result) {
    if (result == null || result.getGenerationResult() == null) {
      return "";
    }
    final int totalTokens = result.getGenerationResult().getTokenUsage();
    return totalTokens > 0 ? String.valueOf(totalTokens) : "";
  }

  private void appendExecutionSection(final StringBuilder html, final ReportData data) {
    if (!data.hasErrors() && !data.hasWarnings()) {
      return;
    }
    html.append("<section class=\"report-section\" id=\"section-execution-notes\">\n");
    html.append("<h2>")
        .append(MessageSource.getMessage("report.section.execution_notes"))
        .append(HTML_H2_CLOSE);
    html.append(HTML_CARD_OPEN);
    if (data.hasErrors()) {
      html.append("<h3>")
          .append(MessageSource.getMessage("report.section.errors"))
          .append("</h3>\n<ul class=\"note-list\">\n");
      for (final String error : data.getErrors()) {
        html.append("<li class=\"failure\">").append(escape(error)).append("</li>\n");
      }
      html.append("</ul>\n");
    }
    if (data.hasWarnings()) {
      html.append("<h3>")
          .append(MessageSource.getMessage("report.section.warnings"))
          .append("</h3>\n<ul class=\"note-list\">\n");
      for (final String warning : data.getWarnings()) {
        html.append("<li class=\"warning\">").append(escape(warning)).append("</li>\n");
      }
      html.append("</ul>\n");
    }
    html.append(HTML_DIV_CLOSE);
    html.append(HTML_SECTION_CLOSE);
  }

  private void appendDocumentEnd(final StringBuilder html) {
    // container
    html.append(HTML_DIV_CLOSE);
    html.append("<footer>")
        .append(MessageSource.getMessage("report.html.footer"))
        .append("</footer>\n");
    appendSortScript(html);
    html.append("</body>\n");
    html.append("</html>\n");
  }

  private void appendSortScript(final StringBuilder html) {
    html.append("<script>\n");
    html.append("(() => {\n");
    html.append("  const tables = document.querySelectorAll('table.sortable');\n");
    html.append("  tables.forEach((table) => {\n");
    html.append("    const headers = Array.from(table.querySelectorAll('th[data-sort]'));\n");
    html.append("    headers.forEach((th, index) => {\n");
    html.append("      th.addEventListener('click', () => {\n");
    html.append(
        "        const dir = th.getAttribute('data-sort-dir') === 'asc' ? 'desc' : 'asc';\n");
    html.append("        headers.forEach((h) => {\n");
    html.append("          h.removeAttribute('data-sort-dir');\n");
    html.append("          h.classList.remove('sort-asc', 'sort-desc');\n");
    html.append("        });\n");
    html.append("        th.setAttribute('data-sort-dir', dir);\n");
    html.append("        th.classList.add(dir === 'asc' ? 'sort-asc' : 'sort-desc');\n");
    html.append("        const tbody = table.tBodies[0];\n");
    html.append("        if (!tbody) return;\n");
    html.append("        const rows = Array.from(tbody.rows);\n");
    html.append("        const type = th.getAttribute('data-sort-type') || 'text';\n");
    html.append("        rows.sort((a, b) => {\n");
    html.append("          const aCell = a.children[index];\n");
    html.append("          const bCell = b.children[index];\n");
    html.append(
        "          const aVal = aCell ? (aCell.getAttribute('data-sort') || aCell.textContent.trim()) : '';\n");
    html.append(
        "          const bVal = bCell ? (bCell.getAttribute('data-sort') || bCell.textContent.trim()) : '';\n");
    html.append("          if (type === 'number') {\n");
    html.append("            const aNum = parseFloat(aVal);\n");
    html.append("            const bNum = parseFloat(bVal);\n");
    html.append("            const aSafe = Number.isFinite(aNum) ? aNum : 0;\n");
    html.append("            const bSafe = Number.isFinite(bNum) ? bNum : 0;\n");
    html.append("            return aSafe - bSafe;\n");
    html.append("          }\n");
    html.append(
        "          return aVal.localeCompare(bVal, undefined, { numeric: true, sensitivity: 'base' });\n");
    html.append("        });\n");
    html.append("        if (dir === 'desc') {\n");
    html.append("          rows.reverse();\n");
    html.append("        }\n");
    html.append("        rows.forEach((row) => tbody.appendChild(row));\n");
    html.append("      });\n");
    html.append("    });\n");
    html.append("  });\n");
    html.append("})();\n");
    html.append("</script>\n");
  }

  private record ClassComplexitySummary(int max, double avg, int count) {}

  private String resolveSuccessRateClass(final double successRate) {
    if (successRate >= 0.8) {
      return CSS_CLASS_SUCCESS;
    }
    if (successRate >= 0.5) {
      return CSS_CLASS_WARNING;
    }
    return CSS_CLASS_FAILURE;
  }

  private Path resolveOutputPath(final Config config) {
    Path baseDir = outputDirectory;
    if (baseDir == null && config.getProject() != null && config.getProject().getRoot() != null) {
      baseDir = Path.of(config.getProject().getRoot()).resolve(DEFAULT_OUTPUT_DIR);
    } else if (baseDir == null) {
      baseDir = Path.of(DEFAULT_OUTPUT_DIR);
    }
    return baseDir.resolve(filename);
  }

  private void ensureDirectoryExists(final Path path) throws IOException {
    final Path parent = path.getParent();
    if (parent != null && !Files.exists(parent)) {
      Files.createDirectories(parent);
    }
  }

  private String escape(final String s) {
    if (s == null) {
      return "";
    }
    return s.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }

  private String pct(final double value) {
    return formatDecimal(value * 100) + "%";
  }

  private String formatTime(final Instant instant) {
    if (instant == null) {
      return MessageSource.getMessage("report.value.na");
    }
    return DATE_FORMAT.format(instant);
  }

  private String formatDuration(final long durationMs) {
    if (durationMs < 1000) {
      return MessageSource.getMessage("report.duration.ms", durationMs);
    } else if (durationMs < 60_000) {
      return MessageSource.getMessage(
          "report.duration.seconds", formatDecimal(durationMs / 1000.0));
    } else {
      final long minutes = durationMs / 60_000;
      final long seconds = (durationMs % 60_000) / 1000;
      return MessageSource.getMessage("report.duration.minutes_seconds", minutes, seconds);
    }
  }

  private String formatDecimal(final double value) {
    Locale locale = MessageSource.getLocale();
    if (locale == null) {
      locale = Locale.ROOT;
    }
    return String.format(locale, "%.1f", value);
  }

  private String htmlLang() {
    final Locale locale = MessageSource.getLocale();
    if (locale == null || locale.getLanguage() == null || locale.getLanguage().isBlank()) {
      return "en";
    }
    return locale.getLanguage();
  }

  /**
   * Returns the report format this writer produces.
   *
   * @return {@link ReportFormat#HTML}
   */
  @Override
  public ReportFormat getFormat() {
    return ReportFormat.HTML;
  }
}
