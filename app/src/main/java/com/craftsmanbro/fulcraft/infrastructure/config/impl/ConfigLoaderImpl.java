package com.craftsmanbro.fulcraft.infrastructure.config.impl;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.config.ConfigLoaderPort;
import com.craftsmanbro.fulcraft.config.ConfigOverride;
import com.craftsmanbro.fulcraft.config.InvalidConfigurationException;
import com.craftsmanbro.fulcraft.infrastructure.config.schema.JsonSchemaValidator;
import com.craftsmanbro.fulcraft.infrastructure.config.validator.ConfigValidator;
import com.craftsmanbro.fulcraft.infrastructure.json.impl.JsonMapperFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.commons.text.StringSubstitutor;
import org.apache.commons.text.lookup.StringLookup;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;

/** Loads and parses the configuration file. */
public class ConfigLoaderImpl implements ConfigLoaderPort {

  /** Default configuration file path. */
  public static final Path DEFAULT_CONFIG_FILE = Path.of("config.json");

  /** Fallback configuration file path under .ful directory. */
  public static final Path FALLBACK_CONFIG_FILE = Path.of(".ful", "config.json");

  private final ConfigValidator validator;

  /** Creates a new ConfigLoaderPort with a default validator. */
  public ConfigLoaderImpl() {
    this(new ConfigValidator());
  }

