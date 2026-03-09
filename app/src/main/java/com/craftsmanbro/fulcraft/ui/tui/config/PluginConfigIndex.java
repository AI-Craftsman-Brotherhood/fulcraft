package com.craftsmanbro.fulcraft.ui.tui.config;

import com.craftsmanbro.fulcraft.config.plugin.PluginConfigPaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class PluginConfigIndex {

  public List<PluginConfigEntry> list(final Path projectRoot) {
    if (projectRoot == null) {
      return List.of();
    }
    final Path pluginsRoot = PluginConfigPaths.pluginsRoot(projectRoot);
    if (!Files.isDirectory(pluginsRoot)) {
      return List.of();
    }
    final List<PluginConfigEntry> entries = new ArrayList<>();
    try (var stream = Files.list(pluginsRoot)) {
      stream
          .filter(Files::isDirectory)
          .sorted(Comparator.comparing(PluginConfigIndex::fileNameOrPath))
          .forEach(
              dir -> {
                final String pluginId = fileNameOrPath(dir);
                final PluginConfigPaths.ResolvedPaths resolvedPaths =
                    PluginConfigPaths.resolve(dir);
                entries.add(
                    new PluginConfigEntry(
                        pluginId,
                        resolvedPaths.pluginDirectory(),
                        resolvedPaths.configPath(),
                        resolvedPaths.schemaPath(),
                        resolvedPaths.configExists(),
                        resolvedPaths.schemaExists()));
              });
    } catch (IOException ignored) {
      return List.of();
    }
    return entries;
  }

  private static String fileNameOrPath(final Path path) {
    if (path == null) {
      return "";
    }
    final Path fileName = path.getFileName();
    return fileName != null ? fileName.toString() : path.toString();
  }
}
