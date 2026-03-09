package com.craftsmanbro.fulcraft.ui.tui.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigEditorCoverageTest {

  @TempDir Path tempDir;

  @Test
  void loadVariantsExposeErrorsAndBasicAccessors() throws IOException {
    ConfigEditor nullPathEditor = ConfigEditor.load(null);

    assertThat(nullPathEditor.getLoadError())
        .contains(MessageSource.getMessage("tui.config_editor.error.path_missing"));
    assertThat(nullPathEditor.isNewFile()).isFalse();
    assertThat(nullPathEditor.toJson()).contains("{");

    Path newConfigPath = tempDir.resolve("new/config.json");
    ConfigEditor newFileEditor = ConfigEditor.load(newConfigPath);

    assertThat(newFileEditor.isNewFile()).isTrue();
    assertThat(newFileEditor.getConfigPath()).isEqualTo(newConfigPath);
    assertThat(newFileEditor.getLoadError()).isNull();

    Path invalidRoot = writeConfig("invalid-root.json", "[]");
    ConfigEditor invalidRootEditor = ConfigEditor.load(invalidRoot);

    assertThat(invalidRootEditor.getLoadError())
        .contains(MessageSource.getMessage("tui.config_editor.error.root_not_object"));
    assertThat(invalidRootEditor.toJson()).contains("{");
  }

  @Test
  void itemAccessorsExposeMapItemsAndAdvancedTopLevelEntries() throws IOException {
    Path configPath =
        writeConfig(
            "items.json",
            """
            {
              "schema_version": 1,
              "project": { "id": "demo", "build_tool": "gradle" },
              "custom": { "enabled": true }
            }
            """);

    ConfigEditor editor = ConfigEditor.load(configPath);
    List<ConfigEditor.ConfigItem> projectItems = editor.getItemsForPath(path(key("project")));
    List<ConfigEditor.ConfigItem> scalarItems =
        editor.getItemsForPath(path(key("project"), key("id")));

    assertThat(projectItems)
        .extracting(ConfigEditor.ConfigItem::label)
        .contains("id", "build_tool");
    assertThat(projectItems)
        .extracting(ConfigEditor.ConfigItem::path)
        .allSatisfy(itemPath -> assertThat(itemPath).hasSize(2));
    assertThat(scalarItems).isEmpty();
    assertThat(editor.getAdvancedTopLevelKeys()).contains("custom");
    assertThat(editor.getAdvancedTopLevelItems())
        .extracting(ConfigEditor.ConfigItem::label)
        .contains("custom");
  }

  @Test
  void mutationApisHandleSetAndListOperationsIncludingEdgeCases() {
    ConfigEditor editor = ConfigEditor.load(tempDir.resolve("mutations.json"));

    List<ConfigEditor.PathSegment> projectId = path(key("project"), key("id"));
    List<ConfigEditor.PathSegment> excludeList =
        path(key("selection_rules"), key("exclude_annotations"));
    List<ConfigEditor.PathSegment> indexedItem =
        path(key("selection_rules"), key("exclude_annotations"), idx(2));

    assertThat(editor.setValue(projectId, "demo")).isFalse();
    assertThat(editor.setValueWithCreate(projectId, "demo")).isTrue();
    assertThat(editor.setValue(projectId, "updated")).isTrue();
    assertThat(editor.getValue(projectId)).isEqualTo("updated");

    assertThat(editor.appendListValueWithCreate(excludeList, "First")).isTrue();
    assertThat(editor.addListValue(excludeList, "Second")).isTrue();
    assertThat(editor.setValueWithCreate(indexedItem, "Third")).isTrue();
    assertThat(editor.getValue(excludeList)).isEqualTo(List.of("First", "Second", "Third"));

    assertThat(editor.removeListIndex(excludeList, 1)).isTrue();
    assertThat(editor.removeListIndex(excludeList, 99)).isFalse();
    assertThat(editor.addListValue(projectId, "x")).isFalse();
    assertThat(editor.setValueWithCreate(path(key("arr"), idx(-1)), "bad")).isFalse();
  }

  @Test
  void validationAndSchemaTypeResolutionCoverSuccessAndFailureCases() {
    ConfigEditor editor = ConfigEditor.load(tempDir.resolve("schema-validation.json"));

    List<ConfigEditor.PathSegment> integerPath =
        path(key("selection_rules"), key("method_min_loc"));
    List<ConfigEditor.PathSegment> minBoundPath =
        path(key("selection_rules"), key("class_min_loc"));
    List<ConfigEditor.PathSegment> enumPath = path(key("project"), key("build_tool"));
    List<ConfigEditor.PathSegment> listPath =
        path(key("selection_rules"), key("exclude_annotations"));

    assertThat(editor.resolveSchemaValueType(integerPath))
        .isEqualTo(ConfigEditor.ValueType.INTEGER);
    assertThat(editor.resolveSchemaValueType(listPath)).isEqualTo(ConfigEditor.ValueType.LIST);
    assertThat(editor.resolveSchemaListItemType(listPath)).isEqualTo(ConfigEditor.ValueType.STRING);
    assertThat(editor.resolveSchemaValueType(path(key("unknown"), key("value")))).isNull();

    ConfigEditor.ValidationResult integerOk = editor.validateValue(integerPath, 2);
    ConfigEditor.ValidationResult integerFailed = editor.validateValue(integerPath, 1.5d);
    ConfigEditor.ValidationResult minBoundFailed = editor.validateValue(minBoundPath, -1);
    ConfigEditor.ValidationResult enumOk = editor.validateValue(enumPath, "gradle");
    ConfigEditor.ValidationResult enumFailed = editor.validateValue(enumPath, "ant");
    ConfigEditor.ValidationResult listItemFailed = editor.validateListItemValue(listPath, 123);
    ConfigEditor.ValidationResult unknownPathOk =
        editor.validateValue(path(key("unknown"), key("value")), "v");

    assertThat(integerOk.ok()).isTrue();
    assertThat(integerFailed.ok()).isFalse();
    assertThat(integerFailed.message())
        .contains(MessageSource.getMessage("tui.config_editor.validation.integer_required"));
    assertThat(minBoundFailed.ok()).isFalse();
    assertThat(minBoundFailed.message()).contains(">=");
    assertThat(enumOk.ok()).isTrue();
    assertThat(enumFailed.ok()).isFalse();
    assertThat(enumFailed.message())
        .contains(MessageSource.getMessage("tui.config_editor.validation.enum_options", ""));
    assertThat(listItemFailed.ok()).isFalse();
    assertThat(listItemFailed.message())
        .contains(MessageSource.getMessage("tui.config_editor.validation.type_mismatch"));
    assertThat(unknownPathOk.ok()).isTrue();
  }

  @Test
  void schemaOverrideAndMetadataFallbackDriveClassification() throws IOException {
    Path emptySchema = writeConfig("empty-schema.json", "{\"type\":\"object\"}");
    ConfigSchemaIndex emptyIndex = ConfigSchemaIndex.forSchemaPath(emptySchema);

    ConfigEditor withMetadata =
        ConfigEditor.load(tempDir.resolve("with-metadata.json"), emptyIndex, true);
    ConfigEditor withoutMetadata =
        ConfigEditor.load(tempDir.resolve("without-metadata.json"), emptyIndex, false);

    assertThat(withMetadata.classifyValue(path(key("llm"), key("provider")), "openai"))
        .isEqualTo(ConfigEditor.ValueType.ENUM);
    assertThat(
            withMetadata.classifyValue(
                path(key("selection_rules"), key("exclude_annotations")), null))
        .isEqualTo(ConfigEditor.ValueType.LIST);
    assertThat(
            withMetadata.classifyValue(
                path(key("selection_rules"), key("exclude_annotations"), idx(0)), "Tag"))
        .isEqualTo(ConfigEditor.ValueType.STRING);
    assertThat(
            withMetadata.classifyValue(
                path(key("analysis"), key("spoon"), key("no_classpath")), true))
        .isEqualTo(ConfigEditor.ValueType.BOOLEAN);
    assertThat(withMetadata.classifyValue(path(key("custom"), key("object")), Map.of("k", "v")))
        .isEqualTo(ConfigEditor.ValueType.OBJECT);
    assertThat(withMetadata.classifyValue(path(key("custom"), key("list")), List.of(1, 2)))
        .isEqualTo(ConfigEditor.ValueType.LIST);
    assertThat(withoutMetadata.classifyValue(path(key("custom"), key("scalar")), 42))
        .isEqualTo(ConfigEditor.ValueType.STRING);
  }

  @Test
  void formattingAndSecretMaskingBehaviorsCoverMultipleBranches() {
    ConfigEditor editor = ConfigEditor.load(tempDir.resolve("formatting.json"));
    List<ConfigEditor.PathSegment> secretPath = path(key("llm"), key("api_key"));
    List<ConfigEditor.PathSegment> normalPath = path(key("project"), key("id"));

    assertThat(editor.isSecretPath(secretPath)).isTrue();
    assertThat(editor.isSecretPath(normalPath)).isFalse();
    assertThat(editor.summarizeValue(secretPath, "secret-token", 80)).isEqualTo("****");
    assertThat(editor.summarizeValue(secretPath, "secret-token", 3)).isEqualTo("...");
    assertThat(editor.summarizeValue(normalPath, "line1\nline2", 80)).isEqualTo("\"line1 line2\"");
    assertThat(editor.summarizeValue("abcdef", 4)).endsWith("...");
    assertThat(editor.formatScalar(null))
        .isEqualTo(MessageSource.getMessage("tui.config_editor.value.null"));
    assertThat(editor.formatScalar("demo")).isEqualTo("demo");
    assertThat(editor.formatScalarForDisplay(secretPath, "x")).isEqualTo("****");
    assertThat(editor.formatScalarForEdit(secretPath, "x")).isEmpty();
    assertThat(editor.formatScalarForDisplay(normalPath, 123)).isEqualTo("123");
    assertThat(editor.formatPathForDisplay(path(key("project"), idx(0), key("id"))))
        .isEqualTo("project[0] > id");
    assertThat(editor.formatPathForDisplay(List.of()))
        .isEqualTo(MessageSource.getMessage("tui.config_editor.path.root"));
  }

  @Test
  void saveDiscardAndDirtyTrackingRoundTripToDisk() throws IOException {
    Path configPath = tempDir.resolve("save/config.json");
    ConfigEditor editor = ConfigEditor.load(configPath);
    List<ConfigEditor.PathSegment> projectId = path(key("project"), key("id"));

    editor.updateDirtyForPath(List.of());
    editor.setValueWithCreate(projectId, "demo");
    editor.updateDirtyForPath(projectId);

    assertThat(editor.isDirty()).isTrue();
    assertThat(editor.getDirtyKeys()).contains("project.id");
    assertThat(editor.save()).isTrue();
    assertThat(Files.exists(configPath)).isTrue();
    assertThat(editor.isDirty()).isFalse();

    editor.setValueWithCreate(projectId, "changed");
    editor.updateDirtyForPath(projectId);
    assertThat(editor.isDirty()).isTrue();

    editor.discard();
    assertThat(editor.isDirty()).isFalse();
    assertThat(editor.getValue(projectId)).isEqualTo("demo");
    assertThat(editor.toJson()).contains("\"project\"");
  }

  @Test
  void enumOptionsCanMergeCustomValues() {
    ConfigEditor editor = ConfigEditor.load(tempDir.resolve("enum-options.json"));
    List<ConfigEditor.PathSegment> enumPath = path(key("project"), key("build_tool"));

    List<String> options = editor.getEnumOptions(enumPath, "ant");

    assertThat(options).contains("gradle", "maven", "ant");
  }

  private Path writeConfig(String fileName, String json) throws IOException {
    Path path = tempDir.resolve(fileName);
    Files.writeString(path, json);
    return path;
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
}
