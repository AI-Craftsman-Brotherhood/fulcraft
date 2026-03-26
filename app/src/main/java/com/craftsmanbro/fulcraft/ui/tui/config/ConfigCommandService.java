package com.craftsmanbro.fulcraft.ui.tui.config;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.json.impl.JsonMapperFactory;
import com.craftsmanbro.fulcraft.ui.tui.PathParser;
import com.craftsmanbro.fulcraft.ui.tui.command.CommandResult;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Config コマンド (/config set, /config search 等) のビジネスロジックを担当するサービス。
 *
 * <p>このクラスは TuiApplication から Config 関連の CLI コマンド処理を分離し、 テスト容易性と保守性を向上させます。
 */
public class ConfigCommandService {

  private static final String MASKED_SECRET = "****";

  // ─────────────────────────────────────────────────────────────────────────────
  // Command types
  // ─────────────────────────────────────────────────────────────────────────────
  /** Config コマンドのタイプ */
  public enum CommandType {
    OPEN,
    GET,
    SET,
    SEARCH,
    VALIDATE,
    UNKNOWN
  }

  /** パースされた Config コマンド */
  public record ParsedCommand(CommandType type, String args) {}

  /** パースされた入力値 */
  private record ParsedInput(String value, boolean quoted) {}

  private record SetRequest(String rawPath, String rawValue) {}

  private record ParseResult<T>(T value, String errorMessage) {

    static <T> ParseResult<T> success(final T value) {
      return new ParseResult<>(value, null);
    }

    static <T> ParseResult<T> error(final String errorMessage) {
      return new ParseResult<>(null, errorMessage);
    }

    boolean hasError() {
      return errorMessage != null;
    }
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Fields
  // ─────────────────────────────────────────────────────────────────────────────
  private final MetadataRegistry metadataRegistry;

  private final ObjectMapper prettyPrintMapper = JsonMapperFactory.createPrettyPrinter();

