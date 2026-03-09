package com.craftsmanbro.fulcraft.ui.tui.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginConfigIndexTest {

  @TempDir Path tempDir;

  @Test
  void listReturnsEmptyWhenProjectRootIsNull() {
    PluginConfigIndex index = new PluginConfigIndex();

    assertThat(index.list(null)).isEmpty();
  }

  @Test
  void listReturnsEmptyWhenPluginsDirectoryDoesNotExist() throws IOException {
    Path projectRoot = tempDir.resolve("project");
    Files.createDirectories(projectRoot);
    PluginConfigIndex index = new PluginConfigIndex();

    assertThat(index.list(projectRoot)).isEmpty();
  }

  @Test
  void listReturnsSortedEntriesWithConfigAndSchemaFlags() throws IOException {
    Path projectRoot = tempDir.resolve("project");
    Path pluginsRoot = projectRoot.resolve(".ful/plugins");
    Files.createDirectories(pluginsRoot.resolve("z-plugin/config"));
    Files.createDirectories(pluginsRoot.resolve("a-plugin/config"));
    Files.createDirectories(pluginsRoot.resolve("m-plugin/config"));
    Files.writeString(pluginsRoot.resolve("a-plugin/config/config.json"), "{}");
    Files.writeString(pluginsRoot.resolve("m-plugin/config/schema.json"), "{}");
    Files.writeString(pluginsRoot.resolve("z-plugin/config/config.json"), "{}");
    Files.writeString(pluginsRoot.resolve("z-plugin/config/schema.json"), "{}");
    Files.writeString(pluginsRoot.resolve("README.md"), "not a plugin directory");

    PluginConfigIndex index = new PluginConfigIndex();
    List<PluginConfigEntry> entries = index.list(projectRoot);

    assertThat(entries)
        .extracting(
            PluginConfigEntry::pluginId,
            PluginConfigEntry::configExists,
            PluginConfigEntry::schemaExists)
        .containsExactly(
            tuple("a-plugin", true, false),
            tuple("m-plugin", false, true),
            tuple("z-plugin", true, true));
    assertThat(entries)
        .extracting(PluginConfigEntry::pluginDirectory)
        .containsExactly(
            pluginsRoot.resolve("a-plugin"),
            pluginsRoot.resolve("m-plugin"),
            pluginsRoot.resolve("z-plugin"));
    assertThat(entries)
        .extracting(PluginConfigEntry::configPath)
        .containsExactly(
            pluginsRoot.resolve("a-plugin/config/config.json"),
            pluginsRoot.resolve("m-plugin/config/config.json"),
            pluginsRoot.resolve("z-plugin/config/config.json"));
    assertThat(entries)
        .extracting(PluginConfigEntry::schemaPath)
        .containsExactly(
            pluginsRoot.resolve("a-plugin/config/schema.json"),
            pluginsRoot.resolve("m-plugin/config/schema.json"),
            pluginsRoot.resolve("z-plugin/config/schema.json"));
  }

  @Test
  void listUsesLegacyPathsWhenNestedConfigDirectoryIsMissing() throws IOException {
    Path projectRoot = tempDir.resolve("project");
    Path pluginsRoot = projectRoot.resolve(".ful/plugins");
    Files.createDirectories(pluginsRoot.resolve("legacy-plugin"));
    Files.writeString(pluginsRoot.resolve("legacy-plugin/config.json"), "{}");
    Files.writeString(pluginsRoot.resolve("legacy-plugin/schema.json"), "{}");

    PluginConfigIndex index = new PluginConfigIndex();
    List<PluginConfigEntry> entries = index.list(projectRoot);

    assertThat(entries).hasSize(1);
    assertThat(entries.get(0).configPath())
        .isEqualTo(pluginsRoot.resolve("legacy-plugin/config.json"));
    assertThat(entries.get(0).schemaPath())
        .isEqualTo(pluginsRoot.resolve("legacy-plugin/schema.json"));
  }
}
