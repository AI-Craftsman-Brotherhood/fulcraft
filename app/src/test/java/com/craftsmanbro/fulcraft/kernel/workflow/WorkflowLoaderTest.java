package com.craftsmanbro.fulcraft.kernel.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.config.InvalidConfigurationException;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.kernel.workflow.model.WorkflowDefinition;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkflowLoaderTest {

  @TempDir Path tempDir;

  @Test
  void load_returnsDefaultWorkflowWhenWorkflowFileIsNotConfigured() {
    final Config config = new Config();
    final WorkflowLoader loader = new WorkflowLoader();

    final Optional<WorkflowDefinition> definition = loader.load(config, tempDir);

    assertThat(definition).isPresent();
    assertThat(definition.orElseThrow().getVersion()).isEqualTo(1);
    assertThat(definition.orElseThrow().getNodes()).hasSize(4);
    assertThat(definition.orElseThrow().getNodes().get(0).getId()).isEqualTo("analyze");
    assertThat(definition.orElseThrow().getNodes().get(0).getPluginId())
        .isEqualTo("analyze-builtin");
  }

  @Test
  void load_readsWorkflowJsonFromProjectRelativePath() throws Exception {
    final Path workflowPath = tempDir.resolve("workflows/default.json");
    Files.createDirectories(workflowPath.getParent());
    Files.writeString(
        workflowPath,
        """
        {
          "version": 1,
          "nodes": [
            {
              "id": "analyze-main",
              "plugin": "plugin.analyze",
              "depends_on": [],
              "with": {"engine": "spoon"},
              "on_failure": "CONTINUE",
              "retry": {"max": 2, "backoff_ms": 250},
              "timeout_sec": 30,
              "enabled": true
            }
          ]
        }
        """);
    final Config config = new Config();
    config.getPipeline().setWorkflowFile("workflows/default.json");
    final WorkflowLoader loader = new WorkflowLoader();

    final Optional<WorkflowDefinition> definition = loader.load(config, tempDir);

    assertThat(definition).isPresent();
    assertThat(definition.orElseThrow().getVersion()).isEqualTo(1);
    assertThat(definition.orElseThrow().getNodes()).hasSize(1);
    assertThat(definition.orElseThrow().getNodes().get(0).getId()).isEqualTo("analyze-main");
    assertThat(definition.orElseThrow().getNodes().get(0).getPluginId())
        .isEqualTo("plugin.analyze");
    assertThat(definition.orElseThrow().getNodes().get(0).getOnFailurePolicy().name())
        .isEqualTo("CONTINUE");
    assertThat(definition.orElseThrow().getNodes().get(0).getRetry().getMax()).isEqualTo(2);
    assertThat(definition.orElseThrow().getNodes().get(0).getRetry().getBackoffMs()).isEqualTo(250);
    assertThat(definition.orElseThrow().getNodes().get(0).getWith())
        .containsEntry("engine", "spoon");
  }

  @Test
  void load_throwsWhenWorkflowFileDoesNotExist() {
    final Config config = new Config();
    config.getPipeline().setWorkflowFile("missing-workflow.json");
    final WorkflowLoader loader = new WorkflowLoader();
    final String expectedMessage =
        MessageSource.getMessage(
            "kernel.workflow.loader.error.file_not_found",
            tempDir.resolve("missing-workflow.json").toAbsolutePath());

    assertThatThrownBy(() -> loader.load(config, tempDir))
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining(expectedMessage);
  }

  @Test
  void load_throwsWhenWorkflowJsonIsInvalid() throws Exception {
    final Path workflowPath = tempDir.resolve("workflow.json");
    Files.writeString(workflowPath, "{invalid");
    final Config config = new Config();
    config.getPipeline().setWorkflowFile("workflow.json");
    final WorkflowLoader loader = new WorkflowLoader();
    final String expectedPrefix =
        MessageSource.getMessage("kernel.workflow.loader.error.file_parse_failed")
            .split("\\{0\\}", 2)[0];

    assertThatThrownBy(() -> loader.load(config, tempDir))
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining(expectedPrefix)
        .hasMessageContaining(workflowPath.toAbsolutePath().toString());
  }
}
