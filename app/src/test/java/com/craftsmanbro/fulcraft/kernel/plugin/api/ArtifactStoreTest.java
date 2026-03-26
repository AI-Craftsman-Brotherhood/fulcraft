package com.craftsmanbro.fulcraft.kernel.plugin.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ArtifactStoreTest {

  @Test
  void implementation_canExposeRunAndActionPaths() throws IOException {
    Path runRoot = Path.of("run-root");
    Path actionsRoot = runRoot.resolve("actions");
    ArtifactStore store =
        new ArtifactStore() {
          @Override
          public Path runRoot() {
            return runRoot;
          }

          @Override
          public Path actionsRoot() {
            return actionsRoot;
          }

          @Override
          public Path actions(String pluginId) {
            return actionsRoot.resolve(pluginId);
          }
        };

    assertThat(store.runRoot()).isEqualTo(runRoot);
    assertThat(store.actionsRoot()).isEqualTo(actionsRoot);
    assertThat(store.actions("demo")).isEqualTo(actionsRoot.resolve("demo"));
  }

  @Test
  void defaultNodeScopedActions_createsDirectoryUnderPluginActions() throws IOException {
    Path runRoot = Path.of("run-root");
    Path actionsRoot = runRoot.resolve("actions");
    ArtifactStore store =
        new ArtifactStore() {
          @Override
          public Path runRoot() {
            return runRoot;
          }

          @Override
          public Path actionsRoot() {
            return actionsRoot;
          }

          @Override
          public Path actions(String pluginId) throws IOException {
            Path pluginDir = actionsRoot.resolve(pluginId);
            Files.createDirectories(pluginDir);
            return pluginDir;
          }
        };

    assertThat(store.actions("demo", "node-1")).isEqualTo(actionsRoot.resolve("demo/node-1"));
  }

  @Test
  void defaultNodeScopedActions_rejectsNullAndBlankIds() {
    ArtifactStore store =
        new ArtifactStore() {
          @Override
          public Path runRoot() {
            return Path.of("run-root");
          }

          @Override
          public Path actionsRoot() {
            return Path.of("run-root/actions");
          }

          @Override
          public Path actions(String pluginId) {
            return Path.of("run-root/actions").resolve(pluginId);
          }
        };

    assertThatThrownBy(() -> store.actions(null, "node-1"))
        .isInstanceOf(NullPointerException.class)
        .hasMessage(MessageSource.getMessage("kernel.common.error.argument_null", "pluginId"));
    assertThatThrownBy(() -> store.actions(" ", "node-1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(MessageSource.getMessage("kernel.plugin.artifact_store.error.plugin_id_blank"));
    assertThatThrownBy(() -> store.actions("demo", null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage(MessageSource.getMessage("kernel.common.error.argument_null", "nodeId"));
    assertThatThrownBy(() -> store.actions("demo", " "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(MessageSource.getMessage("kernel.plugin.artifact_store.error.node_id_blank"));
  }
}
