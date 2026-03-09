package com.craftsmanbro.fulcraft.ui.tui.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MetadataRegistryTest {

  @TempDir Path tempDir;

  @Test
  void findReturnsMetadataForKnownPath() {
    MetadataRegistry registry = MetadataRegistry.getDefault();

    MetadataRegistry.ConfigKeyMetadata metadata = registry.find("llm.provider").orElseThrow();

    assertThat(metadata.type()).isEqualTo(MetadataRegistry.ValueType.STRING);
    assertThat(metadata.enumOptions()).isNotEmpty();
  }

  @Test
  void searchMatchesPathCaseInsensitively() {
    MetadataRegistry registry = MetadataRegistry.getDefault();

    assertThat(registry.search("PROJECT.ID"))
        .anyMatch(metadata -> metadata.path().equals("project.id"));
  }

  @Test
  void visibilityConditionsRespectEditorState() throws IOException {
    MetadataRegistry registry = MetadataRegistry.getDefault();
    MetadataRegistry.ConfigKeyMetadata preprocessTool =
        registry.find("analysis.preprocess.tool").orElseThrow();
    MetadataRegistry.ConfigKeyMetadata azureApiVersion =
        registry.find("llm.azure_api_version").orElseThrow();

    ConfigEditor enabledEditor =
        ConfigEditor.load(
            writeConfig(
                """
                {
                  "analysis": { "preprocess": { "mode": "AUTO" } },
                  "llm": { "provider": "openai" }
                }
                """));
    ConfigEditor disabledEditor =
        ConfigEditor.load(
            writeConfig(
                """
                {
                  "analysis": { "preprocess": { "mode": "OFF" } },
                  "llm": { "provider": "azure-openai" }
                }
                """));

    assertThat(preprocessTool.isVisible(enabledEditor)).isTrue();
    assertThat(preprocessTool.isVisible(disabledEditor)).isFalse();
    assertThat(azureApiVersion.isVisible(enabledEditor)).isFalse();
    assertThat(azureApiVersion.isVisible(disabledEditor)).isTrue();
  }

  @Test
  void findReturnsMetadataForSchemaBackedWorkflowAndQualityGateKeys() {
    MetadataRegistry registry = MetadataRegistry.getDefault();

    MetadataRegistry.ConfigKeyMetadata workflowFile =
        registry.find("pipeline.workflow_file").orElseThrow();
    MetadataRegistry.ConfigKeyMetadata coverageTool =
        registry.find("quality_gate.coverage_tool").orElseThrow();
    MetadataRegistry.ConfigKeyMetadata buildTool =
        registry.find("project.build_tool").orElseThrow();

    assertThat(workflowFile.type()).isEqualTo(MetadataRegistry.ValueType.STRING);
    assertThat(coverageTool.enumOptions()).containsExactly("jacoco", "cobertura");
    assertThat(buildTool.enumOptions()).containsExactly("gradle", "maven");
  }

  private Path writeConfig(String json) throws IOException {
    Path path = Files.createTempFile(tempDir, "config", ".json");
    Files.writeString(path, json);
    return path;
  }
}
