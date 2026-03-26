package com.craftsmanbro.fulcraft.ui.cli.command;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.kernel.pipeline.PipelineNodeIds;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;

/** CLI command for generating exploration artifacts from analysis results. */
@Command(
    name = "explore",
    description = "${command.explore.description}",
    footer = "${command.explore.footer}",
    resourceBundle = "messages",
    mixinStandardHelpOptions = true)
@Category("analysis")
public class ExploreCommand extends AbstractCliCommand {

  private static final Set<String> SUPPORTED_DOCUMENT_FORMATS =
      Set.of("markdown", "html", "pdf", "all");

  private static final List<String> EXPLORE_NODES =
      List.of(
          PipelineNodeIds.ANALYZE,
          PipelineNodeIds.DOCUMENT,
          PipelineNodeIds.REPORT,
          PipelineNodeIds.EXPLORE);

  @Option(
      names = {"--llm"},
      descriptionKey = "option.explore.llm")
  private boolean useLlm;

  @Option(
      names = {"--format", "--document-format"},
      descriptionKey = "option.explore.format")
  private String documentFormat;

  @Override
  protected Integer doCall(final Config config, final Path projectRoot) {
    prepareExploreConfig(config);
    return super.doCall(config, projectRoot);
  }

  @Override
  protected List<String> getNodeIds() {
    return EXPLORE_NODES;
  }

  @Override
  protected String getCommandDescription() {
    return MessageSource.getMessage("explore.command.description");
  }

  private void prepareExploreConfig(final Config config) {
    Objects.requireNonNull(config, "Config is required");
    ensureExploreStagesEnabled(config);
    final Config.DocsConfig docsConfig = ensureDocsConfig(config);
    applyDocumentFormatOverride(docsConfig);
    ensureExploreDocumentFormat(docsConfig);
    applyLlmOverride(docsConfig);
    applyExploreReportFormatOverride(config, docsConfig.getFormat());
  }

  private void ensureExploreStagesEnabled(final Config config) {
    final Config.PipelineConfig pipelineConfig = config.getPipeline();
    config.setPipeline(pipelineConfig);
    final Set<String> enabled = new LinkedHashSet<>(pipelineConfig.getStages());
    enabled.add("analyze");
    enabled.add("document");
    enabled.add("report");
    enabled.add("explore");
    pipelineConfig.setStages(new ArrayList<>(enabled));
  }

  private Config.DocsConfig ensureDocsConfig(final Config config) {
    Config.DocsConfig docsConfig = config.getDocs();
    if (docsConfig == null) {
      docsConfig = new Config.DocsConfig();
      config.setDocs(docsConfig);
    }
    return docsConfig;
  }

  private void ensureExploreDocumentFormat(final Config.DocsConfig docsConfig) {
    if (docsConfig.isMarkdownFormat()) {
      docsConfig.setFormat("html");
    }
  }

  private void applyDocumentFormatOverride(final Config.DocsConfig docsConfig) {
    final String normalizedFormat = normalizeDocumentFormatOption();
    if (normalizedFormat != null) {
      docsConfig.setFormat(normalizedFormat);
    }
  }

  private String normalizeDocumentFormatOption() {
    if (documentFormat == null || documentFormat.isBlank()) {
      return null;
    }
    final String normalized = documentFormat.trim().toLowerCase(Locale.ROOT);
    if ("md".equals(normalized)) {
      return "markdown";
    }
    if (!SUPPORTED_DOCUMENT_FORMATS.contains(normalized)) {
      throw new ParameterException(
          spec.commandLine(),
          MessageSource.getMessage("explore.error.unsupported_format", documentFormat));
    }
    return normalized;
  }

  private void applyExploreReportFormatOverride(
      final Config config, final String documentFormatValue) {
    if (documentFormatValue == null || documentFormatValue.isBlank()) {
      return;
    }
    final String normalizedDocumentFormat = documentFormatValue.trim().toLowerCase(Locale.ROOT);
    if (!"html".equals(normalizedDocumentFormat) && !"all".equals(normalizedDocumentFormat)) {
      return;
    }
    final Config.OutputConfig.FormatConfig formatConfig = config.getOutput().getFormat();
    final String rawReportFormat = formatConfig.getReport();
    if (rawReportFormat == null
        || rawReportFormat.isBlank()
        || "markdown".equalsIgnoreCase(rawReportFormat)
        || "md".equalsIgnoreCase(rawReportFormat)
        || "json".equalsIgnoreCase(rawReportFormat)) {
      formatConfig.setReport("html");
    }
  }

  private void applyLlmOverride(final Config.DocsConfig docsConfig) {
    if (useLlm) {
      docsConfig.setUseLlm(true);
    }
  }
}
