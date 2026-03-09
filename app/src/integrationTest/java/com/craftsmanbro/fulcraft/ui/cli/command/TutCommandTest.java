package com.craftsmanbro.fulcraft.ui.cli.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.ui.banner.StartupBannerSupport;
import com.craftsmanbro.fulcraft.ui.tui.command.CommandDispatcher;
import com.craftsmanbro.fulcraft.ui.tui.config.ConfigCommandService;
import com.craftsmanbro.fulcraft.ui.tui.config.ConfigValidationService;
import com.craftsmanbro.fulcraft.ui.tui.config.MetadataRegistry;
import com.craftsmanbro.fulcraft.ui.tui.plan.PlanBuilder;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ListResourceBundle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TutCommandTest {

  @Test
  void doCall_printsCodexStyleStartupBanner(@TempDir Path tempDir) throws Exception {
    writeConfig(tempDir, "tut-model");
    CapturingTutCommand command = createCommand(tempDir, "");

    int exitCode = command.doCall(Config.createDefault(), Path.of("."));

    assertThat(exitCode).isZero();
    assertThat(command.lines()).anyMatch(line -> line.contains(">_ ful (v"));
    assertThat(command.lines())
        .anyMatch(line -> line.contains("model:     tut-model   /model to change"));
    assertThat(command.lines())
        .anyMatch(
            line -> line.contains("directory: " + StartupBannerSupport.formatDirectory(tempDir)));
    assertThat(command.lines())
        .anyMatch(
            line -> line.contains("Tip: Use /fork to branch the current chat into a new thread."));
    assertThat(command.inlines()).contains("ful> ");
  }

  @Test
  void doCall_printsConfiguredApplicationMetadata(@TempDir Path tempDir) throws Exception {
    writeConfig(tempDir, "tut-model", "custom-ful", "2.3.4");
    CapturingTutCommand command = createCommand(tempDir, "");

    int exitCode = command.doCall(Config.createDefault(), Path.of("."));

    assertThat(exitCode).isZero();
    assertThat(command.lines()).anyMatch(line -> line.contains(">_ custom-ful (v2.3.4)"));
  }

  @Test
  void doCall_dispatchesAnalyzeShortcut(@TempDir Path tempDir) throws Exception {
    writeConfig(tempDir, "tut-model");
    CapturingTutCommand command = createCommand(tempDir, "/analyze\n/q\n");

    int exitCode = command.doCall(Config.createDefault(), Path.of("."));

    assertThat(exitCode).isZero();
    assertThat(command.executedCliArgs()).hasSize(1);
    assertThat(command.executedCliArgs().getFirst())
        .containsExactly(
            "-c", tempDir.resolve("config.json").toString(), "analyze", "-p", tempDir.toString());
  }

  @Test
  void doCall_dispatchesReportShortcutWithArguments(@TempDir Path tempDir) throws Exception {
    writeConfig(tempDir, "tut-model");
    CapturingTutCommand command =
        createCommand(tempDir, "/report --run-id run-1 --format html\n/q\n");

    int exitCode = command.doCall(Config.createDefault(), Path.of("."));

    assertThat(exitCode).isZero();
    assertThat(command.executedCliArgs()).hasSize(1);
    assertThat(command.executedCliArgs().getFirst())
        .containsExactly(
            "-c",
            tempDir.resolve("config.json").toString(),
            "report",
            "-p",
            tempDir.toString(),
            "--run-id",
            "run-1",
            "--format",
            "html");
  }

  @Test
  void doCall_dispatchesDocumentShortcutWithQuotedArgument(@TempDir Path tempDir) throws Exception {
    writeConfig(tempDir, "tut-model");
    CapturingTutCommand command =
        createCommand(tempDir, "/document --output \"docs out\" --format markdown\n/q\n");

    int exitCode = command.doCall(Config.createDefault(), Path.of("."));

    assertThat(exitCode).isZero();
    assertThat(command.executedCliArgs()).hasSize(1);
    assertThat(command.executedCliArgs().getFirst())
        .containsExactly(
            "-c",
            tempDir.resolve("config.json").toString(),
            "document",
            "-p",
            tempDir.toString(),
            "--output",
            "docs out",
            "--format",
            "markdown");
  }

  @Test
  void doCall_ignoresUnsupportedExecShortcut(@TempDir Path tempDir) throws Exception {
    writeConfig(tempDir, "tut-model");
    CapturingTutCommand command = createCommand(tempDir, "/exec pluginA\n/q\n");

    int exitCode = command.doCall(Config.createDefault(), Path.of("."));

    assertThat(exitCode).isZero();
    assertThat(command.executedCliArgs()).isEmpty();
    assertThat(command.lines()).doesNotContain("ERR:tut.exec.args_invalid");
    assertThat(command.lines()).doesNotContain("ERR:tut.exec.failed");
  }

  @Test
  void doCall_reportsErrorForShortcutWithUnbalancedQuotes(@TempDir Path tempDir) throws Exception {
    writeConfig(tempDir, "tut-model");
    CapturingTutCommand command = createCommand(tempDir, "/report --run-id \"run-1\n/q\n");

    int exitCode = command.doCall(Config.createDefault(), Path.of("."));

    assertThat(exitCode).isZero();
    assertThat(command.executedCliArgs()).isEmpty();
    assertThat(command.lines()).contains("ERR:tut.exec.args_invalid");
  }

  @Test
  void doCall_reportsErrorWhenNestedShortcutFails(@TempDir Path tempDir) throws Exception {
    writeConfig(tempDir, "tut-model");
    CapturingTutCommand command = createCommand(tempDir, "/analyze\n/q\n");
    command.setNestedExitCode(9);

    int exitCode = command.doCall(Config.createDefault(), Path.of("."));

    assertThat(exitCode).isZero();
    assertThat(command.executedCliArgs()).hasSize(1);
    assertThat(command.lines()).contains("ERR:tut.exec.failed");
  }

  @Test
  void doCall_outputsModelSummaryInNonInteractiveMode(@TempDir Path tempDir) throws Exception {
    writeConfig(
        tempDir,
        "openai",
        "tut-model",
        null,
        null,
        """
            "allowed_providers": ["openai", "custom-provider"],
            "allowed_models": {
              "custom-provider": ["custom-model", "custom-model", "", 42],
              "openai": ["gpt-5-mini"]
            }
            """);
    CapturingTutCommand command = createCommand(tempDir, "/model\n/q\n");

    int exitCode = command.doCall(Config.createDefault(), Path.of("."));

    assertThat(exitCode).isZero();
    assertThat(command.lines()).contains("CURRENT provider=openai, model=tut-model");
    assertThat(command.lines()).contains("tut.model.usage");
    assertThat(command.lines()).anyMatch(line -> line.equals("  - custom-provider"));
  }

  @Test
  void doCall_appliesModelUsingCurrentProvider(@TempDir Path tempDir) throws Exception {
    writeConfig(tempDir, "tut-model");
    CapturingTutCommand command = createCommand(tempDir, "/model gpt-5-mini\n/q\n");

    int exitCode = command.doCall(Config.createDefault(), Path.of("."));

    assertThat(exitCode).isZero();
    String configJson = Files.readString(tempDir.resolve("config.json"));
    assertThat(configJson).contains("\"provider\" : \"openai\"");
    assertThat(configJson).contains("\"model_name\" : \"gpt-5-mini\"");
    assertThat(command.lines()).contains("UPDATED provider=openai, model=gpt-5-mini");
  }

  @Test
  void doCall_appliesExplicitProviderAndModel(@TempDir Path tempDir) throws Exception {
    writeConfig(tempDir, "tut-model");
    CapturingTutCommand command =
        createCommand(tempDir, "/model anthropic claude-3-7-sonnet-latest\n/q\n");

    int exitCode = command.doCall(Config.createDefault(), Path.of("."));

    assertThat(exitCode).isZero();
    String configJson = Files.readString(tempDir.resolve("config.json"));
    assertThat(configJson).contains("\"provider\" : \"anthropic\"");
    assertThat(configJson).contains("\"model_name\" : \"claude-3-7-sonnet-latest\"");
    assertThat(command.lines())
        .contains("UPDATED provider=anthropic, model=claude-3-7-sonnet-latest");
  }

  @Test
  void doCall_reportsProviderRequiredWhenCurrentProviderIsMissing(@TempDir Path tempDir)
      throws Exception {
    writeConfig(tempDir, "", "tut-model", null, null, null);
    CapturingTutCommand command = createCommand(tempDir, "/model gpt-5\n/q\n");

    int exitCode = command.doCall(Config.createDefault(), Path.of("."));

    assertThat(exitCode).isZero();
    assertThat(command.lines()).contains("ERR:tut.model.provider_required");
  }

  @Test
  void doCall_treatsModelPrefixWithoutWhitespaceAsUnknownSlashCommand(@TempDir Path tempDir)
      throws Exception {
    writeConfig(tempDir, "tut-model");
    CapturingTutCommand command = createCommand(tempDir, "/modelx\n/q\n");

    int exitCode = command.doCall(Config.createDefault(), Path.of("."));

    assertThat(exitCode).isZero();
    assertThat(command.lines())
        .anyMatch(line -> line.contains("/help             show available commands"));
  }

  @Test
  void doCall_handlesConfigOpenAndUnknownSubcommand(@TempDir Path tempDir) throws Exception {
    writeConfig(tempDir, "tut-model");
    CapturingTutCommand command = createCommand(tempDir, "/config\n/config unknown part\n/q\n");

    int exitCode = command.doCall(Config.createDefault(), Path.of("."));

    assertThat(exitCode).isZero();
    assertThat(command.lines()).contains("tut.config.list.header");
    assertThat(command.lines()).contains("ERR:tut.config.unknown_subcommand");
  }

  @Test
  void doCall_handlesConfigSetAndGet(@TempDir Path tempDir) throws Exception {
    writeConfig(tempDir, "tut-model");
    CapturingTutCommand command =
        createCommand(
            tempDir, "/config set llm.model_name gpt-4o-mini\n/config get llm.model_name\n/q\n");

    int exitCode = command.doCall(Config.createDefault(), Path.of("."));

    assertThat(exitCode).isZero();
    assertThat(command.lines()).anyMatch(line -> line.contains("Updated llm.model_name"));
    assertThat(command.lines()).anyMatch(line -> line.contains("gpt-4o-mini"));
  }

  @Test
  void doCall_handlesConfigSearchSingleMatchInNonInteractiveMode(@TempDir Path tempDir)
      throws Exception {
    writeConfig(tempDir, "tut-model");
    CapturingTutCommand command = createCommand(tempDir, "/config search llm.model_name\n/q\n");

    int exitCode = command.doCall(Config.createDefault(), Path.of("."));

    assertThat(exitCode).isZero();
    assertThat(command.lines()).anyMatch(line -> line.contains("tut.config.search.single"));
    assertThat(command.lines()).contains("tut.config.search.single_hint");
  }

  @Test
  void doCall_handlesConfigValidateForInvalidJson(@TempDir Path tempDir) throws Exception {
    writeBrokenConfig(tempDir);
    CapturingTutCommand command = createCommand(tempDir, "/config validate\n/q\n");

    int exitCode = command.doCall(Config.createDefault(), Path.of("."));

    assertThat(exitCode).isZero();
    assertThat(command.lines()).contains("tut.config.validate.ng");
    assertThat(command.lines()).contains("tut.config.validate.issue_no_path");
  }

  @Test
  void doCall_rendersPlanForPlainInput(@TempDir Path tempDir) throws Exception {
    writeConfig(tempDir, "tut-model");
    CapturingTutCommand command = createCommand(tempDir, "Generate unit tests for Foo\n/q\n");

    int exitCode = command.doCall(Config.createDefault(), Path.of("."));

    assertThat(exitCode).isZero();
    assertThat(command.lines()).contains("tut.plan.header");
  }

  @Test
  void doCall_showsHelpForUnknownSlashCommand(@TempDir Path tempDir) throws Exception {
    writeConfig(tempDir, "tut-model");
    CapturingTutCommand command = createCommand(tempDir, "/unknown-command\n/q\n");

    int exitCode = command.doCall(Config.createDefault(), Path.of("."));

    assertThat(exitCode).isZero();
    assertThat(command.lines())
        .anyMatch(line -> line.contains("/analyze          run analyze command"));
  }

  @Test
  void doCall_outputsStatusCommand(@TempDir Path tempDir) throws Exception {
    writeConfig(tempDir, "tut-model");
    CapturingTutCommand command = createCommand(tempDir, "/status\n/q\n");

    int exitCode = command.doCall(Config.createDefault(), Path.of("."));

    assertThat(exitCode).isZero();
    assertThat(command.lines()).contains("Status:");
    assertThat(command.lines()).contains("Mode: TUT (non-screen)");
  }

  @Test
  void doCall_handlesConfigValidateForValidJson(@TempDir Path tempDir) throws Exception {
    writeConfig(tempDir, "tut-model");
    CapturingTutCommand command = createCommand(tempDir, "/config validate\n/q\n");

    int exitCode = command.doCall(Config.createDefault(), Path.of("."));

    assertThat(exitCode).isZero();
    assertThat(command.lines()).contains("tut.config.validate.ok");
  }

  @Test
  void doCall_fallbacksFromRealtimeWhenRawModeIsUnavailable(@TempDir Path tempDir)
      throws Exception {
    writeConfig(tempDir, "tut-model");
    CapturingTutCommand command = createRealtimePreferredCommand(tempDir, "/model\n/q\n");

    int exitCode = command.doCall(Config.createDefault(), Path.of("."));

    assertThat(exitCode).isZero();
    assertThat(command.lines()).contains("CURRENT provider=openai, model=tut-model");
    assertThat(command.inlines()).contains("ful> ");
  }

  @Test
  void utilityMethods_parseSlashArguments_handlesQuotesAndUnbalancedInput() throws Exception {
    CapturingTutCommand command = createCommand(Path.of("."), "");

    assertThat(
            invokePrivate(
                command,
                "parseSlashArguments",
                new Class<?>[] {String.class},
                "--run-id run-1 --output \"docs out\""))
        .isEqualTo(List.of("--run-id", "run-1", "--output", "docs out"));
    assertThat(
            invokePrivate(
                command,
                "parseSlashArguments",
                new Class<?>[] {String.class},
                "--name 'alpha beta'"))
        .isEqualTo(List.of("--name", "alpha beta"));
    assertThatThrownBy(
            () ->
                invokePrivate(command, "parseSlashArguments", new Class<?>[] {String.class}, "\"x"))
        .hasRootCauseInstanceOf(IllegalArgumentException.class)
        .hasRootCauseMessage("Unclosed quote in slash-command arguments");
    assertThat(
            invokePrivate(
                command, "parseSlashArguments", new Class<?>[] {String.class}, (Object) null))
        .isEqualTo(List.of());
  }

  @Test
  void utilityMethods_buildSetArgsFromEditorInput_andCancelInputs() throws Exception {
    CapturingTutCommand command = createCommand(Path.of("."), "");

    assertThat(
            invokePrivate(
                command,
                "buildSetArgsFromEditorInput",
                new Class<?>[] {String.class, String.class},
                "llm.model_name",
                "gpt-5"))
        .isEqualTo("llm.model_name gpt-5");
    assertThat(
            invokePrivate(
                command,
                "buildSetArgsFromEditorInput",
                new Class<?>[] {String.class, String.class},
                "llm.allowed_models",
                "[0] gpt-5"))
        .isEqualTo("llm.allowed_models[0] gpt-5");
    assertThat(
            invokePrivate(
                command,
                "buildSetArgsFromEditorInput",
                new Class<?>[] {String.class, String.class},
                "llm.allowed_models",
                ".openai gpt-5"))
        .isEqualTo("llm.allowed_models.openai gpt-5");
    assertThat(
            invokePrivate(
                command,
                "buildSetArgsFromEditorInput",
                new Class<?>[] {String.class, String.class},
                "llm.allowed_models",
                "[0]"))
        .isNull();

    assertThat(
            invokePrivate(
                command, "isConfigEditorCancelInput", new Class<?>[] {String.class}, "/cancel"))
        .isEqualTo(true);
    assertThat(
            invokePrivate(command, "isConfigEditorCancelInput", new Class<?>[] {String.class}, "x"))
        .isEqualTo(false);
  }

  @Test
  void utilityMethods_quoteFallbackAndPathHelpers() throws Exception {
    CapturingTutCommand command = createCommand(Path.of("."), "");

    assertThat(
            invokePrivate(command, "quoteIfNeeded", new Class<?>[] {String.class}, (Object) null))
        .isEqualTo("\"\"");
    assertThat(invokePrivate(command, "quoteIfNeeded", new Class<?>[] {String.class}, "gpt-5"))
        .isEqualTo("gpt-5");
    assertThat(invokePrivate(command, "quoteIfNeeded", new Class<?>[] {String.class}, "gpt 5"))
        .isEqualTo("\"gpt 5\"");
    assertThat(
            invokePrivate(
                command, "quoteIfNeeded", new Class<?>[] {String.class}, "say \"hello\" now"))
        .isEqualTo("\"say \\\"hello\\\" now\"");

    assertThat(
            invokePrivate(
                command,
                "fallbackDisplay",
                new Class<?>[] {String.class, String.class},
                "  ",
                "fallback"))
        .isEqualTo("fallback");
    assertThat(
            invokePrivate(
                command,
                "fallbackDisplay",
                new Class<?>[] {String.class, String.class},
                "value",
                "fallback"))
        .isEqualTo("value");

    assertThat(
            (List<?>)
                invokePrivate(
                    command, "pathSegments", new Class<?>[] {String.class}, "llm.provider"))
        .hasSize(2);
    assertThat(invokePrivate(command, "pathSegments", new Class<?>[] {String.class}, (Object) null))
        .isEqualTo(List.of());
  }

  @Test
  void utilityMethods_suggestionAndMenuHelpers() throws Exception {
    CapturingTutCommand command = createCommand(Path.of("."), "");

    assertThat(
            invokePrivate(
                command,
                "extractSuggestionCommandToken",
                new Class<?>[] {String.class},
                "  /model            choose provider/model"))
        .isEqualTo("/model");
    assertThat(
            invokePrivate(
                command,
                "extractSuggestionCommandToken",
                new Class<?>[] {String.class},
                "not help"))
        .isEqualTo("");

    Object allSuggestions =
        invokePrivate(command, "resolveSlashSuggestions", new Class<?>[] {String.class}, "/");
    Object filteredSuggestions =
        invokePrivate(command, "resolveSlashSuggestions", new Class<?>[] {String.class}, "/mo");
    Object fallbackSuggestions =
        invokePrivate(command, "resolveSlashSuggestions", new Class<?>[] {String.class}, "/zz");
    assertThat((List<?>) allSuggestions).isNotEmpty();
    assertThat((List<?>) filteredSuggestions)
        .anyMatch(line -> String.valueOf(line).contains("/model"));
    assertThat((List<?>) fallbackSuggestions).isEqualTo(allSuggestions);

    assertThat(
            invokePrivate(
                command,
                "findPreferredIndex",
                new Class<?>[] {List.class, String.class},
                List.of("openai", "anthropic"),
                "ANTHROPIC"))
        .isEqualTo(1);
    assertThat(
            invokePrivate(
                command,
                "findPreferredIndex",
                new Class<?>[] {List.class, String.class},
                List.of("openai"),
                ""))
        .isEqualTo(0);

    assertThat(
            invokePrivate(
                command,
                "truncateForViewport",
                new Class<?>[] {String.class, int.class},
                "abc",
                20))
        .isEqualTo("abc");
    assertThat(
            String.valueOf(
                invokePrivate(
                    command,
                    "truncateForViewport",
                    new Class<?>[] {String.class, int.class},
                    "01234567890123456789",
                    10)))
        .endsWith("...");

    assertThat(
            String.valueOf(
                invokePrivate(
                    command,
                    "formatMenuLine",
                    new Class<?>[] {int.class, int.class, String.class, boolean.class, int.class},
                    0,
                    100,
                    "openai",
                    true,
                    80)))
        .contains("\u001B[7m")
        .contains("\u001B[0m");
  }

  @Test
  void utilityMethods_readNavigationKey_interpretsCommonKeys() throws Exception {
    CapturingTutCommand command = createCommand(Path.of("."), "");

    assertThat(readNavigationKey(command, "\n")).isEqualTo(navKey("NAV_KEY_ENTER"));
    assertThat(readNavigationKey(command, "q")).isEqualTo(navKey("NAV_KEY_QUIT"));
    assertThat(readNavigationKey(command, "k")).isEqualTo(navKey("NAV_KEY_UP"));
    assertThat(readNavigationKey(command, "j")).isEqualTo(navKey("NAV_KEY_DOWN"));
    assertThat(readNavigationKey(command, "\u001B[A")).isEqualTo(navKey("NAV_KEY_UP"));
    assertThat(readNavigationKey(command, "\u001B[B")).isEqualTo(navKey("NAV_KEY_DOWN"));
    assertThat(readNavigationKey(command, "\u001B[H")).isEqualTo(navKey("NAV_KEY_HOME"));
    assertThat(readNavigationKey(command, "\u001B[F")).isEqualTo(navKey("NAV_KEY_END"));
    assertThat(readNavigationKey(command, "\u001B[5~")).isEqualTo(navKey("NAV_KEY_PAGE_UP"));
    assertThat(readNavigationKey(command, "\u001B[6~")).isEqualTo(navKey("NAV_KEY_PAGE_DOWN"));
    assertThat(readNavigationKey(command, "\u001B[C")).isEqualTo(navKey("NAV_KEY_OTHER"));
    assertThat(readNavigationKey(command, "\u001B")).isEqualTo(navKey("NAV_KEY_QUIT"));
    assertThat(readNavigationKey(command, "x")).isEqualTo(navKey("NAV_KEY_OTHER"));
  }

  @Test
  void utilityMethods_promptConfigEditorInput_handlesListObjectAndPlainValue() throws Exception {
    MetadataRegistry.ConfigKeyMetadata listMetadata =
        MetadataRegistry.getDefault().find("project.exclude_paths").orElseThrow();
    MetadataRegistry.ConfigKeyMetadata objectMetadata =
        MetadataRegistry.getDefault().find("llm.allowed_models").orElseThrow();

    CapturingTutCommand listCommand = createCommand(Path.of("."), "\n");
    assertThat(
            invokePrivate(
                listCommand,
                "promptConfigEditorInput",
                new Class<?>[] {MetadataRegistry.ConfigKeyMetadata.class},
                listMetadata))
        .isNull();

    CapturingTutCommand objectCommand = createCommand(Path.of("."), "/cancel\n");
    assertThat(
            invokePrivate(
                objectCommand,
                "promptConfigEditorInput",
                new Class<?>[] {MetadataRegistry.ConfigKeyMetadata.class},
                objectMetadata))
        .isNull();

    CapturingTutCommand plainCommand = createCommand(Path.of("."), "  hello  \n");
    assertThat(
            invokePrivate(
                plainCommand,
                "promptConfigEditorInput",
                new Class<?>[] {MetadataRegistry.ConfigKeyMetadata.class},
                (Object) null))
        .isEqualTo("hello");
  }

  @Test
  void utilityMethods_formatMetadataType_handlesNullListEnumAndObject() throws Exception {
    CapturingTutCommand command = createCommand(Path.of("."), "");
    MetadataRegistry.ConfigKeyMetadata listMetadata =
        MetadataRegistry.getDefault().find("project.exclude_paths").orElseThrow();
    MetadataRegistry.ConfigKeyMetadata enumMetadata =
        MetadataRegistry.getDefault().find("analysis.classpath.mode").orElseThrow();
    MetadataRegistry.ConfigKeyMetadata objectMetadata =
        MetadataRegistry.getDefault().find("llm.allowed_models").orElseThrow();

    assertThat(
            invokePrivate(
                command,
                "formatMetadataType",
                new Class<?>[] {MetadataRegistry.ConfigKeyMetadata.class},
                (Object) null))
        .isEqualTo("unknown");
    assertThat(
            invokePrivate(
                command,
                "formatMetadataType",
                new Class<?>[] {MetadataRegistry.ConfigKeyMetadata.class},
                listMetadata))
        .isEqualTo("list<string>");
    assertThat(
            invokePrivate(
                command,
                "formatMetadataType",
                new Class<?>[] {MetadataRegistry.ConfigKeyMetadata.class},
                enumMetadata))
        .isEqualTo("enum");
    assertThat(
            invokePrivate(
                command,
                "formatMetadataType",
                new Class<?>[] {MetadataRegistry.ConfigKeyMetadata.class},
                objectMetadata))
        .isEqualTo("object");
  }

  @Test
  void utilityMethods_showInteractiveAndChooseFromMenu_returnEarlyWhenOptionsEmpty()
      throws Exception {
    CapturingTutCommand command = createCommand(Path.of("."), "");

    assertThat(
            invokePrivate(
                command,
                "showInteractiveList",
                new Class<?>[] {
                  String.class, List.class, int.class, int.class, String.class, boolean.class
                },
                "title",
                List.of(),
                10,
                0,
                "footer",
                true))
        .isEqualTo(-1);
    assertThat(
            invokePrivate(
                command,
                "chooseFromMenu",
                new Class<?>[] {String.class, List.class, String.class},
                "title",
                List.of(),
                "preferred"))
        .isNull();
  }

  @Test
  void utilityMethods_outputHelpers_coverNullAndDisplayPaths() throws Exception {
    CapturingTutCommand command = createCommand(Path.of("."), "");
    Path configPath = Path.of("config.json");

    invokePrivate(
        command,
        "outputSearchResult",
        new Class<?>[] {ConfigCommandService.SearchResult.class, Path.class},
        null,
        configPath);
    assertThat(command.lines()).doesNotContain("tut.config.search.single");

    ConfigCommandService.SearchResult displayResult =
        ConfigCommandService.SearchResult.displayResults(
            com.craftsmanbro.fulcraft.ui.tui.command.CommandResult.success(List.of("displayed")));
    invokePrivate(
        command,
        "outputSearchResult",
        new Class<?>[] {ConfigCommandService.SearchResult.class, Path.class},
        displayResult,
        configPath);
    assertThat(command.lines()).contains("displayed");

    MetadataRegistry.ConfigKeyMetadata single =
        new MetadataRegistry.ConfigKeyMetadata(
            "custom.path", MetadataRegistry.ValueType.STRING, null, List.of(), List.of(), "", "");
    ConfigCommandService.SearchResult openEditor =
        ConfigCommandService.SearchResult.openEditor(single);
    invokePrivate(
        command,
        "outputSearchResult",
        new Class<?>[] {ConfigCommandService.SearchResult.class, Path.class},
        openEditor,
        configPath);
    assertThat(command.lines()).contains("tut.config.search.single");
    assertThat(command.lines()).contains("tut.config.search.single_hint");

    invokePrivate(
        command,
        "outputValidationResult",
        new Class<?>[] {List.class, Path.class},
        List.of(),
        configPath);
    assertThat(command.lines()).contains("tut.config.validate.ok");

    List<ConfigValidationService.ValidationIssue> issues =
        List.of(new ConfigValidationService.ValidationIssue("llm.provider", "required"));
    invokePrivate(
        command,
        "outputValidationResult",
        new Class<?>[] {List.class, Path.class},
        issues,
        configPath);
    assertThat(command.lines()).contains("tut.config.validate.issue");

    invokePrivate(
        command,
        "outputCommandResult",
        new Class<?>[] {com.craftsmanbro.fulcraft.ui.tui.command.CommandResult.class},
        com.craftsmanbro.fulcraft.ui.tui.command.CommandResult.error("boom"));
    assertThat(command.lines()).contains("ERR:boom");

    int lineCount = command.lines().size();
    invokePrivate(
        command,
        "outputCommandResult",
        new Class<?>[] {com.craftsmanbro.fulcraft.ui.tui.command.CommandResult.class},
        (Object) null);
    assertThat(command.lines().size()).isEqualTo(lineCount);
  }

  @Test
  void utilityMethods_providerAndModelResolution_includeConfigAndPresets(@TempDir Path tempDir)
      throws Exception {
    writeConfig(
        tempDir,
        "openai",
        "custom-current",
        null,
        null,
        """
            "allowed_providers": ["custom-provider"],
            "allowed_models": {
              "custom-provider": ["model-a", "model-a", ""],
              "openai": ["gpt-5-nano", 123]
            }
            """);
    CapturingTutCommand command = createCommand(tempDir, "");
    Object editor =
        invokePrivate(
            command,
            "loadConfigEditor",
            new Class<?>[] {Path.class},
            tempDir.resolve("config.json"));

    assertThat(
            invokePrivate(
                command,
                "readStringValue",
                new Class<?>[] {
                  Class.forName("com.craftsmanbro.fulcraft.ui.tui.config.ConfigEditor"),
                  String.class
                },
                editor,
                "llm.provider"))
        .isEqualTo("openai");

    List<String> providers =
        (List<String>)
            invokePrivate(
                command,
                "resolveProviderOptions",
                new Class<?>[] {
                  Class.forName("com.craftsmanbro.fulcraft.ui.tui.config.ConfigEditor"),
                  String.class
                },
                editor,
                "openai");
    assertThat(providers).contains("openai", "custom-provider");

    List<String> openaiModels =
        (List<String>)
            invokePrivate(
                command,
                "resolveModelOptions",
                new Class<?>[] {
                  Class.forName("com.craftsmanbro.fulcraft.ui.tui.config.ConfigEditor"),
                  String.class,
                  String.class,
                  String.class
                },
                editor,
                "openai",
                "openai",
                "custom-current");
    assertThat(openaiModels).contains("custom-current", "gpt-5-nano");

    List<String> customModels =
        (List<String>)
            invokePrivate(
                command,
                "resolveModelOptions",
                new Class<?>[] {
                  Class.forName("com.craftsmanbro.fulcraft.ui.tui.config.ConfigEditor"),
                  String.class,
                  String.class,
                  String.class
                },
                editor,
                "custom-provider",
                "openai",
                "custom-current");
    assertThat(customModels).containsExactly("model-a");
  }

  @Test
  void utilityMethods_renderAndTerminalHelpers_coverAdditionalBranches() throws Exception {
    CapturingTutCommand command = createCommand(Path.of("."), "");

    int rendered =
        (int)
            invokePrivate(
                command,
                "renderRealtimePrompt",
                new Class<?>[] {String.class, String.class, List.class, int.class},
                "ful> ",
                "/",
                List.of("a", "b"),
                1);
    assertThat(rendered).isEqualTo(2);
    assertThat(command.inlines().getLast()).contains("\u001B[s");

    invokePrivate(
        command,
        "redrawInlineBlock",
        new Class<?>[] {List.class, boolean.class},
        List.of("line1", "line2"),
        false);
    assertThat(command.inlines().getLast()).contains("\u001B[s");

    int columns = (int) invokePrivate(command, "resolveTerminalColumns", new Class<?>[] {});
    assertThat(columns).isGreaterThanOrEqualTo(40);

    InputStreamReader tildeReader =
        new InputStreamReader(
            new ByteArrayInputStream("~".getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
    invokePrivate(command, "skipTilde", new Class<?>[] {InputStreamReader.class}, tildeReader);

    java.io.InputStream originalIn = System.in;
    try {
      System.setIn(new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8)));
      invokePrivate(command, "consumeEscapeSequence", new Class<?>[] {});
    } finally {
      System.setIn(originalIn);
    }
  }

  @Test
  void utilityMethods_showConfigKeyListAndEditor_canReturnWithoutRealtime() throws Exception {
    CapturingTutCommand command = createCommand(Path.of("."), "");

    invokePrivate(
        command,
        "showConfigKeyList",
        new Class<?>[] {Path.class, List.class},
        Path.of("config.json"),
        List.of());
    assertThat(command.lines()).contains("tut.config.list.hint.get");

    int before = command.lines().size();
    invokePrivate(
        command,
        "showConfigKeyEditor",
        new Class<?>[] {Path.class, MetadataRegistry.ConfigKeyMetadata.class},
        Path.of("config.json"),
        null);
    assertThat(command.lines().size()).isEqualTo(before);
  }

  @Test
  void listViewport_navigationStaysWithinBounds() throws Exception {
    TutCommand.ListViewport viewport = new TutCommand.ListViewport(25, 10, 0);

    assertThat(viewport.selectedIndex()).isZero();
    assertThat(viewport.startIndex()).isZero();
    assertThat(viewport.endExclusive()).isEqualTo(10);

    viewport.applyNavigation(navKey("NAV_KEY_PAGE_DOWN"));
    assertThat(viewport.selectedIndex()).isEqualTo(10);
    assertThat(viewport.startIndex()).isEqualTo(1);

    viewport.applyNavigation(navKey("NAV_KEY_END"));
    assertThat(viewport.selectedIndex()).isEqualTo(24);
    assertThat(viewport.startIndex()).isEqualTo(15);

    viewport.applyNavigation(navKey("NAV_KEY_HOME"));
    assertThat(viewport.selectedIndex()).isZero();
    assertThat(viewport.startIndex()).isZero();

    int selectedBeforeOther = viewport.selectedIndex();
    viewport.applyNavigation(navKey("NAV_KEY_OTHER"));
    assertThat(viewport.selectedIndex()).isEqualTo(selectedBeforeOther);

    TutCommand.ListViewport tiny = new TutCommand.ListViewport(0, 1, 999);
    assertThat(tiny.selectedIndex()).isZero();
    assertThat(tiny.startIndex()).isZero();
    assertThat(tiny.endExclusive()).isEqualTo(1);
  }

  @Test
  void commandBehaviorFlagsAreDisabled() {
    CapturingTutCommand command = createCommand(Path.of("."), "");

    assertThat(command.shouldLoadConfig()).isFalse();
    assertThat(command.shouldResolveProjectRoot()).isFalse();
    assertThat(command.shouldApplyProjectRootToConfig()).isFalse();
    assertThat(command.shouldValidateProjectRoot()).isFalse();
    assertThat(command.shouldDisplayStartupBanner(Config.createDefault(), Path.of("."))).isFalse();
  }

  @Test
  void defaultConstructor_isInstantiable() {
    assertThat(new TutCommand()).isNotNull();
  }

  @Test
  void baseMethods_executeNestedCommandAndRealtimeCheck() {
    TutCommand command =
        new TutCommand(
            new BufferedReader(new StringReader("")),
            new CommandDispatcher(),
            new ConfigCommandService(MetadataRegistry.getDefault()),
            new ConfigValidationService(),
            new PlanBuilder());

    assertThat(command.executeNestedCliCommand(List.of("--help"))).isEqualTo(0);
    assertThat(command.shouldUseRealtimeInput()).isFalse();
    command.print(null);
    command.print("text");
    command.printInline(null);
    command.printInline("inline");
  }

  private static CapturingTutCommand createCommand(Path projectRoot, String input) {
    CapturingTutCommand command =
        new CapturingTutCommand(new BufferedReader(new StringReader(input == null ? "" : input)));
    command.setResourceBundle(new TestBundle());
    command.setProjectRoot(projectRoot);
    return command;
  }

  private static CapturingTutCommand createRealtimePreferredCommand(
      Path projectRoot, String input) {
    CapturingTutCommand command =
        new RealtimePreferredTutCommand(
            new BufferedReader(new StringReader(input == null ? "" : input)));
    command.setResourceBundle(new TestBundle());
    command.setProjectRoot(projectRoot);
    return command;
  }

  private static void writeConfig(Path projectRoot, String modelName) throws Exception {
    writeConfig(projectRoot, "openai", modelName, null, null, null);
  }

  private static void writeConfig(
      Path projectRoot, String modelName, String appName, String version) throws Exception {
    writeConfig(projectRoot, "openai", modelName, appName, version, null);
  }

  private static void writeConfig(
      Path projectRoot,
      String provider,
      String modelName,
      String appName,
      String version,
      String llmExtraJson)
      throws Exception {
    String normalizedRoot = projectRoot.toString().replace('\\', '/');
    String appNameSection = appName == null ? "" : "  \"AppName\": \"" + appName + "\",\n";
    String versionSection = version == null ? "" : "  \"version\": \"" + version + "\",\n";
    String llmExtrasSection = "";
    if (llmExtraJson != null && !llmExtraJson.isBlank()) {
      String normalized = llmExtraJson.strip().replace("\r\n", "\n").replace("\r", "\n");
      llmExtrasSection = ",\n            " + normalized.replace("\n", "\n            ");
    }
    String json =
        """
                {
        %s%s  "execution": { "per_task_isolation": false },
                  "llm": {
                    "provider": "%s",
                    "api_key": "dummy",
                    "fix_retries": 2,
                    "max_retries": 3,
                    "model_name": "%s"%s
                  },
                  "project": { "id": "test-project", "root": "%s" },
                  "selection_rules": {
                    "class_min_loc": 10,
                    "class_min_method_count": 1,
                    "exclude_getters_setters": true,
                    "method_max_loc": 2000,
                    "method_min_loc": 3
                  }
                }
                """
            .formatted(
                appNameSection,
                versionSection,
                provider == null ? "" : provider,
                modelName,
                llmExtrasSection,
                normalizedRoot);
    Files.writeString(projectRoot.resolve("config.json"), json);
  }

  private static void writeBrokenConfig(Path projectRoot) throws Exception {
    Files.writeString(projectRoot.resolve("config.json"), "{ invalid-json");
  }

  private static Object invokePrivate(
      Object target, String methodName, Class<?>[] parameterTypes, Object... args)
      throws Exception {
    Method method = TutCommand.class.getDeclaredMethod(methodName, parameterTypes);
    method.setAccessible(true);
    return method.invoke(target, args);
  }

  private static int navKey(String fieldName) throws Exception {
    Field field = TutCommand.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    return field.getInt(null);
  }

  private static int readNavigationKey(TutCommand command, String input) throws Exception {
    InputStreamReader reader =
        new InputStreamReader(
            new ByteArrayInputStream((input == null ? "" : input).getBytes(StandardCharsets.UTF_8)),
            StandardCharsets.UTF_8);
    return (int)
        invokePrivate(
            command, "readNavigationKey", new Class<?>[] {InputStreamReader.class}, reader);
  }

  private static class CapturingTutCommand extends TutCommand {
    private final List<String> lines = new ArrayList<>();
    private final List<String> inlines = new ArrayList<>();
    private final List<List<String>> executedCliArgs = new ArrayList<>();
    private int nestedExitCode;

    private CapturingTutCommand(BufferedReader reader) {
      super(
          reader,
          new CommandDispatcher(),
          new ConfigCommandService(MetadataRegistry.getDefault()),
          new ConfigValidationService(),
          new PlanBuilder());
      this.nestedExitCode = 0;
    }

    @Override
    protected void print(String message) {
      lines.add(message == null ? "" : message);
    }

    @Override
    protected void printInline(String message) {
      inlines.add(message == null ? "" : message);
    }

    @Override
    protected boolean shouldUseRealtimeInput() {
      return false;
    }

    @Override
    protected int executeNestedCliCommand(List<String> args) {
      executedCliArgs.add(List.copyOf(args));
      return nestedExitCode;
    }

    private void setNestedExitCode(int nestedExitCode) {
      this.nestedExitCode = nestedExitCode;
    }

    private List<String> lines() {
      return lines;
    }

    private List<String> inlines() {
      return inlines;
    }

    private List<List<String>> executedCliArgs() {
      return executedCliArgs;
    }
  }

  private static final class RealtimePreferredTutCommand extends CapturingTutCommand {
    private RealtimePreferredTutCommand(BufferedReader reader) {
      super(reader);
    }

    @Override
    protected boolean shouldUseRealtimeInput() {
      return true;
    }
  }

  static class TestBundle extends ListResourceBundle {
    @Override
    protected Object[][] getContents() {
      return new Object[][] {
        {"tut.prompt", "ful> "},
        {"tut.exit.eof", "EOF"},
        {"tut.exit", "EXIT"},
        {"tut.command.error", "ERR:{0}"},
        {"tut.model.current", "CURRENT provider={0}, model={1}"},
        {"tut.model.unset", "UNSET"},
        {"tut.model.updated", "UPDATED provider={0}, model={1}"},
        {"tut.model.hint.validate", "HINT validate"},
      };
    }
  }
}
