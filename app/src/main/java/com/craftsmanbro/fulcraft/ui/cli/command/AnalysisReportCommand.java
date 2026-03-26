package com.craftsmanbro.fulcraft.ui.cli.command;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.fs.impl.RunIdGenerator;
import com.craftsmanbro.fulcraft.infrastructure.fs.model.RunPaths;
import com.craftsmanbro.fulcraft.infrastructure.json.impl.JsonMapperFactory;
import com.craftsmanbro.fulcraft.infrastructure.security.impl.SecretMasker;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.kernel.pipeline.model.RunDirectories;
import com.craftsmanbro.fulcraft.plugins.analysis.context.AnalysisResultContext;
import com.craftsmanbro.fulcraft.plugins.analysis.io.AnalysisResultWriter;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.reporting.contract.ReportWriteException;
import com.craftsmanbro.fulcraft.plugins.reporting.model.ReportData;
import com.craftsmanbro.fulcraft.ui.cli.UiLogger;
import com.craftsmanbro.fulcraft.ui.cli.command.support.AnalysisReportExecutionSupport;
import com.craftsmanbro.fulcraft.ui.cli.command.support.CommandMessageSupport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.stream.Stream;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import tools.jackson.databind.ObjectMapper;

/**
 * CLI command for generating analysis-only reports.
 *
 * <p>This command executes the analysis engine and produces reports using the reporting module,
 * without requiring selection/generation tasks.
 */
@Command(
    name = "report",
    aliases = {"report"},
    description = "${command.analysis_report.description}",
    footer = "${command.analysis_report.footer}",
    resourceBundle = "messages",
    mixinStandardHelpOptions = true)
@Category("analysis")
public class AnalysisReportCommand extends BaseCliCommand {

  private static final String DEFAULT_ENGINE_TYPE = "composite";

  @Option(
      names = {"-p", "--project-root"},
      descriptionKey = "option.common.project_root")
  private Path projectRootOption;

  @Option(
      names = {"--run-id"},
      descriptionKey = "option.report.run_id")
  private String runId;

  @CommandLine.Parameters(
      index = "0",
      arity = "0..1",
      descriptionKey = "option.common.project_root_positional")
  private Path projectRootPositional;

  @Option(
      names = {"-f", "--files"},
      descriptionKey = "option.common.files",
      split = ",")
  private List<String> files;

  @Option(
      names = {"-d", "--dirs"},
      descriptionKey = "option.common.dirs",
      split = ",")
  private List<String> dirs;

  @Option(
      names = {"--exclude-tests"},
      descriptionKey = "option.common.exclude_tests")
  private Boolean excludeTests;

  @Option(
      names = {"--engine"},
      descriptionKey = "option.common.engine",
      defaultValue = DEFAULT_ENGINE_TYPE)
  private String engineType;

  @Option(
      names = {"--format"},
      descriptionKey = "option.analysis_report.format")
  private String format;

  @Option(
      names = {"--output"},
      descriptionKey = "option.analysis_report.output")
  private Path outputPath;

  @Option(
      names = {"-v", "--verbose"},
      descriptionKey = "option.common.verbose")
  private boolean verbose;

  private ResourceBundle resourceBundle;

  public void setResourceBundle(final ResourceBundle resourceBundle) {
    this.resourceBundle = resourceBundle;
  }

  private String msg(final String key, final Object... args) {
    resourceBundle = CommandMessageSupport.resolve(resourceBundle);
    return CommandMessageSupport.message(resourceBundle, key, args);
  }

