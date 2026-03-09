package com.craftsmanbro.fulcraft.ui.tui.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigSchemaIndexCoverageTest {

  @TempDir Path tempDir;

  @Test
  void staticFactoriesCoverVersionResolutionAndErrors() {
    assertThat(ConfigSchemaIndex.latestSchemaVersion()).isEqualTo(1);
    assertThat(ConfigSchemaIndex.resolveSchemaVersion(null)).isEqualTo(1);
    assertThat(ConfigSchemaIndex.resolveSchemaVersion(Map.of("schemaVersion", "1.0.0")))
        .isEqualTo(1);
    assertThat(ConfigSchemaIndex.resolveSchemaVersion(Map.of("schema_version", " 2.3.4 ")))
        .isEqualTo(2);

    ConfigSchemaIndex fromConfig = ConfigSchemaIndex.forConfig(Map.of("schemaVersion", "1"));
    assertThat(fromConfig.getTopLevelKeys()).contains("project", "selection_rules", "llm");

    assertThatThrownBy(
            () -> ConfigSchemaIndex.resolveSchemaVersion(Map.of("schema_version", List.of(1))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("string or integer");
    assertThatThrownBy(() -> ConfigSchemaIndex.forVersion(999))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(
            MessageSource.getMessage("tui.config_schema.error.resource_not_found", ""));
    assertThatThrownBy(() -> ConfigSchemaIndex.forSchemaPath(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(MessageSource.getMessage("tui.config_schema.error.path_required"));
    assertThatThrownBy(() -> ConfigSchemaIndex.forSchemaPath(tempDir.resolve("missing.json")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(
            MessageSource.getMessage("tui.config_schema.error.file_read_failed", ""));
  }

  @Test
  void forSchemaPathFindRuleAndListItemRuleHandleAdditionalPropertiesAndRefs() throws IOException {
    Path schemaPath =
        writeSchema(
            "custom-schema.json",
            """
            {
              "type": "object",
              "properties": {
                "dynamic": {
                  "type": "object",
                  "additionalProperties": { "type": "integer", "minimum": 1 }
                },
                "choices": {
                  "type": ["string", "null"],
                  "enum": ["a", 1, true]
                },
                "list": {
                  "type": "array",
                  "items": { "$ref": "#/definitions/listItem" }
                },
                "refObj": { "$ref": "#/definitions/refType" }
              },
              "definitions": {
                "listItem": { "type": "number", "maximum": 9 },
                "refType": {
                  "type": "object",
                  "properties": {
                    "enabled": { "type": "boolean" }
                  }
                }
              }
            }
            """);

    ConfigSchemaIndex index = ConfigSchemaIndex.forSchemaPath(schemaPath);

    ConfigSchemaIndex.SchemaRule dynamicRule =
        index.findRule(path(key("dynamic"), key("custom_key")));
    ConfigSchemaIndex.SchemaRule choicesRule = index.findRule(path(key("choices")));
    ConfigSchemaIndex.SchemaRule indexedRule = index.findRule(path(key("list"), idx(0)));
    ConfigSchemaIndex.SchemaRule listItemRule = index.findListItemRule(path(key("list")));
    ConfigSchemaIndex.SchemaRule indexedListItemRule =
        index.findListItemRule(path(key("list"), idx(0)));
    ConfigSchemaIndex.SchemaRule refRule = index.findRule(path(key("refObj"), key("enabled")));
    ConfigSchemaIndex.SchemaRule missingRule = index.findRule(path(key("unknown"), key("value")));

    assertThat(index.getTopLevelKeys()).contains("dynamic", "choices", "list", "refObj");

    assertThat(dynamicRule).isNotNull();
    assertThat(dynamicRule.types()).contains("integer");
    assertThat(dynamicRule.minimum()).isEqualTo(1d);

    assertThat(choicesRule).isNotNull();
    assertThat(choicesRule.hasEnum()).isTrue();
    assertThat(choicesRule.enumValues()).contains("a", 1, true);
    assertThat(choicesRule.allowsNull()).isTrue();

    assertThat(indexedRule).isNotNull();
    assertThat(indexedRule.types()).contains("number");
    assertThat(indexedRule.maximum()).isEqualTo(9d);

    assertThat(listItemRule).isNotNull();
    assertThat(listItemRule.types()).contains("number");
    assertThat(indexedListItemRule).isNull();

    assertThat(refRule).isNotNull();
    assertThat(refRule.types()).contains("boolean");
    assertThat(missingRule).isNull();
  }

  @Test
  void refAndTypeEdgeCasesCoverFallbackAndCycleBranches() throws IOException {
    Path schemaPath =
        writeSchema(
            "ref-edge-schema.json",
            """
            {
              "type": "object",
              "properties": {
                "externalRef": { "$ref": "https://craftsmann-bro.com/schema" },
                "missingRef": { "$ref": "#/definitions/missing" },
                "cyclicRef": { "$ref": "#/definitions/cycle" },
                "invalidType": { "type": 123 },
                "enumObject": { "enum": [ { "a": 1 } ] },
                "notAList": { "type": "object" },
                "arr": { "type": "array", "items": { "type": "string" } }
              },
              "definitions": {
                "cycle": { "$ref": "#/definitions/cycle" }
              }
            }
            """);

    ConfigSchemaIndex index = ConfigSchemaIndex.forSchemaPath(schemaPath);

    ConfigSchemaIndex.SchemaRule externalRefRule = index.findRule(path(key("externalRef")));
    ConfigSchemaIndex.SchemaRule missingRefRule = index.findRule(path(key("missingRef")));
    ConfigSchemaIndex.SchemaRule cyclicRefRule = index.findRule(path(key("cyclicRef")));
    ConfigSchemaIndex.SchemaRule invalidTypeRule = index.findRule(path(key("invalidType")));
    ConfigSchemaIndex.SchemaRule enumObjectRule = index.findRule(path(key("enumObject")));

    assertThat(externalRefRule).isNotNull();
    assertThat(externalRefRule.types()).contains("object");
    assertThat(missingRefRule).isNotNull();
    assertThat(missingRefRule.types()).contains("object");
    assertThat(cyclicRefRule).isNull();
    assertThat(invalidTypeRule).isNull();
    assertThat(enumObjectRule).isNotNull();
    assertThat(enumObjectRule.enumValues()).contains("{\"a\":1}");

    assertThat(index.findListItemRule(path(key("notAList")))).isNull();
    assertThat(index.findListItemRule(path(key("arr"), idx(0)))).isNull();
  }

  @Test
  void schemaWithoutPropertiesReturnsEmptyTopLevelKeys() throws IOException {
    Path schemaPath = writeSchema("no-properties-schema.json", "{\"type\":\"object\"}");

    ConfigSchemaIndex index = ConfigSchemaIndex.forSchemaPath(schemaPath);

    assertThat(index.getTopLevelKeys()).isEmpty();
  }

  private static ConfigEditor.PathSegment key(String key) {
    return ConfigEditor.PathSegment.key(key);
  }

  private static ConfigEditor.PathSegment idx(int index) {
    return ConfigEditor.PathSegment.index(index);
  }

  private static List<ConfigEditor.PathSegment> path(ConfigEditor.PathSegment... segments) {
    return List.of(segments);
  }

  private Path writeSchema(String fileName, String schemaJson) throws IOException {
    Path path = tempDir.resolve(fileName);
    Files.writeString(path, schemaJson);
    return path;
  }
}
