package com.craftsmanbro.fulcraft.ui.cli.command;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.config.ConfigLoaderPort;
import com.craftsmanbro.fulcraft.config.ConfigOverride;
import com.craftsmanbro.fulcraft.infrastructure.config.impl.CommonOverrides;
import com.craftsmanbro.fulcraft.infrastructure.config.impl.ConfigLoaderImpl;
import com.craftsmanbro.fulcraft.infrastructure.security.impl.SecretMasker;
import com.craftsmanbro.fulcraft.ui.banner.CliStartupBanner;
import com.craftsmanbro.fulcraft.ui.banner.StartupBannerSupport;
import com.craftsmanbro.fulcraft.ui.cli.CliContext;
import com.craftsmanbro.fulcraft.ui.cli.UiLogger;
import com.craftsmanbro.fulcraft.ui.cli.command.support.CliConfigSupport;
import com.craftsmanbro.fulcraft.ui.cli.command.support.CliLoggerSettingsSupport;
import com.craftsmanbro.fulcraft.ui.cli.command.support.CliProjectRootSupport;
import com.craftsmanbro.fulcraft.ui.cli.command.support.CommonCliOptionAccessors;
import com.craftsmanbro.fulcraft.ui.cli.spi.CliCommand;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * CLIコマンドの共通基底クラスです。
 *
 * <p>このクラスは、設定の読み込み、ロガーの初期化、プロジェクトルートの解決を一元的に管理し、 コマンド固有の振る舞いは {@link #doCall(Config, Path)}
 * に委譲します。
 */
public abstract class BaseCliCommand extends CommonCliOptionAccessors implements CliCommand {

  @ParentCommand protected CliContext main;

  @Spec protected CommandSpec spec;

  private Path resolvedConfigPath;

  /**
   * コマンド実行のエントリポイントです。
   *
   * <p>以下の手順で処理を実行します： 1. プロジェクトルートの解決（CLI引数から） 2. 設定の読み込み 3. ロガーの初期化と設定適用 4. プロジェクトルートの最終解決と検証 5.
   * コマンド固有処理 ({@link #doCall}) の実行
   *
   * @return 終了コード
   */
  @Override
  public Integer call() {
    Config config = null;
    Path projectRoot = null;
    resolvedConfigPath = null;
    try {
      // 1. CLI引数からプロジェクトルートを仮解決（設定ファイル探索用）
      final Path projectRootFromCli =
          shouldResolveProjectRoot() ? resolveProjectRootFromCli() : null;
      // 2. 設定の読み込み
      config = loadConfig(projectRootFromCli);
      if (config == null) {
        config = Config.createDefault();
      }
      enforceExecutionLocalRunsRoot(config);
      // 3. ロガーの初期化とCLIオプションによる設定上書き（カラー、JSON出力など）
      UiLogger.initialize(config);
      applyLoggerSettings(buildCommonOverrides());
      // 4. プロジェクトルートの確定
      if (shouldResolveProjectRoot()) {
        projectRoot = resolveProjectRoot(config, projectRootFromCli);
      }
      // 設定オブジェクトに解決されたルートパスを反映
      if (shouldApplyProjectRootToConfig() && projectRoot != null) {
        applyProjectRootToConfig(config, projectRoot);
      }
      // プロジェクトルートの存在確認
      if (shouldValidateProjectRoot() && projectRoot != null) {
        validateProjectRoot(projectRoot);
      }
      if (shouldDisplayStartupBanner(config, projectRoot)) {
        printStartupBanner(config, projectRoot);
      }
      // 5. サブクラスの実装を実行
      return doCall(config, projectRoot);
    } catch (Exception e) {
      // その他の例外は共通ハンドラで処理
      return handleCommandException(e, config, projectRoot);
    }
  }

  /**
   * 共通セットアップ完了後に呼び出される、コマンド固有のロジックを実行します。
   *
   * @param config 読み込まれた設定
   * @param projectRoot 解決されたプロジェクトルート（無効な場合は null の可能性があります）
   * @return 終了コード
   */
  protected abstract Integer doCall(Config config, Path projectRoot);

  /** 設定ファイルを読み込むかどうかを返します。 デフォルトは true です。 */
  protected boolean shouldLoadConfig() {
    return true;
  }

  /** プロジェクトルートを解決するかどうかを返します。 デフォルトは true です。 */
  protected boolean shouldResolveProjectRoot() {
    return true;
  }

  /** 解決したプロジェクトルートを設定オブジェクト（Config）に反映するかどうかを返します。 デフォルトは true です。 */
  protected boolean shouldApplyProjectRootToConfig() {
    return true;
  }

  /** プロジェクトルートが実在するディレクトリか検証するかどうかを返します。 デフォルトは true です。 */
  protected boolean shouldValidateProjectRoot() {
    return true;
  }

  protected boolean shouldDisplayStartupBanner(final Config config, final Path projectRoot) {
    return !UiLogger.isJsonMode();
  }

  protected void printStartupBanner(final Config config, final Path projectRoot) {
    final Path bannerRoot = resolveBannerProjectRoot(config, projectRoot);
    final Path bannerConfigPath = resolveStartupBannerConfigPath(config, projectRoot);
    for (final String line :
        CliStartupBanner.buildLines(
            resolveStartupBannerApplicationName(config),
            resolveStartupBannerApplicationVersion(config),
            resolveStartupBannerModelName(config),
            bannerRoot,
            bannerConfigPath)) {
      UiLogger.stdout(line);
    }
    UiLogger.stdout("");
  }

  protected Path resolveStartupBannerConfigPath(final Config config, final Path projectRoot) {
    return resolvedConfigPath;
  }

  protected String resolveStartupBannerApplicationName(final Config config) {
    if (config != null) {
      final String configured = config.getAppName();
      if (configured != null && !configured.isBlank()) {
        return configured.trim();
      }
    }
    return StartupBannerSupport.resolveApplicationName();
  }

  protected String resolveStartupBannerApplicationVersion(final Config config) {
    if (config != null) {
      final String configured = config.getVersion();
      if (configured != null && !configured.isBlank()) {
        return configured.trim();
      }
    }
    return StartupBannerSupport.resolveApplicationVersion();
  }

  protected String resolveStartupBannerModelName(final Config config) {
    if (config != null && config.getLlm() != null) {
      final String configured = config.getLlm().getModelName();
      if (configured != null && !configured.isBlank()) {
        return configured.trim();
      }
    }
    return "unknown";
  }

  private Path resolveBannerProjectRoot(final Config config, final Path projectRoot) {
    if (projectRoot != null) {
      return projectRoot;
    }
    if (config != null && config.getProject() != null) {
      final String configuredRoot = config.getProject().getRoot();
      if (configuredRoot != null && !configuredRoot.isBlank()) {
        try {
          return Path.of(configuredRoot.trim());
        } catch (RuntimeException ignored) {
          // Fall through to current directory if config path is malformed.
        }
      }
    }
    return Path.of(".");
  }

  private void enforceExecutionLocalRunsRoot(final Config config) {
    if (config == null) {
      return;
    }
    Config.ExecutionConfig execution = config.getExecution();
    if (execution == null) {
      execution = new Config.ExecutionConfig();
      config.setExecution(execution);
    }
    execution.setLogsRoot(resolveExecutionLocalRunsRoot().toString());
  }

  private Path resolveExecutionLocalRunsRoot() {
    return Path.of(".").toAbsolutePath().normalize().resolve(".ful").resolve("runs").normalize();
  }

  protected ConfigLoaderPort createConfigLoader() {
    return main != null ? main.getConfigLoader() : new ConfigLoaderImpl();
  }

  protected Path resolveConfigPath(final ConfigLoaderPort configLoader, final Path projectRoot) {
    final Path configFile = main != null ? main.getConfigFile() : null;
    if (projectRoot != null) {
      // Keep projectRoot in the base hook contract for subclasses that perform
      // project-root-aware resolution.
      projectRoot.toAbsolutePath().normalize();
    }
    if (configLoader
        instanceof com.craftsmanbro.fulcraft.infrastructure.config.impl.ConfigLoaderImpl) {
      return ((com.craftsmanbro.fulcraft.infrastructure.config.impl.ConfigLoaderImpl) configLoader)
          .resolveConfigPath(configFile);
    }
    return configFile != null
        ? configFile
        : com.craftsmanbro.fulcraft.infrastructure.config.impl.ConfigLoaderImpl.DEFAULT_CONFIG_FILE;
  }

  /**
   * 設定ファイルを読み込み、CLI引数による上書きを適用した Config オブジェクトを生成します。
   *
   * @param projectRoot プロジェクトルート（設定ファイルの探索に使用）
   * @return 読み込まれた Config オブジェクト
   */
  protected Config loadConfig(final Path projectRoot) {
    if (!shouldLoadConfig()) {
      return Config.createDefault();
    }
    final ConfigLoaderPort configLoader = createConfigLoader();
    final Path resolvedConfigPath = resolveConfigPath(configLoader, projectRoot);
    rememberResolvedConfigPath(resolvedConfigPath);
    final List<ConfigOverride> overrides = buildConfigOverrides(projectRoot);
    return CliConfigSupport.loadConfig(configLoader, resolvedConfigPath, overrides);
  }

  protected final Path getResolvedConfigPath() {
    return resolvedConfigPath;
  }

  protected final void rememberResolvedConfigPath(final Path configPath) {
    if (configPath == null) {
      resolvedConfigPath = null;
      return;
    }
    resolvedConfigPath = configPath.toAbsolutePath().normalize();
  }

  protected List<ConfigOverride> buildConfigOverrides(final Path projectRoot) {
    final List<ConfigOverride> overrides = new ArrayList<>();
    if (projectRoot != null) {
      // Keep projectRoot in the override hook contract for subclasses.
      projectRoot.toAbsolutePath().normalize();
    }
    final CommonOverrides commonOverrides = buildCommonOverrides();
    if (commonOverrides != null) {
      overrides.add(commonOverrides);
    }
    return overrides;
  }

  @Override
  protected CommonOverrides buildCommonOverrides() {
    return super.buildCommonOverrides();
  }

  protected Path resolveProjectRootFromCli() {
    return CliProjectRootSupport.resolveProjectRootFromCli(
        getProjectRootOption(), getProjectRootPositional());
  }

  /**
   * プロジェクトルートを解決します。
   *
   * <p>以下の順序で優先されます： 1. CLIオプション (--project-root など) 2. CLIの位置引数 3. 設定ファイル (config.json) 内の指定 4.
   * カレントディレクトリ (".")
   *
   * @param config 読み込まれた設定 (null の場合あり)
   * @param projectRootFromCli CLIオプションから既に解決されたパス (null の場合あり)
   * @return 解決されたプロジェクトルートパス
   */
  protected Path resolveProjectRoot(final Config config, final Path projectRootFromCli) {
    return CliProjectRootSupport.resolveProjectRoot(
        config, getProjectRootOption(), getProjectRootPositional(), projectRootFromCli);
  }

  protected Path resolveProjectRoot(final Config config) {
    return resolveProjectRoot(config, resolveProjectRootFromCli());
  }

  protected void applyProjectRootToConfig(final Config config, final Path projectRoot) {
    CliProjectRootSupport.applyProjectRootToConfig(config, projectRoot);
  }

  /** プロジェクトルートが有効なディレクトリであることを検証します。 存在しない場合は例外をスローします。 */
  protected void validateProjectRoot(final Path projectRoot) {
    CliProjectRootSupport.validateProjectRoot(projectRoot, spec);
  }

  /**
   * CommonOverrides に基づいてロガーの設定を適用します。
   *
   * <p>これにより、JSONモードやカラーモードなどのCLI固有の設定がロガーに反映されます。 これは設定読み込み後、かつコマンド実行前に行われます。
   *
   * @param overrides ログやカラー設定を含む共通上書き設定
   */
  protected void applyLoggerSettings(final CommonOverrides overrides) {
    CliLoggerSettingsSupport.apply(overrides);
  }

  /** 実行中に発生した例外を処理し、エラーメッセージを出力します。 Verboseモードの場合はスタックトレースも出力します。 */
  protected Integer handleException(
      final Exception e, final Config config, final Path projectRoot) {
    // 機密情報が含まれる可能性があるためマスク処理を行う
    getErrWriter().println("ERROR: " + SecretMasker.mask(formatExceptionMessage(e)));
    if (isVerboseEnabled()) {
      writeVerboseContext(config, projectRoot);
      getErrWriter().print(SecretMasker.maskStackTrace(e));
    }
    return CommandLine.ExitCode.SOFTWARE;
  }

  protected final Integer handleCommandException(
      final Exception e, final Config config, final Path projectRoot) {
    return handleCommandException(e, config, projectRoot, null);
  }

  protected final Integer handleCommandException(
      final Exception e, final Config config, final Path projectRoot, final Runnable diagnostics) {
    rethrowIfParameterException(e);
    final Integer exitCode = handleException(e, config, projectRoot);
    if (diagnostics != null) {
      diagnostics.run();
    }
    return exitCode;
  }

  protected final void rethrowIfParameterException(final Exception e) {
    if (e instanceof CommandLine.ParameterException parameterException) {
      throw parameterException;
    }
  }

  private void writeVerboseContext(final Config config, final Path projectRoot) {
    final String projectId =
        config != null && config.getProject() != null ? config.getProject().getId() : null;
    if (projectId != null && !projectId.isBlank()) {
      getErrWriter().println("project.id=" + SecretMasker.mask(projectId));
    }
    if (projectRoot != null) {
      getErrWriter().println("project.root=" + projectRoot.toAbsolutePath().normalize());
    }
    final Path configPath = getResolvedConfigPath();
    if (configPath != null) {
      getErrWriter().println("config.path=" + configPath.toAbsolutePath().normalize());
    }
  }

  protected String formatExceptionMessage(final Exception e) {
    final String message = e.getMessage();
    return message == null || message.isBlank() ? e.toString() : message;
  }

  protected PrintWriter getErrWriter() {
    if (spec != null) {
      return spec.commandLine().getErr();
    }
    return new PrintWriter(System.err, true, StandardCharsets.UTF_8);
  }

  protected Path resolveProjectConfigPath(final Path projectRoot) {
    return CliProjectRootSupport.resolveProjectConfigPath(projectRoot);
  }
}
