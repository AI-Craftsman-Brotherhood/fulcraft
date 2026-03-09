package com.craftsmanbro.fulcraft.ui.tui.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigEditorTest {

  @TempDir Path tempDir;

  @Test
  void updateDirtyForPathClearsWhenValueRestored() throws IOException {
    Path configPath = tempDir.resolve("config.json");
    Files.writeString(
        configPath,
        """
            {
              "schema_version": 1,
              "project": { "id": "demo" }
            }
            """);

    ConfigEditor editor = ConfigEditor.load(configPath);
    List<ConfigEditor.PathSegment> path =
        List.of(ConfigEditor.PathSegment.key("project"), ConfigEditor.PathSegment.key("id"));

    editor.setValueWithCreate(path, "changed");
    editor.updateDirtyForPath(path);
    assertThat(editor.isDirty()).isTrue();
    assertThat(editor.getDirtyKeys()).contains("project.id");

    editor.setValueWithCreate(path, "demo");
    editor.updateDirtyForPath(path);
    assertThat(editor.isDirty()).isFalse();
  }

  @Test
  void appendListValueWithCreateCreatesList() {
    Path configPath = tempDir.resolve("list.json");
    ConfigEditor editor = ConfigEditor.load(configPath);
    List<ConfigEditor.PathSegment> listPath =
        List.of(
            ConfigEditor.PathSegment.key("selection_rules"),
            ConfigEditor.PathSegment.key("exclude_annotations"));

    boolean updated = editor.appendListValueWithCreate(listPath, "Foo");

    assertThat(updated).isTrue();
    Object value = editor.getValue(listPath);
    assertThat(value).isInstanceOf(List.class);
    List<String> listValue = (List<String>) value;
    assertThat(listValue).containsExactly("Foo");
  }

  @Test
  void classifyValueUsesSchemaAndMetadata() {
    Path configPath = tempDir.resolve("classify.json");
    ConfigEditor editor = ConfigEditor.load(configPath);

    List<ConfigEditor.PathSegment> listPath =
        List.of(
            ConfigEditor.PathSegment.key("selection_rules"),
            ConfigEditor.PathSegment.key("exclude_annotations"));
    assertThat(editor.classifyValue(listPath, null)).isEqualTo(ConfigEditor.ValueType.LIST);

    List<ConfigEditor.PathSegment> itemPath =
        List.of(
            ConfigEditor.PathSegment.key("selection_rules"),
            ConfigEditor.PathSegment.key("exclude_annotations"),
            ConfigEditor.PathSegment.index(0));
    assertThat(editor.classifyValue(itemPath, "Foo")).isEqualTo(ConfigEditor.ValueType.STRING);

    List<ConfigEditor.PathSegment> enumPath =
        List.of(
            ConfigEditor.PathSegment.key("project"), ConfigEditor.PathSegment.key("build_tool"));
    assertThat(editor.classifyValue(enumPath, "gradle")).isEqualTo(ConfigEditor.ValueType.ENUM);
  }

  @Test
  void getEnumOptionsIncludesCustomValue() {
    Path configPath = tempDir.resolve("enum.json");
    ConfigEditor editor = ConfigEditor.load(configPath);
    List<ConfigEditor.PathSegment> enumPath =
        List.of(
            ConfigEditor.PathSegment.key("project"), ConfigEditor.PathSegment.key("build_tool"));

    List<String> options = editor.getEnumOptions(enumPath, "ant");

    assertThat(options).contains("gradle", "maven", "ant");
  }

  @Test
  void getAdvancedTopLevelKeysExcludeSchemaKnownRootCategories() throws IOException {
    Path configPath = tempDir.resolve("advanced.json");
    Files.writeString(
        configPath,
        """
            {
              "schema_version": 1,
              "project": { "id": "demo" },
              "pipeline": { "stages": [] },
              "custom": { "enabled": true }
            }
            """);

    ConfigEditor editor = ConfigEditor.load(configPath);

    assertThat(editor.getAdvancedTopLevelKeys()).contains("custom");
    assertThat(editor.getAdvancedTopLevelKeys()).doesNotContain("project");
    assertThat(editor.getAdvancedTopLevelKeys()).doesNotContain("pipeline");
  }
}
