package com.craftsmanbro.fulcraft.ui.cli.command;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.fs.impl.RunIdGenerator;
import com.craftsmanbro.fulcraft.kernel.pipeline.PipelineRunner;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.ui.cli.UiLogger;
import com.craftsmanbro.fulcraft.ui.cli.wiring.PipelineRunnerFactory;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import picocli.CommandLine;
import picocli.CommandLine.Option;

/**
 * ステージ実行系CLIコマンドの抽象基底クラス。
 *
 * <p>以下のサブコマンドに対して共通機能を提供します：
 *
 * <ul>
 *   <li>解析 (analyze)
 *   <li>選定 (select)
 *   <li>生成 (generate)
 * </ul>
 *
 * <p>主な機能：
 *
 * <ul>
 *   <li>共通CLIオプション（プロジェクトルート、Dry-run、Verboseなど）の定義
 *   <li>{@link RunContext}（実行コンテキスト）および Runner の作成
 *   <li>診断情報（警告・エラー）の出力
 * </ul>
 */
public abstract class AbstractCliCommand extends BaseCliCommand {

  private static final String META_START_TIME = "startTime";

  private static final String DEFAULT_ENGINE_TYPE = "composite";

  private static final String CONFIG_REQUIRED_MESSAGE = "Config is required";

  @Option(
      names = {"-p", "--project-root"},
      descriptionKey = "option.common.project_root")
  protected Path projectRootOption;

  @CommandLine.Parameters(
      index = "0",
      arity = "0..1",
      descriptionKey = "option.common.project_root_positional")
  protected Path projectRootPositional;

  @Option(
      names = {"--dry-run"},
      descriptionKey = "option.common.dry_run")
  protected boolean dryRun;

  @Option(
      names = {"--engine"},
      descriptionKey = "option.common.engine",
      defaultValue = DEFAULT_ENGINE_TYPE)
  protected String engineType;

  @Option(
      names = {"--exclude-tests"},
      descriptionKey = "option.common.exclude_tests")
  protected Boolean excludeTests;

  @Option(
      names = {"--version-history"},
      descriptionKey = "option.common.version_history")
  protected Boolean enableVersionHistory;

  @Option(
      names = {"-v", "--verbose"},
      descriptionKey = "option.common.verbose")
  protected boolean verbose;

  @Option(
      names = {"-f", "--files"},
      descriptionKey = "option.common.files",
      split = ",")
  protected List<String> files;

  @Option(
      names = {"-d", "--dirs"},
      descriptionKey = "option.common.dirs",
      split = ",")
  private List<String> dirs;

  @Option(
      names = {"--debug-dynamic-resolution"},
      descriptionKey = "option.common.debug_dynamic_resolution")
  private boolean debugDynamicResolution;

  @Option(
      names = {"--unresolved-policy"},
      descriptionKey = "option.common.unresolved_policy")
  protected String unresolvedPolicy;

  @Option(
      names = {"--max-cyclomatic"},
      descriptionKey = "option.common.max_cyclomatic")
  private Integer maxCyclomatic;

  @Option(
      names = {"--complexity-strategy"},
      descriptionKey = "option.common.complexity_strategy")
  private String complexityStrategy;

  @Option(
      names = {"--tasks-format"},
      descriptionKey = "option.common.tasks_format")
  protected String tasksFormat;

  @Option(
      names = {"--cache-ttl"},
      descriptionKey = "option.common.cache_ttl")
  protected Integer cacheTtl;

  @Option(
      names = {"--cache-revalidate"},
      descriptionKey = "option.common.cache_revalidate")
  protected Boolean cacheRevalidate;

  @Option(
      names = {"--cache-encrypt"},
      descriptionKey = "option.common.cache_encrypt")
  protected Boolean cacheEncrypt;

  @Option(
      names = {"--cache-key-env"},
      descriptionKey = "option.common.cache_key_env")
  protected String cacheKeyEnv;

  @Option(
      names = {"--cache-max-size-mb"},
      descriptionKey = "option.common.cache_max_size_mb")
  protected Integer cacheMaxSizeMb;

  @Option(
      names = {"--cache-version-check"},
      descriptionKey = "option.common.cache_version_check")
  protected Boolean cacheVersionCheck;

  @Option(
      names = {"--color"},
      descriptionKey = "option.common.color")
  protected String colorMode;

  @Option(
      names = {"--log-format"},
      descriptionKey = "option.common.log_format")
  protected String logFormat;

  @Option(
      names = {"--json"},
      descriptionKey = "option.common.json")
  protected boolean jsonOutput;

  @Option(
      names = {"--plugin-classpath"},
      descriptionKey = "option.common.plugin_classpath")
  protected String pluginClasspath;

  @Option(
      names = {"--experimental-candidate-enum"},
      descriptionKey = "option.common.experimental_candidate_enum",
      hidden = true)
  protected boolean experimentalCandidateEnum;

  /** Returns node ids to execute. */
  protected List<String> getNodeIds() {
    throw new UnsupportedOperationException("Subclasses must override getNodeIds()");
  }

