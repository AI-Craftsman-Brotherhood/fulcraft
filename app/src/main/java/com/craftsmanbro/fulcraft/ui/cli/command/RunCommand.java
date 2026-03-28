package com.craftsmanbro.fulcraft.ui.cli.command;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.kernel.pipeline.PipelineRunner;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunMetadataKeys;
import com.craftsmanbro.fulcraft.plugins.reporting.contract.ReportWriteException;
import com.craftsmanbro.fulcraft.ui.cli.UiLogger;
import com.craftsmanbro.fulcraft.ui.cli.command.run.RunAnalysisReportExecutor;
import com.craftsmanbro.fulcraft.ui.cli.command.run.RunPipelineExecution;
import com.craftsmanbro.fulcraft.ui.cli.command.run.RunPipelineLogging;
import com.craftsmanbro.fulcraft.ui.cli.command.run.RunStepSelection;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;

/**
 * テスト生成パイプライン全体を実行するCLIコマンド。
 *
 * <p>このコマンドはパイプライン実行の細かい制御を提供します：
 *
 * <ul>
 *   <li>全ノードの実行、または特定ノードのみの実行
 *   <li>開始ノードと終了ノードの指定 (--from, --to)
 *   <li>検証用の Dry-run モード
 *   <li>Fail-fast（即時エラー終了）挙動
 * </ul>
 */
@Command(
    name = "run",
    description = "${command.run.description}",
    footer = "${command.run.footer}",
    resourceBundle = "messages",
    mixinStandardHelpOptions = true)
@Category("basic")
public class RunCommand extends AbstractCliCommand {

  private static final String NODE_ANALYZE = "analyze";
  private static final String NODE_GENERATE = "generate";
  private static final String NODE_REPORT = "report";
  private static final String NODE_DOCUMENT = "document";
  private static final String NODE_EXPLORE = "explore";

  private static final List<String> LLM_PROPAGATION_STAGES =
      List.of(NODE_ANALYZE, NODE_GENERATE, NODE_REPORT, NODE_DOCUMENT, NODE_EXPLORE);

  private static final Set<String> SUPPORTED_DOCUMENT_FORMATS =
      Set.of("markdown", "html", "pdf", "all");

  @ArgGroup(exclusive = true, multiplicity = "0..1")
  private StepsMode stepsMode;

  @Option(
      names = {"--fail-fast"},
      descriptionKey = "option.run.fail_fast")
  private boolean failFast;

  private boolean showSummary = true;

  @Option(
      names = {"--summary"},
      descriptionKey = "option.run.summary",
      negatable = true,
      defaultValue = "true")
  void setShowSummary(final boolean showSummary) {
    this.showSummary = showSummary;
  }

  @Option(
      names = {"--format", "--document-format"},
      descriptionKey = "option.run.document_format")
  private String documentFormat;

  @Option(
      names = {"--llm"},
      descriptionKey = "option.run.llm")
  private boolean useLlm;

  private static final class StepsMode {

    @Option(
        names = {"--steps", "--nodes"},
        descriptionKey = "option.run.steps",
        split = ",")
    private List<String> specificSteps;

    @ArgGroup(exclusive = false, multiplicity = "0..1")
    private StepRange stepRange;
  }

  private static final class StepRange {

    @Option(
        names = {"--from", "--from-node"},
        descriptionKey = "option.run.from")
    private String fromStep;

    @Option(
        names = {"--to", "--to-node"},
        descriptionKey = "option.run.to")
    private String toStep;
  }

  @Override
  protected String getCommandDescription() {
    return "Starting pipeline execution";
  }

