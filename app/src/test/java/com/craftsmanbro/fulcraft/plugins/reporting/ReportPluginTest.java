package com.craftsmanbro.fulcraft.plugins.reporting;

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

class ReportPluginTest {

  @TempDir Path tempDir;

  @Test
  void id_returnsReportBuiltin() {
    final ReportPlugin plugin = new ReportPlugin();

    assertThat(plugin.id()).isEqualTo(ReportPlugin.PLUGIN_ID);
  }

  @Test
  void kind_returnsWorkflow() {
    final ReportPlugin plugin = new ReportPlugin();

    assertThat(plugin.kind()).isEqualTo("workflow");
  }

  @Test
  void execute_returnsSuccessInDryRun() throws Exception {
    final ReportPlugin plugin = new ReportPlugin();
    final RunContext runContext = new RunContext(tempDir, new Config(), "run-report");
    runContext.withDryRun(true);
    final PluginContext context =
        new PluginContext(
            runContext,
            mock(ArtifactStore.class),
            PluginConfig.empty(),
            "report",
            Map.of(),
            new ServiceRegistry());

    final PluginResult result = plugin.execute(context);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getPluginId()).isEqualTo(ReportPlugin.PLUGIN_ID);
    assertThat(result.getMessage()).isEqualTo("report completed");
  }
}