  /**
   * ログ出力用のコマンド説明を返します。
   *
   * @return コマンドの説明文
   */
  protected abstract String getCommandDescription();

  @Override
  protected Integer doCall(final Config config, final Path projectRoot) {
    Objects.requireNonNull(config, CONFIG_REQUIRED_MESSAGE);
    Objects.requireNonNull(projectRoot, "projectRoot is required");
    RunContext context = null;
    try {
      // 1. 実行コンテキストの作成
      context = createContext(config, projectRoot);
      UiLogger.configureRunLogging(config, context.getRunDirectory(), context.getRunId());
      UiLogger.debug(getCommandDescription() + ": " + projectRoot);
      // 2. 実行ノードの取得
      final List<String> nodeIds = getNodeIds();
      UiLogger.debug("Running nodes: " + nodeIds);
      // 3. Runner の作成と実行
      final PipelineRunner runner = createRunner(config);
      final int exitCode = runner.runNodes(context, nodeIds, null, null);
      // 4. 結果の表示
      printDiagnostics(context);
      return exitCode;
    } catch (Exception e) {
      final RunContext diagnosticsContext = context;
      return handleCommandException(
          e, config, projectRoot, () -> printDiagnostics(diagnosticsContext));
    }
  }

  /**
   * パイプライン実行用の {@link RunContext} を作成します。
   *
   * @param config ロードされた設定
   * @param projectRoot 解決されたプロジェクトルート
   * @return 新しい RunContext インスタンス
   */
  protected RunContext createContext(final Config config, final Path projectRoot) {
    Objects.requireNonNull(config, CONFIG_REQUIRED_MESSAGE);
    final String runId = RunIdGenerator.newRunId();
    final RunContext context =
        new RunContext(projectRoot.toAbsolutePath(), config, runId).withDryRun(dryRun);
    context.putMetadata(META_START_TIME, Instant.now().toEpochMilli());
    return context;
  }

  /**
   * ステージ実行用の {@link PipelineRunner} を作成します。
   *
   * @param config ロードされた設定
   * @return 新しい PipelineRunner インスタンス
   */
  protected PipelineRunner createRunner(final Config config) {
    Objects.requireNonNull(main, "Parent command is required");
    Objects.requireNonNull(config, CONFIG_REQUIRED_MESSAGE);
    return PipelineRunnerFactory.createDefault(
        config, main.getServices(), engineType, pluginClasspath);
  }

  /**
   * Prints diagnostics (errors and warnings) from the RunContext.
   *
   * @param context the RunContext containing diagnostics
   */
  protected void printDiagnostics(final RunContext context) {
    if (context == null) {
      return;
    }
    if (!context.getErrors().isEmpty()) {
      UiLogger.stdout("");
      UiLogger.stdout("=== Errors ===");
      for (final String error : context.getErrors()) {
        UiLogger.stdout("  - " + error);
      }
    }
    if (!context.getWarnings().isEmpty()) {
      UiLogger.stdout("");
      UiLogger.stdout("=== Warnings ===");
      for (final String warning : context.getWarnings()) {
        UiLogger.stdout("  - " + warning);
      }
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
  protected boolean isVerboseEnabled() {
    return verbose;
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
  protected Optional<Boolean> getExcludeTests() {
    return Optional.ofNullable(excludeTests);
  }

  @Override
  protected Optional<Boolean> getEnableVersionHistory() {
    return Optional.ofNullable(enableVersionHistory);
  }

  @Override
  protected boolean isDebugDynamicResolution() {
    return debugDynamicResolution;
  }

  @Override
  protected boolean isExperimentalCandidateEnum() {
    return experimentalCandidateEnum;
  }

  @Override
  protected String getUnresolvedPolicy() {
    return unresolvedPolicy;
  }

  @Override
  protected Integer getMaxCyclomatic() {
    return maxCyclomatic;
  }

  @Override
  protected String getComplexityStrategy() {
    return complexityStrategy;
  }

  @Override
  protected String getTasksFormat() {
    return tasksFormat;
  }

  @Override
  protected Integer getCacheTtl() {
    return cacheTtl;
  }

  @Override
  protected Optional<Boolean> getCacheRevalidate() {
    return Optional.ofNullable(cacheRevalidate);
  }

  @Override
  protected Optional<Boolean> getCacheEncrypt() {
    return Optional.ofNullable(cacheEncrypt);
  }

  @Override
  protected String getCacheKeyEnv() {
    return cacheKeyEnv;
  }

  @Override
  protected Integer getCacheMaxSizeMb() {
    return cacheMaxSizeMb;
  }

  @Override
  protected Optional<Boolean> getCacheVersionCheck() {
    return Optional.ofNullable(cacheVersionCheck);
  }

  @Override
  protected String getColorMode() {
    return colorMode;
  }

  @Override
  protected String getLogFormat() {
    return logFormat;
  }

  @Override
  protected boolean isJsonOutput() {
    return jsonOutput;
  }
}