  /**
   * Creates a new ConfigLoaderPort.
   *
   * @param validator the validator to use
   */
  public ConfigLoaderImpl(final ConfigValidator validator) {
    this.validator =
        Objects.requireNonNull(
            validator,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "infra.common.error.argument_null", "validator"));
  }

  /**
   * Resolves the configuration file path with fallback logic.
   *
   * <p>Resolution order:
   *
   * <ol>
   *   <li>Explicitly provided path (if not null and exists)
   *   <li>Default path: {@code config.json}
   *   <li>Fallback path: {@code .ful/config.json}
   * </ol>
   *
   * @param explicitPath explicitly specified path, may be null
   * @return the resolved configuration file path
   */
  public Path resolveConfigPath(final Path explicitPath) {
    final Path primary = explicitPath != null ? explicitPath : DEFAULT_CONFIG_FILE;
    if (Files.exists(primary)) {
      return primary;
    }
    if (Files.exists(FALLBACK_CONFIG_FILE)) {
      return FALLBACK_CONFIG_FILE;
    }
    return primary;
  }

  /**
   * Loads the configuration from the specified path and applies overrides.
   *
   * @param configFile the path to the configuration file
   * @param overrides configuration overrides to apply after loading
   * @return the loaded and modified Config object
   * @throws InvalidConfigurationException if the file cannot be read or parsed, or is invalid
   */
  public Config load(final Path configFile, final ConfigOverride... overrides) {
    final Config config = load(configFile);
    applyOverrides(config, overrides);
    try {
      validator.validateParsedConfig(config);
    } catch (InvalidConfigurationException e) {
      throw new InvalidConfigurationException(
          """
              Configuration validation failed after applying overrides for: %s
              %s"""
              .formatted(configFile.toAbsolutePath(), e.getMessage()),
          e);
    }
    return config;
  }

  public void applyOverrides(final Config config, final ConfigOverride... overrides) {
    if (config == null || overrides == null) {
      return;
    }
    for (final ConfigOverride override : overrides) {
      if (override != null) {
        override.apply(config);
      }
    }
  }

  /**
   * Loads the configuration from the specified path.
   *
   * @param configFile the path to the configuration file
   * @return the loaded Config object
   * @throws InvalidConfigurationException if the file cannot be read or parsed, or is invalid
   */
  public Config load(final Path configFile) {
    Objects.requireNonNull(
        configFile,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "configFile"));
    final Path resolvedConfigFile = resolveExistingConfigPath(configFile);
    if (resolvedConfigFile == null) {
      return Config.createDefault();
    }
    final String configText;
    try {
      configText = readFile(resolvedConfigFile);
    } catch (IOException e) {
      throw new InvalidConfigurationException(
          "Failed to read configuration file: " + resolvedConfigFile.toAbsolutePath(), e);
    }
    final JsonNode dataNode = parseJsonNode(configText, resolvedConfigFile);
    final int schemaVersion = resolveSchemaVersion(dataNode);
    validateWithSchema(dataNode, schemaVersion, resolvedConfigFile);
    final Config config = parseConfig(configText, resolvedConfigFile);
    if (config == null) {
      throw new InvalidConfigurationException(
          "Configuration is empty or invalid: " + resolvedConfigFile.toAbsolutePath());
    }
    try {
      validator.validateParsedConfig(config);
    } catch (InvalidConfigurationException e) {
      throw new InvalidConfigurationException(
          """
              Configuration validation failed for: %s
              %s"""
              .formatted(resolvedConfigFile.toAbsolutePath(), e.getMessage()),
          e);
    }
    return config;
  }

  private JsonNode parseJsonNode(final String jsonText, final Path configFile) {
    try {
      final ObjectMapper mapper = JsonMapperFactory.create();
      final JsonNode node = mapper.readTree(jsonText);
      if (node == null || node.isMissingNode() || node.isNull()) {
        throw new InvalidConfigurationException(
            "Configuration file is empty or invalid JSON: " + configFile.toAbsolutePath());
      }
      if (!node.isObject()) {
        throw new InvalidConfigurationException(
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "infra.common.error.message", "Configuration root must be an object."));
      }
      return node;
    } catch (JacksonException e) {
      throw new InvalidConfigurationException(
          "Failed to parse configuration file for schema validation: "
              + configFile.toAbsolutePath()
              + " ("
              + e.getOriginalMessage()
              + ")",
          e);
    }
  }

  private int resolveSchemaVersion(final JsonNode dataNode) {
    try {
      return JsonSchemaValidator.resolveSchemaVersion(dataNode);
    } catch (IllegalStateException e) {
      throw new InvalidConfigurationException(e.getMessage(), e);
    }
  }

  private void validateWithSchema(
      final JsonNode dataNode, final int schemaVersion, final Path configFile) {
    final JsonSchemaValidator schemaValidator;
    try {
      schemaValidator = JsonSchemaValidator.loadFromResource(schemaVersion);
    } catch (IllegalStateException e) {
      throw new InvalidConfigurationException(e.getMessage(), e);
    }
    final List<JsonSchemaValidator.SchemaError> errors = schemaValidator.validate(dataNode);
    if (!errors.isEmpty()) {
      final String details =
          errors.stream()
              .map(
                  error ->
                      (error.path() == null || error.path().isBlank())
                          ? error.message()
                          : error.path() + ": " + error.message())
              .sorted()
              .collect(Collectors.joining("\n- ", "- ", ""));
      throw new InvalidConfigurationException(
          """
              Config schema validation failed for: %s
              Schema: schema/config-schema-v%s.json
              Errors:
              %s"""
              .formatted(configFile.toAbsolutePath(), schemaVersion, details));
    }
  }

  private Path resolveExistingConfigPath(final Path configFile) {
    if (Files.exists(configFile)) {
      return configFile;
    }
    if (DEFAULT_CONFIG_FILE.equals(configFile)) {
      if (Files.exists(FALLBACK_CONFIG_FILE)) {
        return FALLBACK_CONFIG_FILE;
      }
      return null;
    }
    if (FALLBACK_CONFIG_FILE.equals(configFile)) {
      return null;
    }
    throw new InvalidConfigurationException(
        "Configuration file not found: " + configFile.toAbsolutePath());
  }

  /**
   * Reads the file content. Protected for testing.
   *
   * @param path file path
   * @return file content
   * @throws IOException if I/O error occurs
   */
  protected String readFile(final Path path) throws IOException {
    final String content = Files.readString(path);
    return substituteVariables(content);
  }

  private String substituteVariables(final String text) {
    final StringLookup lookup =
        key -> {
          final String envValue = System.getenv(key);
          if (envValue != null) {
            return envValue;
          }
          return System.getProperty(key);
        };
    final StringSubstitutor substitutor = new StringSubstitutor(lookup);
    return substitutor.replace(text);
  }

  private Config parseConfig(final String jsonText, final Path configFile) {
    final ObjectMapper mapper =
        JsonMapperFactory.create()
            .rebuild()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();
    try {
      return mapper.readValue(jsonText, Config.class);
    } catch (JacksonException e) {
      throw new InvalidConfigurationException(
          """
              Failed to parse configuration file: %s
              Error: %s
              Please check your JSON syntax."""
              .formatted(configFile.toAbsolutePath(), e.getOriginalMessage()),
          e);
    }
  }
}
