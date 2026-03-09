package com.craftsmanbro.fulcraft.config.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.config.InvalidConfigurationException;
import com.craftsmanbro.fulcraft.kernel.plugin.api.PluginConfig;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class PluginConfigLoaderTest {

  @TempDir Path tempDir;

  @Test
  void load_returnsEmptyConfigWhenMissing() {
    PluginConfigLoader loader = new PluginConfigLoader(tempDir);

    PluginConfig config = loader.load("demo");

    assertThat(config.configExists()).isFalse();
    assertThat(config.getOptionalString("missing")).isEmpty();
  }

  @Test
  void load_readsConfigValues() throws Exception {
    Files.createDirectories(pluginConfigDir("demo"));
    Files.writeString(
        pluginConfigPath("demo"),
        """
            {
              "foo": "bar",
              "nested": { "value": 42 },
              "list": ["one", "two"]
            }
            """);

    PluginConfigLoader loader = new PluginConfigLoader(tempDir);
    PluginConfig config = loader.load("demo");

    assertThat(config.configExists()).isTrue();
    assertThat(config.getOptionalValue("foo", String.class)).contains("bar");
    assertThat(config.getOptionalValue("nested.value", Integer.class)).contains(42);
    assertThat(config.getOptionalValue("list[0]", String.class)).contains("one");
    assertThat(config.getOptionalValue("list[1]", String.class)).contains("two");
  }

  @Test
  void load_readsLegacyConfigWhenNestedConfigDoesNotExist() throws Exception {
    Path pluginDir = pluginDir("demo");
    Files.createDirectories(pluginDir);
    Files.writeString(legacyPluginConfigPath("demo"), "{ \"foo\": \"legacy\" }");

    PluginConfigLoader loader = new PluginConfigLoader(tempDir);
    PluginConfig config = loader.load("demo");

    assertThat(config.configExists()).isTrue();
    assertThat(config.getConfigPath()).isEqualTo(legacyPluginConfigPath("demo"));
    assertThat(config.getOptionalString("foo")).contains("legacy");
  }

  @Test
  void load_prefersNestedConfigOverLegacyConfig() throws Exception {
    Path pluginDir = pluginDir("demo");
    Files.createDirectories(pluginDir);
    Files.createDirectories(pluginConfigDir("demo"));
    Files.writeString(legacyPluginConfigPath("demo"), "{ \"foo\": \"legacy\" }");
    Files.writeString(pluginConfigPath("demo"), "{ \"foo\": \"nested\" }");

    PluginConfigLoader loader = new PluginConfigLoader(tempDir);
    PluginConfig config = loader.load("demo");

    assertThat(config.configExists()).isTrue();
    assertThat(config.getConfigPath()).isEqualTo(pluginConfigPath("demo"));
    assertThat(config.getOptionalString("foo")).contains("nested");
  }

  @Test
  void load_usesLegacySchemaWhenNestedSchemaIsMissing() throws Exception {
    Path pluginDir = pluginDir("demo");
    Files.createDirectories(pluginDir);
    Files.createDirectories(pluginConfigDir("demo"));
    Files.writeString(pluginConfigPath("demo"), "{ \"foo\": \"bar\" }");
    Files.writeString(
        legacyPluginSchemaPath("demo"),
        """
            {
              "type": "object",
              "properties": {
                "foo": { "type": "string" }
              },
              "required": ["foo"],
              "additionalProperties": false
            }
            """);

    PluginConfigLoader loader = new PluginConfigLoader(tempDir);
    PluginConfig config = loader.load("demo");

    assertThat(config.configExists()).isTrue();
    assertThat(config.getSchemaPath()).isEqualTo(legacyPluginSchemaPath("demo"));
    assertThat(config.getOptionalString("foo")).contains("bar");
  }

  @Test
  void load_throwsWhenPluginIdIsBlank() {
    PluginConfigLoader loader = new PluginConfigLoader(tempDir);

    assertThatThrownBy(() -> loader.load("  "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("pluginId must not be blank");
  }

  @Test
  void load_throwsWhenPluginIdIsNull() {
    PluginConfigLoader loader = new PluginConfigLoader(tempDir);

    assertThatThrownBy(() -> loader.load(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("pluginId must not be blank");
  }

  @Test
  void load_returnsEmptyConfigWhenConfigFileIsBlank() throws Exception {
    Files.createDirectories(pluginConfigDir("demo"));
    Files.writeString(pluginConfigPath("demo"), "   ");

    PluginConfigLoader loader = new PluginConfigLoader(tempDir);
    PluginConfig config = loader.load("demo");

    assertThat(config.configExists()).isTrue();
    assertThat(config.getOptionalString("foo")).isEmpty();
  }

  @Test
  void load_returnsEmptyConfigWhenConfigFileIsJsonNull() throws Exception {
    Files.createDirectories(pluginConfigDir("demo"));
    Files.writeString(pluginConfigPath("demo"), "null");

    PluginConfigLoader loader = new PluginConfigLoader(tempDir);
    PluginConfig config = loader.load("demo");

    assertThat(config.configExists()).isTrue();
    assertThat(config.getOptionalString("foo")).isEmpty();
  }

  @Test
  void load_throwsWhenConfigRootIsNotObject() throws Exception {
    Files.createDirectories(pluginConfigDir("demo"));
    Files.writeString(pluginConfigPath("demo"), "[1, 2, 3]");

    PluginConfigLoader loader = new PluginConfigLoader(tempDir);

    assertThatThrownBy(() -> loader.load("demo"))
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining("Plugin config root must be an object");
  }

  @Test
  void load_throwsWhenConfigJsonIsMalformed() throws Exception {
    Files.createDirectories(pluginConfigDir("demo"));
    Files.writeString(pluginConfigPath("demo"), "{");

    PluginConfigLoader loader = new PluginConfigLoader(tempDir);

    assertThatThrownBy(() -> loader.load("demo"))
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining("Failed to parse plugin config");
  }

  @Test
  void load_readsConfigWhenSchemaValidationPasses() throws Exception {
    Files.createDirectories(pluginConfigDir("demo"));
    Files.writeString(
        pluginConfigPath("demo"),
        """
            {
              "foo": "bar"
            }
            """);
    Files.writeString(
        pluginSchemaPath("demo"),
        """
            {
              "type": "object",
              "properties": {
                "foo": { "type": "string" }
              },
              "required": ["foo"],
              "additionalProperties": false
            }
            """);

    PluginConfigLoader loader = new PluginConfigLoader(tempDir);
    PluginConfig config = loader.load("demo");

    assertThat(config.configExists()).isTrue();
    assertThat(config.getOptionalString("foo")).contains("bar");
  }

  @Test
  void load_skipsNullValuesInObjectAndList() throws Exception {
    Files.createDirectories(pluginConfigDir("demo"));
    Files.writeString(
        pluginConfigPath("demo"),
        """
            {
              "nullable": null,
              "nested": { "value": null },
              "list": [null, "ok"]
            }
            """);

    PluginConfigLoader loader = new PluginConfigLoader(tempDir);
    PluginConfig config = loader.load("demo");

    assertThat(config.getOptionalString("nullable")).isEmpty();
    assertThat(config.getOptionalString("nested.value")).isEmpty();
    assertThat(config.getOptionalString("list[0]")).isEmpty();
    assertThat(config.getOptionalString("list[1]")).contains("ok");
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void load_throwsWhenConfigFileIsNotReadable() throws Exception {
    Files.createDirectories(pluginConfigDir("demo"));
    Path configPath = pluginConfigPath("demo");
    Files.writeString(configPath, "{ \"foo\": \"bar\" }");
    PosixFileAttributeView view =
        Files.getFileAttributeView(configPath, PosixFileAttributeView.class);
    if (view == null) {
      return;
    }

    Set<PosixFilePermission> noPermissions = EnumSet.noneOf(PosixFilePermission.class);
    Set<PosixFilePermission> ownerReadWrite =
        EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

    try {
      Files.setPosixFilePermissions(configPath, noPermissions);

      PluginConfigLoader loader = new PluginConfigLoader(tempDir);
      assertThatThrownBy(() -> loader.load("demo"))
          .isInstanceOf(InvalidConfigurationException.class)
          .hasMessageContaining("Failed to read plugin config");
    } finally {
      Files.setPosixFilePermissions(configPath, ownerReadWrite);
    }
  }

  @Test
  void load_exposesPluginConfigSourceProperties() throws Exception {
    Files.createDirectories(pluginConfigDir("demo"));
    Files.writeString(pluginConfigPath("demo"), "{ \"foo\": \"bar\" }");

    PluginConfigLoader loader = new PluginConfigLoader(tempDir);
    PluginConfig pluginConfig = loader.load("demo");
    Config config = pluginConfig.getConfig();

    org.eclipse.microprofile.config.spi.ConfigSource source =
        findConfigSource(config, "plugin:demo");
    assertThat(source.getProperties()).containsEntry("foo", "bar");
    assertThat(source.getPropertyNames()).contains("foo");
  }

  @Test
  void load_throwsWhenSchemaValidationFails() throws Exception {
    Files.createDirectories(pluginConfigDir("demo"));
    Files.writeString(
        pluginSchemaPath("demo"),
        """
            {
              "type": "object",
              "properties": {
                "foo": { "type": "string" }
              },
              "required": ["foo"],
              "additionalProperties": false
            }
            """);

    PluginConfigLoader loader = new PluginConfigLoader(tempDir);

    assertThatThrownBy(() -> loader.load("demo"))
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining("Plugin config validation failed");
  }

  @Test
  void flattenMap_skipsNullMapKeysAndScalarWithoutPrefix() throws Exception {
    PluginConfigLoader loader = new PluginConfigLoader(tempDir);
    Method flattenMap =
        PluginConfigLoader.class.getDeclaredMethod(
            "flattenMap", String.class, Object.class, Map.class);
    flattenMap.setAccessible(true);

    Map<Object, Object> input = new LinkedHashMap<>();
    input.put(null, "ignored");
    input.put("kept", "value");
    Map<String, String> flattened = new LinkedHashMap<>();

    flattenMap.invoke(loader, "", input, flattened);
    flattenMap.invoke(loader, "", "scalar", flattened);

    assertThat(flattened).containsEntry("kept", "value");
    assertThat(flattened).doesNotContainKey("null");
  }

  @Test
  void readJson_returnsEmptyMapWhenPathIsNull() throws Exception {
    PluginConfigLoader loader = new PluginConfigLoader(tempDir);
    Method readJson = PluginConfigLoader.class.getDeclaredMethod("readJson", Path.class);
    readJson.setAccessible(true);

    Object result = readJson.invoke(loader, new Object[] {null});
    assertThat(result).isInstanceOf(Map.class);
    assertThat((Map<?, ?>) result).isEmpty();
  }

  @Test
  void validateWithSchema_returnsWhenSchemaPathIsNull() throws Exception {
    PluginConfigLoader loader = new PluginConfigLoader(tempDir);
    Method validateWithSchema =
        PluginConfigLoader.class.getDeclaredMethod(
            "validateWithSchema", Map.class, Path.class, Path.class);
    validateWithSchema.setAccessible(true);

    validateWithSchema.invoke(loader, Map.of("foo", "bar"), null, tempDir.resolve("config.json"));
  }

  private Path pluginDir(String pluginId) {
    return tempDir.resolve(".ful").resolve("plugins").resolve(pluginId);
  }

  private Path pluginConfigDir(String pluginId) {
    return pluginDir(pluginId).resolve("config");
  }

  private Path pluginConfigPath(String pluginId) {
    return pluginConfigDir(pluginId).resolve("config.json");
  }

  private Path pluginSchemaPath(String pluginId) {
    return pluginConfigDir(pluginId).resolve("schema.json");
  }

  private Path legacyPluginConfigPath(String pluginId) {
    return pluginDir(pluginId).resolve("config.json");
  }

  private Path legacyPluginSchemaPath(String pluginId) {
    return pluginDir(pluginId).resolve("schema.json");
  }

  private org.eclipse.microprofile.config.spi.ConfigSource findConfigSource(
      Config config, String sourceName) {
    for (org.eclipse.microprofile.config.spi.ConfigSource source : config.getConfigSources()) {
      if (sourceName.equals(source.getName())) {
        return source;
      }
    }
    throw new IllegalStateException("ConfigSource not found: " + sourceName);
  }
}
