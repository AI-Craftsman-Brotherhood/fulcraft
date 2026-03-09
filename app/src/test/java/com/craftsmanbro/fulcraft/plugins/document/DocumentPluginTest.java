package com.craftsmanbro.fulcraft.plugins.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.kernel.plugin.api.ActionPluginException;
import com.craftsmanbro.fulcraft.kernel.plugin.api.ArtifactStore;
import com.craftsmanbro.fulcraft.kernel.plugin.api.PluginConfig;
import com.craftsmanbro.fulcraft.kernel.plugin.api.PluginContext;
import com.craftsmanbro.fulcraft.kernel.plugin.api.ServiceRegistry;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocumentPluginTest {

  @TempDir Path tempDir;

  @Test
  void id_returnsDocumentBuiltin() {
    final DocumentPlugin plugin = new DocumentPlugin();

    assertThat(plugin.id()).isEqualTo(DocumentPlugin.PLUGIN_ID);
  }

  @Test
  void kind_returnsWorkflow() {
    final DocumentPlugin plugin = new DocumentPlugin();

    assertThat(plugin.kind()).isEqualTo("workflow");
  }

  @Test
  void execute_throwsWhenDocumentFlowServiceIsMissing() {
    final DocumentPlugin plugin = new DocumentPlugin();
    final PluginContext context = newPluginContext(new ServiceRegistry());

    assertThatThrownBy(() -> plugin.execute(context))
        .isInstanceOf(ActionPluginException.class)
        .hasMessageContaining("DocumentFlow");
  }

  private PluginContext newPluginContext(final ServiceRegistry serviceRegistry) {
    final RunContext runContext = new RunContext(tempDir, new Config(), "run-document");
    final ArtifactStore artifactStore = mock(ArtifactStore.class);
    return new PluginContext(
        runContext, artifactStore, PluginConfig.empty(), "document", Map.of(), serviceRegistry);
  }
}
