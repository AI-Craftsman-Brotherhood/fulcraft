package com.craftsmanbro.fulcraft.ui.cli.command;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.ui.cli.UiLogger;
import com.craftsmanbro.fulcraft.ui.cli.command.support.CommandMessageSupport;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ResourceBundle;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * CI/CD設定ファイルを初期化するCLIコマンド。
 *
 * <p>GitHub Actionsのワークフローファイルをテンプレートから生成し、 ユーザーのプロジェクトに配置します。
 *
 * <p>使用例:
 *
 * <pre>
 *   ful init-ci --github-actions
 *   ful init-ci --github-actions --no-comment
 *   ful init-ci --github-actions --no-quality-gate
 * </pre>
 */
@Command(
    name = "init-ci",
    description = "${command.init-ci.description}",
    footer = "${command.init-ci.footer}",
    resourceBundle = "messages",
    mixinStandardHelpOptions = true)
@Category("configuration")
public class InitCiCommand extends BaseCliCommand {

  // テンプレートパスは内部リソースであり、外部からのカスタマイズは不要
  private static final String TEMPLATE_PATH = "/templates/github-actions/ful-quality-gate.yml.tmpl";

  private static final String DEFAULT_OUTPUT_PATH = ".github/workflows/ful-quality-gate.yml";

  private ResourceBundle resourceBundle;

  @Option(
      names = {"--github-actions"},
      description = "${option.init-ci.github_actions}",
      required = true)
  private boolean githubActions;

  @Option(
      names = {"-o", "--output"},
      description = "${option.init-ci.output}")
  private Path outputPath;

  @Option(
      names = {"-f", "--force"},
      description = "${option.init-ci.force}")
  private boolean force;

  @Option(
      names = {"--dry-run"},
      description = "${option.init-ci.dry_run}")
  private boolean dryRun;

  @Option(
      names = {"--comment"},
      description = "${option.init-ci.comment}",
      defaultValue = "true",
      negatable = true)
  private boolean comment;

  @Option(
      names = {"--coverage-tool"},
      description = "${option.init-ci.coverage_tool}",
      defaultValue = "jacoco")
  private String coverageTool;

  @Option(
      names = {"--static-analysis"},
      description = "${option.init-ci.static_analysis}",
      split = ",")
  private List<String> staticAnalysisTools;

  @Option(
      names = {"--quality-gate"},
      description = "${option.init-ci.quality_gate}",
      defaultValue = "true",
      negatable = true)
  private boolean qualityGate;

  @Option(
      names = {"--coverage-threshold"},
      description = "${option.init-ci.coverage_threshold}")
  private Integer coverageThreshold;

  /**
   * リソースバンドルを設定します（Picocliによって注入されます）。
   *
   * @param resourceBundle リソースバンドル
   */
  public void setResourceBundle(final ResourceBundle resourceBundle) {
    this.resourceBundle = resourceBundle;
  }

  private String msg(final String key, final Object... args) {
    resourceBundle = CommandMessageSupport.resolve(resourceBundle);
    return CommandMessageSupport.message(resourceBundle, key, args);
  }

  /**
   * メッセージを標準出力に出力します。
   *
   * @param message 出力するメッセージ
   */
  protected void print(final String message) {
    if (message == null) {
      UiLogger.stdout("");
    } else {
      UiLogger.stdout(message);
    }
  }

  @Override
  protected Integer doCall(final Config config, final Path projectRoot) {
    if (!githubActions) {
      UiLogger.error(msg("init-ci.error.specify_github_actions"));
      return 1;
    }
    String content = loadTemplate();
    if (content == null) {
      UiLogger.error(msg("init-ci.error.template_load_failed", TEMPLATE_PATH));
      return 1;
    }
    // Apply customizations
    content = applyCustomizations(content);
    if (dryRun) {
      print(msg("init-ci.dryrun.header"));
      print(content);
      return 0;
    }
    Path target = outputPath != null ? outputPath : Path.of(DEFAULT_OUTPUT_PATH);
    target = target.toAbsolutePath().normalize();
    if (Files.exists(target) && !force) {
      UiLogger.error(msg("init-ci.error.file_exists", target));
      print(msg("init-ci.error.use_force"));
      return 1;
    }
    try {
      final Path parent = target.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Files.writeString(target, content);
      print(msg("init-ci.success.created", target));
      print(msg("init-ci.success.secrets_hint"));
      printQualityGateInfo();
    } catch (IOException e) {
      UiLogger.error(msg("init-ci.error.write_failed", e.getMessage()));
      return 1;
    }
    return 0;
  }

  @Override
  protected boolean shouldLoadConfig() {
    return false;
  }

  @Override
  protected boolean shouldResolveProjectRoot() {
    return false;
  }

  @Override
  protected boolean shouldValidateProjectRoot() {
    return false;
  }

  @Override
  protected boolean shouldApplyProjectRootToConfig() {
    return false;
  }

  /**
   * テンプレートにプレースホルダー置換を適用します。
   *
   * @param content 元のテンプレート内容
   * @return プレースホルダー置換済みの内容
   */
  private String applyCustomizations(final String content) {
    String customized = content;
    // PRコメントの条件
    final String commentCondition =
        comment
            ? "github.event_name == 'pull_request' && always()"
            : "false # Disabled by init-ci --no-comment";
    customized = customized.replace("{{COMMENT_CONDITION}}", commentCondition);
    // 品質ゲートの有効/無効
    final String qualityGatePrefix =
        qualityGate ? "" : "# Disabled by init-ci --no-quality-gate\n      # ";
    customized = customized.replace("{{QUALITY_GATE_PREFIX}}", qualityGatePrefix);
    // カバレッジ閾値のコメント追加
    if (coverageThreshold != null) {
      customized =
          customized
              + "\n# Note: Configure coverage_threshold: "
              + (coverageThreshold / 100.0)
              + " in config.json\n";
    }
    return customized;
  }

  /** 品質ゲート設定情報をコンソールに出力します。 */
  private void printQualityGateInfo() {
    print("");
    print(msg("init-ci.qg.title"));
    print(msg("init-ci.qg.coverage_tool", coverageTool));
    if (staticAnalysisTools != null && !staticAnalysisTools.isEmpty()) {
      print(msg("init-ci.qg.static_analysis", String.join(", ", staticAnalysisTools)));
    } else {
      print(msg("init-ci.qg.static_analysis_default"));
    }
    if (qualityGate) {
      print(msg("init-ci.qg.enabled"));
    } else {
      print(msg("init-ci.qg.disabled"));
    }
    if (coverageThreshold != null) {
      print(msg("init-ci.qg.coverage_threshold", coverageThreshold));
    }
    print("");
    print(msg("init-ci.qg.config_hint"));
    print(msg("init-ci.qg.config_example1"));
    print(msg("init-ci.qg.config_example2"));
    print(msg("init-ci.qg.config_example3"));
  }

  /**
   * リソースからテンプレートファイルを読み込みます。
   *
   * @return テンプレート内容、見つからない場合は null
   */
  private String loadTemplate() {
    final URL templateUrl = getClass().getResource(TEMPLATE_PATH);
    if (templateUrl == null) {
      return null;
    }
    try (InputStream stream = templateUrl.openStream()) {
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      UiLogger.error(msg("init-ci.error.template_load_failed", TEMPLATE_PATH));
      return null;
    }
  }
}
