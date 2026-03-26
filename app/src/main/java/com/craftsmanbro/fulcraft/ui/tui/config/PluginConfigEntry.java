package com.craftsmanbro.fulcraft.ui.tui.config;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import java.nio.file.Path;

public record PluginConfigEntry(
    String pluginId,
    Path pluginDirectory,
    Path configPath,
    Path schemaPath,
    boolean configExists,
    boolean schemaExists) {

  public String summary() {
    final String config =
        configExists
            ? MessageSource.getMessage("tui.plugin_config.config_present")
            : MessageSource.getMessage("tui.plugin_config.config_missing");
    final String schema =
        schemaExists
            ? MessageSource.getMessage("tui.plugin_config.schema_present")
            : MessageSource.getMessage("tui.plugin_config.schema_missing");
    return MessageSource.getMessage("tui.plugin_config.summary", config, schema);
  }
}