  public ConfigCommandService(final MetadataRegistry metadataRegistry) {
    this.metadataRegistry = Objects.requireNonNull(metadataRegistry, "metadataRegistry");
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Command parsing
  // ─────────────────────────────────────────────────────────────────────────────
  /**
   * 入力文字列を Config コマンドとしてパースします。
   *
   * @param input 入力文字列
   * @return ParsedCommand、または入力が /config コマンドでない場合は null
   */
  public ParsedCommand parseCommand(final String input) {
    final String trimmed = input == null ? "" : input.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    final String[] parts = trimmed.split("\\s+", 2);
    final String command = parts[0].trim();
    if (!"/config".equalsIgnoreCase(command)) {
      return null;
    }
    if (parts.length == 1) {
      return new ParsedCommand(CommandType.OPEN, "");
    }
    final String remainder = parts[1].trim();
    if (remainder.isEmpty()) {
      return new ParsedCommand(CommandType.OPEN, "");
    }
    final String[] subParts = remainder.split("\\s+", 2);
    final String subcommand = subParts[0].trim().toLowerCase(java.util.Locale.ROOT);
    final String args = subParts.length > 1 ? subParts[1].trim() : "";
    return switch (subcommand) {
      case "get" -> new ParsedCommand(CommandType.GET, args);
      case "set" -> new ParsedCommand(CommandType.SET, args);
      case "search" -> new ParsedCommand(CommandType.SEARCH, args);
      case "validate" -> new ParsedCommand(CommandType.VALIDATE, args);
      default -> new ParsedCommand(CommandType.UNKNOWN, remainder);
    };
  }

  /**
   * /config get コマンドを実行します。
   *
   * @param args コマンド引数 (path)。未指定時は全体を表示
   * @param configPath 設定ファイルのパス
   * @return コマンド結果
   */
  public CommandResult applyGet(final String args, final Path configPath) {
    final ParseResult<ConfigEditor> editorResult = loadEditor(configPath);
    if (editorResult.hasError()) {
      return CommandResult.error(editorResult.errorMessage());
    }
    final ConfigEditor editor = editorResult.value();
    final String rawPath = args == null ? "" : args.trim();
    if (rawPath.isEmpty()) {
      final Object rootValue = editor.getValue(List.of());
      final Object masked = maskSecrets(editor, List.of(), rootValue);
      return CommandResult.success(formatGetOutput(msg("tui.config_editor.path.root"), masked));
    }
    final ParseResult<PathParser.ParsedPath> pathResult = parsePath(rawPath);
    if (pathResult.hasError()) {
      return CommandResult.error(pathResult.errorMessage());
    }
    final PathParser.ParsedPath parsed = pathResult.value();
    if (parsed.append()) {
      return CommandResult.error(msg("tui.config_cmd.list_append_not_allowed", rawPath));
    }
    if (!editor.pathExists(parsed.segments())) {
      return CommandResult.error(msg("tui.config_cmd.path_not_found", rawPath));
    }
    final Object value = editor.getValue(parsed.segments());
    final Object masked = maskSecrets(editor, parsed.segments(), value);
    final String displayPath = PathParser.toPathString(parsed.segments());
    return CommandResult.success(formatGetOutput(displayPath, masked));
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // /config set
  // ─────────────────────────────────────────────────────────────────────────────
  /**
   * /config set コマンドを実行します。
   *
   * @param args コマンド引数 (path value)
   * @param configPath 設定ファイルのパス
   * @return コマンド結果
   */
  public CommandResult applySet(final String args, final Path configPath) {
    final ParseResult<SetRequest> requestResult = parseSetRequest(args);
    if (requestResult.hasError()) {
      return CommandResult.error(requestResult.errorMessage());
    }
    final SetRequest request = requestResult.value();
    final ParseResult<PathParser.ParsedPath> pathResult = parsePath(request.rawPath());
    if (pathResult.hasError()) {
      return CommandResult.error(pathResult.errorMessage());
    }
    final PathParser.ParsedPath parsed = pathResult.value();
    final ParseResult<ConfigEditor> editorResult = loadEditor(configPath);
    if (editorResult.hasError()) {
      return CommandResult.error(editorResult.errorMessage());
    }
    final ConfigEditor editor = editorResult.value();
    final String metadataPath = PathParser.toMetadataPath(parsed.segments());
    final MetadataRegistry.ConfigKeyMetadata metadata =
        metadataRegistry.find(metadataPath).orElse(null);
    final boolean listOperation = parsed.append() || parsed.hasIndex();
    final String listError = validateListOperation(request.rawPath(), metadata, listOperation);
    if (listError != null) {
      return CommandResult.error(listError);
    }
    final ParseResult<Object> valueResult = parseValue(request.rawValue(), metadata, listOperation);
    if (valueResult.hasError()) {
      return CommandResult.error(valueResult.errorMessage());
    }
    final Object value = valueResult.value();
    final String updateError = applyUpdate(editor, parsed, value, request.rawPath());
    if (updateError != null) {
      return CommandResult.error(updateError);
    }
    final String summary = editor.summarizeValue(parsed.segments(), value, 80);
    final String action =
        parsed.append() ? msg("tui.config_cmd.action_added") : msg("tui.config_cmd.action_updated");
    return CommandResult.success(
        msg("tui.config_cmd.set_result", action, request.rawPath(), summary));
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // /config search
  // ─────────────────────────────────────────────────────────────────────────────
  /** 検索結果: 単一マッチの場合はエディタを開く必要がある */
  public record SearchResult(
      CommandResult commandResult, MetadataRegistry.ConfigKeyMetadata singleMatch) {

    public static SearchResult openEditor(final MetadataRegistry.ConfigKeyMetadata metadata) {
      return new SearchResult(null, metadata);
    }

    public static SearchResult displayResults(final CommandResult result) {
      return new SearchResult(result, null);
    }

    public boolean shouldOpenEditor() {
      return singleMatch != null;
    }
  }

  /**
   * /config search コマンドを実行します。
   *
   * @param args 検索キーワード
   * @param configPath 設定ファイルのパス
   * @return 検索結果
   */
  public SearchResult applySearch(final String args, final Path configPath) {
    if (args == null || args.isBlank()) {
      return SearchResult.displayResults(CommandResult.error(msg("tui.config_cmd.usage_search")));
    }
    final String keyword = args.trim();
    final ConfigEditor editor = ConfigEditor.load(configPath);
    if (editor.getLoadError() != null && !editor.isNewFile()) {
      return SearchResult.displayResults(CommandResult.error(editor.getLoadError()));
    }
    final List<MetadataRegistry.ConfigKeyMetadata> matches = metadataRegistry.search(keyword);
    if (matches.isEmpty()) {
      return SearchResult.displayResults(
          CommandResult.success(msg("tui.config_cmd.no_matches", keyword)));
    }
    final List<MetadataRegistry.ConfigKeyMetadata> visible = new ArrayList<>();
    for (final MetadataRegistry.ConfigKeyMetadata metadata : matches) {
      if (metadata.isVisible(editor)) {
        visible.add(metadata);
      }
    }
    final List<MetadataRegistry.ConfigKeyMetadata> candidates =
        visible.isEmpty() ? matches : visible;
    if (candidates.size() == 1) {
      return SearchResult.openEditor(candidates.get(0));
    }
    final int limit = Math.min(20, candidates.size());
    final List<String> lines = new ArrayList<>();
    lines.add(msg("tui.config_cmd.matches_header"));
    for (int i = 0; i < limit; i++) {
      final MetadataRegistry.ConfigKeyMetadata metadata = candidates.get(i);
      final String typeLabel = formatMetadataType(metadata);
      final String description =
          metadata.localizedDescription() == null || metadata.localizedDescription().isBlank()
              ? ""
              : msg("tui.config_cmd.search_description", metadata.localizedDescription());
      lines.add(msg("tui.config_cmd.search_item", metadata.path(), typeLabel, description));
    }
    if (candidates.size() > limit) {
      lines.add(msg("tui.config_cmd.more_results", candidates.size() - limit));
    }
    lines.add(msg("tui.config_cmd.refine_search"));
    return SearchResult.displayResults(CommandResult.success(lines));
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // /config validate
  // ─────────────────────────────────────────────────────────────────────────────
  /**
   * Config のバリデーションを実行します。
   *
   * @param configPath 設定ファイルのパス
   * @param validationService バリデーションサービス
   * @return バリデーション結果 (issues のリスト、空の場合はバリデーション成功)
   */
  public List<ConfigValidationService.ValidationIssue> validate(
      final Path configPath, final ConfigValidationService validationService) {
    final ConfigEditor editor = ConfigEditor.load(configPath);
    if (editor.getLoadError() != null && !editor.isNewFile()) {
      return List.of(new ConfigValidationService.ValidationIssue("", editor.getLoadError()));
    }
    return validationService.validate(editor.toJson());
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Value parsing helpers
  // ─────────────────────────────────────────────────────────────────────────────
  private ParsedInput parseRawValue(final String rawValue) {
    if (rawValue == null) {
      return new ParsedInput("", false);
    }
    final String trimmed = rawValue.trim();
    if ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
        || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
      if (trimmed.length() < 2) {
        return new ParsedInput("", true);
      }
      return new ParsedInput(trimmed.substring(1, trimmed.length() - 1), true);
    }
    return new ParsedInput(trimmed, false);
  }

  private Object parseConfigValue(
      final ParsedInput input,
      final MetadataRegistry.ConfigKeyMetadata metadata,
      final boolean listOperation) {
    if (metadata == null) {
      return inferScalar(input);
    }
    if (metadata.isList() && listOperation) {
      if (metadata.isEnum()) {
        return parseEnum(input, metadata.enumOptions());
      }
      final MetadataRegistry.ValueType itemType = metadata.elementType();
      if (itemType == null) {
        return inferScalar(input);
      }
      return parseByType(input, itemType);
    }
    if (metadata.enumOptions() != null && !metadata.enumOptions().isEmpty()) {
      return parseEnum(input, metadata.enumOptions());
    }
    return parseByType(input, metadata.type());
  }

  private Object parseEnum(final ParsedInput input, final List<String> options) {
    for (final String option : options) {
      if (option.equalsIgnoreCase(input.value())) {
        return option;
      }
    }
    throw new IllegalArgumentException(msg("tui.config_cmd.invalid_enum", input.value()));
  }

  private Object parseByType(final ParsedInput input, final MetadataRegistry.ValueType type) {
    if (type == null) {
      return inferScalar(input);
    }
    return switch (type) {
      case BOOLEAN -> parseBooleanStrict(input.value());
      case INTEGER -> Long.parseLong(input.value());
      case FLOAT -> Double.parseDouble(input.value());
      default -> input.value();
    };
  }

  private Object inferScalar(final ParsedInput input) {
    if (input.quoted()) {
      return input.value();
    }
    final String value = input.value();
    if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
      return Boolean.parseBoolean(value);
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException ignored) {
      // Not a long
    }
    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException ignored) {
      // Not a double
    }
    return value;
  }

  private String formatMetadataType(final MetadataRegistry.ConfigKeyMetadata metadata) {
    if (metadata.isList()) {
      final MetadataRegistry.ValueType itemType = metadata.elementType();
      return itemType != null
          ? msg("tui.config_cmd.type.list_of", localizeValueType(itemType))
          : msg("tui.config_cmd.type.list");
    }
    if (metadata.enumOptions() != null && !metadata.enumOptions().isEmpty()) {
      return msg("tui.config_cmd.type.enum");
    }
    if (metadata.type() != null) {
      return localizeValueType(metadata.type());
    }
    return msg("tui.config_cmd.type.unknown");
  }

  private String localizeValueType(final MetadataRegistry.ValueType type) {
    if (type == null) {
      return msg("tui.config_cmd.type.unknown");
    }
    return switch (type) {
      case BOOLEAN -> msg("tui.config.value_type.boolean");
      case INTEGER -> msg("tui.config.value_type.integer");
      case FLOAT -> msg("tui.config.value_type.float");
      case STRING -> msg("tui.config.value_type.string");
      case LIST -> msg("tui.config.value_type.list");
      case OBJECT -> msg("tui.config.value_type.object");
    };
  }

  private boolean parseBooleanStrict(final String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(msg("tui.config_cmd.invalid_boolean"));
    }
    if ("true".equalsIgnoreCase(value)) {
      return true;
    }
    if ("false".equalsIgnoreCase(value)) {
      return false;
    }
    throw new IllegalArgumentException(msg("tui.config_cmd.invalid_boolean_value", value));
  }

  private ParseResult<SetRequest> parseSetRequest(final String args) {
    if (args == null || args.isBlank()) {
      return ParseResult.error(msg("tui.config_cmd.usage_set"));
    }
    final String[] parts = args.trim().split("\\s+", 2);
    if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
      return ParseResult.error(msg("tui.config_cmd.usage_set"));
    }
    return ParseResult.success(new SetRequest(parts[0].trim(), parts[1].trim()));
  }

