package com.craftsmanbro.fulcraft.ui.tui.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigEditorBranchCoverageTest {

  @TempDir Path tempDir;

  @Test
  void loadHandlesBlankNullLiteralAndUnreadablePath() throws IOException {
    Path blank = writeFile("blank.json", "   \n");
    ConfigEditor blankEditor = ConfigEditor.load(blank);

    assertThat(blankEditor.getLoadError()).isNull();
    assertThat(blankEditor.toJson()).contains("{");

    Path nullLiteral = writeFile("null-literal.json", "null");
    ConfigEditor nullLiteralEditor = ConfigEditor.load(nullLiteral);

    assertThat(nullLiteralEditor.getLoadError()).isNull();
    assertThat(nullLiteralEditor.toJson()).contains("{");

    Path directory = tempDir.resolve("as-directory");
    Files.createDirectories(directory);
    ConfigEditor directoryEditor = ConfigEditor.load(directory);

    assertThat(directoryEditor.getLoadError())
        .contains(MessageSource.getMessage("tui.config_editor.error.load_failed", ""));
  }

  @Test
  void classifyValueFallsBackToRuntimeTypesWhenSchemaTypeIsUndetermined() throws IOException {
    Path schemaPath =
        writeFile(
            "runtime-types-schema.json",
            """
            {
              "type": "object",
              "properties": {
                "arr": {
                  "type": "array",
                  "items": {
                    "type": "array",
                    "items": { "type": "string" }
                  }
                }
              }
            }
            """);
    ConfigSchemaIndex schema = ConfigSchemaIndex.forSchemaPath(schemaPath);
    ConfigEditor editor = ConfigEditor.load(tempDir.resolve("runtime-types.json"), schema, false);
    List<ConfigEditor.PathSegment> arrayItemPath = path(key("arr"), idx(0));

    assertThat(editor.classifyValue(arrayItemPath, null)).isEqualTo(ConfigEditor.ValueType.NULL);
    assertThat(editor.classifyValue(arrayItemPath, true)).isEqualTo(ConfigEditor.ValueType.BOOLEAN);
    assertThat(editor.classifyValue(arrayItemPath, 10)).isEqualTo(ConfigEditor.ValueType.INTEGER);
    assertThat(editor.classifyValue(arrayItemPath, 1.5d)).isEqualTo(ConfigEditor.ValueType.FLOAT);
    assertThat(editor.classifyValue(arrayItemPath, Map.of("k", "v")))
        .isEqualTo(ConfigEditor.ValueType.OBJECT);
    assertThat(editor.classifyValue(arrayItemPath, List.of("x")))
        .isEqualTo(ConfigEditor.ValueType.LIST);
    assertThat(editor.classifyValue(arrayItemPath, "text"))
        .isEqualTo(ConfigEditor.ValueType.STRING);
    assertThat(editor.classifyValue(arrayItemPath, new Object()))
        .isEqualTo(ConfigEditor.ValueType.UNKNOWN);
  }

  @Test
  void validateValueCoversNullAndCompositeTypeBranches() throws IOException {
    Path schemaPath =
        writeFile(
            "validate-branches-schema.json",
            """
            {
              "type": "object",
              "properties": {
                "nullable": { "type": ["string", "null"] },
                "bool": { "type": "boolean" },
                "num": { "type": "number", "maximum": 5 },
                "obj": { "type": "object" },
                "arr": { "type": "array" },
                "int": { "type": "integer", "minimum": 1 }
              }
            }
            """);
    ConfigSchemaIndex schema = ConfigSchemaIndex.forSchemaPath(schemaPath);
    ConfigEditor editor =
        ConfigEditor.load(tempDir.resolve("validate-branches.json"), schema, false);

    assertThat(editor.validateValue(path(key("nullable")), null).ok()).isTrue();
    assertThat(editor.validateValue(path(key("bool")), true).ok()).isTrue();
    assertThat(editor.validateValue(path(key("num")), 6).ok()).isFalse();
    assertThat(editor.validateValue(path(key("num")), 6).message()).contains("<=");
    assertThat(editor.validateValue(path(key("obj")), Map.of("k", 1)).ok()).isTrue();
    assertThat(editor.validateValue(path(key("arr")), List.of(1)).ok()).isTrue();
    assertThat(editor.validateValue(path(key("int")), 1.5d).ok()).isFalse();
    assertThat(editor.validateValue(path(key("int")), 2).ok()).isTrue();
    assertThat(editor.validateValue(path(key("int")), "oops").ok()).isFalse();
  }

  @Test
  void setAndAppendSupportIndexedContainerCreationPaths() {
    ConfigEditor editor = ConfigEditor.load(tempDir.resolve("index-paths.json"));
    List<ConfigEditor.PathSegment> nestedPath = path(key("matrix"), idx(1), idx(0), key("name"));

    assertThat(editor.setValueWithCreate(nestedPath, "node")).isTrue();
    assertThat(editor.pathExists(nestedPath)).isTrue();
    assertThat(editor.getValue(nestedPath)).isEqualTo("node");

    assertThat(editor.setValue(path(key("matrix"), idx(9)), "invalid")).isFalse();
    assertThat(editor.appendListValueWithCreate(path(key("lists"), idx(2)), "v1")).isTrue();
    assertThat(editor.appendListValueWithCreate(path(key("lists"), idx(2)), "v2")).isTrue();
    assertThat(editor.appendListValueWithCreate(path(key("lists"), idx(-1)), "bad")).isFalse();
    assertThat(editor.getValue(path(key("lists"), idx(2)))).isEqualTo(List.of("v1", "v2"));
  }

  @Test
  void metadataEnumOptionsAndItemListingCoverAdditionalBranches() throws Exception {
    Path emptySchema = writeFile("empty-schema.json", "{\"type\":\"object\"}");
    ConfigEditor editor =
        ConfigEditor.load(
            tempDir.resolve("metadata-branches.json"),
            ConfigSchemaIndex.forSchemaPath(emptySchema),
            true);

    List<String> optionsWithKnownValue =
        editor.getEnumOptions(path(key("llm"), key("provider")), "OPENAI");
    List<String> optionsWithCustomValue =
        editor.getEnumOptions(path(key("llm"), key("provider")), "custom-provider");

    assertThat(optionsWithKnownValue).contains("openai").doesNotContain("OPENAI");
    assertThat(optionsWithCustomValue).contains("custom-provider");

    Map<Object, Object> mixed = new LinkedHashMap<>();
    mixed.put(1, "ignored");
    mixed.put("ok", "value");
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("mixed", mixed);
    setRoot(editor, root);

    List<ConfigEditor.ConfigItem> items = editor.getItemsForPath(path(key("mixed")));
    assertThat(items).extracting(ConfigEditor.ConfigItem::label).containsExactly("ok");

    editor.updateDirtyForPath(List.of(idx(0)));
    assertThat(editor.getDirtyKeys()).isEmpty();
    assertThat(editor.isSecretPath(List.of(key("")))).isFalse();
  }

  @Test
  void saveAndToJsonHandleNonMapRootAndSerializationFailure() throws Exception {
    ConfigEditor editor = ConfigEditor.load(tempDir.resolve("save-branches.json"));

    setRoot(editor, new ArrayList<>());
    assertThat(editor.toJson()).isEmpty();
    assertThat(editor.save()).isFalse();
    assertThat(editor.getAdvancedTopLevelKeys()).isEmpty();
    assertThat(editor.getAdvancedTopLevelItems()).isEmpty();

    Map<String, Object> cyclic = new LinkedHashMap<>();
    cyclic.put("self", cyclic);
    setRoot(editor, cyclic);
    assertThat(editor.toJson()).isEqualTo("{}");
  }

  @Test
  void traversalAndListCreationBranchesHandleContainerMismatchCases() throws Exception {
    ConfigEditor editor = ConfigEditor.load(tempDir.resolve("traversal-branches.json"));

    Map<String, Object> root = new LinkedHashMap<>();
    root.put("arr", new ArrayList<>(List.of(new LinkedHashMap<>(), "scalar")));
    root.put("objectChild", new LinkedHashMap<>(Map.of("node", "scalar")));
    setRoot(editor, root);

    assertThat(editor.setValue(path(key("arr"), idx(5), key("name")), "value")).isFalse();
    assertThat(editor.setValue(path(key("objectChild"), idx(0), key("name")), "value")).isFalse();
    assertThat(editor.setValue(path(key("arr"), key("x"), key("y")), "value")).isFalse();
    assertThat(editor.setValueWithCreate(path(key("arr"), idx(1), key("name")), "value")).isFalse();
    assertThat(editor.setValueWithCreate(path(key("arr"), key("x"), key("y")), "value")).isFalse();
    assertThat(editor.setValueWithCreate(path(idx(0)), "value")).isFalse();
    assertThat(editor.appendListValueWithCreate(path(key("objectChild"), key("node")), "x"))
        .isFalse();
    assertThat(editor.appendListValueWithCreate(path(key("arr"), idx(1)), "x")).isFalse();
  }

  @Test
  void privateContainerHelpersCoverNullAndTypeBranches() throws Exception {
    assertThat(invokeCreateContainerFor(idx(0))).isInstanceOf(List.class);
    assertThat(invokeCreateContainerFor(null)).isInstanceOf(Map.class);

    assertThat(invokeIsContainerFor(null, new Object())).isFalse();
    assertThat(invokeIsContainerFor(key("k"), null)).isFalse();
    assertThat(invokeIsContainerFor(idx(0), new ArrayList<>())).isTrue();
    assertThat(invokeIsContainerFor(idx(0), new LinkedHashMap<>())).isFalse();
    assertThat(invokeIsContainerFor(key("k"), new LinkedHashMap<>())).isTrue();
    assertThat(invokeIsContainerFor(key("k"), new ArrayList<>())).isFalse();
  }

  private Path writeFile(String fileName, String content) throws IOException {
    Path path = tempDir.resolve(fileName);
    Files.writeString(path, content);
    return path;
  }

  private static ConfigEditor.PathSegment key(String value) {
    return ConfigEditor.PathSegment.key(value);
  }

  private static ConfigEditor.PathSegment idx(int value) {
    return ConfigEditor.PathSegment.index(value);
  }

  private static List<ConfigEditor.PathSegment> path(ConfigEditor.PathSegment... segments) {
    return List.of(segments);
  }

  private static void setRoot(ConfigEditor editor, Object value) throws Exception {
    Field field = ConfigEditor.class.getDeclaredField("root");
    field.setAccessible(true);
    field.set(editor, value);
  }

  private static Object invokeCreateContainerFor(ConfigEditor.PathSegment next) throws Exception {
    Method method =
        ConfigEditor.class.getDeclaredMethod("createContainerFor", ConfigEditor.PathSegment.class);
    method.setAccessible(true);
    return method.invoke(null, next);
  }

  private static boolean invokeIsContainerFor(ConfigEditor.PathSegment next, Object candidate)
      throws Exception {
    Method method =
        ConfigEditor.class.getDeclaredMethod(
            "isContainerFor", ConfigEditor.PathSegment.class, Object.class);
    method.setAccessible(true);
    return (boolean) method.invoke(null, next, candidate);
  }
}
