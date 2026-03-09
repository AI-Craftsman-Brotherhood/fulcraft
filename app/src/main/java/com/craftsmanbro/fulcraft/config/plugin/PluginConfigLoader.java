package com.craftsmanbro.fulcraft.config.plugin;

import com.craftsmanbro.fulcraft.config.InvalidConfigurationException;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.config.schema.JsonSchemaValidator;
import com.craftsmanbro.fulcraft.infrastructure.json.impl.JsonMapperFactory;
import com.craftsmanbro.fulcraft.kernel.plugin.api.PluginConfig;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.microprofile.config.spi.ConfigSource;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Loads plugin-scoped configuration from .ful/plugins/&lt;pluginId&gt;/config/config.json.
 *
 * <p>Legacy layout (.ful/plugins/&lt;pluginId&gt;/config.json) is supported as a fallback.
 */
public final class PluginConfigLoader {

  private static final int PLUGIN_CONFIG_ORDINAL = 450;

  private final Path projectRoot;

  public PluginConfigLoader(final Path projectRoot) {
    this.projectRoot = Objects.requireNonNull(projectRoot, "projectRoot");
  }

  public PluginConfig load(final String pluginId) {
    if (pluginId == null || pluginId.isBlank()) {
      throw new IllegalArgumentException(message("config.plugin.loader.error.plugin_id_blank"));
    }
    final PluginConfigPaths.ResolvedPaths resolvedPaths =
        Objects.requireNonNull(PluginConfigPaths.resolve(projectRoot, pluginId));
    final Path configPath = resolvedPaths.configPath();
    final Path schemaPath = resolvedPaths.schemaPath();
    final Map<?, ?> rawPluginConfig = readJson(configPath);
    validateWithSchema(rawPluginConfig, schemaPath, configPath);
    final Map<String, String> flattenedProperties = new LinkedHashMap<>();
    flattenMap("", rawPluginConfig, flattenedProperties);
    final SmallRyeConfig pluginConfig =
        new SmallRyeConfigBuilder()
            .addDefaultInterceptors()
            .withSources(
                new MapConfigSource(
                    "plugin:" + pluginId, flattenedProperties, PLUGIN_CONFIG_ORDINAL))
            .build();
    return new PluginConfig(
        pluginId, configPath, schemaPath, pluginConfig, resolvedPaths.configExists());
  }

  private Map<?, ?> readJson(final Path configPath) {
    if (configPath == null || !Files.isRegularFile(configPath)) {
      return Map.of();
    }
    try {
      final String configJsonText = Files.readString(configPath);
      if (configJsonText == null || configJsonText.isBlank()) {
        return Map.of();
      }
      final ObjectMapper objectMapper = JsonMapperFactory.create();
      final JsonNode rootNode = objectMapper.readTree(configJsonText);
      if (rootNode == null || rootNode.isMissingNode() || rootNode.isNull()) {
        return Map.of();
      }
      if (!rootNode.isObject()) {
        throw new InvalidConfigurationException(
            message("config.plugin.loader.error.root_not_object", configPath.toAbsolutePath()));
      }
      return objectMapper.convertValue(rootNode, Map.class);
    } catch (JacksonException e) {
      throw new InvalidConfigurationException(
          message(
              "config.plugin.loader.error.parse_failed",
              configPath.toAbsolutePath(),
              e.getOriginalMessage()),
          e);
    } catch (IOException e) {
      throw new InvalidConfigurationException(
          message("config.plugin.loader.error.read_failed", configPath.toAbsolutePath()), e);
    }
  }

  private void validateWithSchema(
      final Map<?, ?> rawConfig, final Path schemaPath, final Path configPath) {
    if (schemaPath == null || !Files.isRegularFile(schemaPath)) {
      return;
    }
    final ObjectMapper objectMapper = JsonMapperFactory.create();
    final JsonNode configNode = objectMapper.valueToTree(rawConfig);
    final JsonSchemaValidator schemaValidator = JsonSchemaValidator.loadFromPath(schemaPath);
    final List<JsonSchemaValidator.SchemaError> validationErrors =
        schemaValidator.validate(configNode);
    if (!validationErrors.isEmpty()) {
      // Keep one schema error per line for readable CLI output.
      final String details =
          validationErrors.stream()
              .map(schemaError -> "- " + schemaError.path() + ": " + schemaError.message())
              .collect(Collectors.joining("\n"));
      throw new InvalidConfigurationException(
          message("config.plugin.loader.error.validation_failed", configPath.toAbsolutePath())
              + "\n"
              + details);
    }
  }

  private void flattenMap(
      final String keyPrefix,
      final Object currentValue,
      final Map<String, String> flattenedValues) {
    if (currentValue instanceof Map<?, ?> mapValue) {
      for (final Map.Entry<?, ?> mapEntry : mapValue.entrySet()) {
        if (mapEntry.getKey() == null) {
          continue;
        }
        final String entryKey = mapEntry.getKey().toString();
        final String childPrefix = keyPrefix.isEmpty() ? entryKey : keyPrefix + "." + entryKey;
        flattenMap(childPrefix, mapEntry.getValue(), flattenedValues);
      }
      return;
    }
    if (currentValue instanceof List<?> listValue) {
      // Preserve list positions using indexed keys (for example: items[0]).
      for (int i = 0; i < listValue.size(); i++) {
        final String childPrefix = keyPrefix + "[" + i + "]";
        flattenMap(childPrefix, listValue.get(i), flattenedValues);
      }
      return;
    }
    if (keyPrefix.isEmpty() || currentValue == null) {
      return;
    }
    flattenedValues.put(keyPrefix, currentValue.toString());
  }

  private static final class MapConfigSource implements ConfigSource {

    private final String name;

    private final Map<String, String> values;

    private final int ordinal;

    private MapConfigSource(
        final String name, final Map<String, String> values, final int ordinal) {
      this.name = name;
      this.values = Map.copyOf(values);
      this.ordinal = ordinal;
    }

    @Override
    public Map<String, String> getProperties() {
      return values;
    }

    @Override
    public Set<String> getPropertyNames() {
      return values.keySet();
    }

    @Override
    public String getValue(final String propertyName) {
      return values.get(propertyName);
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public int getOrdinal() {
      return ordinal;
    }
  }

  private static String message(final String key, final Object... args) {
    return MessageSource.getMessage(key, args);
  }
}
