package com.craftsmanbro.fulcraft.infrastructure.config.schema;

import com.craftsmanbro.fulcraft.infrastructure.json.impl.JsonMapperFactory;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import tools.jackson.databind.JsonNode;

public final class JsonSchemaValidator {

  private static final int LATEST_SCHEMA_VERSION = 1;

  private static final String SCHEMA_RESOURCE_PREFIX = "schema/config-schema-v";

  private static final JsonSchemaFactory SCHEMA_FACTORY =
      JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);

  private static final com.fasterxml.jackson.databind.ObjectMapper COMPAT_MAPPER =
      new com.fasterxml.jackson.databind.ObjectMapper();

  private static final tools.jackson.databind.ObjectMapper SCHEMA_MAPPER =
      JsonMapperFactory.create();

  private static final Map<Integer, JsonSchemaValidator> RESOURCE_SCHEMAS =
      new ConcurrentHashMap<>();

  public record SchemaError(String path, String message) {}

  private final JsonSchema schema;

  private JsonSchemaValidator(final JsonNode root) {
    this.schema = SCHEMA_FACTORY.getSchema(toCompatNode(root));
  }

  public static JsonSchemaValidator loadFromResource(final int schemaVersion) {
    return RESOURCE_SCHEMAS.computeIfAbsent(schemaVersion, v -> loadResourceSchema(v));
  }

  private static JsonSchemaValidator loadResourceSchema(final int schemaVersion) {
    final String resource = SCHEMA_RESOURCE_PREFIX + schemaVersion + ".json";
    final InputStream resourceStream =
        JsonSchemaValidator.class.getClassLoader().getResourceAsStream(resource);
    if (resourceStream == null) {
      throw new IllegalStateException(
          "Schema file not found for schema_version " + schemaVersion + ": " + resource);
    }
    try (InputStream input = resourceStream) {
      return new JsonSchemaValidator(SCHEMA_MAPPER.readTree(input));
    } catch (IOException | RuntimeException e) {
      throw new IllegalStateException(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.message", "Failed to read schema file: " + resource),
          e);
    }
  }

  public static JsonSchemaValidator loadFromPath(final Path schemaPath) {
    if (schemaPath == null) {
      throw new IllegalStateException(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.message", "Schema path is required."));
    }
    try (InputStream input = Files.newInputStream(schemaPath)) {
      return new JsonSchemaValidator(SCHEMA_MAPPER.readTree(input));
    } catch (IOException | RuntimeException e) {
      throw new IllegalStateException(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.message", "Failed to read schema file: " + schemaPath),
          e);
    }
  }

  public static int resolveSchemaVersion(final JsonNode configRoot) {
    if (configRoot == null || configRoot.isMissingNode()) {
      return LATEST_SCHEMA_VERSION;
    }
    JsonNode versionNode = configRoot.path("schema_version");
    if (versionNode.isMissingNode()) {
      versionNode = configRoot.path("schemaVersion");
    }
    if (versionNode.isMissingNode() || versionNode.isNull()) {
      return LATEST_SCHEMA_VERSION;
    }
    if (versionNode.isNumber()) {
      final double doubleValue = versionNode.asDouble();
      if (Math.floor(doubleValue) != doubleValue) {
        throw new IllegalStateException(
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "infra.common.error.message", "schema_version must be an integer."));
      }
      final int major = versionNode.asInt();
      if (major < 1) {
        throw new IllegalStateException(
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "infra.common.error.message", "schema_version must be >= 1."));
      }
      return major;
    }
    if (versionNode.isString()) {
      final String trimmed = versionNode.asString().trim();
      if (trimmed.isEmpty()) {
        throw new IllegalStateException(
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "infra.common.error.message", "schema_version must be a non-empty string."));
      }
      final String majorPart = trimmed.split("\\.")[0];
      try {
        final int major = Integer.parseInt(majorPart);
        if (major < 1) {
          throw new IllegalStateException(
              com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                  "infra.common.error.message", "schema_version must be >= 1."));
        }
        return major;
      } catch (NumberFormatException e) {
        throw new IllegalStateException(
            "schema_version must be an integer or SemVer (e.g., 1 or 1.0.0).", e);
      }
    }
    throw new IllegalStateException(
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.message", "schema_version must be a string or integer."));
  }

  public List<SchemaError> validate(final JsonNode data) {
    final Set<ValidationMessage> messages = schema.validate(toCompatNode(data));
    final List<SchemaError> errors = new ArrayList<>(messages.size());
    for (final ValidationMessage message : messages) {
      errors.add(toSchemaError(message));
    }
    errors.sort(Comparator.comparing(SchemaError::path).thenComparing(SchemaError::message));
    return errors;
  }

  private static com.fasterxml.jackson.databind.JsonNode toCompatNode(final JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return com.fasterxml.jackson.databind.node.NullNode.getInstance();
    }
    if (node.isObject()) {
      final com.fasterxml.jackson.databind.node.ObjectNode objectNode =
          COMPAT_MAPPER.createObjectNode();
      for (final Map.Entry<String, JsonNode> entry : node.properties()) {
        objectNode.set(entry.getKey(), toCompatNode(entry.getValue()));
      }
      return objectNode;
    }
    if (node.isArray()) {
      final com.fasterxml.jackson.databind.node.ArrayNode arrayNode =
          COMPAT_MAPPER.createArrayNode();
      for (final JsonNode element : node) {
        arrayNode.add(toCompatNode(element));
      }
      return arrayNode;
    }
    if (node.isString()) {
      return com.fasterxml.jackson.databind.node.TextNode.valueOf(node.stringValue());
    }
    if (node.isBoolean()) {
      return com.fasterxml.jackson.databind.node.BooleanNode.valueOf(node.booleanValue());
    }
    if (node.isBinary()) {
      return com.fasterxml.jackson.databind.node.BinaryNode.valueOf(node.binaryValue());
    }
    if (node.isNumber()) {
      return toCompatNumberNode(node.numberValue());
    }
    return com.fasterxml.jackson.databind.node.TextNode.valueOf(node.asString());
  }

  private static com.fasterxml.jackson.databind.JsonNode toCompatNumberNode(final Number value) {
    if (value instanceof Integer intValue) {
      return com.fasterxml.jackson.databind.node.IntNode.valueOf(intValue);
    }
    if (value instanceof Long longValue) {
      return com.fasterxml.jackson.databind.node.LongNode.valueOf(longValue);
    }
    if (value instanceof Short shortValue) {
      return com.fasterxml.jackson.databind.node.ShortNode.valueOf(shortValue);
    }
    if (value instanceof Byte byteValue) {
      return com.fasterxml.jackson.databind.node.ShortNode.valueOf(byteValue.shortValue());
    }
    if (value instanceof Float floatValue) {
      return com.fasterxml.jackson.databind.node.FloatNode.valueOf(floatValue);
    }
    if (value instanceof Double doubleValue) {
      return com.fasterxml.jackson.databind.node.DoubleNode.valueOf(doubleValue);
    }
    if (value instanceof BigInteger bigInteger) {
      return com.fasterxml.jackson.databind.node.BigIntegerNode.valueOf(bigInteger);
    }
    if (value instanceof BigDecimal bigDecimal) {
      return com.fasterxml.jackson.databind.node.DecimalNode.valueOf(bigDecimal);
    }
    return com.fasterxml.jackson.databind.node.DecimalNode.valueOf(
        new BigDecimal(value.toString()));
  }

  private SchemaError toSchemaError(final ValidationMessage validationMessage) {
    final String rawPath =
        validationMessage.getInstanceLocation() != null
            ? validationMessage.getInstanceLocation().toString()
            : "";
    final Object[] arguments = validationMessage.getArguments();
    final String rawMessage = validationMessage.getMessage();
    final String keyword = validationMessage.getType();
    final String property = validationMessage.getProperty();
    String path = normalizePath(rawPath);
    final String normalizedMessage = normalizeMessage(keyword, rawMessage, arguments);
    if ("required".equalsIgnoreCase(keyword) && property != null && !property.isBlank()) {
      path = path.isBlank() ? property : joinPath(path, property);
    } else if ("additionalproperties".equalsIgnoreCase(keyword)
        && property != null
        && !property.isBlank()) {
      path = path.isBlank() ? property : joinPath(path, property);
    }
    return new SchemaError(path, normalizedMessage);
  }

  private String normalizeMessage(
      final String keyword, final String rawMessage, final Object[] arguments) {
    if (rawMessage == null || rawMessage.isBlank()) {
      return "Schema validation failed.";
    }
    final String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
    return switch (normalizedKeyword) {
      case "required" -> "Field is required.";
      case "additionalproperties" -> "Unknown field.";
      case "enum" -> "Value must be one of the allowed enum values.";
      case "minimum" -> formatBoundMessage(arguments, rawMessage, "Value must be >= ");
      case "maximum" -> formatBoundMessage(arguments, rawMessage, "Value must be <= ");
      case "minlength" -> formatLengthMessage(arguments, rawMessage, "Value length must be >= ");
      case "maxlength" -> formatLengthMessage(arguments, rawMessage, "Value length must be <= ");
      case "type" -> {
        if (isNullTypeViolation(arguments)) {
          yield "Value is required.";
        }
        yield "Value type does not match schema.";
      }
      default -> rawMessage;
    };
  }

  private String formatBoundMessage(
      final Object[] arguments, final String rawMessage, final String prefix) {
    if (arguments == null || arguments.length == 0 || arguments[0] == null) {
      return rawMessage;
    }
    return prefix + arguments[0];
  }

  private String formatLengthMessage(
      final Object[] arguments, final String rawMessage, final String prefix) {
    if (arguments == null || arguments.length == 0 || arguments[0] == null) {
      return rawMessage;
    }
    return prefix + arguments[0];
  }

  private boolean isNullTypeViolation(final Object[] arguments) {
    if (arguments == null || arguments.length == 0) {
      return false;
    }
    final Object actualType = arguments[0];
    return actualType == null || "null".equalsIgnoreCase(String.valueOf(actualType));
  }

  private String normalizePath(final String rawPath) {
    if (rawPath == null) {
      return "";
    }
    final String trimmed = rawPath.trim();
    if (trimmed.isEmpty() || "$".equals(trimmed)) {
      return "";
    }
    if (trimmed.startsWith("$.")) {
      return trimmed.substring(2);
    }
    if (trimmed.startsWith("$[")) {
      return trimmed.substring(1);
    }
    return trimmed;
  }

  private String joinPath(final String base, final String key) {
    if (base == null || base.isEmpty()) {
      return key;
    }
    if (key == null || key.isEmpty()) {
      return base;
    }
    if (key.startsWith("[")) {
      return base + key;
    }
    return base + "." + key;
  }
}
