package com.craftsmanbro.fulcraft.plugins.exploration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.kernel.plugin.api.ArtifactStore;
import com.craftsmanbro.fulcraft.kernel.plugin.api.PluginConfig;
import com.craftsmanbro.fulcraft.kernel.plugin.api.PluginContext;
import com.craftsmanbro.fulcraft.kernel.plugin.api.PluginResult;
import com.craftsmanbro.fulcraft.kernel.plugin.api.ServiceRegistry;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExplorePluginTest {

  @TempDir Path tempDir;

  @Test
  void id_returnsExploreBuiltin() {
    final ExplorePlugin plugin = new ExplorePlugin();

    assertThat(plugin.id()).isEqualTo(ExplorePlugin.PLUGIN_ID);
  }

  @Test
  void kind_returnsWorkflow() {
    final ExplorePlugin plugin = new ExplorePlugin();

    assertThat(plugin.kind()).isEqualTo("workflow");
  }

  @Test
  void execute_returnsSuccessInDryRun() throws Exception {
    final ExplorePlugin plugin = new ExplorePlugin();
    final RunContext runContext = new RunContext(tempDir, new Config(), "run-explore");
    runContext.withDryRun(true);
    final PluginContext context =
        new PluginContext(
            runContext,
            mock(ArtifactStore.class),
            PluginConfig.empty(),
            "explore",
            Map.of(),
            new ServiceRegistry());

    final PluginResult result = plugin.execute(context);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getPluginId()).isEqualTo(ExplorePlugin.PLUGIN_ID);
    assertThat(result.getMessage()).isEqualTo("explore completed");
  }
}
