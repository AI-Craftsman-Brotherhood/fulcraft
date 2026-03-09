package com.craftsmanbro.fulcraft.ui.tui.config;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.config.schema.JsonSchemaValidator;
import com.craftsmanbro.fulcraft.infrastructure.json.impl.JsonMapperFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class ConfigSchemaIndex {

  private static final int LATEST_SCHEMA_VERSION = 1;

  private static final String SCHEMA_RESOURCE_PREFIX = "schema/config-schema-v";

  record SchemaRule(
      Set<String> types,
      List<Object> enumValues,
      Double minimum,
      Double maximum,
      boolean allowsNull) {

    boolean hasEnum() {
      return enumValues != null && !enumValues.isEmpty();
    }
  }

  private final JsonNode root;

  private final Set<String> topLevelKeys;

  private ConfigSchemaIndex(final JsonNode root) {
    this.root = root;
    this.topLevelKeys = extractTopLevelKeys(root);
  }

  static ConfigSchemaIndex forConfig(final Map<?, ?> rawConfig) {
    final int version = resolveSchemaVersion(rawConfig);
    return forVersion(version);
  }

  static int latestSchemaVersion() {
    return LATEST_SCHEMA_VERSION;
  }

  static ConfigSchemaIndex forVersion(final int version) {
    final String resource = SCHEMA_RESOURCE_PREFIX + version + ".json";
    try (InputStream input =
        ConfigSchemaIndex.class.getClassLoader().getResourceAsStream(resource)) {
      if (input == null) {
        throw new IllegalStateException(
            MessageSource.getMessage("tui.config_schema.error.resource_not_found", resource));
      }
      final ObjectMapper mapper = JsonMapperFactory.create();
      return new ConfigSchemaIndex(mapper.readTree(input));
    } catch (IOException e) {
      throw new IllegalStateException(
          MessageSource.getMessage("tui.config_schema.error.resource_read_failed", resource), e);
    }
  }

  public static ConfigSchemaIndex forSchemaPath(final Path schemaPath) {
    if (schemaPath == null) {
      throw new IllegalStateException(
          MessageSource.getMessage("tui.config_schema.error.path_required"));
    }
    try (InputStream input = Files.newInputStream(schemaPath)) {
      final ObjectMapper mapper = JsonMapperFactory.create();
      return new ConfigSchemaIndex(mapper.readTree(input));
    } catch (IOException | RuntimeException e) {
      throw new IllegalStateException(
          MessageSource.getMessage("tui.config_schema.error.file_read_failed", schemaPath), e);
    }
  }

  Set<String> getTopLevelKeys() {
    return topLevelKeys;
  }

  SchemaRule findRule(final List<ConfigEditor.PathSegment> path) {
    return toRule(resolvePathNode(path));
  }

  SchemaRule findListItemRule(final List<ConfigEditor.PathSegment> listPath) {
    if (listPath == null || listPath.isEmpty() || containsIndex(listPath)) {
      return null;
    }
    final JsonNode items = resolveItemsNode(resolvePathNode(listPath));
    return items == null ? null : toRule(resolveRef(items));
  }

  private JsonNode resolvePathNode(final List<ConfigEditor.PathSegment> path) {
    if (path == null || path.isEmpty()) {
      return null;
    }
    JsonNode node = root;
    for (final ConfigEditor.PathSegment segment : path) {
      node = stepNode(node, segment);
      if (node == null) {
        return null;
      }
    }
    return resolveRef(node);
  }

  private JsonNode stepNode(final JsonNode node, final ConfigEditor.PathSegment segment) {
    final JsonNode resolvedNode = resolveRef(node);
    if (segment.isKey()) {
      return resolveProperty(resolvedNode, segment.key());
    }
    if (segment.isIndex()) {
      return resolveItemsNode(resolvedNode);
    }
    return null;
  }

  private JsonNode resolveItemsNode(final JsonNode node) {
    if (node == null) {
      return null;
    }
    final JsonNode items = resolveRef(node).get("items");
    if (items == null || items.isMissingNode() || !items.isObject()) {
      return null;
    }
    return items;
  }

  private boolean containsIndex(final List<ConfigEditor.PathSegment> path) {
    for (final ConfigEditor.PathSegment segment : path) {
      if (segment.isIndex()) {
        return true;
      }
    }
    return false;
  }

  private JsonNode resolveProperty(final JsonNode node, final String key) {
    final JsonNode properties = node.get("properties");
    if (properties != null && properties.isObject()) {
      final JsonNode next = properties.get(key);
      if (next != null && !next.isMissingNode()) {
        return next;
      }
    }
    final JsonNode additionalProperties = node.get("additionalProperties");
    if (additionalProperties != null
        && !additionalProperties.isMissingNode()
        && additionalProperties.isObject()) {
      return additionalProperties;
    }
    return null;
  }

  private SchemaRule toRule(final JsonNode node) {
    if (node == null) {
      return null;
    }
    final Set<String> types = parseTypes(node);
    final boolean allowsNull = types.remove("null");
    final List<Object> enumValues = parseEnum(node);
    final Double minimum = node.has("minimum") ? node.get("minimum").asDouble() : null;
    final Double maximum = node.has("maximum") ? node.get("maximum").asDouble() : null;
    if (types.isEmpty() && enumValues.isEmpty()) {
      return null;
    }
    return new SchemaRule(types, enumValues, minimum, maximum, allowsNull);
  }

  private Set<String> parseTypes(final JsonNode node) {
    final Set<String> types = new LinkedHashSet<>();
    final JsonNode typeNode = node.get("type");
    if (typeNode == null || typeNode.isMissingNode()) {
      return types;
    }
    if (typeNode.isString()) {
      types.add(typeNode.asString());
      return types;
    }
    if (typeNode.isArray()) {
      for (final JsonNode entry : typeNode) {
        if (entry.isString()) {
          types.add(entry.asString());
        }
      }
    }
    return types;
  }

  private List<Object> parseEnum(final JsonNode node) {
    final JsonNode array = node.get("enum");
    if (array == null || !array.isArray()) {
      return List.of();
    }
    final List<Object> values = new ArrayList<>();
    for (final JsonNode entry : array) {
      if (entry.isString()) {
        values.add(entry.asString());
      } else if (entry.isNumber()) {
        values.add(entry.numberValue());
      } else if (entry.isBoolean()) {
        values.add(entry.asBoolean());
      } else {
        values.add(entry.toString());
      }
    }
    return values;
  }

  private JsonNode resolveRef(final JsonNode node) {
    JsonNode current = node;
    final Set<String> seen = new HashSet<>();
    while (current.has("$ref")) {
      final String ref = current.get("$ref").asString("");
      if (ref.isEmpty() || !seen.add(ref)) {
        break;
      }
      current = resolvePointer(ref);
    }
    return current;
  }

  private JsonNode resolvePointer(final String ref) {
    if (!ref.startsWith("#/")) {
      return root;
    }
    final String pointer = ref.substring(1);
    final JsonNode resolved = root.at(pointer);
    return resolved.isMissingNode() ? root : resolved;
  }

  static int resolveSchemaVersion(final Map<?, ?> rawConfig) {
    final JsonNode node =
        rawConfig == null ? null : JsonMapperFactory.create().valueToTree(rawConfig);
    return JsonSchemaValidator.resolveSchemaVersion(node);
  }

  private Set<String> extractTopLevelKeys(final JsonNode schemaRoot) {
    final JsonNode properties = schemaRoot.get("properties");
    if (properties == null || properties.isMissingNode()) {
      return Set.of();
    }
    final Set<String> keys = new LinkedHashSet<>();
    keys.addAll(properties.propertyNames());
    return keys;
  }
}
