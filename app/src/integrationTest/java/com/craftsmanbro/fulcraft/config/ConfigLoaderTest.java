package com.craftsmanbro.fulcraft.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.infrastructure.config.impl.ConfigLoaderImpl;
import com.craftsmanbro.fulcraft.infrastructure.config.validator.ConfigValidator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigLoaderTest {

  @TempDir java.nio.file.Path tempDir;

  @Test
  void failsWhenExplicitConfigMissing() {
    ConfigLoaderImpl loader = new ConfigLoaderImpl(new ConfigValidator());
    Path missing = tempDir.resolve("nope.json");

    assertThatThrownBy(() -> loader.load(missing))
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining("Configuration file not found");
  }

  @Test
  void loadsAndValidatesConfig() throws Exception {
    String json = minimumValidConfigJson("demo");
    Path file = tempDir.resolve("config.json");
    Files.writeString(file, json);

    ConfigLoaderImpl loader = new ConfigLoaderImpl(new ConfigValidator());
    Config config = loader.load(file);

    assertThat(config).isNotNull();
    assertThat(config.getProject().getId()).isEqualTo("demo");
    assertThat(config.getLlm().getProvider()).isEqualTo("mock");
    if (config.getExecution() != null) {
      assertThat(config.getExecution().getLogsRoot())
          .as("Validator should NOT inject default logs_root")
          .isNull();
    }
  }

  @Test
  void resolveConfigPathPrefersExplicitPathAndHandlesNull() throws Exception {
    ConfigLoaderImpl loader = new ConfigLoaderImpl(new ConfigValidator());
    Path explicit = tempDir.resolve("explicit.json");
    Files.writeString(explicit, "{}");

    assertThat(loader.resolveConfigPath(explicit)).isEqualTo(explicit);
    assertThat(loader.resolveConfigPath(null)).isEqualTo(ConfigLoaderImpl.DEFAULT_CONFIG_FILE);

    Path missing = tempDir.resolve("missing.json");
    Path expected =
        Files.exists(ConfigLoaderImpl.FALLBACK_CONFIG_FILE)
            ? ConfigLoaderImpl.FALLBACK_CONFIG_FILE
            : missing;
    assertThat(loader.resolveConfigPath(missing)).isEqualTo(expected);
  }

  @Test
  void loadWithOverridesAppliesNonNullOverrides() throws Exception {
    Path file = tempDir.resolve("config-with-overrides.json");
    Files.writeString(file, minimumValidConfigJson("demo"));

    ConfigLoaderImpl loader = new ConfigLoaderImpl(new ConfigValidator());
    Config loaded =
        loader.load(
            file,
            null,
            config -> config.getProject().setId("override-id"),
            config -> config.getLlm().setProvider("mock"));

    assertThat(loaded.getProject().getId()).isEqualTo("override-id");
  }

  @Test
  void loadWithNullOverridesArrayStillLoadsConfig() throws Exception {
    Path file = tempDir.resolve("config-null-overrides.json");
    Files.writeString(file, minimumValidConfigJson("demo"));

    ConfigLoaderImpl loader = new ConfigLoaderImpl(new ConfigValidator());
    Config loaded = loader.load(file, (ConfigOverride[]) null);

    assertThat(loaded.getProject().getId()).isEqualTo("demo");
  }

  @Test
  void loadWithOverridesWrapsValidationErrors() throws Exception {
    Path file = tempDir.resolve("config-invalid-after-override.json");
    Files.writeString(file, minimumValidConfigJson("demo"));

    ConfigLoaderImpl loader = new ConfigLoaderImpl(new ConfigValidator());

    assertThatThrownBy(() -> loader.load(file, config -> config.getLlm().setProvider("local")))
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining("Configuration validation failed after applying overrides");
  }

  @Test
  void returnsDefaultConfigWhenFallbackPathIsMissing() {
    Assumptions.assumeFalse(Files.exists(ConfigLoaderImpl.FALLBACK_CONFIG_FILE));
    ConfigLoaderImpl loader = new ConfigLoaderImpl(new ConfigValidator());

    Config loaded = loader.load(ConfigLoaderImpl.FALLBACK_CONFIG_FILE);

    assertThat(loaded.getProject().getId()).isEqualTo("default");
  }

  @Test
  void loadsAppMetadataFromConfig() throws Exception {
    String json =
        """
        {
          "AppName": "demo-app",
          "version": "1.2.3",
          "project": { "id": "demo" },
          "selection_rules": {
            "class_min_loc": 1,
            "class_min_method_count": 1,
            "method_min_loc": 1,
            "method_max_loc": 2,
            "max_methods_per_class": 1,
            "exclude_getters_setters": true
          },
          "llm": { "provider": "mock" }
        }
        """;
    Path file = tempDir.resolve("config-with-app-metadata.json");
    Files.writeString(file, json);

    ConfigLoaderImpl loader = new ConfigLoaderImpl(new ConfigValidator());
    Config config = loader.load(file);

    assertThat(config.getAppName()).isEqualTo("demo-app");
    assertThat(config.getVersion()).isEqualTo("1.2.3");
  }

  @Test
  void wrapsJsonParseErrors() throws Exception {
    String json = "{ \"project\": }";
    Path file = tempDir.resolve("broken.json");
    Files.writeString(file, json);

    ConfigLoaderImpl loader = new ConfigLoaderImpl(new ConfigValidator());

    assertThatThrownBy(() -> loader.load(file))
        .isInstanceOf(InvalidConfigurationException.class)
        .extracting(Throwable::getMessage)
        .asString()
        .matches(".*(Failed to parse configuration file|Failed to validate configuration).*");
  }

  @Test
  void wrapsValidationErrorsWithContext() throws Exception {
    String json =
        """
        {
          "project": { "id": "demo" },
          "selection_rules": {
            "class_min_loc": 1,
            "class_min_method_count": 1,
            "method_min_loc": 1,
            "method_max_loc": 2,
            "max_methods_per_class": 1,
            "exclude_getters_setters": true
          },
          "llm": {
            "provider": "local",
            "url": "",
            "model_name": ""
          }
        }
        """;
    Path file = tempDir.resolve("invalid.json");
    Files.writeString(file, json);

    ConfigLoaderImpl loader = new ConfigLoaderImpl(new ConfigValidator());

    assertThatThrownBy(() -> loader.load(file))
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining("Configuration validation failed");
  }

  @Test
  void failsWhenSchemaVersionIsUnknown() throws Exception {
    String json =
        """
        {
          "schema_version": 9,
          "project": { "id": "demo" },
          "selection_rules": {
            "class_min_loc": 1,
            "class_min_method_count": 1,
            "method_min_loc": 1,
            "method_max_loc": 2,
            "max_methods_per_class": 1,
            "exclude_getters_setters": true
          },
          "llm": { "provider": "mock" }
        }
        """;
    Path file = tempDir.resolve("unknown-schema.json");
    Files.writeString(file, json);

    ConfigLoaderImpl loader = new ConfigLoaderImpl(new ConfigValidator());

    assertThatThrownBy(() -> loader.load(file))
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining("Schema file not found");
  }

  @Test
  void usesSchemaVersionToSelectSchema() throws Exception {
    String json =
        """
        {
          "schema_version": 2,
          "project": { "id": "demo" },
          "selection_rules": {
            "class_min_loc": 1,
            "class_min_method_count": 1,
            "method_min_loc": 1,
            "method_max_loc": 2,
            "max_methods_per_class": 1,
            "exclude_getters_setters": true
          },
          "llm": { "provider": "mock" }
        }
        """;
    Path file = tempDir.resolve("schema-v2.json");
    Files.writeString(file, json);

    ConfigLoaderImpl loader = new ConfigLoaderImpl(new ConfigValidator());

    assertThatThrownBy(() -> loader.load(file))
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining("config-schema-v2.json")
        .hasMessageContaining("new_required");
  }

  @Test
  void schemaValidationIncludesFieldDetails() throws Exception {
    String json =
        """
        {
          "schema_version": 1,
          "project": { "id": 123 },
          "selection_rules": {
            "class_min_loc": 1,
            "class_min_method_count": 1,
            "method_min_loc": 1,
            "method_max_loc": 2,
            "max_methods_per_class": 1,
            "exclude_getters_setters": true
          },
          "llm": { "provider": "mock" }
        }
        """;
    Path file = tempDir.resolve("schema-invalid.json");
    Files.writeString(file, json);

    ConfigLoaderImpl loader = new ConfigLoaderImpl(new ConfigValidator());

    assertThatThrownBy(() -> loader.load(file))
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining("schema/config-schema-v1.json")
        .extracting(Throwable::getMessage)
        .asString()
        .containsIgnoringCase("project");
  }

  @Test
  void substitutesSystemPropertiesInJson() throws Exception {
    String previous = System.getProperty("PROJECT_ID");
    System.setProperty("PROJECT_ID", "demo");
    try {
      String json =
          """
          {
            "project": { "id": "${PROJECT_ID}" },
            "selection_rules": {
              "class_min_loc": 1,
              "class_min_method_count": 1,
              "method_min_loc": 1,
              "method_max_loc": 2,
              "max_methods_per_class": 1,
              "exclude_getters_setters": true
            },
            "llm": { "provider": "mock" }
          }
          """;
      Path file = tempDir.resolve("config-subst.json");
      Files.writeString(file, json);

      ConfigLoaderImpl loader = new ConfigLoaderImpl(new ConfigValidator());
      Config config = loader.load(file);

      assertThat(config.getProject().getId()).isEqualTo("demo");
    } finally {
      if (previous == null) {
        System.clearProperty("PROJECT_ID");
      } else {
        System.setProperty("PROJECT_ID", previous);
      }
    }
  }

  @Test
  void keepsPlaceholdersWhenNoVariableValueExists() throws Exception {
    Path file = tempDir.resolve("config-subst-missing.json");
    Files.writeString(file, minimumValidConfigJson("${MISSING_PROJECT_ID}"));

    ConfigLoaderImpl loader = new ConfigLoaderImpl(new ConfigValidator());
    Config config = loader.load(file);

    assertThat(config.getProject().getId()).isEqualTo("${MISSING_PROJECT_ID}");
  }

  @Test
  void wrapsIoErrorsWhenReadingConfig() throws Exception {
    Path file = tempDir.resolve("io-error.json");
    Files.writeString(file, "{}");

    ConfigLoaderImpl loader =
        new ConfigLoaderImpl(new ConfigValidator()) {
          @Override
          protected String readFile(Path path) throws IOException {
            throw new IOException("boom");
          }
        };

    assertThatThrownBy(() -> loader.load(file))
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining("Failed to read configuration file");
  }

  @Test
  void rejectsNonObjectJsonRoot() throws Exception {
    Path file = tempDir.resolve("array-root.json");
    Files.writeString(file, "[]");

    ConfigLoaderImpl loader = new ConfigLoaderImpl(new ConfigValidator());

    assertThatThrownBy(() -> loader.load(file))
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining("Configuration root must be an object");
  }

  @Test
  void rejectsUnsupportedSchemaVersionType() throws Exception {
    Path file = tempDir.resolve("invalid-schema-version.json");
    Files.writeString(
        file,
        """
        {
          "schema_version": true,
          "project": { "id": "demo" },
          "selection_rules": {
            "class_min_loc": 1,
            "class_min_method_count": 1,
            "method_min_loc": 1,
            "method_max_loc": 2,
            "max_methods_per_class": 1,
            "exclude_getters_setters": true
          },
          "llm": { "provider": "mock" }
        }
        """);

    ConfigLoaderImpl loader = new ConfigLoaderImpl(new ConfigValidator());

    assertThatThrownBy(() -> loader.load(file))
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining("schema_version must be a string or integer");
  }

  private static String minimumValidConfigJson(String projectId) {
    return """
        {
          "project": { "id": "%s" },
          "selection_rules": {
            "class_min_loc": 1,
            "class_min_method_count": 1,
            "method_min_loc": 1,
            "method_max_loc": 2,
            "max_methods_per_class": 1,
            "exclude_getters_setters": true
          },
          "llm": { "provider": "mock" }
        }
        """
        .formatted(projectId);
  }
}
