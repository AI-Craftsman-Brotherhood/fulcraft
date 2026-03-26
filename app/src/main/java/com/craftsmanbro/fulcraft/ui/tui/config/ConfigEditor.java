package com.craftsmanbro.fulcraft.ui.tui.config;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.collection.contract.CollectionServicePort;
import com.craftsmanbro.fulcraft.infrastructure.collection.impl.DefaultCollectionService;
import com.craftsmanbro.fulcraft.infrastructure.json.impl.JsonMapperFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public final class ConfigEditor {

  public static final List<String> CATEGORIES =
      List.of(
          "project",
          "analysis",
          "selection_rules",
          "llm",
          "context_awareness",
          "generation",
          "governance",
          "execution",
          "quality_gate",
          "output",
          "cli",
          "pipeline");

  private static final Set<String> PRIMARY_CATEGORIES = Set.copyOf(CATEGORIES);

  private static final String MASKED_VALUE = "****";

  private static final List<String> SECRET_KEY_MARKERS =
      List.of(
          "api_key",
          "apikey",
          "secret",
          "password",
          "token",
          "access_key",
          "private_key",
          "authorization",
          "custom_headers");

  public enum ValueType {
    BOOLEAN,
    ENUM,
    INTEGER,
    FLOAT,
    STRING,
    LIST,
    OBJECT,
    NULL,
    UNKNOWN
  }

  public static final class PathSegment {

    private final String key;

    private final Integer index;

    private PathSegment(final String key, final Integer index) {
      this.key = key;
      this.index = index;
    }

    public static PathSegment key(final String key) {
      return new PathSegment(Objects.requireNonNull(key, "key"), null);
    }

    public static PathSegment index(final int index) {
      return new PathSegment(null, index);
    }

    public boolean isKey() {
      return key != null;
    }

    public boolean isIndex() {
      return index != null;
    }

    public String key() {
      return key;
    }

    public int index() {
      return index != null ? index : -1;
    }
  }

  public record ConfigItem(String label, Object value, List<PathSegment> path) {}

  private final Path configPath;

  private Object root;

  private Object originalRoot;

  private final LinkedHashSet<String> dirtyKeys = new LinkedHashSet<>();

  private String loadError;

  private boolean newFile;

  private ConfigSchemaIndex schemaIndex;

  private Integer schemaVersionCached;

  private ConfigSchemaIndex schemaIndexOverride;

  private boolean useMetadataRegistry = true;

  private static final CollectionServicePort collectionService = new DefaultCollectionService();

  private ConfigEditor(final Path configPath) {
    this.configPath = configPath;
  }

  public static ConfigEditor load(final Path configPath) {
    final ConfigEditor editor = new ConfigEditor(configPath);
    if (configPath == null) {
      editor.loadError = msg("tui.config_editor.error.path_missing");
      editor.root = new LinkedHashMap<>();
      editor.originalRoot = deepCopy(editor.root);
      return editor;
    }
    if (!Files.exists(configPath)) {
      editor.newFile = true;
      editor.root = new LinkedHashMap<>();
      editor.originalRoot = deepCopy(editor.root);
      return editor;
    }
    try {
      final String jsonText = Files.readString(configPath);
      if (jsonText == null || jsonText.isBlank()) {
        editor.root = new LinkedHashMap<>();
      } else {
        final Object loaded = parseJson(jsonText);
        if (loaded == null) {
          editor.root = new LinkedHashMap<>();
        } else {
          editor.root = normalize(loaded);
        }
      }
      if (!(editor.root instanceof Map)) {
        editor.loadError = msg("tui.config_editor.error.root_not_object");
        editor.root = new LinkedHashMap<>();
      }
    } catch (IOException e) {
      editor.loadError = msg("tui.config_editor.error.load_failed", e.getMessage());
      editor.root = new LinkedHashMap<>();
    }
    editor.originalRoot = deepCopy(editor.root);
    return editor;
  }

  public static ConfigEditor load(
      final Path configPath,
      final ConfigSchemaIndex schemaIndexOverride,
      final boolean useMetadataRegistry) {
    final ConfigEditor editor = load(configPath);
    editor.schemaIndexOverride = schemaIndexOverride;
    editor.useMetadataRegistry = useMetadataRegistry;
    return editor;
  }

  public String getLoadError() {
    return loadError;
  }

  public boolean isNewFile() {
    return newFile;
  }

  public Path getConfigPath() {
    return configPath;
  }

  public boolean isDirty() {
    return !dirtyKeys.isEmpty();
  }

  public List<String> getDirtyKeys() {
    return List.copyOf(dirtyKeys);
  }

  public List<String> getAdvancedTopLevelKeys() {
    final Map<String, Object> rootMap = rootAsMap();
    final Set<String> schemaKeys = getSchemaIndex().getTopLevelKeys();
    final List<String> keys = new ArrayList<>();
    for (final String key : rootMap.keySet()) {
      if (!PRIMARY_CATEGORIES.contains(key) || !schemaKeys.contains(key)) {
        keys.add(key);
      }
    }
    return keys;
  }

  public List<ConfigItem> getItemsForPath(final List<PathSegment> path) {
    final Object value = getValue(path);
    if (!(value instanceof Map<?, ?> mapValue)) {
      return List.of();
    }
    final List<ConfigItem> items = new ArrayList<>();
    for (final Map.Entry<?, ?> entry : mapValue.entrySet()) {
      if (!(entry.getKey() instanceof String key)) {
        continue;
      }
      final List<PathSegment> itemPath = new ArrayList<>(path);
      itemPath.add(PathSegment.key(key));
      items.add(new ConfigItem(key, entry.getValue(), itemPath));
    }
    return items;
  }

  public List<ConfigItem> getAdvancedTopLevelItems() {
    final Map<String, Object> rootMap = rootAsMap();
    final Set<String> schemaKeys = getSchemaIndex().getTopLevelKeys();
    final List<ConfigItem> items = new ArrayList<>();
    for (final Map.Entry<String, Object> entry : rootMap.entrySet()) {
      final String key = entry.getKey();
      if (PRIMARY_CATEGORIES.contains(key) && schemaKeys.contains(key)) {
        continue;
      }
      final List<PathSegment> itemPath = List.of(PathSegment.key(key));
      items.add(new ConfigItem(key, entry.getValue(), itemPath));
    }
    return items;
  }

  public Object getValue(final List<PathSegment> path) {
    return getValue(root, path);
  }

  public boolean pathExists(final List<PathSegment> path) {
    return pathExists(root, path);
  }

  public ValueType classifyValue(final List<PathSegment> path, final Object value) {
    final ConfigSchemaIndex.SchemaRule schemaRule = getSchemaRule(path);
    final ValueType schemaType = resolveSchemaType(schemaRule, path);
    if (schemaType != null) {
      return schemaType;
    }
    final MetadataRegistry.ConfigKeyMetadata metadata = resolveMetadata(path);
    final ValueType metadataType = resolveMetadataType(metadata, path);
    if (metadataType != null) {
      return metadataType;
    }
    final ValueType enumType = resolveEnumType(path, value);
    if (enumType != null) {
      return enumType;
    }
    if (shouldDefaultToString(schemaRule, metadata, value)) {
      return ValueType.STRING;
    }
    final ValueType valueType = resolveValueType(value);
    if (valueType != null) {
      return valueType;
    }
    return schemaRule == null ? ValueType.STRING : ValueType.UNKNOWN;
  }

  public List<String> getEnumOptions(final List<PathSegment> path, final Object value) {
    final ConfigSchemaIndex.SchemaRule schemaRule = getSchemaRule(path);
    if (schemaRule != null && schemaRule.hasEnum()) {
      final List<String> values = new ArrayList<>();
      for (final Object entry : schemaRule.enumValues()) {
        values.add(String.valueOf(entry));
      }
      if (value instanceof String str && !containsIgnoreCase(values, str)) {
        values.add(str);
      }
      return values;
    }
    final String key = pathKey(path);
    if (key.isEmpty()) {
      return List.of();
    }
    MetadataRegistry.ConfigKeyMetadata metadata = null;
    if (useMetadataRegistry) {
      metadata = MetadataRegistry.getDefault().find(key).orElse(null);
    }
    if (metadata == null || metadata.enumOptions().isEmpty()) {
      return List.of();
    }
    if (metadata.isList() && !containsIndex(path)) {
      return List.of();
    }
    final List<String> merged = new ArrayList<>(metadata.enumOptions());
    if (value instanceof String str && !containsIgnoreCase(metadata.enumOptions(), str)) {
      merged.add(str);
    }
    return merged;
  }

  private ValueType resolveSchemaType(
      final ConfigSchemaIndex.SchemaRule schemaRule, final List<PathSegment> path) {
    if (schemaRule == null) {
      return null;
    }
    return toSchemaValueType(schemaRule, path);
  }

  private MetadataRegistry.ConfigKeyMetadata resolveMetadata(final List<PathSegment> path) {
    if (!useMetadataRegistry) {
      return null;
    }
    return MetadataRegistry.getDefault().find(pathKey(path)).orElse(null);
  }

  private ValueType resolveMetadataType(
      final MetadataRegistry.ConfigKeyMetadata metadata, final List<PathSegment> path) {
    if (metadata == null) {
      return null;
    }
    final boolean hasIndex = containsIndex(path);
    if (metadata.isList()) {
      if (!hasIndex) {
        return ValueType.LIST;
      }
      if (metadata.isEnum()) {
        return ValueType.ENUM;
      }
      final MetadataRegistry.ValueType elementType =
          metadata.elementType() != null
              ? metadata.elementType()
              : MetadataRegistry.ValueType.STRING;
      return toValueType(elementType);
    }
    if (metadata.isEnum()) {
      return ValueType.ENUM;
    }
    return toValueType(metadata.type());
  }

  private ValueType resolveEnumType(final List<PathSegment> path, final Object value) {
    final List<String> enumOptions = getEnumOptions(path, value);
    return enumOptions.isEmpty() ? null : ValueType.ENUM;
  }

  private boolean shouldDefaultToString(
      final ConfigSchemaIndex.SchemaRule schemaRule,
      final MetadataRegistry.ConfigKeyMetadata metadata,
      final Object value) {
    return schemaRule == null
        && metadata == null
        && !(value instanceof Map)
        && !(value instanceof List);
  }

  private ValueType resolveValueType(final Object value) {
    if (value == null) {
      return ValueType.NULL;
    }
    if (value instanceof Boolean) {
      return ValueType.BOOLEAN;
    }
    if (value instanceof Number numberValue) {
      if (numberValue instanceof Double
          || numberValue instanceof Float
          || numberValue instanceof java.math.BigDecimal) {
        return ValueType.FLOAT;
      }
      return ValueType.INTEGER;
    }
    if (value instanceof Map) {
      return ValueType.OBJECT;
    }
    if (value instanceof List) {
      return ValueType.LIST;
    }
    if (value instanceof String) {
      return ValueType.STRING;
    }
    return null;
  }

  private static boolean containsIndex(final List<PathSegment> path) {
    if (path == null || path.isEmpty()) {
      return false;
    }
    for (final PathSegment segment : path) {
      if (segment.isIndex()) {
        return true;
      }
    }
    return false;
  }

  private static ValueType toValueType(final MetadataRegistry.ValueType type) {
    if (type == null) {
      return null;
    }
    return switch (type) {
      case BOOLEAN -> ValueType.BOOLEAN;
      case INTEGER -> ValueType.INTEGER;
      case FLOAT -> ValueType.FLOAT;
      case STRING -> ValueType.STRING;
      case LIST -> ValueType.LIST;
      case OBJECT -> ValueType.OBJECT;
    };
  }

  public ValidationResult validateValue(final List<PathSegment> path, final Object value) {
    final ConfigSchemaIndex.SchemaRule schemaRule = getSchemaRule(path);
    if (schemaRule == null) {
      return ValidationResult.success();
    }
    return validateAgainstRule(schemaRule, value);
  }

  public ValidationResult validateListItemValue(
      final List<PathSegment> listPath, final Object value) {
    final ConfigSchemaIndex.SchemaRule schemaRule = getListItemRule(listPath);
    if (schemaRule == null) {
      return ValidationResult.success();
    }
    return validateAgainstRule(schemaRule, value);
  }

  public ValueType resolveSchemaValueType(final List<PathSegment> path) {
    final ConfigSchemaIndex.SchemaRule schemaRule = getSchemaRule(path);
    if (schemaRule == null) {
      return null;
    }
    return toSchemaValueType(schemaRule, path);
  }

  public ValueType resolveSchemaListItemType(final List<PathSegment> listPath) {
    final ConfigSchemaIndex.SchemaRule schemaRule = getListItemRule(listPath);
    if (schemaRule == null) {
      return null;
    }
    return toSchemaValueType(schemaRule, listPath);
  }

  private ValueType toSchemaValueType(
      final ConfigSchemaIndex.SchemaRule rule, final List<PathSegment> path) {
    if (rule == null) {
      return null;
    }
    if (rule.hasEnum()) {
      return ValueType.ENUM;
    }
    final Set<String> types = rule.types();
    final boolean hasIndex = containsIndex(path);
    if (types.contains("array") && !hasIndex) {
      return ValueType.LIST;
    }
    if (types.contains("object")) {
      return ValueType.OBJECT;
    }
    final boolean integerOnly = types.contains("integer") && !types.contains("number");
    if (integerOnly) {
      return ValueType.INTEGER;
    }
    if (types.contains("number")) {
      return ValueType.FLOAT;
    }
    if (types.contains("integer")) {
      return ValueType.INTEGER;
    }
    if (types.contains("boolean")) {
      return ValueType.BOOLEAN;
    }
    if (types.contains("string")) {
      return ValueType.STRING;
    }
    return null;
  }

  private ConfigSchemaIndex.SchemaRule getSchemaRule(final List<PathSegment> path) {
    final ConfigSchemaIndex index = getSchemaIndex();
    return index.findRule(path);
  }

  private ConfigSchemaIndex.SchemaRule getListItemRule(final List<PathSegment> listPath) {
    final ConfigSchemaIndex index = getSchemaIndex();
    return index.findListItemRule(listPath);
  }

  private ConfigSchemaIndex getSchemaIndex() {
    if (schemaIndexOverride != null) {
      return schemaIndexOverride;
    }
    final Map<String, Object> rootMap = rootAsMap();
    int version;
    try {
      version = ConfigSchemaIndex.resolveSchemaVersion(rootMap);
    } catch (IllegalStateException e) {
      version = ConfigSchemaIndex.latestSchemaVersion();
    }
    if (schemaIndex == null || schemaVersionCached == null || schemaVersionCached != version) {
      try {
        schemaIndex = ConfigSchemaIndex.forVersion(version);
      } catch (IllegalStateException e) {
        schemaIndex = ConfigSchemaIndex.forVersion(ConfigSchemaIndex.latestSchemaVersion());
      }
      schemaVersionCached = version;
    }
    return schemaIndex;
  }

  private ValidationResult validateAgainstRule(
      final ConfigSchemaIndex.SchemaRule rule, final Object value) {
    if (value == null) {
      if (rule.allowsNull()) {
        return ValidationResult.success();
      }
      return ValidationResult.failure(msg("tui.config_editor.validation.value_required"));
    }
    if (rule.hasEnum()) {
      for (final Object option : rule.enumValues()) {
        if (Objects.equals(option, value) || String.valueOf(option).equals(String.valueOf(value))) {
          return ValidationResult.success();
        }
      }
      return ValidationResult.failure(
          msg("tui.config_editor.validation.enum_options", rule.enumValues()));
    }
    final Set<String> types = rule.types();
    if (!types.isEmpty()) {
      if (types.contains("string") && value instanceof String) {
        return ValidationResult.success();
      }
      if (types.contains("boolean") && value instanceof Boolean) {
        return ValidationResult.success();
      }
      if (types.contains("integer") && value instanceof Number) {
        if (value instanceof Float || value instanceof Double) {
          final double dbl = ((Number) value).doubleValue();
          if (Math.floor(dbl) != dbl) {
            return ValidationResult.failure(msg("tui.config_editor.validation.integer_required"));
          }
        }
        return validateNumberBounds(rule, ((Number) value).doubleValue());
      }
      if (types.contains("number") && value instanceof Number) {
        return validateNumberBounds(rule, ((Number) value).doubleValue());
      }
      if (types.contains("array") && value instanceof List) {
        return ValidationResult.success();
      }
      if (types.contains("object") && value instanceof Map) {
        return ValidationResult.success();
      }
      return ValidationResult.failure(msg("tui.config_editor.validation.type_mismatch"));
    }
    return ValidationResult.success();
  }

  private ValidationResult validateNumberBounds(
      final ConfigSchemaIndex.SchemaRule rule, final double number) {
    if (rule.minimum() != null && number < rule.minimum()) {
      return ValidationResult.failure(
          msg("tui.config_editor.validation.min_bound", rule.minimum()));
    }
    if (rule.maximum() != null && number > rule.maximum()) {
      return ValidationResult.failure(
          msg("tui.config_editor.validation.max_bound", rule.maximum()));
    }
    return ValidationResult.success();
  }

  public record ValidationResult(boolean ok, String message) {

    public static ValidationResult success() {
      return new ValidationResult(true, "");
    }

    public static ValidationResult failure(final String message) {
      return new ValidationResult(
          false, message == null ? msg("tui.config_editor.validation.invalid_value") : message);
    }
  }

  public boolean setValue(final List<PathSegment> path, final Object value) {
    if (path == null || path.isEmpty()) {
      return false;
    }
    final Object normalized = normalize(value);
    final List<PathSegment> parentPath = path.subList(0, path.size() - 1);
    final PathSegment last = path.get(path.size() - 1);
    final Object newRoot =
        rebuild(
            root,
            parentPath,
            0,
            false,
            parent -> {
              if (last.isKey() && parent instanceof Map<?, ?>) {
                final Map<String, Object> map = collectionService.toMap(parent);
                map.put(last.key(), normalized);
                return map;
              }
              if (last.isIndex() && parent instanceof List<?> parentList) {
                final int index = last.index();
                if (index < 0 || index >= parentList.size()) {
                  return REBUILD_FAILED;
                }
                final List<Object> list = collectionService.toList(parent);
                list.set(index, normalized);
                return list;
              }
              return REBUILD_FAILED;
            });
    if (newRoot == REBUILD_FAILED) {
      return false;
    }
    root = newRoot;
    return true;
  }

  public boolean setValueWithCreate(final List<PathSegment> path, final Object value) {
    if (path == null || path.isEmpty()) {
      return false;
    }
    final Object normalized = normalize(value);
    final List<PathSegment> parentPath = path.subList(0, path.size() - 1);
    final PathSegment last = path.get(path.size() - 1);
    final Object newRoot =
        rebuild(
            root,
            parentPath,
            0,
            true,
            parent -> {
              if (last.isKey()) {
                final Map<String, Object> map;
                if (parent instanceof Map<?, ?>) {
                  map = collectionService.toMap(parent);
                } else if (parent == null) {
                  map = new LinkedHashMap<>();
                } else {
                  return REBUILD_FAILED;
                }
                map.put(last.key(), normalized);
                return map;
              }
              if (last.isIndex()) {
                final int index = last.index();
                if (index < 0) {
                  return REBUILD_FAILED;
                }
                final List<Object> list;
                if (parent instanceof List<?>) {
                  list = collectionService.toList(parent);
                } else if (parent == null) {
                  list = new ArrayList<>();
                } else {
                  return REBUILD_FAILED;
                }
                while (list.size() <= index) {
                  list.add(null);
                }
                list.set(index, normalized);
                return list;
              }
              return REBUILD_FAILED;
            });
    if (newRoot == REBUILD_FAILED) {
      return false;
    }
    root = newRoot;
    return true;
  }

  public boolean appendListValueWithCreate(final List<PathSegment> listPath, final Object value) {
    if (listPath == null || listPath.isEmpty()) {
      return false;
    }
    final Object normalized = normalize(value);
    final Object newRoot =
        rebuild(
            root,
            listPath,
            0,
            true,
            current -> {
              final List<Object> list;
              if (current instanceof List<?>) {
                list = collectionService.toList(current);
              } else if (current == null) {
                list = new ArrayList<>();
              } else {
                return REBUILD_FAILED;
              }
              list.add(normalized);
              return list;
            });
    if (newRoot == REBUILD_FAILED) {
      return false;
    }
    root = newRoot;
    return true;
  }

  public boolean removeListIndex(final List<PathSegment> listPath, final int index) {
    if (listPath == null || listPath.isEmpty()) {
      return false;
    }
    final Object newRoot =
        rebuild(
            root,
            listPath,
            0,
            false,
            current -> {
              if (!(current instanceof List<?> list)) {
                return REBUILD_FAILED;
              }
              if (index < 0 || index >= list.size()) {
                return REBUILD_FAILED;
              }
              final List<Object> newList = collectionService.toList(current);
              newList.remove(index);
              return newList;
            });
    if (newRoot == REBUILD_FAILED) {
      return false;
    }
    root = newRoot;
    return true;
  }

  public boolean addListValue(final List<PathSegment> listPath, final Object value) {
    if (listPath == null || listPath.isEmpty()) {
      return false;
    }
    final Object normalized = normalize(value);
    final Object newRoot =
        rebuild(
            root,
            listPath,
            0,
            false,
            current -> {
              if (!(current instanceof List<?>)) {
                return REBUILD_FAILED;
              }
              final List<Object> newList = collectionService.toList(current);
              newList.add(normalized);
              return newList;
            });
    if (newRoot == REBUILD_FAILED) {
      return false;
    }
    root = newRoot;
    return true;
  }

  public void updateDirtyForPath(final List<PathSegment> path) {
    if (path == null || path.isEmpty()) {
      return;
    }
    final String key = pathKey(path);
    if (key.isEmpty()) {
      return;
    }
    final Object currentValue = getValue(root, path);
    final Object originalValue = getValue(originalRoot, path);
    final boolean currentExists = pathExists(root, path);
    final boolean originalExists = pathExists(originalRoot, path);
    final boolean equals =
        Objects.equals(currentValue, originalValue) && currentExists == originalExists;
    if (equals) {
      dirtyKeys.remove(key);
    } else {
      dirtyKeys.add(key);
    }
  }

  public String summarizeValue(final Object value, final int maxLength) {
    return summarizeValue(null, value, maxLength);
  }

  public String summarizeValue(
      final List<PathSegment> path, final Object value, final int maxLength) {
    if (isSecretPath(path)) {
      return truncateValue(MASKED_VALUE, maxLength);
    }
    final String text;
    if (value == null) {
      text = msg("tui.config_editor.value.null");
    } else if (value instanceof String s) {
      final String normalized = s.replace("\n", " ").replace("\r", " ");
      text = "\"" + normalized + "\"";
    } else if (value instanceof Map<?, ?> map) {
      text = msg("tui.config_editor.value.map_summary", map.size());
    } else if (value instanceof List<?> list) {
      text = msg("tui.config_editor.value.list_summary", list.size());
    } else {
      text = String.valueOf(value);
    }
    if (maxLength > 0 && text.length() > maxLength) {
      return truncateValue(text, maxLength);
    }
    return text;
  }

  public String formatPathForDisplay(final List<PathSegment> path) {
    if (path == null || path.isEmpty()) {
      return msg("tui.config_editor.path.root");
    }
    final StringBuilder sb = new StringBuilder();
    for (final PathSegment segment : path) {
      if (segment.isKey()) {
        if (sb.length() > 0) {
          sb.append(" > ");
        }
        sb.append(segment.key());
      } else if (segment.isIndex()) {
        sb.append("[").append(segment.index()).append("]");
      }
    }
    return sb.toString();
  }

  public String formatScalar(final Object value) {
    if (value == null) {
      return msg("tui.config_editor.value.null");
    }
    if (value instanceof String s) {
      return s;
    }
    return String.valueOf(value);
  }

  public String formatScalarForDisplay(final List<PathSegment> path, final Object value) {
    if (isSecretPath(path)) {
      return MASKED_VALUE;
    }
    return formatScalar(value);
  }

  public String formatScalarForEdit(final List<PathSegment> path, final Object value) {
    if (isSecretPath(path)) {
      return "";
    }
    return formatScalar(value);
  }

  public boolean isSecretPath(final List<PathSegment> path) {
    if (path == null || path.isEmpty()) {
      return false;
    }
    for (final PathSegment segment : path) {
      if (segment.isKey() && isSecretKey(segment.key())) {
        return true;
      }
    }
    return false;
  }

  private static boolean isSecretKey(final String key) {
    if (key == null || key.isBlank()) {
      return false;
    }
    final String lowered = key.toLowerCase(java.util.Locale.ROOT);
    for (final String marker : SECRET_KEY_MARKERS) {
      if (lowered.contains(marker)) {
        return true;
      }
    }
    return false;
  }

  private static String truncateValue(final String text, final int maxLength) {
    if (maxLength > 0 && text.length() > maxLength) {
      return text.substring(0, Math.max(0, maxLength - 3)) + "...";
    }
    return text;
  }

  public boolean save() throws IOException {
    if (!(root instanceof Map)) {
      return false;
    }
    final String output = dumpJson();
    final Path parent = configPath.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    Files.writeString(configPath, output);
    originalRoot = deepCopy(root);
    dirtyKeys.clear();
    return true;
  }

  public String toJson() {
    if (!(root instanceof Map)) {
      return "";
    }
    return dumpJson();
  }

  private String dumpJson() {
    final ObjectMapper mapper = JsonMapperFactory.createPrettyPrinter();
    try {
      return mapper.writeValueAsString(root);
    } catch (JacksonException e) {
      return "{}";
    }
  }

  public void discard() {
    root = deepCopy(originalRoot);
    dirtyKeys.clear();
  }

  private static boolean containsIgnoreCase(final List<String> options, final String value) {
    for (final String option : options) {
      if (option.equalsIgnoreCase(value)) {
        return true;
      }
    }
    return false;
  }

  private static String pathKey(final List<PathSegment> path) {
    if (path == null || path.isEmpty()) {
      return "";
    }
    final StringBuilder sb = new StringBuilder();
    for (final PathSegment segment : path) {
      if (segment.isKey()) {
        if (sb.length() > 0) {
          sb.append('.');
        }
        sb.append(segment.key());
      }
    }
    return sb.toString();
  }

  private Map<String, Object> rootAsMap() {
    if (!(root instanceof Map<?, ?>)) {
      return new LinkedHashMap<>();
    }
    return collectionService.toMap(root);
  }

  private static Object getValue(final Object base, final List<PathSegment> path) {
    Object current = base;
    for (final PathSegment segment : path) {
      if (segment.isKey()) {
        if (!(current instanceof Map<?, ?> map)) {
          return null;
        }
        current = map.get(segment.key());
      } else if (segment.isIndex()) {
        if (!(current instanceof List<?> list)) {
          return null;
        }
        final int index = segment.index();
        if (index < 0 || index >= list.size()) {
          return null;
        }
        current = list.get(index);
      }
    }
    return current;
  }

  private static boolean pathExists(final Object base, final List<PathSegment> path) {
    Object current = base;
    for (final PathSegment segment : path) {
      if (segment.isKey()) {
        if (!(current instanceof Map<?, ?> map)) {
          return false;
        }
        if (!map.containsKey(segment.key())) {
          return false;
        }
        current = map.get(segment.key());
      } else if (segment.isIndex()) {
        if (!(current instanceof List<?> list)) {
          return false;
        }
        final int index = segment.index();
        if (index < 0 || index >= list.size()) {
          return false;
        }
        current = list.get(index);
      }
    }
    return true;
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Immutable tree rebuild
  //
  // Instead of mutating Map<?, ?> / List<?> in-place (which requires raw-type
  // casts and @SuppressWarnings), every mutation creates type-safe defensive
  // copies along the path from root to the target node, applies the change on
  // the copy, and returns a new root. Only the affected path is copied;
  // unaffected branches share references with the original tree.
  // ─────────────────────────────────────────────────────────────────────────────

  /** Sentinel indicating a rebuild operation failed. */
  private static final Object REBUILD_FAILED = new Object();

  /**
   * Recursively rebuilds the tree, applying {@code mutation} at the node identified by {@code
   * path[depth..]}. At each non-terminal level a type-safe defensive copy of the Map or List is
   * created so that the original tree is never mutated.
   *
   * @param current the current subtree node
   * @param path path segments to traverse
   * @param depth current depth (0-based)
   * @param createIntermediate when true, create missing intermediate containers
   * @param mutation function applied at the terminal node; must return the replacement value or
   *     {@link #REBUILD_FAILED}
   * @return the new subtree, or {@link #REBUILD_FAILED} on failure
   */
  private static Object rebuild(
      final Object current,
      final List<PathSegment> path,
      final int depth,
      final boolean createIntermediate,
      final java.util.function.Function<Object, Object> mutation) {

    // Terminal: apply the mutation
    if (depth == path.size()) {
      return mutation.apply(current);
    }

    final PathSegment segment = path.get(depth);

    if (segment.isKey()) {
      final Map<String, Object> copy;
      if (isContainerFor(segment, current)) {
        copy = collectionService.toMap(current);
      } else if (current == null && createIntermediate) {
        copy = collectionService.toMap(createContainerFor(segment));
      } else {
        return REBUILD_FAILED;
      }
      final Object child = copy.get(segment.key());
      final Object newChild = rebuild(child, path, depth + 1, createIntermediate, mutation);
      if (newChild == REBUILD_FAILED) {
        return REBUILD_FAILED;
      }
      copy.put(segment.key(), newChild);
      return copy;
    }

    if (segment.isIndex()) {
      final List<Object> copy;
      if (isContainerFor(segment, current)) {
        copy = collectionService.toList(current);
      } else if (current == null && createIntermediate) {
        copy = collectionService.toList(createContainerFor(segment));
      } else {
        return REBUILD_FAILED;
      }
      final int index = segment.index();
      if (index < 0) {
        return REBUILD_FAILED;
      }
      if (createIntermediate) {
        while (copy.size() <= index) {
          copy.add(null);
        }
      } else if (index >= copy.size()) {
        return REBUILD_FAILED;
      }
      final Object child = copy.get(index);
      final Object newChild = rebuild(child, path, depth + 1, createIntermediate, mutation);
      if (newChild == REBUILD_FAILED) {
        return REBUILD_FAILED;
      }
      copy.set(index, newChild);
      return copy;
    }

    return REBUILD_FAILED;
  }

  private static Object createContainerFor(final PathSegment next) {
    if (next != null && next.isIndex()) {
      return new ArrayList<>();
    }
    return new LinkedHashMap<>();
  }

  private static boolean isContainerFor(final PathSegment next, final Object candidate) {
    if (next == null || candidate == null) {
      return false;
    }
    if (next.isIndex()) {
      return candidate instanceof List<?>;
    }
    return candidate instanceof Map<?, ?>;
  }

  private static Object deepCopy(final Object value) {
    if (value instanceof Map<?, ?> map) {
      final Map<String, Object> copy = new LinkedHashMap<>();
      for (final Map.Entry<?, ?> entry : map.entrySet()) {
        if (!(entry.getKey() instanceof String key)) {
          continue;
        }
        copy.put(key, deepCopy(entry.getValue()));
      }
      return copy;
    }
    if (value instanceof List<?> list) {
      final List<Object> copy = new ArrayList<>();
      for (final Object item : list) {
        copy.add(deepCopy(item));
      }
      return copy;
    }
    return value;
  }

  private static Object normalize(final Object value) {
    if (value instanceof Map<?, ?> map) {
      final Map<String, Object> normalized = new LinkedHashMap<>();
      for (final Map.Entry<?, ?> entry : map.entrySet()) {
        normalized.put(String.valueOf(entry.getKey()), normalize(entry.getValue()));
      }
      return normalized;
    }
    if (value instanceof List<?> list) {
      final List<Object> normalized = new ArrayList<>();
      for (final Object item : list) {
        normalized.add(normalize(item));
      }
      return normalized;
    }
    return value;
  }

  private static Object parseJson(final String jsonText) throws IOException {
    final ObjectMapper mapper = JsonMapperFactory.create();
    try {
      return mapper.readValue(jsonText, Object.class);
    } catch (JacksonException e) {
      final String message =
          e.getOriginalMessage() == null ? e.getMessage() : e.getOriginalMessage();
      throw new IOException(message, e);
    }
  }

  private static String msg(final String key, final Object... args) {
    return MessageSource.getMessage(key, args);
  }
}
