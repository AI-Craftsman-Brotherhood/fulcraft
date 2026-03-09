package com.craftsmanbro.fulcraft.ui.tui.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.ui.tui.command.CommandResult;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigCommandServiceTest {

  @TempDir Path tempDir;

  @Test
  void parseCommandShouldIgnoreNonConfigTokens() {
    ConfigCommandService service = new ConfigCommandService(MetadataRegistry.getDefault());

    ConfigCommandService.ParsedCommand parsed = service.parseCommand("/configx set foo bar");

    assertThat(parsed).isNull();
  }

  @Test
  void parseCommandShouldHandleCaseInsensitiveConfigCommand() {
    ConfigCommandService service = new ConfigCommandService(MetadataRegistry.getDefault());

    ConfigCommandService.ParsedCommand parsed =
        service.parseCommand("/CONFIG set llm.deterministic true");

    assertThat(parsed).isNotNull();
    assertThat(parsed.type()).isEqualTo(ConfigCommandService.CommandType.SET);
    assertThat(parsed.args()).isEqualTo("llm.deterministic true");
  }

  @Test
  void parseCommandShouldRecognizeGetSubcommand() {
    ConfigCommandService service = new ConfigCommandService(MetadataRegistry.getDefault());

    ConfigCommandService.ParsedCommand parsed = service.parseCommand("/config get llm.provider");

    assertThat(parsed).isNotNull();
    assertThat(parsed.type()).isEqualTo(ConfigCommandService.CommandType.GET);
    assertThat(parsed.args()).isEqualTo("llm.provider");
  }

  @Test
  void applySetShouldRejectInvalidBooleanValue() {
    ConfigCommandService service = new ConfigCommandService(MetadataRegistry.getDefault());
    Path configPath = tempDir.resolve("config.json");

    CommandResult result = service.applySet("analysis.spoon.no_classpath maybe", configPath);

    assertThat(result.success()).isFalse();
    assertThat(result.errorMessage()).contains("Invalid boolean value");
  }

  @Test
  void applySetShouldAppendListValueAndPersist() {
    ConfigCommandService service = new ConfigCommandService(MetadataRegistry.getDefault());
    Path configPath = tempDir.resolve("list.json");

    CommandResult result =
        service.applySet("selection_rules.exclude_annotations[] Foo", configPath);

    assertThat(result.success()).isTrue();
    ConfigEditor editor = ConfigEditor.load(configPath);
    List<ConfigEditor.PathSegment> listPath =
        List.of(
            ConfigEditor.PathSegment.key("selection_rules"),
            ConfigEditor.PathSegment.key("exclude_annotations"));
    Object value = editor.getValue(listPath);
    assertThat(value).isInstanceOf(List.class);
    List<String> listValue = (List<String>) value;
    assertThat(listValue).containsExactly("Foo");
  }

  @Test
  void applySetShouldNormalizeListEnumValueCase() {
    ConfigCommandService service = new ConfigCommandService(MetadataRegistry.getDefault());
    Path configPath = tempDir.resolve("pipeline-stages.json");

    CommandResult result = service.applySet("pipeline.stages[] REPORT", configPath);

    assertThat(result.success()).isTrue();
    ConfigEditor editor = ConfigEditor.load(configPath);
    List<ConfigEditor.PathSegment> path =
        List.of(ConfigEditor.PathSegment.key("pipeline"), ConfigEditor.PathSegment.key("stages"));
    Object value = editor.getValue(path);
    assertThat(value).isInstanceOf(List.class);
    List<String> stages = (List<String>) value;
    assertThat(stages).containsExactly("report");
  }

  @Test
  void applySetShouldRejectInvalidListEnumValue() {
    ConfigCommandService service = new ConfigCommandService(MetadataRegistry.getDefault());
    Path configPath = tempDir.resolve("invalid-pipeline-stage.json");

    CommandResult result = service.applySet("pipeline.stages[] invalid-stage", configPath);

    assertThat(result.success()).isFalse();
    assertThat(result.errorMessage())
        .contains(MessageSource.getMessage("tui.config_cmd.invalid_enum", "invalid-stage"));
  }

  @Test
  void applySetShouldRejectListPathWithoutIndex() {
    ConfigCommandService service = new ConfigCommandService(MetadataRegistry.getDefault());
    Path configPath = tempDir.resolve("invalid-list.json");

    CommandResult result = service.applySet("selection_rules.exclude_annotations Foo", configPath);

    assertThat(result.success()).isFalse();
    assertThat(result.errorMessage()).contains("List path requires");
  }

  @Test
  void applySetShouldRejectListOperationOnScalarPath() {
    ConfigCommandService service = new ConfigCommandService(MetadataRegistry.getDefault());
    Path configPath = tempDir.resolve("invalid-scalar.json");

    CommandResult result = service.applySet("llm.provider[] mock", configPath);

    assertThat(result.success()).isFalse();
    assertThat(result.errorMessage()).contains("Path is not a list");
  }

  @Test
  void applySetShouldNormalizeEnumValueCase() {
    ConfigCommandService service = new ConfigCommandService(MetadataRegistry.getDefault());
    Path configPath = tempDir.resolve("enum.json");

    CommandResult result = service.applySet("llm.provider OPENAI", configPath);

    assertThat(result.success()).isTrue();
    ConfigEditor editor = ConfigEditor.load(configPath);
    List<ConfigEditor.PathSegment> path =
        List.of(ConfigEditor.PathSegment.key("llm"), ConfigEditor.PathSegment.key("provider"));
    assertThat(editor.getValue(path)).isEqualTo("openai");
  }

  @Test
  void applyGetShouldReturnScalarValueAtPath() {
    ConfigCommandService service = new ConfigCommandService(MetadataRegistry.getDefault());
    Path configPath = tempDir.resolve("get-scalar.json");
    service.applySet("llm.provider openai", configPath);

    CommandResult result = service.applyGet("llm.provider", configPath);

    assertThat(result.success()).isTrue();
    assertThat(result.outputLines()).contains("Config value (llm.provider):");
    assertThat(result.outputLines()).contains("\"openai\"");
  }

  @Test
  void applyGetShouldMaskSensitiveValue() {
    ConfigCommandService service = new ConfigCommandService(MetadataRegistry.getDefault());
    Path configPath = tempDir.resolve("get-secret.json");
    service.applySet("llm.api_key secret-value", configPath);

    CommandResult result = service.applyGet("llm.api_key", configPath);

    assertThat(result.success()).isTrue();
    assertThat(result.outputLines()).contains("\"****\"");
    assertThat(String.join("\n", result.outputLines())).doesNotContain("secret-value");
  }

  @Test
  void applyGetShouldReturnWholeConfigWhenPathIsOmitted() {
    ConfigCommandService service = new ConfigCommandService(MetadataRegistry.getDefault());
    Path configPath = tempDir.resolve("get-root.json");
    service.applySet("llm.provider openai", configPath);
    service.applySet("analysis.spoon.no_classpath true", configPath);

    CommandResult result = service.applyGet("", configPath);

    assertThat(result.success()).isTrue();
    assertThat(result.outputLines()).contains("Config value ((root)):");
    assertThat(String.join("\n", result.outputLines())).contains("\"llm\"");
    assertThat(String.join("\n", result.outputLines())).contains("\"provider\" : \"openai\"");
  }
}
