package com.craftsmanbro.fulcraft.infrastructure.config.validator;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.config.InvalidConfigurationException;
import com.craftsmanbro.fulcraft.infrastructure.config.schema.JsonSchemaValidator;
import com.craftsmanbro.fulcraft.infrastructure.config.validator.llm.LlmConfigValidator;
import com.craftsmanbro.fulcraft.infrastructure.config.validator.llm.LlmSectionValidator;
import com.craftsmanbro.fulcraft.infrastructure.config.validator.section.AuditSectionValidator;
import com.craftsmanbro.fulcraft.infrastructure.config.validator.section.ExecutionSectionValidator;
import com.craftsmanbro.fulcraft.infrastructure.config.validator.section.ProjectSectionValidator;
import com.craftsmanbro.fulcraft.infrastructure.config.validator.section.QuotaSectionValidator;
import com.craftsmanbro.fulcraft.infrastructure.json.impl.JsonMapperFactory;
import com.craftsmanbro.fulcraft.plugins.analysis.config.validation.AnalysisSectionValidator;
import com.craftsmanbro.fulcraft.plugins.analysis.config.validation.SelectionRulesSectionValidator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;

public class ConfigValidator {

  private static final ObjectMapper RAW_JSON_MAPPER = JsonMapperFactory.create();

  private static final ObjectMapper CONFIG_MAPPER =
      JsonMapperFactory.create()
          .rebuild()
          .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
          .build();

  private final List<ConfigSectionValidator> sectionValidators;

  public ConfigValidator() {
    this(new LlmSectionValidator());
  }

  public ConfigValidator(final Map<String, LlmConfigValidator> validators) {
    this(new LlmSectionValidator(validators));
  }

  private ConfigValidator(final LlmSectionValidator llmSectionValidator) {
    this.sectionValidators =
        List.of(
            new ProjectSectionValidator(),
            new SelectionRulesSectionValidator(),
            llmSectionValidator,
            new ExecutionSectionValidator(),
            new AnalysisSectionValidator(),
            new AuditSectionValidator(),
            new QuotaSectionValidator());
  }

  /**
   * Validates the structure and content of a raw JSON configuration string.
   *
   * @param jsonText The raw JSON content.
   * @throws InvalidConfigurationException If the JSON is invalid or validation fails.
   */
  public void validateRawJson(final String jsonText) {
    final List<String> errors = new ArrayList<>();
    try {
      final JsonNode node = RAW_JSON_MAPPER.readTree(jsonText);
      if (node == null || node.isMissingNode() || node.isNull()) {
        errors.add("Configuration file is empty or invalid JSON. Please populate required fields.");
        throwAggregated(errors);
        return;
      }
      if (!node.isObject()) {
        errors.add("Configuration root must be an object.");
        throwAggregated(errors);
        return;
      }
      validateSchema(node, errors);
      throwAggregated(errors);
      final Config config = parseConfig(node);
      validateParsedConfig(config);
      throwAggregated(errors);
    } catch (JacksonException e) {
      throw new InvalidConfigurationException(
          "Failed to validate configuration: " + e.getOriginalMessage(), e);
    } catch (IllegalArgumentException e) {
      throw new InvalidConfigurationException(
          "Failed to parse configuration during validation: " + e.getMessage(), e);
    }
  }

  /**
   * Validates a parsed {@link Config} object.
   *
   * @param config The parsed configuration object.
   * @throws InvalidConfigurationException If validation fails.
   */
  public void validateParsedConfig(final Config config) {
    final List<String> errors = new ArrayList<>();
    if (config == null) {
      errors.add("Configuration file is empty after parsing. Please populate required fields.");
      throwAggregated(errors);
      return;
    }
    for (final ConfigSectionValidator validator : sectionValidators) {
      validator.validateParsedConfig(config, errors);
    }
    throwAggregated(errors);
  }

  private void validateSchema(final JsonNode node, final List<String> errors) {
    final int schemaVersion;
    try {
      schemaVersion = JsonSchemaValidator.resolveSchemaVersion(node);
    } catch (IllegalStateException e) {
      errors.add(e.getMessage());
      return;
    }
    final JsonSchemaValidator schemaValidator;
    try {
      schemaValidator = JsonSchemaValidator.loadFromResource(schemaVersion);
    } catch (IllegalStateException e) {
      errors.add(e.getMessage());
      return;
    }
    final List<JsonSchemaValidator.SchemaError> schemaErrors = schemaValidator.validate(node);
    for (final JsonSchemaValidator.SchemaError error : schemaErrors) {
      if (error.path() == null || error.path().isBlank()) {
        errors.add(error.message());
      } else {
        errors.add(error.path() + ": " + error.message());
      }
    }
  }

  private Config parseConfig(final JsonNode node) {
    return CONFIG_MAPPER.treeToValue(node, Config.class);
  }

  private void throwAggregated(final List<String> errors) {
    if (!errors.isEmpty()) {
      throw new InvalidConfigurationException(errors);
    }
  }
}
