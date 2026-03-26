package com.craftsmanbro.fulcraft.ui.cli.command;

import com.craftsmanbro.fulcraft.Main;
import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.config.impl.ConfigLoaderImpl;
import com.craftsmanbro.fulcraft.ui.banner.StartupBannerSupport;
import com.craftsmanbro.fulcraft.ui.cli.UiLogger;
import com.craftsmanbro.fulcraft.ui.cli.command.support.CommandMessageSupport;
import com.craftsmanbro.fulcraft.ui.tui.command.CommandDispatcher;
import com.craftsmanbro.fulcraft.ui.tui.command.CommandResult;
import com.craftsmanbro.fulcraft.ui.tui.config.ConfigCommandService;
import com.craftsmanbro.fulcraft.ui.tui.config.ConfigEditor;
import com.craftsmanbro.fulcraft.ui.tui.config.ConfigValidationService;
import com.craftsmanbro.fulcraft.ui.tui.config.MetadataRegistry;
import com.craftsmanbro.fulcraft.ui.tui.plan.Plan;
import com.craftsmanbro.fulcraft.ui.tui.plan.PlanBuilder;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Non-screen interactive mode (Codex-style) for line-based conversation.
 *
 * <p>This mode does not require Lanterna full-screen rendering and is intended for environments
 * where TUI screen mode is unavailable.
 */
@Command(
    name = "tut",
    description = "${command.tut.description}",
    footer = "${command.tut.footer}",
    mixinStandardHelpOptions = true)
@Category("basic")
public class TutCommand extends BaseCliCommand {

  private static final String TTY_DEVICE = "/dev/tty";

  private static final String ANSI_CLEAR_LINE = "\u001B[2K";

  private static final String ANSI_CLEAR_TO_END = "\u001B[J";

  private static final String ANSI_SAVE_CURSOR = "\u001B[s";

  private static final String ANSI_RESTORE_CURSOR = "\u001B[u";

  private static final String ANSI_HIGHLIGHT = "\u001B[7m";

  private static final String ANSI_STYLE_RESET = "\u001B[0m";

  private static final int CONFIG_LIST_PAGE_SIZE = 20;

  private static final int CONFIG_EDITOR_ACTION_PAGE_SIZE = 6;

  private static final int NAV_KEY_ENTER = 1;

  private static final int NAV_KEY_QUIT = 2;

  private static final int NAV_KEY_UP = 3;

  private static final int NAV_KEY_DOWN = 4;

  private static final int NAV_KEY_PAGE_UP = 5;

  private static final int NAV_KEY_PAGE_DOWN = 6;

  private static final int NAV_KEY_HOME = 7;

  private static final int NAV_KEY_END = 8;

  private static final int NAV_KEY_OTHER = 0;

  private static final int STARTUP_BOX_WIDTH = 53;

  private static final String STARTUP_MODEL_HINT_KEY = "tut.startup.model_hint";

  private static final String STARTUP_TIP_KEY = "tut.startup.tip";

  private static final int DEFAULT_TERMINAL_COLUMNS = 120;

  private static final int MIN_TERMINAL_COLUMNS = 40;

  private static final int MIN_LIST_PAGE_SIZE = 5;

  private static final int MODEL_PICKER_PAGE_SIZE = 10;

  private static final String SLASH_ANALYZE = "/analyze";

  private static final String SLASH_REPORT = "/report";

  private static final String SLASH_DOCUMENT = "/document";

  private static List<String> buildHelpLines() {
    return List.of(
        "",
        MessageSource.getMessage("tut.help.help"),
        MessageSource.getMessage("tut.help.model"),
        MessageSource.getMessage("tut.help.status"),
        MessageSource.getMessage("tut.help.analyze"),
        MessageSource.getMessage("tut.help.report"),
        MessageSource.getMessage("tut.help.document"),
        MessageSource.getMessage("tut.help.config"),
        MessageSource.getMessage("tut.help.config_get"),
        MessageSource.getMessage("tut.help.config_set"),
        MessageSource.getMessage("tut.help.config_search"),
        MessageSource.getMessage("tut.help.config_validate"),
        MessageSource.getMessage("tut.help.quit"));
  }

  private static final List<String> MODEL_PROVIDER_PRESETS =
      List.of(
          "openai",
          "anthropic",
          "gemini",
          "azure-openai",
          "vertex-ai",
          "bedrock",
          "openai-compatible",
          "local",
          "ollama",
          "mock");

  private static final Map<String, List<String>> MODEL_PRESETS_BY_PROVIDER =
      buildModelPresetsByProvider();

  @Option(
      names = {"-p", "--project-root"},
      descriptionKey = "option.tut.project_root")
  private Path projectRoot;

  private ResourceBundle resourceBundle;

  private final BufferedReader reader;

  private final CommandDispatcher commandDispatcher;

  private final ConfigCommandService configCommandService;

  private final ConfigValidationService configValidationService;

  private final PlanBuilder planBuilder;

  private Path activeProjectRoot = Path.of(".");

  private Path activeConfigPath = Path.of("config.json");

  private boolean realtimeInputDisabled;

