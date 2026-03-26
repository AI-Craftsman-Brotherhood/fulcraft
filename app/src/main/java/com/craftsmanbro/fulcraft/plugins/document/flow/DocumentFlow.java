package com.craftsmanbro.fulcraft.plugins.document.flow;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.llm.contract.LlmClientPort;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.PromptRedactionService;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.kernel.pipeline.model.RunDirectories;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.document.adapter.DiagramDocumentGenerator;
import com.craftsmanbro.fulcraft.plugins.document.adapter.HtmlDocumentGenerator;
import com.craftsmanbro.fulcraft.plugins.document.adapter.LlmDocumentGenerator;
import com.craftsmanbro.fulcraft.plugins.document.adapter.MarkdownDocumentGenerator;
import com.craftsmanbro.fulcraft.plugins.document.adapter.PdfDocumentGenerator;
import com.craftsmanbro.fulcraft.plugins.document.contract.DocumentGenerator;
import com.craftsmanbro.fulcraft.plugins.document.core.service.document.TaskLinkRegistrationService;
import com.craftsmanbro.fulcraft.plugins.document.core.service.document.TestLinkResolver;
import com.craftsmanbro.fulcraft.plugins.document.core.util.DocumentUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Orchestrates documentation generation from analysis results.
 *
 * <p>This flow is intentionally CLI-agnostic. Callers provide output events via {@link
 * ProgressListener} and convert {@link ValidationException} into UI-specific errors if needed.
 */
public class DocumentFlow {

  private static final String DEFAULT_OUTPUT_DIR = ".ful/docs";

  private static final String FORMAT_MARKDOWN = "markdown";

  private static final String TEST_LINKS_START = "<!-- ful:test-links:start -->";

  private static final String TEST_LINKS_END = "<!-- ful:test-links:end -->";

  private static final String PLAN_DIR = "plan";

  private final BiFunction<Config, Path, LlmClientPort> llmClientFactory;

  private final TaskLinkRegistrationService taskLinkRegistrationService;

  public DocumentFlow(final BiFunction<Config, Path, LlmClientPort> llmClientFactory) {
    this(llmClientFactory, new TaskLinkRegistrationService());
  }

