package com.craftsmanbro.fulcraft.ui.tui.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.ui.tui.command.CommandResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigCommandServiceCoverageTest {

  @TempDir Path tempDir;

  @Test
  void parseCommandHandlesOpenAndUnknownSubcommands() {
    ConfigCommandService service = new ConfigCommandService(MetadataRegistry.getDefault());

    ConfigCommandService.ParsedCommand open = service.parseCommand("/config");
    ConfigCommandService.ParsedCommand openWithSpaces = service.parseCommand("/config   ");
    ConfigCommandService.ParsedCommand unknown = service.parseCommand("/config foo bar");

    assertThat(open).isNotNull();
    assertThat(open.type()).isEqualTo(ConfigCommandService.CommandType.OPEN);
    assertThat(open.args()).isEmpty();
    assertThat(openWithSpaces).isNotNull();
    assertThat(openWithSpaces.type()).isEqualTo(ConfigCommandService.CommandType.OPEN);
    assertThat(unknown).isNotNull();
    assertThat(unknown.type()).isEqualTo(ConfigCommandService.CommandType.UNKNOWN);
    assertThat(unknown.args()).isEqualTo("foo bar");
  }

  @Test
  void applyGetHandlesPathErrorsAndLoadFailures() throws IOException {
    ConfigCommandService service = new ConfigCommandService(MetadataRegistry.getDefault());
    Path configPath = tempDir.resolve("get-errors.json");
    service.applySet("llm.provider openai", configPath);

    CommandResult appendPath =
        service.applyGet("selection_rules.exclude_annotations[]", configPath);
    CommandResult invalidPath = service.applyGet("llm.provider[", configPath);
    CommandResult notFound = service.applyGet("llm.missing", configPath);

    Path brokenConfig = writeConfig("broken-get.json", "[]");
    CommandResult loadError = service.applyGet("llm.provider", brokenConfig);

    assertThat(appendPath.success()).isFalse();
    assertThat(appendPath.errorMessage())
        .contains(MessageSource.getMessage("tui.config_cmd.list_append_not_allowed", ""));
    assertThat(invalidPath.success()).isFalse();
    assertThat(invalidPath.errorMessage())
        .contains(MessageSource.getMessage("tui.path_parser.error.missing_closing_bracket"));
    assertThat(notFound.success()).isFalse();
    assertThat(notFound.errorMessage())
        .contains(MessageSource.getMessage("tui.config_cmd.path_not_found", ""));
    assertThat(loadError.success()).isFalse();
    assertThat(loadError.errorMessage())
        .contains(MessageSource.getMessage("tui.config_editor.error.root_not_object"));
  }

  @Test
  void applySetSupportsTypedAndInferredValues() {
    ConfigCommandService service = new ConfigCommandService(MetadataRegistry.getDefault());
    Path configPath = tempDir.resolve("typed-values.json");

    assertThat(service.applySet("selection_rules.max_targets 7", configPath).success()).isTrue();
    assertThat(service.applySet("llm.retry_backoff_multiplier 1.5", configPath).success()).isTrue();
    assertThat(service.applySet("analysis.spoon.no_classpath TRUE", configPath).success()).isTrue();
    assertThat(service.applySet("project.id \"Demo Project\"", configPath).success()).isTrue();
    assertThat(service.applySet("custom.flag true", configPath).success()).isTrue();
    assertThat(service.applySet("custom.count 42", configPath).success()).isTrue();
    assertThat(service.applySet("custom.ratio 1.25", configPath).success()).isTrue();

    ConfigEditor editor = ConfigEditor.load(configPath);
    assertThat(((Number) editor.getValue(path("selection_rules", "max_targets"))).longValue())
        .isEqualTo(7L);
    assertThat(((Number) editor.getValue(path("llm", "retry_backoff_multiplier"))).doubleValue())
        .isEqualTo(1.5d);
    assertThat(editor.getValue(path("analysis", "spoon", "no_classpath"))).isEqualTo(true);
    assertThat(editor.getValue(path("project", "id"))).isEqualTo("Demo Project");
    assertThat(editor.getValue(path("custom", "flag"))).isEqualTo(true);
    assertThat(((Number) editor.getValue(path("custom", "count"))).longValue()).isEqualTo(42L);
    assertThat(((Number) editor.getValue(path("custom", "ratio"))).doubleValue()).isEqualTo(1.25d);
  }

  @Test
  void applySetReportsUsageEnumAndUpdateFailures() throws IOException {
    ConfigCommandService service = new ConfigCommandService(MetadataRegistry.getDefault());
    Path configPath = tempDir.resolve("set-errors.json");

    CommandResult usage = service.applySet("llm.provider", configPath);
    CommandResult invalidEnum = service.applySet("llm.provider unknown-provider", configPath);

    Path brokenParent = writeConfig("broken-parent.json", "{\"llm\":\"plain\"}");
    CommandResult updateFailure = service.applySet("llm.provider openai", brokenParent);

    assertThat(usage.success()).isFalse();
    assertThat(usage.errorMessage()).contains(MessageSource.getMessage("tui.config_cmd.usage_set"));
    assertThat(invalidEnum.success()).isFalse();
    assertThat(invalidEnum.errorMessage())
        .contains(MessageSource.getMessage("tui.config_cmd.invalid_enum", ""));
    assertThat(updateFailure.success()).isFalse();
    assertThat(updateFailure.errorMessage())
        .contains(MessageSource.getMessage("tui.config_cmd.update_failed", ""));
  }

  @Test
  void applySearchCoversUsageNoMatchSingleAndMultipleMatches() throws IOException {
    ConfigCommandService service = new ConfigCommandService(MetadataRegistry.getDefault());
    Path configPath = writeConfig("search.json", "{\"llm\":{\"provider\":\"bedrock\"}}");

    ConfigCommandService.SearchResult usage = service.applySearch(" ", configPath);
    ConfigCommandService.SearchResult noMatch =
        service.applySearch("no-such-config-key", configPath);
    ConfigCommandService.SearchResult single =
        service.applySearch("llm.azure_api_version", configPath);
    ConfigCommandService.SearchResult multiple = service.applySearch("llm.aws_", configPath);

    assertThat(usage.shouldOpenEditor()).isFalse();
    assertThat(usage.commandResult()).isNotNull();
    assertThat(usage.commandResult().success()).isFalse();
    assertThat(usage.commandResult().errorMessage())
        .contains(MessageSource.getMessage("tui.config_cmd.usage_search"));

    assertThat(noMatch.shouldOpenEditor()).isFalse();
    assertThat(noMatch.commandResult()).isNotNull();
    assertThat(noMatch.commandResult().success()).isTrue();
    assertThat(noMatch.commandResult().outputLines().get(0))
        .contains(MessageSource.getMessage("tui.config_cmd.no_matches", ""));

    assertThat(single.shouldOpenEditor()).isTrue();
    assertThat(single.singleMatch()).isNotNull();
    assertThat(single.singleMatch().path()).isEqualTo("llm.azure_api_version");
    assertThat(single.commandResult()).isNull();

    assertThat(multiple.shouldOpenEditor()).isFalse();
    assertThat(multiple.commandResult()).isNotNull();
    assertThat(multiple.commandResult().success()).isTrue();
    assertThat(multiple.commandResult().outputLines())
        .contains(MessageSource.getMessage("tui.config_cmd.matches_header"));
    assertThat(multiple.commandResult().outputLines())
        .anyMatch(
            line ->
                line.contains(
                    "llm.aws_access_key_id ("
                        + MessageSource.getMessage("tui.config.value_type.string")
                        + ")"));
    assertThat(multiple.commandResult().outputLines())
        .contains(MessageSource.getMessage("tui.config_cmd.refine_search"));
  }

  @Test
  void applySearchAndValidateReturnLoadErrorsForBrokenFiles() throws IOException {
    ConfigCommandService service = new ConfigCommandService(MetadataRegistry.getDefault());
    Path brokenConfig = writeConfig("broken-search.json", "[]");

    ConfigCommandService.SearchResult searchResult = service.applySearch("llm", brokenConfig);
    List<ConfigValidationService.ValidationIssue> issues =
        service.validate(brokenConfig, new ConfigValidationService());

    assertThat(searchResult.shouldOpenEditor()).isFalse();
    assertThat(searchResult.commandResult()).isNotNull();
    assertThat(searchResult.commandResult().success()).isFalse();
    assertThat(searchResult.commandResult().errorMessage())
        .contains(MessageSource.getMessage("tui.config_editor.error.root_not_object"));
    assertThat(issues).hasSize(1);
    assertThat(issues.get(0).message())
        .contains(MessageSource.getMessage("tui.config_editor.error.root_not_object"));
  }

  @Test
  void validateReturnsCrossFieldIssuesForParsedConfig() throws IOException {
    ConfigCommandService service = new ConfigCommandService(MetadataRegistry.getDefault());
    Path invalidConfig =
        writeConfig(
            "invalid-config.json",
            """
            {
              "schema_version": 1,
              "project": { "id": "demo" },
              "selection_rules": {
                "class_min_loc": 1,
                "class_min_method_count": 1,
                "method_min_loc": 5,
                "method_max_loc": 3
              },
              "llm": { "provider": "mock" }
            }
            """);

    List<ConfigValidationService.ValidationIssue> issues =
        service.validate(invalidConfig, new ConfigValidationService());

    assertThat(issues).isNotEmpty();
    assertThat(issues)
        .anyMatch(
            issue ->
                issue.path().equals("selection_rules.method_min_loc")
                    && issue.message().contains("cannot be greater"));
  }

  private Path writeConfig(String fileName, String json) throws IOException {
    Path path = tempDir.resolve(fileName);
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