  private List<String> formatGetOutput(final String displayPath, final Object value) {
    final List<String> lines = new ArrayList<>();
    lines.add(msg("tui.config_cmd.config_value", displayPath));
    final String pretty = prettyPrint(value);
    for (final String line : pretty.split("\\R", -1)) {
      lines.add(line);
    }
    return lines;
  }

  private String prettyPrint(final Object value) {
    try {
      return prettyPrintMapper.writeValueAsString(value);
    } catch (JacksonException e) {
      return String.valueOf(value);
    }
  }

  private Object maskSecrets(
      final ConfigEditor editor, final List<ConfigEditor.PathSegment> path, final Object value) {
    if (editor.isSecretPath(path)) {
      return MASKED_SECRET;
    }
    if (value instanceof Map<?, ?> mapValue) {
      final Map<String, Object> masked = new LinkedHashMap<>();
      for (final Map.Entry<?, ?> entry : mapValue.entrySet()) {
        final String key = String.valueOf(entry.getKey());
        final List<ConfigEditor.PathSegment> childPath = new ArrayList<>(path);
        childPath.add(ConfigEditor.PathSegment.key(key));
        masked.put(key, maskSecrets(editor, childPath, entry.getValue()));
      }
      return masked;
    }
    if (value instanceof List<?> listValue) {
      final List<Object> masked = new ArrayList<>();
      for (int i = 0; i < listValue.size(); i++) {
        final List<ConfigEditor.PathSegment> childPath = new ArrayList<>(path);
        childPath.add(ConfigEditor.PathSegment.index(i));
        masked.add(maskSecrets(editor, childPath, listValue.get(i)));
      }
      return masked;
    }
    return value;
  }