  @Override
  protected Integer doCall(final Config config, final Path projectRoot) {
    Objects.requireNonNull(config, "Config is required");
    Objects.requireNonNull(projectRoot, "projectRoot is required");
    UiLogger.info("Starting pipeline execution...");

    final RunStepSelection stepSelection = enrichStepSelection(resolveStepSelection());
    applyLlmOverrides(config);

    RunContext context = null;
    try {
      context = super.createContext(config, projectRoot);
      context.withFailFast(failFast).withShowSummary(showSummary);

      if (isAnalysisReportOnly(stepSelection)) {
        applyDocumentFormatOverrides(config, stepSelection.specificSteps());
        propagateLlmUsage(context, stepSelection.specificSteps());
        final int exitCode = runAnalysisReportOnly(context);
        printDiagnostics(context);
        return exitCode;
      }
      if (isAnalysisReportDocumentOnly(stepSelection)) {
        applyDocumentFormatOverrides(config, stepSelection.specificSteps());
        propagateLlmUsage(context, stepSelection.specificSteps());
        final int exitCode = runAnalysisReportWithDocument(context);
        printDiagnostics(context);
        return exitCode;
      }

      final PipelineRunner runner = createRunner(config);
      final List<String> nodeOrder = new ArrayList<>(runner.getPipeline().getStageNodes().keySet());
      validateStepOptions(stepSelection, nodeOrder);

      final List<String> selectedNodes = stepSelection.resolveNodesToRun(nodeOrder);
      applyDocumentFormatOverrides(config, selectedNodes);
      propagateLlmUsage(context, selectedNodes);

      addPipelineLogging(runner);
      return runPipeline(
          runner,
          context,
          stepSelection.specificSteps(),
          stepSelection.fromStep(),
          stepSelection.toStep());
    } catch (IOException | ReportWriteException | RuntimeException e) {
      final RunContext diagnosticsContext = context;
      return handleCommandException(
          e, config, projectRoot, () -> printDiagnostics(diagnosticsContext));
    }
  }

  private RunStepSelection resolveStepSelection() {
    final var specificSteps = stepsMode != null ? stepsMode.specificSteps : null;
    final var range = stepsMode != null ? stepsMode.stepRange : null;
    final var fromStep = range != null ? range.fromStep : null;
    final var toStep = range != null ? range.toStep : null;
    return RunStepSelection.resolve(specificSteps, fromStep, toStep, null);
  }

  private RunStepSelection enrichStepSelection(final RunStepSelection selection) {
    Objects.requireNonNull(selection, "selection is required");
    final List<String> specificSteps = selection.specificSteps();
    if (specificSteps == null
        || specificSteps.isEmpty()
        || !specificSteps.contains(NODE_EXPLORE)
        || specificSteps.contains(NODE_DOCUMENT)) {
      return selection;
    }
    final List<String> enriched = new ArrayList<>(specificSteps.size() + 1);
    boolean insertedDocument = false;
    for (final String step : specificSteps) {
      if (NODE_EXPLORE.equals(step) && !insertedDocument) {
        enriched.add(NODE_DOCUMENT);
        insertedDocument = true;
      }
      enriched.add(step);
    }
    return RunStepSelection.resolve(enriched, selection.fromStep(), selection.toStep(), null);
  }

  private boolean isAnalysisReportOnly(final RunStepSelection selection) {
    Objects.requireNonNull(selection, "selection is required");
    return selection.isAnalysisReportOnly();
  }

  private boolean isAnalysisReportDocumentOnly(final RunStepSelection selection) {
    Objects.requireNonNull(selection, "selection is required");
    return selection.isAnalysisReportDocumentOnly();
  }

  private int runAnalysisReportOnly(final RunContext context)
      throws IOException, ReportWriteException {
    return new RunAnalysisReportExecutor(main.getServices(), engineType).execute(context);
  }

  private int runAnalysisReportWithDocument(final RunContext context)
      throws IOException, ReportWriteException {
    return new RunAnalysisReportExecutor(main.getServices(), engineType).execute(context, true);
  }

  private int runPipeline(
      final PipelineRunner runner,
      final RunContext context,
      final List<String> specificSteps,
      final String fromStep,
      final String toStep) {
    return new RunPipelineExecution(runner)
        .executeNodes(context, specificSteps, fromStep, toStep, this::printDiagnostics);
  }

  private void addPipelineLogging(final PipelineRunner runner) {
    RunPipelineLogging.attach(runner);
  }

  private void validateStepOptions(final RunStepSelection selection, final List<String> stepOrder) {
    Objects.requireNonNull(selection, "selection is required");
    selection.validate(spec, stepOrder);
  }

  private void applyLlmOverrides(final Config config) {
    Objects.requireNonNull(config, "Config is required");
    if (!useLlm) {
      return;
    }
    Config.DocsConfig docsConfig = config.getDocs();
    if (docsConfig == null) {
      docsConfig = new Config.DocsConfig();
      config.setDocs(docsConfig);
    }
    docsConfig.setUseLlm(true);
  }

