package com.craftsmanbro.fulcraft.ui.cli.command;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.json.impl.JsonMapperFactory;
import com.craftsmanbro.fulcraft.ui.cli.UiLogger;
import com.craftsmanbro.fulcraft.ui.cli.command.support.CommandMessageSupport;
import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ResourceBundle;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * FULの設定ファイルを初期化するCLIコマンド。
 *
 * <p>インタラクティブなウィザードを通じて設定ファイルを作成します。
 *
 * <p>使用例:
 *
 * <pre>
 *   ful init
 *   ful init --force
 *   ful init -d /path/to/project
 * </pre>
 */
@Command(
    name = "init",
    description = "${command.init.description}",
    footer = "${command.init.footer}",
    resourceBundle = "messages",
    mixinStandardHelpOptions = true)
@Category("basic")
public class InitCommand extends BaseCliCommand {

  // File constants
  private static final String CONFIG_FILE = "config.json";

  // Provider constants
  private static final String PROVIDER_GEMINI = "gemini";

  private static final String PROVIDER_OPENAI = "openai";

  private static final String PROVIDER_ANTHROPIC = "anthropic";

  private static final String PROVIDER_LOCAL = "local";

  // Message key constants (to avoid literal duplication)
  private static final String MSG_KEY_DEFAULT_PREFIX = "init.prompt.default_prefix";

  private static final String MSG_KEY_INPUT_HINT = "init.prompt.input_hint";

  private static final String MSG_KEY_ARROW = "init.prompt.arrow";

  private static final String MSG_KEY_WILL_USE_SUFFIX = "init.prompt.will_use_suffix";

  private ResourceBundle resourceBundle;

  @Option(
      names = {"-d", "--directory"},
      description = "${option.init.directory}",
      defaultValue = ".")
  private Path directory;

  @Option(
      names = {"-f", "--force"},
      description = "${option.init.force}")
  private boolean force;

  private final BufferedReader reader;

