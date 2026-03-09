package com.craftsmanbro.fulcraft.plugins.noop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.kernel.plugin.api.PluginContext;
import com.craftsmanbro.fulcraft.kernel.plugin.api.PluginResult;
import com.craftsmanbro.fulcraft.ui.cli.wiring.ArtifactStoreAdapters;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NoopGeneratePluginTest {

  @TempDir Path tempDir;

  @Test
  void shouldExposePluginIdentityAndKind() {
    NoopGeneratePlugin plugin = new NoopGeneratePlugin();

    assertThat(plugin.id()).isEqualTo(NoopGeneratePlugin.PLUGIN_ID);
    assertThat(plugin.kind()).isEqualTo("generate");
  }

  @Test
  void executeMarksRunMetadataAndReturnsSuccess() {
    NoopGeneratePlugin plugin = new NoopGeneratePlugin();
    RunContext runContext = new RunContext(tempDir, minimalConfig(), "run-1");
    PluginContext context =
        new PluginContext(
            runContext, ArtifactStoreAdapters.fromRunRoot(runContext.getRunDirectory()));

    PluginResult result = plugin.execute(context);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getPluginId()).isEqualTo(NoopGeneratePlugin.PLUGIN_ID);
    assertThat(result.getMessage()).isEqualTo("noop");
    assertThat(result.getError()).isNull();
    assertThat(runContext.getMetadata()).containsEntry("plugin.noop.executed", true);
    assertThat(runContext.getMetadata("plugin.noop.executed", Boolean.class)).contains(true);
  }

  @Test
  void executeRejectsNullContext() {
    NoopGeneratePlugin plugin = new NoopGeneratePlugin();

    assertThatThrownBy(() -> plugin.execute(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("context");
  }

  private Config minimalConfig() {
    Config config = new Config();
    Config.ProjectConfig projectConfig = new Config.ProjectConfig();
    projectConfig.setId("test-project");
    config.setProject(projectConfig);
    return config;
  }
}
