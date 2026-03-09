package com.craftsmanbro.fulcraft.ui.tui.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfigSchemaIndexTest {

  @Test
  void resolveSchemaVersionHandlesNumericAndSemVerStrings() {
    assertThat(ConfigSchemaIndex.resolveSchemaVersion(Map.of("schema_version", 1))).isEqualTo(1);
    assertThat(ConfigSchemaIndex.resolveSchemaVersion(Map.of("schema_version", "1.0.0")))
        .isEqualTo(1);
  }

  @Test
  void resolveSchemaVersionRejectsInvalidValues() {
    assertThatThrownBy(() -> ConfigSchemaIndex.resolveSchemaVersion(Map.of("schema_version", 0)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(">= 1");
    assertThatThrownBy(() -> ConfigSchemaIndex.resolveSchemaVersion(Map.of("schema_version", 1.2)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("integer");
    assertThatThrownBy(() -> ConfigSchemaIndex.resolveSchemaVersion(Map.of("schema_version", "")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("non-empty string");
    assertThatThrownBy(
            () -> ConfigSchemaIndex.resolveSchemaVersion(Map.of("schema_version", "not-a-version")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("SemVer");
  }

  @Test
  void forVersionLoadsSchemaAndTopLevelKeys() {
    ConfigSchemaIndex index = ConfigSchemaIndex.forVersion(1);

    assertThat(index.getTopLevelKeys())
        .contains("project", "selection_rules", "llm")
        .doesNotContain("nonexistent");
  }

  @Test
  void findRuleAndListItemRuleResolveSchemaPaths() {
    ConfigSchemaIndex index = ConfigSchemaIndex.forVersion(1);

    List<ConfigEditor.PathSegment> projectId =
        List.of(ConfigEditor.PathSegment.key("project"), ConfigEditor.PathSegment.key("id"));
    ConfigSchemaIndex.SchemaRule projectRule = index.findRule(projectId);
    assertThat(projectRule).isNotNull();
    assertThat(projectRule.types()).contains("string");

    List<ConfigEditor.PathSegment> listPath =
        List.of(
            ConfigEditor.PathSegment.key("selection_rules"),
            ConfigEditor.PathSegment.key("exclude_annotations"));
    ConfigSchemaIndex.SchemaRule listRule = index.findRule(listPath);
    assertThat(listRule).isNotNull();
    assertThat(listRule.types()).contains("array");

    ConfigSchemaIndex.SchemaRule listItemRule = index.findListItemRule(listPath);
    assertThat(listItemRule).isNotNull();
    assertThat(listItemRule.types()).contains("string");

    List<ConfigEditor.PathSegment> indexedPath =
        List.of(
            ConfigEditor.PathSegment.key("selection_rules"),
            ConfigEditor.PathSegment.key("exclude_annotations"),
            ConfigEditor.PathSegment.index(0));
    ConfigSchemaIndex.SchemaRule indexedRule = index.findRule(indexedPath);
    assertThat(indexedRule).isNotNull();
    assertThat(indexedRule.types()).contains("string");
  }
}
