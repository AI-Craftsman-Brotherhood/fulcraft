package com.craftsmanbro.fulcraft.config.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginConfigPathsTest {

  @TempDir Path tempDir;

  @Test
  void resolvePrefersNestedLayoutWhenPresent() throws IOException {
    Path pluginDir = tempDir.resolve(".ful").resolve("plugins").resolve("demo");
    Files.createDirectories(pluginDir.resolve("config"));
    Files.writeString(pluginDir.resolve("config").resolve("config.json"), "{}");
    Files.writeString(pluginDir.resolve("config").resolve("schema.json"), "{}");
    Files.writeString(pluginDir.resolve("config.json"), "{\"legacy\":true}");
    Files.writeString(pluginDir.resolve("schema.json"), "{\"legacy\":true}");

    PluginConfigPaths.ResolvedPaths resolved = PluginConfigPaths.resolve(tempDir, "demo");

    assertThat(resolved.configPath()).isEqualTo(pluginDir.resolve("config").resolve("config.json"));
    assertThat(resolved.schemaPath()).isEqualTo(pluginDir.resolve("config").resolve("schema.json"));
    assertThat(resolved.configExists()).isTrue();
    assertThat(resolved.schemaExists()).isTrue();
  }

  @Test
  void resolveFallsBackToLegacyLayoutWhenNestedFilesAreMissing() throws IOException {
    Path pluginDir = tempDir.resolve(".ful").resolve("plugins").resolve("legacy");
    Files.createDirectories(pluginDir);
    Files.writeString(pluginDir.resolve("config.json"), "{}");
    Files.writeString(pluginDir.resolve("schema.json"), "{}");

    PluginConfigPaths.ResolvedPaths resolved = PluginConfigPaths.resolve(tempDir, "legacy");

    assertThat(resolved.configPath()).isEqualTo(pluginDir.resolve("config.json"));
    assertThat(resolved.schemaPath()).isEqualTo(pluginDir.resolve("schema.json"));
    assertThat(resolved.configExists()).isTrue();
    assertThat(resolved.schemaExists()).isTrue();
  }

  @Test
  void resolveReturnsNestedTargetsWhenFilesDoNotExistYet() {
    PluginConfigPaths.ResolvedPaths resolved = PluginConfigPaths.resolve(tempDir, "missing");

    assertThat(resolved.configPath())
        .isEqualTo(
            tempDir
                .resolve(".ful")
                .resolve("plugins")
                .resolve("missing")
                .resolve("config")
                .resolve("config.json"));
    assertThat(resolved.schemaPath())
        .isEqualTo(
            tempDir
                .resolve(".ful")
                .resolve("plugins")
                .resolve("missing")
                .resolve("config")
                .resolve("schema.json"));
    assertThat(resolved.configExists()).isFalse();
    assertThat(resolved.schemaExists()).isFalse();
  }
}