  @Override
  protected Integer doCall(final Config config, final Path projectRoot) {
    try {
      applyFormatOverride(config);
      UiLogger.stdout(msg("analysis_report.start", projectRoot));
      final AnalysisResult result;
      final String effectiveRunId;
      if (runId != null) {
        effectiveRunId = runId;
        result = loadAnalysisResult(config, projectRoot, runId);
      } else {
        effectiveRunId = RunIdGenerator.newRunId();
        result = analyzeProject(config, projectRoot);
        // Save analysis artifacts
        final Path runDir = RunDirectories.resolveRunRoot(config, projectRoot, effectiveRunId);
        final Path analysisDir = runDir.resolve(RunPaths.ANALYSIS_DIR);
        new AnalysisResultWriter()
            .saveAnalysisResult(result, analysisDir, projectRoot, config.getProject().getId());
      }
      if (result.getClasses().isEmpty()) {
        UiLogger.stdout(msg("analysis_report.no_classes"));
        return 0;
      }
      final RunContext context =
          new RunContext(projectRoot.toAbsolutePath(), config, effectiveRunId);
      AnalysisResultContext.set(context, result);
      final AnalysisReportExecutionSupport.ReportOutput output =
          AnalysisReportExecutionSupport.resolveOutputOverride(projectRoot, context, outputPath);
      AnalysisReportExecutionSupport.generateReports(
          context, result, output, true, () -> buildReportData(context, result));
      UiLogger.debug(msg("analysis_report.generated", output.outputDirectory()));
      return 0;
    } catch (IOException | ReportWriteException e) {
      UiLogger.error(msg("analysis_report.failed", e.getMessage()));
      if (verbose) {
        UiLogger.stderr(SecretMasker.maskStackTrace(e));
      }
      return 1;
    }
  }

  private void applyFormatOverride(final Config config) {
    if (format == null || config == null) {
      return;
    }
    config.getOutput().getFormat().setReport(format.trim().toLowerCase(Locale.ROOT));
  }

  private AnalysisResult analyzeProject(final Config config, final Path projectRoot)
      throws IOException {
    final var analysisPort = main.getServices().createAnalysisPort(engineType);
    return analysisPort.analyze(projectRoot, config);
  }

  private ReportData buildReportData(final RunContext context, final AnalysisResult result) {
    Objects.requireNonNull(context, "context must not be null");
    Objects.requireNonNull(result, "result must not be null");
    AnalysisResultContext.set(context, result);
    return ReportData.fromContext(context);
  }

  private AnalysisResult loadAnalysisResult(
      final Config config, final Path projectRoot, final String runId) {
    final Path runDir = RunDirectories.resolveRunRoot(config, projectRoot, runId);
    if (!Files.isDirectory(runDir)) {
      throw new ParameterException(
          spec.commandLine(), msg("analysis_report.error.run_dir_not_found", runDir));
    }
    final Path analysisDir = runDir.resolve(RunPaths.ANALYSIS_DIR);
    if (!Files.isDirectory(analysisDir)) {
      throw new ParameterException(
          spec.commandLine(), msg("analysis_report.error.analysis_dir_not_found", analysisDir));
    }
    final ObjectMapper mapper = JsonMapperFactory.create();
    final AnalysisResult analysisResult = new AnalysisResult();
    final List<ClassInfo> classes = new ArrayList<>();
    try (Stream<Path> stream = Files.walk(analysisDir)) {
      final List<Path> analysisFiles =
          stream
              .filter(Files::isRegularFile)
              .filter(
                  p -> {
                    final Path fileName = p.getFileName();
                    if (fileName == null) {
                      return false;
                    }
                    final String name = fileName.toString();
                    return name.startsWith("analysis_") && name.endsWith(".json");
                  })
              .toList();
      for (final Path p : analysisFiles) {
        final List<ClassInfo> partialClasses = loadPartialAnalysis(mapper, p);
        if (!partialClasses.isEmpty()) {
          classes.addAll(partialClasses);
        }
      }
    } catch (IOException e) {
      throw new ParameterException(
          spec.commandLine(),
          msg("analysis_report.error.read_analysis_dir_failed", e.getMessage()),
          e);
    }
    analysisResult.setClasses(classes);
    UiLogger.info(msg("analysis_report.info.classes_loaded", classes.size(), runId));
    return analysisResult;
  }

  private List<ClassInfo> loadPartialAnalysis(final ObjectMapper mapper, final Path path) {
    try {
      final AnalysisResult partial = mapper.readValue(path.toFile(), AnalysisResult.class);
      return partial.getClasses();
    } catch (tools.jackson.core.JacksonException e) {
      UiLogger.warn(msg("analysis_report.warn.analysis_file_load_failed", path, e.getMessage()));
      return List.of();
    }
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
  protected java.util.Optional<Boolean> getExcludeTests() {
    return java.util.Optional.ofNullable(excludeTests);
  }

  @Override
  protected boolean isVerboseEnabled() {
    return verbose;
  }
}
