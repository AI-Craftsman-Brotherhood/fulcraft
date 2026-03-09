package com.craftsmanbro.fulcraft.ui.cli.command;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.config.ConfigOverride;
import com.craftsmanbro.fulcraft.infrastructure.security.impl.SecretMasker;
import com.craftsmanbro.fulcraft.plugins.analysis.core.service.dynamic.DynamicResolutionApplier;
import com.craftsmanbro.fulcraft.plugins.analysis.core.service.dynamic.DynamicResolutions;
import com.craftsmanbro.fulcraft.plugins.analysis.flow.SourcePreprocessingFlow;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.document.config.DocumentOverrides;
import com.craftsmanbro.fulcraft.plugins.document.flow.DocumentFlow;
import com.craftsmanbro.fulcraft.ui.cli.UiLogger;
import com.craftsmanbro.fulcraft.ui.cli.command.support.CommandMessageSupport;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;

/** CLI command for generating human-readable documentation from source code analysis. */
@Command(
    name = "document",
    aliases = {"doc"},
    description = "${command.document.description}",
    footer = "${command.document.footer}",
    resourceBundle = "messages",
    mixinStandardHelpOptions = true)
@Category("other")
public class DocumentCommand extends BaseCliCommand {

  @Option(
      names = {"-p", "--project-root"},
      descriptionKey = "option.document.project_root")
  private Path projectRootOption;

  @CommandLine.Parameters(
      index = "0",
      arity = "0..1",
      descriptionKey = "option.document.project_root_positional")
  private Path projectRootPositional;

  @Option(
      names = {"-f", "--files"},
      descriptionKey = "option.document.files",
      split = ",")
  private List<String> files;

  @Option(
      names = {"-d", "--dirs"},
      descriptionKey = "option.document.dirs",
      split = ",")
  private List<String> dirs;

  @Option(
      names = {"-o", "--output"},
      descriptionKey = "option.document.output")
  private Path outputDirOption;

  @Option(
      names = {"--single-file"},
      descriptionKey = "option.document.single_file")
  private boolean singleFile;

  @Option(
      names = {"--llm"},
      descriptionKey = "option.document.llm")
  private boolean useLlm;

  @Option(
      names = {"--format"},
      descriptionKey = "option.document.format")
  private String format;

  @Option(
      names = {"--diagram"},
      descriptionKey = "option.document.diagram")
  private boolean diagram;

  @Option(
      names = {"--include-tests"},
      descriptionKey = "option.document.include_tests")
  private boolean includeTests;

  @Option(
      names = {"--diagram-format"},
      descriptionKey = "option.document.diagram_format")
  private String diagramFormat;

  @Option(
      names = {"-v", "--verbose"},
      descriptionKey = "option.document.verbose")
  private boolean verbose;

  private java.util.ResourceBundle resourceBundle;

  public void setResourceBundle(final java.util.ResourceBundle resourceBundle) {
    this.resourceBundle = resourceBundle;
  }

  private String msg(final String key, final Object... args) {
    resourceBundle = CommandMessageSupport.resolve(resourceBundle);
    return CommandMessageSupport.message(resourceBundle, key, args);
  }

  @Override
  protected Integer doCall(final Config config, final Path projectRoot) {
    try {
      UiLogger.stdout(msg("document.start", projectRoot));
      final AnalysisResult result = analyzeProject(config, projectRoot);
      if (result.getClasses().isEmpty()) {
        UiLogger.stdout(msg("document.no_classes"));
        return 0;
      }
      Path outputPath = outputDirOption;
      if (outputPath == null) {
        final String runId =
            com.craftsmanbro.fulcraft.infrastructure.fs.impl.RunIdGenerator.newRunId();
        final Path runsRoot =
            com.craftsmanbro.fulcraft.kernel.pipeline.model.RunDirectories.resolveRunsRoot(
                config, projectRoot);
        outputPath = runsRoot.resolve(runId).resolve("docs");
      }
      final DocumentFlow.Result generationResult =
          createDocumentFlow()
              .generate(result, config, projectRoot, outputPath, createProgressListener());
      UiLogger.stdout(
          msg("document.complete", generationResult.totalCount(), generationResult.outputPath()));
      return 0;
    } catch (DocumentFlow.ValidationException e) {
      throw new ParameterException(spec.commandLine(), e.getMessage(), e);
    } catch (IOException e) {
      UiLogger.error(msg("document.failed", e.getMessage()));
      if (verbose) {
        UiLogger.stderr(SecretMasker.maskStackTrace(e));
      }
      return 1;
    }
  }