  DocumentFlow(
      final BiFunction<Config, Path, LlmClientPort> llmClientFactory,
      final TaskLinkRegistrationService taskLinkRegistrationService) {
    this.llmClientFactory =
        Objects.requireNonNull(
            llmClientFactory,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "document.common.error.argument_null", "llmClientFactory"));
    this.taskLinkRegistrationService =
        Objects.requireNonNull(
            taskLinkRegistrationService,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "document.common.error.argument_null", "taskLinkRegistrationService"));
  }

  public Result generate(
      final AnalysisResult result,
      final Config config,
      final Path projectRoot,
      final Path outputDirOverride)
      throws IOException, ValidationException {
    return generate(result, config, projectRoot, outputDirOverride, ProgressListener.noop());
  }

  public Result generate(
      final AnalysisResult result,
      final Config config,
      final Path projectRoot,
      final Path outputDirOverride,
      final ProgressListener progress)
      throws IOException, ValidationException {
    Objects.requireNonNull(
        result,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "document.common.error.argument_null", "result"));
    Objects.requireNonNull(
        config,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "document.common.error.argument_null", "config"));
    Objects.requireNonNull(
        projectRoot,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "document.common.error.argument_null", "projectRoot"));
    final ProgressListener effectiveProgress =
        progress != null ? progress : ProgressListener.noop();
    final Path outputPath = resolveOutputDir(projectRoot, config, outputDirOverride);
    Files.createDirectories(outputPath);
    int totalCount = 0;
    final String effectiveFormat = getEffectiveFormat(config);
    validateFormat(effectiveFormat);
    if (isLlmEnabled(config)) {
      validateLlmConfig(config);
      effectiveProgress.onLlmGenerating();
      final int llmCount = generateWithLlm(result, config, outputPath, projectRoot);
      effectiveProgress.onLlmComplete(llmCount, outputPath);
      totalCount += llmCount;
      if (isIncludeTestsEnabled(config)) {
        appendTestLinksToMarkdown(result, outputPath, config, projectRoot, "_detail.md");
      }
    } else if (isSingleFileEnabled(config)) {
      final Path singleFile = generateCombinedDocument(result, outputPath, projectRoot, config);
      effectiveProgress.onSingleFileComplete(singleFile);
      totalCount = 1;
    } else {
      totalCount +=
          generateByFormat(
              result, outputPath, config, projectRoot, effectiveFormat, effectiveProgress);
    }
    if (isDiagramEnabled(config)) {
      effectiveProgress.onDiagramGenerating();
      final Path diagramPath = outputPath.resolve("diagrams");
      final DiagramDocumentGenerator diagramGenerator = DiagramDocumentGenerator.fromConfig(config);
      totalCount += diagramGenerator.generate(result, diagramPath, config);
    }
    return new Result(totalCount, outputPath);
  }

  private int generateByFormat(
      final AnalysisResult result,
      final Path outputPath,
      final Config config,
      final Path projectRoot,
      final String effectiveFormat,
      final ProgressListener progress)
      throws IOException {
    int count = 0;
    if ("all".equalsIgnoreCase(effectiveFormat)) {
      count +=
          generateMarkdown(
              result, outputPath.resolve(FORMAT_MARKDOWN), config, projectRoot, progress);
      count += generateHtml(result, outputPath.resolve("html"), config, progress);
      count += generatePdf(result, outputPath.resolve("pdf"), config, progress);
    } else if ("html".equalsIgnoreCase(effectiveFormat)) {
      count += generateHtml(result, outputPath, config, progress);
    } else if ("pdf".equalsIgnoreCase(effectiveFormat)) {
      count += generatePdf(result, outputPath, config, progress);
    } else {
      count += generateMarkdown(result, outputPath, config, projectRoot, progress);
    }
    return count;
  }

  private int generateMarkdown(
      final AnalysisResult result,
      final Path outputPath,
      final Config config,
      final Path projectRoot,
      final ProgressListener progress)
      throws IOException {
    Files.createDirectories(outputPath);
    progress.onMarkdownGenerating();
    final MarkdownDocumentGenerator generator = new MarkdownDocumentGenerator();
    final int count = generator.generate(result, outputPath, config);
    if (isIncludeTestsEnabled(config)) {
      appendTestLinksToMarkdown(result, outputPath, config, projectRoot, ".md");
    }
    progress.onMarkdownComplete(count);
    return count;
  }

  private int generateHtml(
      final AnalysisResult result,
      final Path outputPath,
      final Config config,
      final ProgressListener progress)
      throws IOException {
    Files.createDirectories(outputPath);
    progress.onHtmlGenerating();
    final DocumentGenerator generator = new HtmlDocumentGenerator();
    return generator.generate(result, outputPath, config);
  }

  private int generatePdf(
      final AnalysisResult result,
      final Path outputPath,
      final Config config,
      final ProgressListener progress)
      throws IOException {
    Files.createDirectories(outputPath);
    progress.onPdfGenerating();
    final DocumentGenerator generator = new PdfDocumentGenerator();
    return generator.generate(result, outputPath, config);
  }

  private int generateWithLlm(
      final AnalysisResult result,
      final Config config,
      final Path outputPath,
      final Path projectRoot)
      throws IOException {
    final LlmClientPort llmClient = llmClientFactory.apply(config, projectRoot);
    final PromptRedactionService promptRedactionService =
        PromptRedactionService.fromConfig(config, projectRoot);
    final LlmDocumentGenerator llmGenerator =
        new LlmDocumentGenerator(llmClient, promptRedactionService);
    return llmGenerator.generate(result, outputPath, config);
  }

  private void appendTestLinksToMarkdown(
      final AnalysisResult result,
      final Path outputPath,
      final Config config,
      final Path projectRoot,
      final String extension)
      throws IOException {
    final TestLinkResolver resolver = createTestLinkResolver(config, projectRoot, outputPath);
    populateTestLinksFromTasks(resolver, config, projectRoot);
    for (final ClassInfo classInfo : result.getClasses()) {
      final Path filePath = resolveMarkdownOutputFile(outputPath, classInfo, extension);
      if (Files.exists(filePath)) {
        final String content = Files.readString(filePath, StandardCharsets.UTF_8);
        final String testLinksSection = resolver.generateTestLinksSection(classInfo, true);
        final String updated = replaceTestLinksSection(content, testLinksSection);
        Files.writeString(filePath, updated, StandardCharsets.UTF_8);
      }
    }
  }

  private Path resolveMarkdownOutputFile(
      final Path outputPath, final ClassInfo classInfo, final String extension) {
    final String sourceAlignedPath =
        DocumentUtils.generateSourceAlignedReportPath(classInfo, extension);
    final Path sourceAlignedFile = outputPath.resolve(sourceAlignedPath);
    if (Files.exists(sourceAlignedFile)) {
      return sourceAlignedFile;
    }
    final String legacyName = DocumentUtils.generateFileName(classInfo.getFqn(), extension);
    final Path legacyFile = outputPath.resolve(legacyName);
    if (Files.exists(legacyFile)) {
      return legacyFile;
    }
    return sourceAlignedFile;
  }

  private TestLinkResolver createTestLinkResolver(
      final Config config, final Path projectRoot, final Path outputPath) {
    final Path resolvedTestOutputRoot = resolveTestOutputRoot(config, projectRoot);
    final Path linkBase = outputPath.toAbsolutePath().normalize();
    final Path linkRoot = relativizePath(linkBase, resolvedTestOutputRoot);
    return new TestLinkResolver(linkRoot, linkBase);
  }

  private void populateTestLinksFromTasks(
      final TestLinkResolver resolver, final Config config, final Path projectRoot) {
    final Path tasksFile = resolveTasksFileForLinks(config, projectRoot);
    if (tasksFile == null) {
      return;
    }
    taskLinkRegistrationService.registerTaskLinksFromTasksFile(resolver, tasksFile);
  }

  private Path resolveTasksFileForLinks(final Config config, final Path projectRoot) {
    final Path runsRoot = RunDirectories.resolveRunsRoot(config, projectRoot);
    if (!Files.isDirectory(runsRoot)) {
      return null;
    }
    final Path latestRunDir = findLatestRunDir(runsRoot);
    if (latestRunDir == null) {
      return null;
    }
    return resolveTasksFile(latestRunDir);
  }

  private Path findLatestRunDir(final Path runsRoot) {
    try (Stream<Path> runDirs = Files.list(runsRoot)) {
      return runDirs
          .filter(Files::isDirectory)
          .filter(dir -> resolveTasksFile(dir) != null)
          .max(Comparator.comparingLong(this::safeLastModified))
          .orElse(null);
    } catch (IOException e) {
      Logger.warn(msg("document.flow.scan_runs_failed", e.getMessage()));
      return null;
    }
  }

  private Path resolveTasksFile(final Path runDir) {
    final Path planDir = runDir.resolve(PLAN_DIR);
    if (Files.isDirectory(planDir)) {
      final Path tasksFile = taskLinkRegistrationService.resolveExistingTasksFile(planDir);
      if (tasksFile != null) {
        return tasksFile;
      }
    }
    return taskLinkRegistrationService.resolveExistingTasksFile(runDir);
  }

  private long safeLastModified(final Path path) {
    try {
      return Files.getLastModifiedTime(path).toMillis();
    } catch (IOException e) {
      return 0L;
    }
  }

  private Path resolveTestOutputRoot(final Config config, final Path projectRoot) {
    Path testOutputRoot = null;
    if (config.getDocs() != null && config.getDocs().getTestOutputRoot() != null) {
      testOutputRoot = Path.of(config.getDocs().getTestOutputRoot());
    }
    if (testOutputRoot == null) {
      testOutputRoot = Path.of("src/test/java");
    }
    if (testOutputRoot.isAbsolute()) {
      return testOutputRoot.normalize();
    }
    return projectRoot.resolve(testOutputRoot).normalize();
  }

  private Path relativizePath(final Path base, final Path target) {
    final Path normalizedBase = base.toAbsolutePath().normalize();
    final Path normalizedTarget = target.toAbsolutePath().normalize();
    final Path baseRoot = normalizedBase.getRoot();
    final Path targetRoot = normalizedTarget.getRoot();
    if (baseRoot != null && baseRoot.equals(targetRoot)) {
      try {
        return normalizedBase.relativize(normalizedTarget);
      } catch (IllegalArgumentException ignored) {
        // Fallback to absolute target path.
      }
    }
    return normalizedTarget;
  }

  private Path resolveOutputDir(
      final Path projectRoot, final Config config, final Path outputDirOverride) {
    if (outputDirOverride != null) {
      return outputDirOverride.isAbsolute()
          ? outputDirOverride
          : projectRoot.resolve(outputDirOverride);
    }
    if (config.getProject() != null
        && config.getProject().getDocsOutput() != null
        && !config.getProject().getDocsOutput().isBlank()) {
      final Path configPath = Path.of(config.getProject().getDocsOutput());
      return configPath.isAbsolute() ? configPath : projectRoot.resolve(configPath);
    }
    return projectRoot.resolve(DEFAULT_OUTPUT_DIR);
  }

  private String getEffectiveFormat(final Config config) {
    if (config.getDocs() != null) {
      final String configFormat = config.getDocs().getFormat();
      if (configFormat != null && !configFormat.isBlank()) {
        return configFormat;
      }
    }
    return FORMAT_MARKDOWN;
  }

  private void validateFormat(final String effectiveFormat) throws ValidationException {
    if (effectiveFormat == null || effectiveFormat.isBlank()) {
      return;
    }
    final String normalized = effectiveFormat.trim().toLowerCase(Locale.ROOT);
    if (!FORMAT_MARKDOWN.equals(normalized)
        && !"html".equals(normalized)
        && !"pdf".equals(normalized)
        && !"all".equals(normalized)) {
      throw new ValidationException(
          msg("document.flow.validation.unsupported_format", effectiveFormat));
    }
  }

  private void validateLlmConfig(final Config config) throws ValidationException {
    if (config.getLlm() == null) {
      throw new ValidationException(msg("document.flow.validation.llm_config_required"));
    }
  }

  private boolean isLlmEnabled(final Config config) {
    return config.getDocs() != null && config.getDocs().isUseLlm();
  }

  private boolean isSingleFileEnabled(final Config config) {
    return config.getDocs() != null && config.getDocs().isSingleFile();
  }

  private boolean isDiagramEnabled(final Config config) {
    return config.getDocs() != null && config.getDocs().isDiagram();
  }

  private boolean isIncludeTestsEnabled(final Config config) {
    return config.getDocs() != null && config.getDocs().isIncludeTests();
  }

  private String replaceTestLinksSection(final String content, final String testLinksSection) {
    final String block = TEST_LINKS_START + "\n" + testLinksSection + "\n" + TEST_LINKS_END;
    final Pattern pattern =
        Pattern.compile(
            "(?s)" + Pattern.quote(TEST_LINKS_START) + ".*?" + Pattern.quote(TEST_LINKS_END));
    if (pattern.matcher(content).find()) {
      return pattern.matcher(content).replaceAll(block);
    }
    return content + "\n\n" + block;
  }

  private Path generateCombinedDocument(
      final AnalysisResult result,
      final Path outputPath,
      final Path projectRoot,
      final Config config)
      throws IOException {
    Files.createDirectories(outputPath);
    final MarkdownDocumentGenerator generator = new MarkdownDocumentGenerator();
    final StringBuilder combined = new StringBuilder();
    combined.append("# ").append(MessageSource.getMessage("document.report.title")).append("\n\n");
    combined
        .append("**")
        .append(MessageSource.getMessage("document.report.date"))
        .append("**: ")
        .append(LocalDateTime.now())
        .append("\n");
    combined
        .append("**")
        .append(MessageSource.getMessage("document.report.class_count"))
        .append("**: ")
        .append(result.getClasses().size())
        .append("\n\n");
    combined.append("---\n\n");
    combined.append("## ").append(MessageSource.getMessage("document.report.toc")).append("\n\n");
    for (final ClassInfo classInfo : result.getClasses()) {
      final String simpleName =
          classInfo.getFqn().substring(classInfo.getFqn().lastIndexOf('.') + 1);
      final String anchor = classInfo.getFqn().replace('.', '-').toLowerCase(Locale.ROOT);
      combined.append("- [").append(simpleName).append("](#").append(anchor).append(")\n");
    }
    combined.append("\n---\n\n");
    final TestLinkResolver resolver =
        isIncludeTestsEnabled(config)
            ? createTestLinkResolver(config, projectRoot, outputPath)
            : null;
    if (resolver != null) {
      populateTestLinksFromTasks(resolver, config, projectRoot);
    }
    for (final ClassInfo classInfo : result.getClasses()) {
      combined.append(generator.generateClassDocument(classInfo));
      if (resolver != null) {
        combined.append(resolver.generateTestLinksSection(classInfo, true));
      }
      combined.append("\n---\n\n");
    }
    final Path outputFile = outputPath.resolve("analysis_report.md");
    Files.writeString(outputFile, combined.toString(), StandardCharsets.UTF_8);
    return outputFile;
  }

  private static String msg(final String key, final Object... args) {
    return MessageSource.getMessage(key, args);
  }

  public record Result(int totalCount, Path outputPath) {}

  public interface ProgressListener {

    void onMarkdownGenerating();

    void onMarkdownComplete(int count);

    void onHtmlGenerating();

    void onPdfGenerating();

    void onDiagramGenerating();

    void onSingleFileComplete(Path outputFile);

    void onLlmGenerating();

    void onLlmComplete(int count, Path outputPath);

    static ProgressListener noop() {
      return NoopProgressListener.INSTANCE;
    }
  }

  private enum NoopProgressListener implements ProgressListener {
    INSTANCE;

    @Override
    public void onMarkdownGenerating() {}

    @Override
    public void onMarkdownComplete(final int count) {}

    @Override
    public void onHtmlGenerating() {}

    @Override
    public void onPdfGenerating() {}

    @Override
    public void onDiagramGenerating() {}

    @Override
    public void onSingleFileComplete(final Path outputFile) {}

    @Override
    public void onLlmGenerating() {}

    @Override
    public void onLlmComplete(final int count, final Path outputPath) {}
  }

  /** Validation failure for command-agnostic document generation options. */
  public static final class ValidationException extends Exception {

    private static final long serialVersionUID = 1L;

    public ValidationException(final String message) {
      super(message);
    }
  }
}
