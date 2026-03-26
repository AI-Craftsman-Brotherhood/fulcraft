package com.craftsmanbro.fulcraft.kernel.plugin.api;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Run-scoped artifact store for plugins. */
public interface ArtifactStore {

  Path runRoot();

  Path actionsRoot() throws IOException;

  Path actions(String pluginId) throws IOException;

  /**
   * Resolves a node-scoped action directory.
   *
   * <p>Default implementation places nodes under {@code actions(pluginId)/nodeId}.
   */
  default Path actions(final String pluginId, final String nodeId) throws IOException {
    Objects.requireNonNull(
        pluginId, MessageSource.getMessage("kernel.common.error.argument_null", "pluginId"));
    if (pluginId.isBlank()) {
      throw new IllegalArgumentException(
          MessageSource.getMessage("kernel.plugin.artifact_store.error.plugin_id_blank"));
    }
    Objects.requireNonNull(
        nodeId, MessageSource.getMessage("kernel.common.error.argument_null", "nodeId"));
    if (nodeId.isBlank()) {
      throw new IllegalArgumentException(
          MessageSource.getMessage("kernel.plugin.artifact_store.error.node_id_blank"));
    }
    final Path dir = actions(pluginId).resolve(nodeId);
    Files.createDirectories(dir);
    return dir;
  }
}