  protected AnalysisResult analyzeProject(final Config config, final Path projectRoot)
      throws IOException {
    final var analysisPort = main.getServices().createAnalysisPort("composite");
    final AnalysisResult result = analysisPort.analyze(projectRoot, config);
    enrichAnalysisResultWithDynamicResolutions(result, config, projectRoot);
    return result;
  }

  private void enrichAnalysisResultWithDynamicResolutions(
      final AnalysisResult result, final Config config, final Path projectRoot) {
    if (result == null || result.getClasses().isEmpty()) {
      return;
    }
    try {
      final SourcePreprocessingFlow sourcePreprocessingFlow = new SourcePreprocessingFlow();
      final var symbolIndex =
          sourcePreprocessingFlow.buildProjectSymbolIndex(projectRoot, config, null);
      final var externalConfigValues =
          sourcePreprocessingFlow.loadExternalConfigValues(projectRoot, config);
      final DynamicResolutions dynamicResolutions = new DynamicResolutions();
      dynamicResolutions.setSymbolIndex(symbolIndex);
      dynamicResolutions.setExternalConfigValues(externalConfigValues);
      boolean enableInterprocedural = false;
      int callsiteLimit = 20;
      boolean debugDynamicResolution = false;
      boolean experimentalCandidateEnum = false;
      if (config != null && config.getAnalysis() != null) {
        enableInterprocedural = config.getAnalysis().getEnableInterproceduralResolution();
        callsiteLimit = config.getAnalysis().getInterproceduralCallsiteLimit();
        debugDynamicResolution = config.getAnalysis().getDebugDynamicResolution();
        experimentalCandidateEnum = config.getAnalysis().getExperimentalCandidateEnum();
      }
      dynamicResolutions.resolve(
          result,
          projectRoot,
          enableInterprocedural,
          callsiteLimit,
          debugDynamicResolution,
          experimentalCandidateEnum);
      DynamicResolutionApplier.apply(result, dynamicResolutions.getResolutions());
    } catch (RuntimeException e) {
      UiLogger.warn(msg("document.dynamic_resolution_skipped", e.getMessage()));
    }
  }

  protected DocumentFlow createDocumentFlow() {
    return new DocumentFlow(main.getServices()::createDecoratedLlmClient);
  }

  private DocumentFlow.ProgressListener createProgressListener() {
    return new DocumentFlow.ProgressListener() {

      @Override
      public void onMarkdownGenerating() {
        UiLogger.stdout(msg("document.markdown.generating"));
      }

      @Override
      public void onMarkdownComplete(final int count) {
        UiLogger.stdout(msg("document.markdown.complete", count));
      }

      @Override
      public void onHtmlGenerating() {
        UiLogger.stdout(msg("document.html.generating"));
      }

      @Override
      public void onPdfGenerating() {
        UiLogger.stdout(msg("document.pdf.generating"));
      }

      @Override
      public void onDiagramGenerating() {
        UiLogger.stdout(msg("document.diagram.generating"));
      }

      @Override
      public void onSingleFileComplete(final Path outputFile) {
        UiLogger.stdout(msg("document.single.complete", outputFile));
      }

      @Override
      public void onLlmGenerating() {
        UiLogger.stdout(msg("document.llm.generating"));
      }

      @Override
      public void onLlmComplete(final int count, final Path outputPath) {
        UiLogger.stdout(msg("document.llm.complete", count, outputPath));
      }
    };
  }

  @Override
  protected List<ConfigOverride> buildConfigOverrides(final Path projectRoot) {
    final List<ConfigOverride> overrides = new ArrayList<>(super.buildConfigOverrides(projectRoot));
    overrides.add(
        new DocumentOverrides()
            .withFiles(files)
            .withDirs(dirs)
            .withFormat(format)
            .withDiagram(diagram)
            .withIncludeTests(includeTests)
            .withUseLlm(useLlm)
            .withSingleFile(singleFile)
            .withDiagramFormat(diagramFormat));
    return overrides;
  }

  @Override
  protected Path getProjectRootOption() {
    return projectRootOption;
  }

  @Override
  protected Path getProjectRootPositional() {
    return projectRootPositional;
  }

  @Override
  protected List<String> getFiles() {
    return files;
  }

  @Override
  protected List<String> getDirs() {
    return dirs;
  }

  @Override
  protected boolean isVerboseEnabled() {
    return verbose;
  }

  @Override
  protected boolean shouldApplyProjectRootToConfig() {
    return false;
  }
}
