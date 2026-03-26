package com.craftsmanbro.fulcraft.ui.tui.config;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.config.InvalidConfigurationException;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.config.schema.JsonSchemaValidator;
import com.craftsmanbro.fulcraft.infrastructure.config.validator.ConfigValidator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.dataformat.yaml.YAMLMapper;

public final class ConfigValidationService {

  public record ValidationIssue(String path, String message) {}

  private static final Pattern QUOTED_PATH = Pattern.compile("'([^']+)'");

  private static final Pattern VARIABLE_PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)\\}");

  private final ConfigValidator validator = new ConfigValidator();

  private final ObjectMapper yamlMapper = YAMLMapper.builder().build();

  private final ObjectMapper configMapper =
      YAMLMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();

  public List<ValidationIssue> validate(final String jsonText) {
    final String input = Objects.requireNonNullElse(jsonText, "");
    final String substituted = substituteVariables(input);
    final List<ValidationIssue> issues = new ArrayList<>();
    if (!runSchemaValidation(substituted, issues)) {
      return issues;
    }
    final Config config;
    try {
      config = parseConfig(substituted);
    } catch (IllegalStateException e) {
      issues.add(
          new ValidationIssue(
              "",
              Objects.requireNonNullElse(
                  e.getMessage(), msg("tui.config_validation.parse_failed"))));
      return issues;
    }
    if (config == null) {
      issues.add(new ValidationIssue("", msg("tui.config_validation.empty_after_parse")));
      return issues;
    }
    runParsedValidation(config, issues);
    return issues;
  }

  public List<ValidationIssue> validateWithSchema(final String jsonText, final Path schemaPath) {
    final String input = Objects.requireNonNullElse(jsonText, "");
    final String substituted = substituteVariables(input);
    final List<ValidationIssue> issues = new ArrayList<>();
    if (schemaPath == null) {
      issues.add(new ValidationIssue("", msg("tui.config_validation.schema_required")));
      return issues;
    }
    if (!Files.exists(schemaPath)) {
      issues.add(
          new ValidationIssue("", msg("tui.config_validation.schema_not_found", schemaPath)));
      return issues;
    }
    final JsonNode dataNode;
    try {
      dataNode = parseJsonNode(substituted);
    } catch (IllegalStateException e) {
      issues.add(new ValidationIssue("", e.getMessage()));
      return issues;
    }
    final JsonSchemaValidator schemaValidator;
    try {
      schemaValidator = JsonSchemaValidator.loadFromPath(schemaPath);
    } catch (IllegalStateException e) {
      issues.add(new ValidationIssue("", e.getMessage()));
      return issues;
    }
    final List<JsonSchemaValidator.SchemaError> errors = schemaValidator.validate(dataNode);
    addSchemaIssues(errors, issues);
    return issues;
  }

  private boolean runSchemaValidation(final String jsonText, final List<ValidationIssue> issues) {
    final JsonNode dataNode;
    try {
      dataNode = parseJsonNode(jsonText);
    } catch (IllegalStateException e) {
      issues.add(new ValidationIssue("", e.getMessage()));
      return false;
    }
    final int schemaVersion;
    try {
      schemaVersion = JsonSchemaValidator.resolveSchemaVersion(dataNode);
    } catch (IllegalStateException e) {
      issues.add(new ValidationIssue("schema_version", e.getMessage()));
      return false;
    }
    final JsonSchemaValidator schemaValidator;
    try {
      schemaValidator = JsonSchemaValidator.loadFromResource(schemaVersion);
    } catch (IllegalStateException e) {
      issues.add(new ValidationIssue("schema_version", e.getMessage()));
      return false;
    }
    final List<JsonSchemaValidator.SchemaError> errors = schemaValidator.validate(dataNode);
    if (errors.isEmpty()) {
      return true;
    }
    addSchemaIssues(errors, issues);
    return false;
  }

  private JsonNode parseJsonNode(final String jsonText) {
    try {
      final JsonNode node = yamlMapper.readTree(jsonText);
      if (node == null || node.isMissingNode() || node.isNull()) {
        throw new IllegalStateException(msg("tui.config_validation.empty_or_invalid"));
      }
      if (!node.isObject()) {
        throw new IllegalStateException(msg("tui.config_validation.root_must_be_object"));
      }
      return node;
    } catch (JacksonException e) {
      throw new IllegalStateException(
          msg("tui.config_validation.parse_configuration_failed", e.getOriginalMessage()), e);
    }
  }

  private void runParsedValidation(final Config config, final List<ValidationIssue> issues) {
    try {
      validator.validateParsedConfig(config);
    } catch (InvalidConfigurationException | IllegalStateException e) {
      issues.addAll(parseIssues(e.getMessage()));
      if (issues.isEmpty()) {
        issues.add(
            new ValidationIssue(
                "",
                Objects.requireNonNullElse(e.getMessage(), msg("tui.config_validation.invalid"))));
      }
    }
  }

  private Config parseConfig(final String jsonText) {
    try {
      return configMapper.readValue(jsonText, Config.class);
    } catch (JacksonException e) {
      throw new IllegalStateException(
          msg("tui.config_validation.parse_configuration_failed", e.getOriginalMessage()), e);
    }
  }

  private void addSchemaIssues(
      final List<JsonSchemaValidator.SchemaError> errors, final List<ValidationIssue> issues) {
    for (final JsonSchemaValidator.SchemaError error : errors) {
      issues.add(new ValidationIssue(error.path(), error.message()));
    }
  }

  private List<ValidationIssue> parseIssues(final String message) {
    final List<ValidationIssue> issues = new ArrayList<>();
    if (message == null || message.isBlank()) {
      return issues;
    }
    final String[] lines = message.split("\\R");
    for (final String line : lines) {
      final String trimmed = line.trim();
      if (!trimmed.startsWith("- ")) {
        continue;
      }
      final String error = trimmed.substring(2).trim();
      if (error.isEmpty()) {
        continue;
      }
      issues.add(new ValidationIssue(extractPath(error), error));
    }
    return issues;
  }

  private String extractPath(final String error) {
    if (error == null) {
      return "";
    }
    final Matcher matcher = QUOTED_PATH.matcher(error);
    if (matcher.find()) {
      return matcher.group(1);
    }
    return "";
  }

  private String substituteVariables(final String text) {
    final Matcher matcher = VARIABLE_PLACEHOLDER.matcher(text);
    final StringBuilder sb = new StringBuilder();
    while (matcher.find()) {
      final String varName = matcher.group(1);
      final String envValue = System.getenv(varName);
      if (envValue != null) {
        matcher.appendReplacement(sb, Matcher.quoteReplacement(envValue));
      } else {
        final String propValue = System.getProperty(varName);
        if (propValue != null) {
          matcher.appendReplacement(sb, Matcher.quoteReplacement(propValue));
        } else {
          matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
        }
      }
    }
    matcher.appendTail(sb);
    return sb.toString();
  }

  private static String msg(final String key, final Object... args) {
    return MessageSource.getMessage(key, args);
  }
}
