package com.craftsmanbro.fulcraft.ui.tui.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.ui.tui.command.CommandResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigCommandServiceBranchCoverageTest {

  @TempDir Path tempDir;

  @Test
  void parseCommandCoversNullValidateAndSearchSubcommands() {
    ConfigCommandService service = new ConfigCommandService(MetadataRegistry.getDefault());

    assertThat(service.parseCommand(null)).isNull();

    ConfigCommandService.ParsedCommand validate = service.parseCommand("/config validate");
    ConfigCommandService.ParsedCommand search = service.parseCommand("/config search llm");

    assertThat(validate).isNotNull();
    assertThat(validate.type()).isEqualTo(ConfigCommandService.CommandType.VALIDATE);
    assertThat(validate.args()).isEmpty();

    assertThat(search).isNotNull();
    assertThat(search.type()).isEqualTo(ConfigCommandService.CommandType.SEARCH);
    assertThat(search.args()).isEqualTo("llm");
  }

  @Test
  void applySetCoversParseLoadAndSaveFailureBranches() throws IOException {
    ConfigCommandService service = new ConfigCommandService(MetadataRegistry.getDefault());

    CommandResult invalidPath =
        service.applySet("llm.provider[ openai", tempDir.resolve("invalid-path.json"));
    assertThat(invalidPath.success()).isFalse();
    assertThat(invalidPath.errorMessage())
        .contains(MessageSource.getMessage("tui.path_parser.error.missing_closing_bracket"));

    Path brokenRoot = writeFile("broken-root.json", "[]");
    CommandResult loadFailure = service.applySet("llm.provider openai", brokenRoot);
    assertThat(loadFailure.success()).isFalse();
    assertThat(loadFailure.errorMessage())
        .contains(MessageSource.getMessage("tui.config_editor.error.root_not_object"));

    Path parentFile = writeFile("blocked-parent", "not-a-directory");
    Path saveFailurePath = parentFile.resolve("config.json");
    CommandResult saveFailure = service.applySet("custom.value 1", saveFailurePath);
    assertThat(saveFailure.success()).isFalse();
    assertThat(saveFailure.errorMessage())
        .contains(MessageSource.getMessage("tui.config_cmd.save_failed", ""));

    CommandResult emptyQuotedScalar =
        service.applySet("custom.label '", tempDir.resolve("quoted-empty.json"));
    assertThat(emptyQuotedScalar.success()).isTrue();
    ConfigEditor quotedEditor = ConfigEditor.load(tempDir.resolve("quoted-empty.json"));
    assertThat(quotedEditor.getValue(path("custom", "label"))).isEqualTo("");

    CommandResult emptyBoolean =
        service.applySet("analysis.spoon.no_classpath \"\"", tempDir.resolve("empty-boolean.json"));
    assertThat(emptyBoolean.success()).isFalse();
    assertThat(emptyBoolean.errorMessage())
        .contains(MessageSource.getMessage("tui.config_cmd.invalid_boolean"));
  }

  @Test
  void applySearchCoversNullArgsDescriptionMatchAndOverflowDisplay() {
    ConfigCommandService service = new ConfigCommandService(MetadataRegistry.getDefault());
    Path configPath = tempDir.resolve("search-branches.json");

    ConfigCommandService.SearchResult nullArgs = service.applySearch(null, configPath);
    assertThat(nullArgs.shouldOpenEditor()).isFalse();
    assertThat(nullArgs.commandResult()).isNotNull();
    assertThat(nullArgs.commandResult().success()).isFalse();
    assertThat(nullArgs.commandResult().errorMessage())
        .contains(MessageSource.getMessage("tui.config_cmd.usage_search"));

    ConfigCommandService.SearchResult descriptionMatch =
        service.applySearch("identifier", configPath);
    assertThat(descriptionMatch.shouldOpenEditor() || descriptionMatch.commandResult() != null)
        .isTrue();
    if (descriptionMatch.shouldOpenEditor()) {
      assertThat(descriptionMatch.singleMatch().path()).isEqualTo("project.id");
    } else {
      assertThat(descriptionMatch.commandResult().outputLines())
          .anyMatch(line -> line.contains("project.id"));
    }

    ConfigCommandService overflowService =
        new ConfigCommandService(buildRegistryWithEntries("overflow.key", 25));
    ConfigCommandService.SearchResult overflow =
        overflowService.applySearch("overflow.key", configPath);
    assertThat(overflow.shouldOpenEditor()).isFalse();
    assertThat(overflow.commandResult()).isNotNull();
    assertThat(overflow.commandResult().success()).isTrue();
    assertThat(overflow.commandResult().outputLines())
        .anyMatch(
            line ->
                line.equals(MessageSource.getMessage("tui.config_cmd.more_results", 5))
                    || line.equals(MessageSource.getMessage("tui.common.more_count", 5)));
  }

  @Test
  void applyGetCoversNullArgsAndListMaskTraversal() {
    ConfigCommandService service = new ConfigCommandService(MetadataRegistry.getDefault());
    Path configPath = tempDir.resolve("get-branches.json");

    assertThat(service.applySet("custom.values[0] one", configPath).success()).isTrue();
    assertThat(service.applySet("custom.values[1] two", configPath).success()).isTrue();

    CommandResult root = service.applyGet(null, configPath);
    CommandResult list = service.applyGet("custom.values", configPath);

    assertThat(root.success()).isTrue();
    assertThat(root.outputLines())
        .contains(
            MessageSource.getMessage(
                "tui.config_cmd.config_value",
                MessageSource.getMessage("tui.config_editor.path.root")));
    assertThat(list.success()).isTrue();
    assertThat(String.join("\n", list.outputLines())).contains("\"one\"");
    assertThat(String.join("\n", list.outputLines())).contains("\"two\"");
  }

  @Test
  void applySetReportsUsageWhenArgumentsAreMissing() {
    ConfigCommandService service = new ConfigCommandService(MetadataRegistry.getDefault());
    Path configPath = tempDir.resolve("set-usage-branches.json");

    CommandResult nullArgs = service.applySet(null, configPath);
    CommandResult blankArgs = service.applySet("   ", configPath);
    CommandResult pathOnly = service.applySet("project.id", configPath);

    assertThat(nullArgs.success()).isFalse();
    assertThat(blankArgs.success()).isFalse();
    assertThat(pathOnly.success()).isFalse();
    assertThat(nullArgs.errorMessage())
        .contains(MessageSource.getMessage("tui.config_cmd.usage_set"));
    assertThat(blankArgs.errorMessage())
        .contains(MessageSource.getMessage("tui.config_cmd.usage_set"));
    assertThat(pathOnly.errorMessage())
        .contains(MessageSource.getMessage("tui.config_cmd.usage_set"));
  }

  @Test
  void applySearchFormatsAllMetadataTypeLabels() {
    ConfigCommandService service = new ConfigCommandService(buildTypeLabelRegistry());
    Path configPath = tempDir.resolve("search-type-labels.json");

    ConfigCommandService.SearchResult result = service.applySearch("typed.key", configPath);

    assertThat(result.shouldOpenEditor()).isFalse();
    assertThat(result.commandResult()).isNotNull();
    assertThat(result.commandResult().success()).isTrue();
    assertThat(result.commandResult().outputLines())
        .anyMatch(
            line ->
                line.contains(
                    "typed.key.listtyped ("
                        + MessageSource.getMessage(
                            "tui.config_cmd.type.list_of",
                            MessageSource.getMessage("tui.config.value_type.integer"))
                        + ")"));
    assertThat(result.commandResult().outputLines())
        .anyMatch(
            line ->
                line.contains(
                    "typed.key.list ("
                        + MessageSource.getMessage("tui.config_cmd.type.list")
                        + ")"));
    assertThat(result.commandResult().outputLines())
        .anyMatch(
            line ->
                line.contains(
                    "typed.key.enum ("
                        + MessageSource.getMessage("tui.config_cmd.type.enum")
                        + ")"));
    assertThat(result.commandResult().outputLines())
        .anyMatch(
            line ->
                line.contains(
                    "typed.key.bool ("
                        + MessageSource.getMessage("tui.config.value_type.boolean")
                        + ")"));
  }

  private Path writeFile(String fileName, String content) throws IOException {
    Path path = tempDir.resolve(fileName);
    Files.writeString(path, content);
    return path;
  }

  private MetadataRegistry buildRegistryWithEntries(String prefix, int count) {
    try {
      java.util.Map<String, MetadataRegistry.ConfigKeyMetadata> entries =
          new java.util.LinkedHashMap<>();
      for (int i = 0; i < count; i++) {
        String path = prefix + "." + i;
        entries.put(
            path,
            new MetadataRegistry.ConfigKeyMetadata(
                path,
                MetadataRegistry.ValueType.STRING,
                null,
                java.util.List.of(),
                java.util.List.of(),
                "entry-" + i,
                ""));
      }
      java.lang.reflect.Constructor<MetadataRegistry> ctor =
          MetadataRegistry.class.getDeclaredConstructor(java.util.Map.class);
      ctor.setAccessible(true);
      return ctor.newInstance(entries);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Failed to construct metadata registry for test", e);
    }
  }

  private MetadataRegistry buildTypeLabelRegistry() {
    try {
      java.util.Map<String, MetadataRegistry.ConfigKeyMetadata> entries =
          new java.util.LinkedHashMap<>();
      entries.put(
          "typed.key.listtyped",
          new MetadataRegistry.ConfigKeyMetadata(
              "typed.key.listtyped",
              MetadataRegistry.ValueType.LIST,
              MetadataRegistry.ValueType.INTEGER,
              java.util.List.of(),
              java.util.List.of(),
              "typed list",
              ""));
      entries.put(
          "typed.key.list",
          new MetadataRegistry.ConfigKeyMetadata(
              "typed.key.list",
              MetadataRegistry.ValueType.LIST,
              null,
              java.util.List.of(),
              java.util.List.of(),
              "untyped list",
              ""));
      entries.put(
          "typed.key.enum",
          new MetadataRegistry.ConfigKeyMetadata(
              "typed.key.enum",
              MetadataRegistry.ValueType.STRING,
              null,
              java.util.List.of("a", "b"),
              java.util.List.of(),
              "enum value",
              ""));
      entries.put(
          "typed.key.bool",
          new MetadataRegistry.ConfigKeyMetadata(
              "typed.key.bool",
              MetadataRegistry.ValueType.BOOLEAN,
              null,
              java.util.List.of(),
              java.util.List.of(),
              "bool value",
              ""));
      java.lang.reflect.Constructor<MetadataRegistry> ctor =
          MetadataRegistry.class.getDeclaredConstructor(java.util.Map.class);
      ctor.setAccessible(true);
      return ctor.newInstance(entries);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Failed to construct metadata registry for test", e);
    }
  }

  private static java.util.List<ConfigEditor.PathSegment> path(String... keys) {
    java.util.List<ConfigEditor.PathSegment> segments = new java.util.ArrayList<>();
    for (String key : keys) {
      segments.add(ConfigEditor.PathSegment.key(key));
    }
    return segments;
  }
}
