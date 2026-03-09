package com.craftsmanbro.fulcraft.plugins.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.kernel.plugin.api.ArtifactStore;
import com.craftsmanbro.fulcraft.kernel.plugin.api.PluginContext;
import com.craftsmanbro.fulcraft.kernel.plugin.api.ServiceRegistry;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnalyzePluginTest {

  @TempDir Path tempDir;

  @Test
  void id_returnsAnalyzeBuiltin() {
    final AnalyzePlugin plugin = new AnalyzePlugin();

    assertThat(plugin.id()).isEqualTo(AnalyzePlugin.PLUGIN_ID);
  }

  @Test
  void kind_returnsWorkflow() {
    final AnalyzePlugin plugin = new AnalyzePlugin();

    assertThat(plugin.kind()).isEqualTo("workflow");
  }

  @Test
  void execute_throwsWhenAnalysisPortServiceIsMissing() {
    final AnalyzePlugin plugin = new AnalyzePlugin();
    final PluginContext context = newPluginContext(new ServiceRegistry());

    assertThatThrownBy(() -> plugin.execute(context))
        .isInstanceOf(com.craftsmanbro.fulcraft.kernel.plugin.api.ActionPluginException.class)
        .hasMessageContaining("AnalysisPort");
  }

  private PluginContext newPluginContext(final ServiceRegistry serviceRegistry) {
    final RunContext runContext = new RunContext(tempDir, new Config(), "run-analyze");
    final ArtifactStore artifactStore = mock(ArtifactStore.class);
    return new PluginContext(
        runContext,
        artifactStore,
        com.craftsmanbro.fulcraft.kernel.plugin.api.PluginConfig.empty(),
        "analyze",
        java.util.Map.of(),
        serviceRegistry);
  }
}