  private void propagateLlmUsage(final RunContext context, final List<String> selectedNodes) {
    Objects.requireNonNull(context, "context is required");
    final boolean llmEnabled = isLlmEnabled(context.getConfig());
    context.putMetadata(RunMetadataKeys.LLM_ENABLED, llmEnabled);

    final Set<String> includedStages = resolveIncludedStages(selectedNodes);
    final Map<String, Boolean> stageFlags = new LinkedHashMap<>();
    for (final String stage : LLM_PROPAGATION_STAGES) {
      final boolean enabledForStage = llmEnabled && includedStages.contains(stage);
      context.putMetadata(RunMetadataKeys.llmStageKey(stage), enabledForStage);
      stageFlags.put(stage, enabledForStage);
    }
    context.putMetadata(RunMetadataKeys.LLM_STAGE_FLAGS, Map.copyOf(stageFlags));
  }

  private boolean isLlmEnabled(final Config config) {
    return config != null && config.getDocs() != null && config.getDocs().isUseLlm();
  }

  private Set<String> resolveIncludedStages(final List<String> selectedNodes) {
    final LinkedHashSet<String> included = new LinkedHashSet<>();
    if (selectedNodes == null || selectedNodes.isEmpty()) {
      return included;
    }
    for (final String nodeId : selectedNodes) {
      final String stage = classifyStage(nodeId);
      if (stage != null) {
        included.add(stage);
      }
    }
    return included;
  }

  private String classifyStage(final String nodeId) {
    if (nodeId == null || nodeId.isBlank()) {
      return null;
    }
    final String normalized = nodeId.trim().toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case NODE_ANALYZE -> NODE_ANALYZE;
      case NODE_REPORT -> NODE_REPORT;
      case NODE_DOCUMENT -> NODE_DOCUMENT;
      case NODE_EXPLORE -> NODE_EXPLORE;
      default -> NODE_GENERATE;
    };
  }

  private void applyDocumentFormatOverrides(final Config config, final List<String> selectedNodes) {
    Objects.requireNonNull(config, "Config is required");
    Objects.requireNonNull(selectedNodes, "selectedNodes is required");

    final String normalizedFormat = normalizeDocumentFormatOption();
    final boolean exploreIncluded = includesNode(selectedNodes, NODE_EXPLORE);
    if (normalizedFormat == null && !exploreIncluded) {
      return;
    }
    Config.DocsConfig docsConfig = config.getDocs();
    if (docsConfig == null) {
      docsConfig = new Config.DocsConfig();
      config.setDocs(docsConfig);
    }
    if (normalizedFormat != null) {
      docsConfig.setFormat(normalizedFormat);
      applyExploreReportFormatOverride(config, selectedNodes, normalizedFormat);
      return;
    }
    if (docsConfig.isMarkdownFormat()) {
      docsConfig.setFormat("html");
      applyExploreReportFormatOverride(config, selectedNodes, "html");
    }
  }

  private void applyExploreReportFormatOverride(
      final Config config,
      final List<String> selectedNodes,
      final String normalizedDocumentFormat) {
    Objects.requireNonNull(config, "Config is required");
    Objects.requireNonNull(selectedNodes, "selectedNodes is required");
    if (normalizedDocumentFormat == null || normalizedDocumentFormat.isBlank()) {
      return;
    }
    if (!"html".equalsIgnoreCase(normalizedDocumentFormat)) {
      return;
    }
    if (!includesNode(selectedNodes, NODE_EXPLORE)) {
      return;
    }
    final Config.OutputConfig outputConfig = config.getOutput();
    final Config.OutputConfig.FormatConfig formatConfig = outputConfig.getFormat();
    final String rawReportFormat = formatConfig.getReport();
    if (rawReportFormat == null
        || rawReportFormat.isBlank()
        || "markdown".equalsIgnoreCase(rawReportFormat)
        || "md".equalsIgnoreCase(rawReportFormat)) {
      formatConfig.setReport("html");
    }
  }

  private boolean includesNode(final List<String> selectedNodes, final String targetNode) {
    Objects.requireNonNull(selectedNodes, "selectedNodes is required");
    Objects.requireNonNull(targetNode, "targetNode is required");
    if (selectedNodes.isEmpty()) {
      return false;
    }
    return selectedNodes.contains(targetNode);
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
          "Unsupported --format value: " + documentFormat + ". Use markdown, html, pdf, or all.");
    }
    return normalized;
  }
}
