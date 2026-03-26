package com.craftsmanbro.fulcraft.ui.tui.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PluginConfigEntryTest {

  @Test
  void summaryShowsPresentAndMissingFlags() {
    PluginConfigEntry entry =
        new PluginConfigEntry(
            "demo",
            Path.of("demo"),
            Path.of("demo/config.json"),
            Path.of("demo/schema.json"),
            true,
            false);

    assertThat(entry.summary()).isEqualTo("config: present, schema: missing");
  }

  @Test
  void summaryShowsBothPresentWhenConfigAndSchemaExist() {
    PluginConfigEntry entry =
        new PluginConfigEntry(
            "demo",
            Path.of("demo"),
            Path.of("demo/config.json"),
            Path.of("demo/schema.json"),
            true,
            true);

    assertThat(entry.summary()).isEqualTo("config: present, schema: present");
  }

  @Test
  void summaryShowsMissingConfigWhenOnlySchemaExists() {
    PluginConfigEntry entry =
        new PluginConfigEntry(
            "demo",
            Path.of("demo"),
            Path.of("demo/config.json"),
            Path.of("demo/schema.json"),
            false,
            true);

    assertThat(entry.summary()).isEqualTo("config: missing, schema: present");
  }
}