  private ParseResult<PathParser.ParsedPath> parsePath(final String rawPath) {
    try {
      return ParseResult.success(PathParser.parse(rawPath));
    } catch (IllegalArgumentException e) {
      return ParseResult.error(e.getMessage());
    }
  }

  private ParseResult<ConfigEditor> loadEditor(final Path configPath) {
    final ConfigEditor editor = ConfigEditor.load(configPath);
    if (editor.getLoadError() != null && !editor.isNewFile()) {
      return ParseResult.error(editor.getLoadError());
    }
    return ParseResult.success(editor);
  }

  private String validateListOperation(
      final String rawPath,
      final MetadataRegistry.ConfigKeyMetadata metadata,
      final boolean listOperation) {
    if (metadata == null) {
      return null;
    }
    if (metadata.isList() && !listOperation) {
      return msg("tui.config_cmd.list_requires_index", rawPath);
    }
    if (!metadata.isList() && listOperation) {
      return msg("tui.config_cmd.not_a_list", rawPath);
    }
    return null;
  }

  private ParseResult<Object> parseValue(
      final String rawValue,
      final MetadataRegistry.ConfigKeyMetadata metadata,
      final boolean listOperation) {
    final ParsedInput parsedInput = parseRawValue(rawValue);
    try {
      return ParseResult.success(parseConfigValue(parsedInput, metadata, listOperation));
    } catch (IllegalArgumentException e) {
      return ParseResult.error(e.getMessage());
    }
  }

  private String applyUpdate(
      final ConfigEditor editor,
      final PathParser.ParsedPath parsed,
      final Object value,
      final String rawPath) {
    final boolean updated =
        parsed.append()
            ? editor.appendListValueWithCreate(parsed.segments(), value)
            : editor.setValueWithCreate(parsed.segments(), value);
    if (!updated) {
      return msg("tui.config_cmd.update_failed", rawPath);
    }
    try {
      editor.save();
    } catch (IOException e) {
      return msg("tui.config_cmd.save_failed", e.getMessage());
    }
    return null;
  }

  private static String msg(final String key, final Object... args) {
    return MessageSource.getMessage(key, args);
  }
}
