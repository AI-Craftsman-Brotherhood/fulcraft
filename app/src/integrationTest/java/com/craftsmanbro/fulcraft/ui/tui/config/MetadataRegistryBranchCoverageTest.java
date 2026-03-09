package com.craftsmanbro.fulcraft.ui.tui.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MetadataRegistryBranchCoverageTest {

  @TempDir Path tempDir;

  @Test
  void findAndSearchHandleBlankInputsAndDescriptionMatches() {
    MetadataRegistry registry = MetadataRegistry.getDefault();

    assertThat(registry.find(null)).isEmpty();
    assertThat(registry.find(" ")).isEmpty();
    assertThat(registry.all()).isNotEmpty();

    List<MetadataRegistry.ConfigKeyMetadata> byDescription = registry.search("identifier");
    assertThat(byDescription).anyMatch(metadata -> metadata.path().equals("project.id"));

    List<MetadataRegistry.ConfigKeyMetadata> allByBlank = registry.search(" ");
    assertThat(allByBlank).hasSize(registry.all().size());
  }

  @Test
  void visibilityConditionCoversAnyOfNoneOfAndInvalidInputs() throws IOException {
    ConfigEditor openaiEditor =
        ConfigEditor.load(
            writeConfig(
                """
                {
                  "llm": { "provider": "openai" }
                }
                """));
    ConfigEditor bedrockEditor =
        ConfigEditor.load(
            writeConfig(
                """
                {
                  "llm": { "provider": "bedrock" }
                }
                """));

    MetadataRegistry.VisibilityCondition anyOf =
        new MetadataRegistry.VisibilityCondition(
            path("llm", "provider"),
            MetadataRegistry.ConditionOperator.ANY_OF,
            List.of("openai", "azure-openai"));
    MetadataRegistry.VisibilityCondition noneOf =
        new MetadataRegistry.VisibilityCondition(
            path("llm", "provider"), MetadataRegistry.ConditionOperator.NONE_OF, List.of("openai"));
    MetadataRegistry.VisibilityCondition invalid =
        new MetadataRegistry.VisibilityCondition(
            List.of(), MetadataRegistry.ConditionOperator.ANY_OF, List.of("openai"));

    assertThat(anyOf.matches(openaiEditor)).isTrue();
    assertThat(anyOf.matches(bedrockEditor)).isFalse();
    assertThat(anyOf.matches(null)).isFalse();
    assertThat(noneOf.matches(openaiEditor)).isFalse();
    assertThat(noneOf.matches(bedrockEditor)).isTrue();
    assertThat(invalid.matches(openaiEditor)).isFalse();
  }

  @Test
  void configKeyMetadataCoversFallbacksAndVisibilityDefaults() {
    MetadataRegistry.ConfigKeyMetadata metadata =
        new MetadataRegistry.ConfigKeyMetadata(
            "custom.key",
            MetadataRegistry.ValueType.STRING,
            null,
            null,
            null,
            "fallback-description",
            "config.desc.missing.key");

    assertThat(metadata.isEnum()).isFalse();
    assertThat(metadata.isVisible(null)).isTrue();
    assertThat(metadata.localizedDescription()).isEqualTo("fallback-description");
    assertThat(metadata.enumOptions()).isEmpty();
    assertThat(metadata.visibilityConditions()).isEmpty();

    MetadataRegistry.ConfigKeyMetadata enumMetadata =
        new MetadataRegistry.ConfigKeyMetadata(
            "custom.enum",
            MetadataRegistry.ValueType.STRING,
            null,
            List.of("a"),
            List.of(),
            "enum-description",
            null);
    assertThat(enumMetadata.isEnum()).isTrue();
  }

  private Path writeConfig(String json) throws IOException {
    Path path = Files.createTempFile(tempDir, "metadata", ".json");
    Files.writeString(path, json);
    return path;
  }

  private static List<ConfigEditor.PathSegment> path(String... keys) {
    List<ConfigEditor.PathSegment> segments = new java.util.ArrayList<>();
    for (String key : keys) {
      segments.add(ConfigEditor.PathSegment.key(key));
    }
    return segments;
  }
}