  /** デフォルトコンストラクタ（標準入力を使用）。 */
  public InitCommand() {
    this(new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)));
  }

  /** テスト用コンストラクタ（カスタムリーダーを指定可能）。 */
  InitCommand(final BufferedReader reader) {
    this.reader = reader;
  }

  /**
   * リソースバンドルを設定します（Picocliによって注入されます）。
   *
   * @param resourceBundle リソースバンドル
   */
  public void setResourceBundle(final ResourceBundle resourceBundle) {
    this.resourceBundle = resourceBundle;
  }

  /**
   * メッセージキーからローカライズされたメッセージを取得します。
   *
   * @param key メッセージキー
   * @param args プレースホルダーに埋め込む引数
   * @return ローカライズされたメッセージ
   */
  private String msg(final String key, final Object... args) {
    resourceBundle = CommandMessageSupport.resolve(resourceBundle);
    return CommandMessageSupport.message(resourceBundle, key, args);
  }

  /**
   * メッセージを標準出力に出力します。
   *
   * <p>テスト容易性のため抽出。テスト時にオーバーライドして出力をキャプチャできます。
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
    print(msg("init.wizard.title") + "\n");
    final Path configPath = directory.resolve(CONFIG_FILE);
    if (!checkExistingConfig(configPath)) {
      return 1;
    }
    try {
      final Path resolvedProjectRoot = promptProjectRoot();
      final String provider = promptProvider();
      final String apiKey = promptApiKey(provider);
      final String modelName = promptModel(provider);
      final String docsOutput = promptDocsOutput();
      return saveConfigFile(
          configPath, resolvedProjectRoot, docsOutput, provider, apiKey, modelName);
    } catch (IOException e) {
      UiLogger.error(msg("init.error.create_failed", e.getMessage()));
      return 1;
    }
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

  private boolean checkExistingConfig(final Path configPath) {
    if (Files.exists(configPath) && !force) {
      print(msg("init.config.exists", configPath));
      print(msg("init.config.use_force"));
      return false;
    }
    return true;
  }

  private Path promptProjectRoot() {
    final Path currentDir = directory.toAbsolutePath().normalize();
    print(msg("init.prompt.project_root.title"));
    print(msg("init.prompt.project_root.hint"));
    print(msg(MSG_KEY_DEFAULT_PREFIX) + currentDir);
    final String projectRootInput = prompt(msg(MSG_KEY_INPUT_HINT), null);
    final Path projectRoot =
        (projectRootInput != null && !projectRootInput.isBlank())
            ? Path.of(projectRootInput.trim()).toAbsolutePath().normalize()
            : currentDir;
    print(msg(MSG_KEY_ARROW) + projectRoot + msg(MSG_KEY_WILL_USE_SUFFIX) + "\n");
    return projectRoot;
  }

  private String promptProvider() {
    print(msg("init.prompt.provider.title"));
    print(msg("init.prompt.provider.gemini"));
    print(msg("init.prompt.provider.openai"));
    print(msg("init.prompt.provider.anthropic"));
    print(msg("init.prompt.provider.local"));
    final String providerChoice = prompt("> ", "1");
    final String provider =
        switch (providerChoice.trim()) {
          case "2" -> PROVIDER_OPENAI;
          case "3" -> PROVIDER_ANTHROPIC;
          case "4" -> PROVIDER_LOCAL;
          default -> PROVIDER_GEMINI;
        };
    print(msg("init.prompt.provider.selected", provider) + "\n");
    return provider;
  }

  private String promptApiKey(final String provider) {
    final String envVar = getEnvVarName(provider);
    final String existingKey = System.getenv(envVar);
    if (existingKey != null && !existingKey.isBlank()) {
      return promptApiKeyFromEnv(envVar);
    }
    if (!PROVIDER_LOCAL.equals(provider)) {
      return promptApiKeyManually(envVar);
    }
    return null;
  }

  private String promptApiKeyFromEnv(final String envVar) {
    print(msg("init.prompt.apikey.title"));
    print(msg("init.prompt.apikey.detected", envVar));
    print(msg("init.prompt.apikey.reference", envVar));
    while (true) {
      print(msg("init.prompt.apikey.use_env"));
      final String useEnv = prompt("> ", "Y");
      if (useEnv == null || useEnv.isBlank() || "y".equalsIgnoreCase(useEnv.trim())) {
        return "${" + envVar + "}";
      }
      if ("n".equalsIgnoreCase(useEnv.trim())) {
        return promptApiKeyManually(envVar);
      }
      print(msg("init.prompt.apikey.use_env.invalid"));
    }
  }

  private String promptApiKeyManually(final String envVar) {
    print(msg("init.prompt.apikey.manual"));
    print(msg("init.prompt.apikey.env_hint", envVar));
    final String apiKey = readApiKey();
    if (apiKey == null || apiKey.isBlank()) {
      print(msg("init.prompt.apikey.not_set") + "\n");
      return null;
    }
    print(msg("init.prompt.apikey.set") + "\n");
    return apiKey;
  }

  private String readApiKey() {
    final Console console = System.console();
    if (console != null) {
      final char[] keyChars = console.readPassword("> ");
      if (keyChars != null && keyChars.length > 0) {
        return new String(keyChars);
      }
      return null;
    }
    return prompt("> ", null);
  }

  private String promptModel(final String provider) {
    String modelName = getDefaultModel(provider);
    print(msg("init.prompt.model.title"));
    print(msg(MSG_KEY_DEFAULT_PREFIX) + modelName);
    final String customModel = prompt(msg(MSG_KEY_INPUT_HINT), null);
    if (customModel != null && !customModel.isBlank()) {
      modelName = customModel.trim();
      print(msg(MSG_KEY_ARROW) + modelName + msg(MSG_KEY_WILL_USE_SUFFIX) + "\n");
    } else {
      print(msg("init.prompt.model.using_default") + "\n");
    }
    return modelName;
  }

  private String promptDocsOutput() {
    final String defaultDocsOutput = "docs";
    print(msg("init.prompt.docs.title"));
    print(msg("init.prompt.docs.hint"));
    print(msg(MSG_KEY_DEFAULT_PREFIX) + defaultDocsOutput);
    final String docsOutputInput = prompt(msg(MSG_KEY_INPUT_HINT), null);
    final String docsOutput =
        (docsOutputInput != null && !docsOutputInput.isBlank())
            ? docsOutputInput.trim()
            : defaultDocsOutput;
    print(msg(MSG_KEY_ARROW) + docsOutput + msg(MSG_KEY_WILL_USE_SUFFIX) + "\n");
    return docsOutput;
  }

  private int saveConfigFile(
      final Path configPath,
      final Path projectRoot,
      final String docsOutput,
      final String provider,
      final String apiKey,
      final String modelName)
      throws IOException {
    final String configContent =
        generateConfigJson(projectRoot, docsOutput, provider, apiKey, modelName);
    final Path parent = configPath.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    Files.writeString(configPath, configContent, StandardCharsets.UTF_8);
    printSuccessMessage(configPath);
    return 0;
  }

  private void printSuccessMessage(final Path configPath) {
    print(msg("init.success.saved", configPath));
    print("");
    print(msg("init.success.next_steps"));
    print(msg("init.success.run_generate"));
    print(msg("init.success.run_generate_cmd"));
    print("");
    print(msg("init.success.run_specific"));
    print(msg("init.success.run_specific_cmd"));
    print("");
    print(msg("init.success.customize"));
    print("     " + configPath);
  }

  private String prompt(final String message, final String defaultValue) {
    UiLogger.stdoutInline(message);
    try {
      final String line = reader.readLine();
      if (line == null || line.isBlank()) {
        return defaultValue;
      }
      return line.trim();
    } catch (IOException e) {
      return defaultValue;
    }
  }

  private String getEnvVarName(final String provider) {
    return switch (provider) {
      case PROVIDER_OPENAI -> "OPENAI_API_KEY";
      case PROVIDER_ANTHROPIC -> "ANTHROPIC_API_KEY";
      default -> "GEMINI_API_KEY";
    };
  }

  private String getDefaultModel(final String provider) {
    return switch (provider) {
      case PROVIDER_OPENAI -> "gpt-4o";
      case PROVIDER_ANTHROPIC -> "claude-sonnet-4-20250514";
      case PROVIDER_LOCAL -> "llama3.1";
      default -> "gemini-2.0-flash-exp";
    };
  }

  private String generateConfigJson(
      final Path projectRoot,
      final String docsOutput,
      final String provider,
      final String apiKey,
      final String modelName) {
    final Map<String, Object> root = new LinkedHashMap<>();
    final Map<String, Object> project = new LinkedHashMap<>();
    project.put("id", "my-project");
    project.put("root", projectRoot.toString());
    project.put("docs_output", docsOutput);
    root.put("project", project);
    final Map<String, Object> llm = new LinkedHashMap<>();
    llm.put("provider", provider);
    llm.put("model_name", modelName);
    if (apiKey != null && !apiKey.isBlank()) {
      llm.put("api_key", apiKey);
    }
    llm.put("max_retries", 3);
    llm.put("fix_retries", 2);
    if (PROVIDER_LOCAL.equals(provider)) {
      llm.put("url", "http://localhost:11434");
    }
    root.put("llm", llm);
    final Map<String, Object> selectionRules = new LinkedHashMap<>();
    selectionRules.put("class_min_loc", 10);
    selectionRules.put("class_min_method_count", 1);
    selectionRules.put("exclude_getters_setters", true);
    selectionRules.put("method_min_loc", 3);
    selectionRules.put("method_max_loc", 2000);
    root.put("selection_rules", selectionRules);
    final Map<String, Object> execution = new LinkedHashMap<>();
    execution.put("per_task_isolation", false);
    root.put("execution", execution);
    final ObjectMapper mapper = JsonMapperFactory.createPrettyPrinter();
    try {
      return mapper.writeValueAsString(root);
    } catch (JacksonException e) {
      return "{}";
    }
  }
}