  public TutCommand() {
    this(
        new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)),
        new CommandDispatcher(),
        new ConfigCommandService(MetadataRegistry.getDefault()),
        new ConfigValidationService(),
        new PlanBuilder());
  }

  TutCommand(
      final BufferedReader reader,
      final CommandDispatcher commandDispatcher,
      final ConfigCommandService configCommandService,
      final ConfigValidationService configValidationService,
      final PlanBuilder planBuilder) {
    this.reader = reader;
    this.commandDispatcher = commandDispatcher;
    this.configCommandService = configCommandService;
    this.configValidationService = configValidationService;
    this.planBuilder = planBuilder;
    registerTutSpecificCommands();
  }

  public void setResourceBundle(final ResourceBundle resourceBundle) {
    this.resourceBundle = resourceBundle;
  }

  void setProjectRoot(final Path projectRoot) {
    this.projectRoot = projectRoot;
  }

  private String msg(final String key, final Object... args) {
    resourceBundle = CommandMessageSupport.resolve(resourceBundle);
    return CommandMessageSupport.message(resourceBundle, key, args);
  }

  @Override
  protected Integer doCall(final Config config, final Path ignoredProjectRoot) {
    final Path effectiveProjectRoot = projectRoot != null ? projectRoot : Path.of(".");
    final Path configPath = effectiveProjectRoot.resolve("config.json");
    activeProjectRoot = effectiveProjectRoot;
    activeConfigPath = configPath;
    printStartupBanner(effectiveProjectRoot);
    while (true) {
      try {
        final InputLine inputLine = readInputLine();
        final String line = inputLine.text();
        if (line == null) {
          print("");
          print(msg("tut.exit.eof"));
          return 0;
        }
        final String input = line.trim();
        if (input.isEmpty()) {
          continue;
        }
        if (isExitInput(input)) {
          print(msg("tut.exit"));
          return 0;
        }
        if (commandDispatcher.isCommand(input)) {
          handleCommandInput(input);
        } else {
          renderPlan(input);
        }
      } catch (IOException e) {
        UiLogger.error(msg("tut.io_error", e.getMessage()));
        return 1;
      }
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
  protected boolean shouldApplyProjectRootToConfig() {
    return false;
  }

  @Override
  protected boolean shouldValidateProjectRoot() {
    return false;
  }

  @Override
  protected boolean shouldDisplayStartupBanner(final Config config, final Path projectRoot) {
    return false;
  }

  private void printStartupBanner(final Path root) {
    final int innerWidth = STARTUP_BOX_WIDTH - 2;
    final String horizontal = "─".repeat(innerWidth);
    final ConfigLoaderImpl configLoader = new ConfigLoaderImpl();
    final String startupTitle =
        ">_ "
            + StartupBannerSupport.resolveApplicationName(root, configLoader)
            + " (v"
            + StartupBannerSupport.resolveApplicationVersion(root, configLoader)
            + ")";
    final String startupModelLabel =
        "model:     "
            + StartupBannerSupport.resolveModelName(root, configLoader)
            + "   "
            + MessageSource.getMessage(STARTUP_MODEL_HINT_KEY);
    print("╭" + horizontal + "╮");
    print(formatStartupBoxLine(" " + startupTitle, innerWidth));
    print(formatStartupBoxLine("", innerWidth));
    print(formatStartupBoxLine(" " + startupModelLabel, innerWidth));
    print(
        formatStartupBoxLine(
            " directory: " + StartupBannerSupport.formatDirectory(root), innerWidth));
    print("╰" + horizontal + "╯");
    print("");
    print("  " + MessageSource.getMessage(STARTUP_TIP_KEY));
  }

  private String formatStartupBoxLine(final String content, final int innerWidth) {
    final String normalized = content == null ? "" : content;
    final String clipped =
        normalized.length() > innerWidth ? normalized.substring(0, innerWidth) : normalized;
    final StringBuilder builder = new StringBuilder(innerWidth + 2);
    builder.append('│').append(clipped);
    while (builder.length() < innerWidth + 1) {
      builder.append(' ');
    }
    return builder.append('│').toString();
  }

  private void handleCommandInput(final String input) {
    final Path configPath = activeConfigPath;
    if (tryHandleModelCommand(input, configPath)) {
      return;
    }
    final ConfigCommandService.ParsedCommand parsed = configCommandService.parseCommand(input);
    if (parsed != null) {
      handleConfigCommand(parsed, configPath);
      return;
    }
    outputCommandResult(commandDispatcher.dispatch(input));
  }

  private CommandResult executeCommandShortcut(
      final String slashCommand, final String commandName, final String args) {
    final List<String> cliArgs = new ArrayList<>();
    cliArgs.add("-c");
    cliArgs.add(activeConfigPath.toString());
    cliArgs.add(commandName);
    cliArgs.add("-p");
    cliArgs.add(activeProjectRoot.toString());
    final List<String> extraArgs;
    try {
      extraArgs = parseSlashArguments(args);
    } catch (IllegalArgumentException e) {
      return CommandResult.error(msg("tut.exec.args_invalid", slashCommand));
    }
    cliArgs.addAll(extraArgs);
    final int exitCode = executeNestedCliCommand(cliArgs);
    if (exitCode == 0) {
      return CommandResult.success(List.of());
    }
    return CommandResult.error(msg("tut.exec.failed", slashCommand, exitCode));
  }

  private List<String> parseSlashArguments(final String args) {
    if (args == null || args.isBlank()) {
      return List.of();
    }
    final List<String> tokens = new ArrayList<>();
    final StringBuilder current = new StringBuilder();
    boolean inSingleQuote = false;
    boolean inDoubleQuote = false;
    for (int i = 0; i < args.length(); i++) {
      final char ch = args.charAt(i);
      if (ch == '\'' && !inDoubleQuote) {
        inSingleQuote = !inSingleQuote;
        continue;
      }
      if (ch == '"' && !inSingleQuote) {
        inDoubleQuote = !inDoubleQuote;
        continue;
      }
      if (!inSingleQuote && !inDoubleQuote && Character.isWhitespace(ch)) {
        if (current.length() > 0) {
          tokens.add(current.toString());
          current.setLength(0);
        }
        continue;
      }
      current.append(ch);
    }
    if (inSingleQuote || inDoubleQuote) {
      throw new IllegalArgumentException("Unclosed quote in slash-command arguments");
    }
    if (current.length() > 0) {
      tokens.add(current.toString());
    }
    return tokens;
  }

  protected int executeNestedCliCommand(final List<String> args) {
    final String[] argv = args.toArray(new String[0]);
    if (spec != null && spec.root() != null && spec.root().commandLine() != null) {
      return spec.root().commandLine().execute(argv);
    }
    return Main.createCommandLine(argv).execute(argv);
  }

  private boolean tryHandleModelCommand(final String input, final Path configPath) {
    final String trimmed = input == null ? "" : input.trim();
    if (!trimmed.startsWith("/model")) {
      return false;
    }
    if (trimmed.length() > "/model".length()
        && !Character.isWhitespace(trimmed.charAt("/model".length()))) {
      return false;
    }
    final String args = trimmed.length() <= "/model".length() ? "" : trimmed.substring(6).trim();
    if (args.isBlank()) {
      outputModelSelection(configPath);
      return true;
    }
    outputCommandResult(applyModelCommand(args, configPath));
    return true;
  }

  private void outputModelSelection(final Path configPath) {
    final ConfigEditor editor = loadConfigEditor(configPath);
    if (editor == null) {
      return;
    }
    final String currentProvider = readStringValue(editor, "llm.provider");
    final String currentModel = readStringValue(editor, "llm.model_name");
    final List<String> providers = resolveProviderOptions(editor, currentProvider);
    if (!shouldUseRealtimeInput()) {
      outputCommandResult(nonInteractiveModelSummary(currentProvider, currentModel, providers));
      return;
    }
    try {
      final String selectedProvider =
          chooseFromMenu(msg("tut.model.provider.title"), providers, currentProvider);
      if (selectedProvider == null) {
        outputCommandResult(CommandResult.success(msg("tut.model.cancelled")));
        return;
      }
      final List<String> models =
          resolveModelOptions(editor, selectedProvider, currentProvider, currentModel);
      if (models.isEmpty()) {
        outputCommandResult(CommandResult.error(msg("tut.model.no_models", selectedProvider)));
        return;
      }
      final String preferredModel =
          selectedProvider.equalsIgnoreCase(currentProvider) ? currentModel : "";
      final String selectedModel =
          chooseFromMenu(msg("tut.model.model.title", selectedProvider), models, preferredModel);
      if (selectedModel == null) {
        outputCommandResult(CommandResult.success(msg("tut.model.cancelled")));
        return;
      }
      outputCommandResult(applyProviderAndModel(configPath, selectedProvider, selectedModel));
    } catch (IOException e) {
      realtimeInputDisabled = true;
      outputCommandResult(nonInteractiveModelSummary(currentProvider, currentModel, providers));
    }
  }

  private CommandResult applyModelCommand(final String args, final Path configPath) {
    final String raw = args == null ? "" : args.trim();
    if (raw.isBlank()) {
      return CommandResult.error(msg("tut.model.usage"));
    }
    final int firstSpace = raw.indexOf(' ');
    if (firstSpace < 0) {
      final ConfigEditor editor = loadConfigEditor(configPath);
      if (editor == null) {
        return CommandResult.error(msg("tut.model.load_failed"));
      }
      final String provider = readStringValue(editor, "llm.provider");
      if (provider == null || provider.isBlank()) {
        return CommandResult.error(msg("tut.model.provider_required"));
      }
      return applyProviderAndModel(configPath, provider, raw);
    }
    final String provider = raw.substring(0, firstSpace).trim();
    final String model = raw.substring(firstSpace + 1).trim();
    if (provider.isBlank() || model.isBlank()) {
      return CommandResult.error(msg("tut.model.usage"));
    }
    return applyProviderAndModel(configPath, provider, model);
  }

  private CommandResult applyProviderAndModel(
      final Path configPath, final String provider, final String model) {
    final String normalizedProvider = provider == null ? "" : provider.trim();
    final String normalizedModel = model == null ? "" : model.trim();
    if (normalizedProvider.isBlank() || normalizedModel.isBlank()) {
      return CommandResult.error(msg("tut.model.usage"));
    }
    final CommandResult providerResult =
        configCommandService.applySet(
            "llm.provider " + quoteIfNeeded(normalizedProvider), configPath);
    if (!providerResult.success()) {
      return providerResult;
    }
    final CommandResult modelResult =
        configCommandService.applySet(
            "llm.model_name " + quoteIfNeeded(normalizedModel), configPath);
    if (!modelResult.success()) {
      return modelResult;
    }
    return CommandResult.success(
        List.of(
            msg("tut.model.updated", normalizedProvider, normalizedModel),
            msg("tut.model.hint.validate")));
  }

  private CommandResult nonInteractiveModelSummary(
      final String currentProvider, final String currentModel, final List<String> providers) {
    final List<String> lines = new ArrayList<>();
    lines.add(
        msg(
            "tut.model.current",
            fallbackDisplay(currentProvider, msg("tut.model.unset")),
            fallbackDisplay(currentModel, msg("tut.model.unset"))));
    lines.add(msg("tut.model.usage"));
    if (providers != null && !providers.isEmpty()) {
      lines.add(msg("tut.model.providers"));
      for (final String provider : providers) {
        lines.add("  - " + provider);
      }
    }
    return CommandResult.success(lines);
  }

  private void handleConfigCommand(
      final ConfigCommandService.ParsedCommand parsed, final Path configPath) {
    switch (parsed.type()) {
      case OPEN -> outputConfigItemLines(configPath);
      case GET -> outputCommandResult(configCommandService.applyGet(parsed.args(), configPath));
      case SET -> outputCommandResult(configCommandService.applySet(parsed.args(), configPath));
      case SEARCH ->
          outputSearchResult(
              configCommandService.applySearch(parsed.args(), configPath), configPath);
      case VALIDATE ->
          outputValidationResult(
              configCommandService.validate(configPath, configValidationService), configPath);
      case UNKNOWN ->
          outputCommandResult(
              CommandResult.error(msg("tut.config.unknown_subcommand", parsed.args())));
    }
  }

  private void outputConfigItemLines(final Path configPath) {
    final List<MetadataRegistry.ConfigKeyMetadata> keys = sortedConfigKeys();
    final List<String> lines = configItemLines(configPath, keys);
    if (!shouldUseRealtimeInput() || keys.isEmpty()) {
      outputCommandResult(CommandResult.success(lines));
      return;
    }
    try {
      showConfigKeyList(configPath, keys);
    } catch (IOException e) {
      realtimeInputDisabled = true;
      outputCommandResult(CommandResult.success(lines));
    }
  }

  private List<MetadataRegistry.ConfigKeyMetadata> sortedConfigKeys() {
    final List<MetadataRegistry.ConfigKeyMetadata> keys =
        new ArrayList<>(MetadataRegistry.getDefault().all());
    keys.sort(java.util.Comparator.comparing(MetadataRegistry.ConfigKeyMetadata::path));
    return keys;
  }

  private List<String> configItemLines(
      final Path configPath, final List<MetadataRegistry.ConfigKeyMetadata> keys) {
    final List<String> lines = new java.util.ArrayList<>();
    lines.add("");
    lines.add(msg("tut.config.list.header", keys.size()));
    for (final MetadataRegistry.ConfigKeyMetadata key : keys) {
      final String description = key.localizedDescription();
      if (description == null || description.isBlank()) {
        lines.add(msg("tut.config.list.item_no_description", key.path()));
      } else {
        lines.add(msg("tut.config.list.item", key.path(), description));
      }
    }
    lines.add("");
    lines.add(msg("tut.config.list.hint.get"));
    lines.add(msg("tut.config.list.hint.search"));
    lines.add(msg("tut.config.list.hint.set"));
    lines.add(msg("tut.config.list.hint.validate", configPath));
    return lines;
  }

  private void showConfigKeyList(
      final Path configPath, final List<MetadataRegistry.ConfigKeyMetadata> keys)
      throws IOException {
    final List<String> options = new ArrayList<>(keys.size());
    for (final MetadataRegistry.ConfigKeyMetadata key : keys) {
      final String description = key.localizedDescription();
      if (description == null || description.isBlank()) {
        options.add(msg("tut.config.list.item_no_description", key.path()));
      } else {
        options.add(msg("tut.config.list.item", key.path(), description));
      }
    }
    final int selectedIndex =
        showInteractiveList(
            msg("tut.config.list.header", keys.size()),
            options,
            CONFIG_LIST_PAGE_SIZE,
            0,
            "tut.config.list.viewer.footer",
            true);
    if (selectedIndex >= 0 && selectedIndex < keys.size()) {
      showConfigKeyEditor(configPath, keys.get(selectedIndex));
    }
    print(msg("tut.config.list.hint.get"));
    print(msg("tut.config.list.hint.search"));
    print(msg("tut.config.list.hint.set"));
    print(msg("tut.config.list.hint.validate", configPath));
  }

  private void showConfigKeyEditor(
      final Path configPath, final MetadataRegistry.ConfigKeyMetadata metadata) throws IOException {
    if (metadata == null) {
      return;
    }
    final String path = metadata.path();
    while (true) {
      print("");
      print(msg("tut.config.editor.path", path));
      print(msg("tut.config.editor.type", formatMetadataType(metadata)));
      final String description = metadata.localizedDescription();
      if (description == null || description.isBlank()) {
        print(msg("tut.config.editor.description.empty"));
      } else {
        print(msg("tut.config.editor.description", description));
      }
      print(msg("tut.config.editor.current"));
      outputCommandResult(configCommandService.applyGet(path, configPath));
      final int selectedAction =
          showInteractiveList(
              msg("tut.config.editor.action.title", path),
              List.of(msg("tut.config.editor.action.edit"), msg("tut.config.editor.action.back")),
              CONFIG_EDITOR_ACTION_PAGE_SIZE,
              0,
              "tut.config.editor.action.footer",
              true);
      if (selectedAction < 0 || selectedAction == 1) {
        return;
      }
      final String rawInput = promptConfigEditorInput(metadata);
      if (rawInput == null) {
        outputCommandResult(CommandResult.success(msg("tut.config.editor.cancelled")));
        continue;
      }
      final String setArgs = buildSetArgsFromEditorInput(path, rawInput);
      if (setArgs == null) {
        outputCommandResult(CommandResult.error(msg("tut.config.editor.input.invalid")));
        continue;
      }
      outputCommandResult(configCommandService.applySet(setArgs, configPath));
    }
  }

  private String promptConfigEditorInput(final MetadataRegistry.ConfigKeyMetadata metadata)
      throws IOException {
    print(msg("tut.config.editor.input.hint"));
    if (metadata != null && metadata.isList()) {
      print(msg("tut.config.editor.input.list_hint"));
    } else if (metadata != null && metadata.type() == MetadataRegistry.ValueType.OBJECT) {
      print(msg("tut.config.editor.input.object_hint"));
    }
    printInline(msg("tut.config.editor.input.prompt"));
    final String line = readLine();
    if (line == null) {
      return null;
    }
    final String trimmed = line.trim();
    if (trimmed.isEmpty() || isConfigEditorCancelInput(trimmed)) {
      return null;
    }
    return trimmed;
  }

  private String buildSetArgsFromEditorInput(final String basePath, final String rawInput) {
    final String path = basePath == null ? "" : basePath.trim();
    final String trimmed = rawInput == null ? "" : rawInput.trim();
    if (path.isEmpty() || trimmed.isEmpty()) {
      return null;
    }
    if (trimmed.startsWith("[") || trimmed.startsWith(".")) {
      final int firstSpace = trimmed.indexOf(' ');
      if (firstSpace <= 0 || firstSpace >= trimmed.length() - 1) {
        return null;
      }
      final String suffix = trimmed.substring(0, firstSpace).trim();
      final String value = trimmed.substring(firstSpace + 1).trim();
      if (suffix.isEmpty() || value.isEmpty()) {
        return null;
      }
      return path + suffix + " " + value;
    }
    return path + " " + trimmed;
  }

  private boolean isConfigEditorCancelInput(final String input) {
    final String normalized = input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
    return "q".equals(normalized)
        || "/q".equals(normalized)
        || "b".equals(normalized)
        || "/b".equals(normalized)
        || "back".equals(normalized)
        || "/back".equals(normalized)
        || "cancel".equals(normalized)
        || "/cancel".equals(normalized);
  }

  private String formatMetadataType(final MetadataRegistry.ConfigKeyMetadata metadata) {
    if (metadata == null) {
      return "unknown";
    }
    if (metadata.isList()) {
      final MetadataRegistry.ValueType itemType = metadata.elementType();
      return itemType != null ? "list<" + itemType.name().toLowerCase(Locale.ROOT) + ">" : "list";
    }
    if (metadata.enumOptions() != null && !metadata.enumOptions().isEmpty()) {
      return "enum";
    }
    if (metadata.type() != null) {
      return metadata.type().name().toLowerCase(Locale.ROOT);
    }
    return "unknown";
  }

  private ConfigEditor loadConfigEditor(final Path configPath) {
    final ConfigEditor editor = ConfigEditor.load(configPath);
    if (editor.getLoadError() != null && !editor.isNewFile()) {
      outputCommandResult(CommandResult.error(editor.getLoadError()));
      return null;
    }
    return editor;
  }

  private List<String> resolveProviderOptions(
      final ConfigEditor editor, final String currentProvider) {
    final LinkedHashMap<String, String> providers = new LinkedHashMap<>();
    addUniqueValue(providers, currentProvider);
    addUniqueValues(providers, MODEL_PROVIDER_PRESETS);
    final Object allowedProviders = editor.getValue(pathSegments("llm.allowed_providers"));
    if (allowedProviders instanceof List<?> list) {
      for (final Object item : list) {
        if (item instanceof String provider) {
          addUniqueValue(providers, provider);
        }
      }
    }
    final Map<String, List<String>> allowedModels = readAllowedModels(editor);
    for (final String provider : allowedModels.keySet()) {
      addUniqueValue(providers, provider);
    }
    if (providers.isEmpty()) {
      addUniqueValue(providers, "openai");
    }
    return List.copyOf(providers.values());
  }

  private List<String> resolveModelOptions(
      final ConfigEditor editor,
      final String provider,
      final String currentProvider,
      final String currentModel) {
    final LinkedHashMap<String, String> models = new LinkedHashMap<>();
    if (provider != null
        && currentProvider != null
        && provider.equalsIgnoreCase(currentProvider)
        && currentModel != null
        && !currentModel.isBlank()) {
      addUniqueValue(models, currentModel);
    }
    final Map<String, List<String>> allowedModels = readAllowedModels(editor);
    final String normalizedProvider = normalizeKey(provider);
    final List<String> fromConfig = allowedModels.get(normalizedProvider);
    if (fromConfig != null) {
      addUniqueValues(models, fromConfig);
    }
    final List<String> fromPreset = MODEL_PRESETS_BY_PROVIDER.get(normalizedProvider);
    if (fromPreset != null) {
      addUniqueValues(models, fromPreset);
    }
    return List.copyOf(models.values());
  }

  private Map<String, List<String>> readAllowedModels(final ConfigEditor editor) {
    final Object raw = editor.getValue(pathSegments("llm.allowed_models"));
    if (!(raw instanceof Map<?, ?> map)) {
      return Map.of();
    }
    final Map<String, List<String>> parsed = new LinkedHashMap<>();
    for (final Map.Entry<?, ?> entry : map.entrySet()) {
      if (!(entry.getKey() instanceof String provider)) {
        continue;
      }
      if (!(entry.getValue() instanceof List<?> list)) {
        continue;
      }
      final LinkedHashSet<String> models = new LinkedHashSet<>();
      for (final Object item : list) {
        if (item instanceof String model && !model.isBlank()) {
          models.add(model.trim());
        }
      }
      if (models.isEmpty()) {
        continue;
      }
      parsed.put(normalizeKey(provider), List.copyOf(models));
    }
    return parsed;
  }

  private String readStringValue(final ConfigEditor editor, final String dottedPath) {
    final Object value = editor.getValue(pathSegments(dottedPath));
    if (!(value instanceof String text)) {
      return "";
    }
    return text.trim();
  }

  private List<ConfigEditor.PathSegment> pathSegments(final String dottedPath) {
    if (dottedPath == null || dottedPath.isBlank()) {
      return List.of();
    }
    final String[] parts = dottedPath.split("\\.");
    final List<ConfigEditor.PathSegment> segments = new ArrayList<>(parts.length);
    for (final String part : parts) {
      if (!part.isBlank()) {
        segments.add(ConfigEditor.PathSegment.key(part.trim()));
      }
    }
    return segments;
  }

  private String chooseFromMenu(
      final String title, final List<String> options, final String preferred) throws IOException {
    if (options == null || options.isEmpty()) {
      return null;
    }
    final int selectedIndex =
        showInteractiveList(
            title,
            options,
            MODEL_PICKER_PAGE_SIZE,
            findPreferredIndex(options, preferred),
            "tut.model.viewer.footer",
            true);
    if (selectedIndex < 0) {
      return null;
    }
    return options.get(selectedIndex);
  }

  private int findPreferredIndex(final List<String> options, final String preferred) {
    if (preferred == null || preferred.isBlank()) {
      return 0;
    }
    for (int i = 0; i < options.size(); i++) {
      if (options.get(i).equalsIgnoreCase(preferred.trim())) {
        return i;
      }
    }
    return 0;
  }

  private String formatMenuLine(
      final int index,
      final int totalCount,
      final String value,
      final boolean selected,
      final int terminalColumns) {
    final int digits = Math.max(2, String.valueOf(Math.max(1, totalCount)).length());
    final String numberLabel = String.format(Locale.ROOT, "%" + digits + "d. ", index + 1);
    final String marker = selected ? "› " : "  ";
    final String plain = marker + numberLabel + (value == null ? "" : value);
    final String clipped = truncateForViewport(plain, terminalColumns);
    if (!selected) {
      return clipped;
    }
    return ANSI_HIGHLIGHT + clipped + ANSI_STYLE_RESET;
  }

  private int showInteractiveList(
      final String title,
      final List<String> options,
      final int pageSize,
      final int preferredIndex,
      final String footerMessageKey,
      final boolean cancelOnQuit)
      throws IOException {
    if (options == null || options.isEmpty()) {
      return -1;
    }
    final ListViewport viewport = new ListViewport(options.size(), pageSize, preferredIndex);
    boolean anchorSaved = false;
    try (TerminalModeGuard ignored = TerminalModeGuard.enableRawMode();
        InputStreamReader rawReader = createRawReader()) {
      final int terminalColumns = resolveTerminalColumns();
      while (true) {
        final List<String> frame = new ArrayList<>();
        frame.add(truncateForViewport(title, terminalColumns));
        for (int i = viewport.startIndex(); i < viewport.endExclusive(); i++) {
          frame.add(
              formatMenuLine(
                  i,
                  options.size(),
                  options.get(i),
                  i == viewport.selectedIndex(),
                  terminalColumns));
        }
        frame.add(
            truncateForViewport(
                msg(footerMessageKey, viewport.selectedIndex() + 1, options.size()),
                terminalColumns));
        redrawInlineBlock(frame, anchorSaved);
        anchorSaved = true;
        final int navKey = readNavigationKey(rawReader);
        if (navKey == NAV_KEY_ENTER) {
          return viewport.selectedIndex();
        }
        if (navKey == NAV_KEY_QUIT) {
          return cancelOnQuit ? -1 : viewport.selectedIndex();
        }
        viewport.applyNavigation(navKey);
      }
    } finally {
      print("");
    }
  }

  private String fallbackDisplay(final String value, final String fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    return value;
  }

  private String quoteIfNeeded(final String value) {
    if (value == null) {
      return "\"\"";
    }
    final String trimmed = value.trim();
    if (trimmed.isEmpty()) {
      return "\"\"";
    }
    if (trimmed.contains(" ")) {
      return "\"" + trimmed.replace("\"", "\\\"") + "\"";
    }
    return trimmed;
  }

  private static void addUniqueValues(
      final LinkedHashMap<String, String> target, final List<String> values) {
    if (values == null) {
      return;
    }
    for (final String value : values) {
      addUniqueValue(target, value);
    }
  }

  private static void addUniqueValue(
      final LinkedHashMap<String, String> target, final String value) {
    if (target == null || value == null || value.isBlank()) {
      return;
    }
    final String trimmed = value.trim();
    target.putIfAbsent(normalizeKey(trimmed), trimmed);
  }

  private static String normalizeKey(final String value) {
    if (value == null) {
      return "";
    }
    return value.trim().toLowerCase(Locale.ROOT);
  }

  private static Map<String, List<String>> buildModelPresetsByProvider() {
    final Map<String, List<String>> models = new LinkedHashMap<>();
    models.put("openai", List.of("gpt-5", "gpt-5-mini", "gpt-4.1", "gpt-4o"));
    models.put(
        "anthropic",
        List.of("claude-sonnet-4-20250514", "claude-3-7-sonnet-latest", "claude-3-5-haiku-latest"));
    models.put("gemini", List.of("gemini-2.5-pro", "gemini-2.5-flash", "gemini-2.0-flash-exp"));
    models.put("azure-openai", List.of("gpt-5", "gpt-4.1", "gpt-4o"));
    models.put("vertex-ai", List.of("gemini-2.5-pro", "gemini-2.5-flash", "gemini-2.0-flash-exp"));
    models.put("bedrock", List.of("anthropic.claude-3-5-sonnet-20240620-v1:0"));
    models.put("openai-compatible", List.of("gpt-4o", "gpt-4o-mini"));
    models.put("local", List.of("llama3.1:8b", "qwen2.5:14b"));
    models.put("ollama", List.of("llama3.1:8b", "qwen2.5:14b"));
    models.put("mock", List.of("mock-model"));
    return Map.copyOf(models);
  }

  private int resolveTerminalColumns() {
    try {
      final ProcessBuilder pb = new ProcessBuilder("stty", "size");
      pb.redirectInput(new File(TTY_DEVICE));
      final Process process = pb.start();
      final String stdout;
      try (var out = process.getInputStream();
          var err = process.getErrorStream()) {
        stdout = new String(out.readAllBytes(), StandardCharsets.UTF_8).trim();
        // drain to avoid blocking
        err.readAllBytes();
      }
      final int exitCode;
      try {
        exitCode = process.waitFor();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return DEFAULT_TERMINAL_COLUMNS;
      }
      if (exitCode != 0 || stdout.isBlank()) {
        return DEFAULT_TERMINAL_COLUMNS;
      }
      final String[] parts = stdout.split("\\s+");
      if (parts.length < 2) {
        return DEFAULT_TERMINAL_COLUMNS;
      }
      final int columns = Integer.parseInt(parts[1]);
      return Math.max(MIN_TERMINAL_COLUMNS, columns);
    } catch (IOException | NumberFormatException e) {
      return DEFAULT_TERMINAL_COLUMNS;
    }
  }

  private String truncateForViewport(final String line, final int terminalColumns) {
    final String text = line == null ? "" : line;
    final int maxWidth = Math.max(8, terminalColumns - 1);
    if (text.length() <= maxWidth) {
      return text;
    }
    final int head = Math.max(1, maxWidth - 3);
    return text.substring(0, head) + "...";
  }

  private void redrawInlineBlock(final List<String> lines, final boolean anchorSaved) {
    final StringBuilder frame = new StringBuilder();
    if (anchorSaved) {
      frame.append(ANSI_RESTORE_CURSOR);
    } else {
      frame.append(ANSI_SAVE_CURSOR);
    }
    frame.append(ANSI_CLEAR_TO_END);
    for (int i = 0; i < lines.size(); i++) {
      frame.append('\r').append(ANSI_CLEAR_LINE);
      frame.append(lines.get(i));
      if (i < lines.size() - 1) {
        frame.append('\n');
      }
    }
    printInline(frame.toString());
  }

  private int readNavigationKey(final InputStreamReader reader) throws IOException {
    final int codePoint = reader.read();
    if (codePoint < 0) {
      return NAV_KEY_QUIT;
    }
    if (codePoint == '\r' || codePoint == '\n') {
      return NAV_KEY_ENTER;
    }
    if (codePoint == 'q' || codePoint == 'Q') {
      return NAV_KEY_QUIT;
    }
    if (codePoint == 'k' || codePoint == 'K') {
      return NAV_KEY_UP;
    }
    if (codePoint == 'j' || codePoint == 'J') {
      return NAV_KEY_DOWN;
    }
    if (codePoint != 27) {
      return NAV_KEY_OTHER;
    }
    final int second = reader.read();
    if (second != '[') {
      return NAV_KEY_QUIT;
    }
    final int third = reader.read();
    return switch (third) {
      case 'A' -> NAV_KEY_UP;
      case 'B' -> NAV_KEY_DOWN;
      case 'H' -> NAV_KEY_HOME;
      case 'F' -> NAV_KEY_END;
      case '5' -> {
        skipTilde(reader);
        yield NAV_KEY_PAGE_UP;
      }
      case '6' -> {
        skipTilde(reader);
        yield NAV_KEY_PAGE_DOWN;
      }
      default -> NAV_KEY_OTHER;
    };
  }

  private void skipTilde(final InputStreamReader reader) throws IOException {
    final int next = reader.read();
    if (next == '~') {
      return;
    }
    while (System.in.available() > 0) {
      if (System.in.read() < 0) {
        return;
      }
    }
  }

  private void outputSearchResult(
      final ConfigCommandService.SearchResult result, final Path configPath) {
    if (result == null) {
      return;
    }
    if (result.shouldOpenEditor()) {
      final MetadataRegistry.ConfigKeyMetadata metadata = result.singleMatch();
      if (metadata == null) {
        return;
      }
      final String description = metadata.localizedDescription();
      if (description == null || description.isBlank()) {
        print(msg("tut.config.search.single", metadata.path()));
      } else {
        print(msg("tut.config.search.single_with_description", metadata.path(), description));
      }
      if (shouldUseRealtimeInput()) {
        try {
          showConfigKeyEditor(configPath, metadata);
          return;
        } catch (IOException e) {
          realtimeInputDisabled = true;
        }
      }
      print(msg("tut.config.search.single_hint", metadata.path()));
      return;
    }
    outputCommandResult(result.commandResult());
  }

  private void outputValidationResult(
      final List<ConfigValidationService.ValidationIssue> issues, final Path configPath) {
    if (issues == null || issues.isEmpty()) {
      print(msg("tut.config.validate.ok", configPath));
      return;
    }
    print(msg("tut.config.validate.ng", issues.size(), configPath));
    for (final ConfigValidationService.ValidationIssue issue : issues) {
      if (issue.path() == null || issue.path().isBlank()) {
        print(msg("tut.config.validate.issue_no_path", issue.message()));
      } else {
        print(msg("tut.config.validate.issue", issue.path(), issue.message()));
      }
    }
  }

  private void outputCommandResult(final CommandResult result) {
    if (result == null) {
      return;
    }
    if (result.success()) {
      for (final String line : result.outputLines()) {
        print(line);
      }
      return;
    }
    print(msg("tut.command.error", result.errorMessage()));
  }

  private void renderPlan(final String input) {
    final Plan plan = planBuilder.build(input);
    print(msg("tut.plan.header"));
    for (final String line : plan.toDisplayString().split("\\R")) {
      print(line);
    }
  }

  private boolean isExitInput(final String input) {
    final String normalized = input.toLowerCase(java.util.Locale.ROOT);
    return "q".equals(normalized)
        || "quit".equals(normalized)
        || "exit".equals(normalized)
        || "/q".equals(normalized)
        || "/quit".equals(normalized)
        || "/exit".equals(normalized);
  }

  private void registerTutSpecificCommands() {
    commandDispatcher.setHelpLines(buildHelpLines());
    commandDispatcher.register(
        "/status",
        args ->
            CommandResult.success(
                List.of(
                    MessageSource.getMessage("tut.status.header"),
                    "",
                    MessageSource.getMessage("tut.status.mode"),
                    MessageSource.getMessage("tut.status.state"),
                    MessageSource.getMessage("tut.status.ready"))));
    commandDispatcher.register(
        SLASH_ANALYZE, args -> executeCommandShortcut(SLASH_ANALYZE, "analyze", args));
    commandDispatcher.register(
        SLASH_REPORT, args -> executeCommandShortcut(SLASH_REPORT, "report", args));
    commandDispatcher.register(
        SLASH_DOCUMENT, args -> executeCommandShortcut(SLASH_DOCUMENT, "document", args));
  }

  protected void print(final String message) {
    if (message == null) {
      UiLogger.stdout("");
    } else {
      UiLogger.stdout(message);
    }
  }

  protected void printInline(final String message) {
    if (message == null) {
      UiLogger.stdoutInline("");
    } else {
      UiLogger.stdoutInline(message);
    }
  }

  protected String readLine() throws IOException {
    return reader.readLine();
  }

  private InputLine readInputLine() throws IOException {
    if (shouldUseRealtimeInput()) {
      try {
        return readRealtimeInputLine();
      } catch (IOException e) {
        realtimeInputDisabled = true;
      }
    }
    printInline(msg("tut.prompt"));
    return new InputLine(readLine());
  }

  protected boolean shouldUseRealtimeInput() {
    return !realtimeInputDisabled && System.console() != null;
  }

  private InputLine readRealtimeInputLine() throws IOException {
    try (TerminalModeGuard ignored = TerminalModeGuard.enableRawMode();
        InputStreamReader rawReader = createRawReader()) {
      final String prompt = msg("tut.prompt");
      final StringBuilder buffer = new StringBuilder();
      int renderedSuggestionLines = 0;
      printInline(prompt);
      while (true) {
        final int codePoint = rawReader.read();
        if (codePoint < 0) {
          return new InputLine(null);
        }
        if (codePoint == '\r' || codePoint == '\n') {
          renderRealtimePrompt(prompt, buffer.toString(), List.of(), renderedSuggestionLines);
          print("");
          return new InputLine(buffer.toString());
        }
        if (codePoint == 8 || codePoint == 127) {
          if (!buffer.isEmpty()) {
            buffer.deleteCharAt(buffer.length() - 1);
          }
          renderedSuggestionLines =
              renderRealtimePrompt(
                  prompt,
                  buffer.toString(),
                  resolveSlashSuggestions(buffer.toString()),
                  renderedSuggestionLines);
          continue;
        }
        if (codePoint == 27) {
          consumeEscapeSequence();
          renderedSuggestionLines =
              renderRealtimePrompt(
                  prompt,
                  buffer.toString(),
                  resolveSlashSuggestions(buffer.toString()),
                  renderedSuggestionLines);
          continue;
        }
        if (Character.isISOControl(codePoint)) {
          continue;
        }
        final char typed = (char) codePoint;
        buffer.append(typed);
        renderedSuggestionLines =
            renderRealtimePrompt(
                prompt,
                buffer.toString(),
                resolveSlashSuggestions(buffer.toString()),
                renderedSuggestionLines);
      }
    }
  }

  private InputStreamReader createRawReader() {
    return new InputStreamReader(new NonClosingInputStream(System.in), StandardCharsets.UTF_8);
  }

  private int renderRealtimePrompt(
      final String prompt,
      final String input,
      final List<String> suggestions,
      final int previousSuggestionLines) {
    final StringBuilder frame = new StringBuilder();
    frame
        .append('\r')
        .append(ANSI_CLEAR_LINE)
        .append(prompt)
        .append(input)
        .append(ANSI_SAVE_CURSOR);
    int renderedLines = 0;
    for (final String line : suggestions) {
      frame.append('\n').append(ANSI_CLEAR_LINE).append(line);
      renderedLines++;
    }
    for (int i = renderedLines; i < previousSuggestionLines; i++) {
      frame.append('\n').append(ANSI_CLEAR_LINE);
    }
    frame.append(ANSI_RESTORE_CURSOR);
    printInline(frame.toString());
    return renderedLines;
  }

  private List<String> resolveSlashSuggestions(final String inputBuffer) {
    if (inputBuffer == null || !inputBuffer.startsWith("/")) {
      return List.of();
    }
    final List<String> all = commandDispatcher.getHelpLines();
    if (inputBuffer.length() <= 1) {
      return all;
    }
    final String prefix = inputBuffer.toLowerCase(java.util.Locale.ROOT);
    final List<String> filtered =
        all.stream()
            .filter(
                line -> {
                  final String commandToken = extractSuggestionCommandToken(line);
                  if (commandToken.isEmpty()) {
                    return false;
                  }
                  return commandToken.toLowerCase(java.util.Locale.ROOT).startsWith(prefix);
                })
            .toList();
    return filtered.isEmpty() ? all : filtered;
  }

  private String extractSuggestionCommandToken(final String suggestionLine) {
    if (suggestionLine == null) {
      return "";
    }
    final String trimmed = suggestionLine.stripLeading();
    if (!trimmed.startsWith("/")) {
      return "";
    }
    final int separator = trimmed.indexOf("  ");
    if (separator < 0) {
      return trimmed;
    }
    return trimmed.substring(0, separator).trim();
  }

  private void consumeEscapeSequence() throws IOException {
    while (System.in.available() > 0) {
      if (System.in.read() < 0) {
        return;
      }
    }
  }

  static final class ListViewport {

    private final int itemCount;

    private final int pageSize;

    private int selectedIndex;

    private int startIndex;

    ListViewport(final int itemCount, final int requestedPageSize, final int initialIndex) {
      this.itemCount = Math.max(1, itemCount);
      this.pageSize = Math.max(MIN_LIST_PAGE_SIZE, requestedPageSize);
      this.selectedIndex = clamp(initialIndex, 0, this.itemCount - 1);
      this.startIndex = 0;
      alignStartToSelection();
    }

    int selectedIndex() {
      return selectedIndex;
    }

    int startIndex() {
      return startIndex;
    }

    int endExclusive() {
      return Math.min(itemCount, startIndex + pageSize);
    }

    void applyNavigation(final int navKey) {
      switch (navKey) {
        case NAV_KEY_UP -> moveUp();
        case NAV_KEY_DOWN -> moveDown();
        case NAV_KEY_PAGE_UP -> pageUp();
        case NAV_KEY_PAGE_DOWN -> pageDown();
        case NAV_KEY_HOME -> moveHome();
        case NAV_KEY_END -> moveEnd();
        default -> {}
      }
    }

    void moveUp() {
      moveBy(-1);
    }

    void moveDown() {
      moveBy(1);
    }

    void pageUp() {
      moveBy(-pageSize);
    }

    void pageDown() {
      moveBy(pageSize);
    }

    void moveHome() {
      moveTo(0);
    }

    void moveEnd() {
      moveTo(itemCount - 1);
    }

    private void moveBy(final int delta) {
      moveTo(selectedIndex + delta);
    }

    private void moveTo(final int index) {
      selectedIndex = clamp(index, 0, itemCount - 1);
      alignStartToSelection();
    }

    private void alignStartToSelection() {
      final int maxStart = Math.max(0, itemCount - pageSize);
      if (selectedIndex < startIndex) {
        startIndex = selectedIndex;
        return;
      }
      if (selectedIndex >= startIndex + pageSize) {
        startIndex = selectedIndex - pageSize + 1;
      }
      startIndex = clamp(startIndex, 0, maxStart);
    }

    private static int clamp(final int value, final int min, final int max) {
      return Math.max(min, Math.min(max, value));
    }
  }

  private record InputLine(String text) {}

  private static final class NonClosingInputStream extends java.io.FilterInputStream {

    NonClosingInputStream(final java.io.InputStream in) {
      super(in);
    }

    @Override
    public void close() {
      // Keep System.in open for subsequent prompt reads.
    }
  }

  private static final class TerminalModeGuard implements AutoCloseable {

    private final String originalMode;

    private boolean closed;

    private TerminalModeGuard(final String originalMode) {
      this.originalMode = originalMode;
      this.closed = false;
    }

    static TerminalModeGuard enableRawMode() throws IOException {
      final String original = runStty("-g");
      runStty("-icanon", "-echo", "min", "1", "time", "0");
      return new TerminalModeGuard(original);
    }

    @Override
    public void close() throws IOException {
      if (closed) {
        return;
      }
      closed = true;
      runStty(originalMode);
    }

    private static String runStty(final String... args) throws IOException {
      final List<String> command = new ArrayList<>();
      command.add("stty");
      command.addAll(List.of(args));
      final ProcessBuilder pb = new ProcessBuilder(command);
      pb.redirectInput(new File(TTY_DEVICE));
      final Process process = pb.start();
      final String stdout;
      final String stderr;
      try (var out = process.getInputStream();
          var err = process.getErrorStream()) {
        stdout = new String(out.readAllBytes(), StandardCharsets.UTF_8).trim();
        stderr = new String(err.readAllBytes(), StandardCharsets.UTF_8).trim();
      }
      final int exitCode;
      try {
        exitCode = process.waitFor();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IOException("Interrupted while configuring terminal mode", e);
      }
      if (exitCode != 0) {
        final String detail = stderr.isBlank() ? "exit code " + exitCode : stderr;
        throw new IOException("stty failed: " + detail);
      }
      return stdout;
    }
  }
}
