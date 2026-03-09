package com.craftsmanbro.fulcraft.ui.cli.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.json.impl.JsonMapperFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ListResourceBundle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.mockito.MockedStatic;
import picocli.CommandLine;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Isolated
class InitCommandTest {

  @Test
  void commandDisablesBaseCommandSetup() {
    InitCommand command = new InitCommand();
    assertThat(command.shouldLoadConfig()).isFalse();
    assertThat(command.shouldResolveProjectRoot()).isFalse();
    assertThat(command.shouldValidateProjectRoot()).isFalse();
    assertThat(command.shouldApplyProjectRootToConfig()).isFalse();
  }

  @Test
  void defaultConstructor_canBeCreated() {
    assertThat(new InitCommand()).isNotNull();
  }

  @Test
  void doCall_returnsOne_whenConfigAlreadyExistsAndForceIsFalse(@TempDir Path tempDir)
      throws IOException {
    Path configPath = tempDir.resolve("config.json");
    Files.writeString(configPath, "{\"existing\":true}", StandardCharsets.UTF_8);

    InitCommand command = newCommandWithInputs("");
    command.setResourceBundle(new TestBundle());
    new CommandLine(command).parseArgs("-d", tempDir.toString());

    int exitCode = command.doCall(new Config(), tempDir);

    assertThat(exitCode).isEqualTo(1);
    assertThat(Files.readString(configPath, StandardCharsets.UTF_8))
        .isEqualTo("{\"existing\":true}");
  }

  @Test
  void doCall_overwritesConfig_whenForceEnabled(@TempDir Path tempDir) throws Exception {
    Path configPath = tempDir.resolve("config.json");
    Files.writeString(configPath, "{\"old\":true}", StandardCharsets.UTF_8);

    InitCommand command = newCommandWithInputs("\n4\n\n\n\n");
    command.setResourceBundle(new TestBundle());
    new CommandLine(command).parseArgs("-d", tempDir.toString(), "--force");

    int exitCode = command.doCall(new Config(), tempDir);

    assertThat(exitCode).isEqualTo(0);
    JsonNode root = readConfig(configPath);
    assertThat(root.path("llm").path("provider").asText()).isEqualTo("local");
    assertThat(root.path("llm").path("model_name").asText()).isEqualTo("llama3.1");
    assertThat(root.path("llm").path("url").asText()).isEqualTo("http://localhost:11434");
  }

  @Test
  void doCall_returnsOne_whenConfigCannotBeWritten(@TempDir Path tempDir) throws IOException {
    Path blockedDirectory = tempDir.resolve("blocked");
    Files.writeString(blockedDirectory, "not-a-directory", StandardCharsets.UTF_8);

    InitCommand command = newCommandWithInputs("\n4\n\n\n\n");
    command.setResourceBundle(new TestBundle());
    new CommandLine(command).parseArgs("-d", blockedDirectory.toString());

    int exitCode = command.doCall(new Config(), tempDir);

    assertThat(exitCode).isEqualTo(1);
    assertThat(Files.isRegularFile(blockedDirectory)).isTrue();
  }

  @Test
  void doCall_usesDefaults_whenReaderThrowsIOException(@TempDir Path tempDir) throws Exception {
    InitCommand command = new InitCommand(new ThrowingReader());
    command.setResourceBundle(new TestBundle());
    new CommandLine(command).parseArgs("-d", tempDir.toString());

    int exitCode = command.doCall(new Config(), tempDir);

    assertThat(exitCode).isEqualTo(0);
    JsonNode root = readConfig(tempDir.resolve("config.json"));
    assertThat(root.path("project").path("root").asText())
        .isEqualTo(tempDir.toAbsolutePath().normalize().toString());
    assertThat(root.path("project").path("docs_output").asText()).isEqualTo("docs");
    assertThat(root.path("llm").path("provider").asText()).isEqualTo("gemini");
    assertThat(root.path("llm").path("model_name").asText()).isEqualTo("gemini-2.0-flash-exp");
  }

  @Test
  void promptProjectRoot_usesCustomInput_whenProvided(@TempDir Path tempDir) throws Exception {
    Path customRoot = tempDir.resolve("custom/project").toAbsolutePath();

    InitCommand command = newCommandWithInputs(customRoot + "\n");
    command.setResourceBundle(new TestBundle());
    new CommandLine(command).parseArgs("-d", tempDir.toString());

    Path resolvedProjectRoot = (Path) invoke(command, "promptProjectRoot");
    assertThat(resolvedProjectRoot).isEqualTo(customRoot.normalize());
  }

  @Test
  void promptProvider_mapsChoicesToProviders() throws Exception {
    assertThat(invokePromptProvider("2\n")).isEqualTo("openai");
    assertThat(invokePromptProvider("3\n")).isEqualTo("anthropic");
    assertThat(invokePromptProvider("4\n")).isEqualTo("local");
    assertThat(invokePromptProvider("unknown\n")).isEqualTo("gemini");
  }

  @Test
  void promptModel_usesCustomValue_whenProvided() throws Exception {
    InitCommand command = newCommandWithInputs("my-custom-model\n");
    command.setResourceBundle(new TestBundle());
    String model = (String) invoke(command, "promptModel", String.class, "openai");

    assertThat(model).isEqualTo("my-custom-model");
  }

  @Test
  void promptDocsOutput_usesCustomValue_whenProvided() throws Exception {
    InitCommand command = newCommandWithInputs("my-docs\n");
    command.setResourceBundle(new TestBundle());
    String docsOutput = (String) invoke(command, "promptDocsOutput");

    assertThat(docsOutput).isEqualTo("my-docs");
  }

  @Test
  void promptApiKeyFromEnv_returnsPlaceholderByDefault() throws Exception {
    InitCommand command = newCommandWithInputs("\n");
    command.setResourceBundle(new TestBundle());

    String apiKey = (String) invoke(command, "promptApiKeyFromEnv", String.class, "OPENAI_API_KEY");
    assertThat(apiKey).isEqualTo("${OPENAI_API_KEY}");
  }

  @Test
  void promptApiKeyFromEnv_repromptsAndAllowsManualEntry() throws Exception {
    InitCommand command = newCommandWithInputs("invalid\nn\nmanual-key\n");
    command.setResourceBundle(new TestBundle());

    String apiKey = (String) invoke(command, "promptApiKeyFromEnv", String.class, "OPENAI_API_KEY");
    assertThat(apiKey).isEqualTo("manual-key");
  }

  @Test
  void promptApiKeyManually_returnsNull_whenBlankInput() throws Exception {
    InitCommand command = newCommandWithInputs("\n");
    command.setResourceBundle(new TestBundle());

    String apiKey =
        (String) invoke(command, "promptApiKeyManually", String.class, "OPENAI_API_KEY");
    assertThat(apiKey).isNull();
  }

  @Test
  void getEnvVarName_resolvesKnownProvidersAndDefault() throws Exception {
    InitCommand command = newCommandWithInputs("");
    assertThat((String) invoke(command, "getEnvVarName", String.class, "openai"))
        .isEqualTo("OPENAI_API_KEY");
    assertThat((String) invoke(command, "getEnvVarName", String.class, "anthropic"))
        .isEqualTo("ANTHROPIC_API_KEY");
    assertThat((String) invoke(command, "getEnvVarName", String.class, "local"))
        .isEqualTo("GEMINI_API_KEY");
    assertThat((String) invoke(command, "getEnvVarName", String.class, "gemini"))
        .isEqualTo("GEMINI_API_KEY");
  }

  @Test
  void getDefaultModel_resolvesKnownProvidersAndDefault() throws Exception {
    InitCommand command = newCommandWithInputs("");
    assertThat((String) invoke(command, "getDefaultModel", String.class, "openai"))
        .isEqualTo("gpt-4o");
    assertThat((String) invoke(command, "getDefaultModel", String.class, "anthropic"))
        .isEqualTo("claude-sonnet-4-20250514");
    assertThat((String) invoke(command, "getDefaultModel", String.class, "local"))
        .isEqualTo("llama3.1");
    assertThat((String) invoke(command, "getDefaultModel", String.class, "gemini"))
        .isEqualTo("gemini-2.0-flash-exp");
  }

  @Test
  void generateConfigJson_returnsEmptyObject_whenSerializationFails() throws Exception {
    InitCommand command = newCommandWithInputs("");
    ObjectMapper mapper = mock(ObjectMapper.class);
    when(mapper.writeValueAsString(any())).thenThrow(new JacksonException("boom") {});

    try (MockedStatic<JsonMapperFactory> ignored = mockStatic(JsonMapperFactory.class)) {
      ignored.when(JsonMapperFactory::createPrettyPrinter).thenReturn(mapper);

      String json =
          (String)
              invoke(
                  command,
                  "generateConfigJson",
                  new Class<?>[] {
                    Path.class, String.class, String.class, String.class, String.class
                  },
                  Path.of("/tmp/project"),
                  "docs",
                  "gemini",
                  "api-key",
                  "model");

      assertThat(json).isEqualTo("{}");
    }
  }

  private static InitCommand newCommandWithInputs(String inputs) {
    return new InitCommand(new BufferedReader(new StringReader(inputs)));
  }

  private static JsonNode readConfig(Path configPath) throws IOException {
    return JsonMapperFactory.create()
        .readTree(Files.readString(configPath, StandardCharsets.UTF_8));
  }

  private static String invokePromptProvider(String input) throws Exception {
    InitCommand command = newCommandWithInputs(input);
    command.setResourceBundle(new TestBundle());
    return (String) invoke(command, "promptProvider");
  }

  private static Object invoke(InitCommand command, String methodName) throws Exception {
    Method method = InitCommand.class.getDeclaredMethod(methodName);
    method.setAccessible(true);
    return method.invoke(command);
  }

  private static Object invoke(InitCommand command, String methodName, Class<?> argType, Object arg)
      throws Exception {
    Method method = InitCommand.class.getDeclaredMethod(methodName, argType);
    method.setAccessible(true);
    return method.invoke(command, arg);
  }

  private static Object invoke(
      InitCommand command, String methodName, Class<?>[] argTypes, Object... args)
      throws Exception {
    Method method = InitCommand.class.getDeclaredMethod(methodName, argTypes);
    method.setAccessible(true);
    return method.invoke(command, args);
  }

  private static final class ThrowingReader extends BufferedReader {
    private ThrowingReader() {
      super(new StringReader(""));
    }

    @Override
    public String readLine() throws IOException {
      throw new IOException("simulated read failure");
    }
  }

  static class TestBundle extends ListResourceBundle {
    @Override
    protected Object[][] getContents() {
      return new Object[][] {
        {"init.wizard.title", "Init Wizard"},
        {"init.prompt.project_root.title", "Project Root"},
        {"init.prompt.project_root.hint", "hint"},
        {"init.prompt.default_prefix", "Default: "},
        {"init.prompt.input_hint", "Input: "},
        {"init.prompt.arrow", "-> "},
        {"init.prompt.will_use_suffix", " will be used"},
        {"init.prompt.provider.title", "Provider"},
        {"init.prompt.provider.gemini", "1. Gemini"},
        {"init.prompt.provider.openai", "2. OpenAI"},
        {"init.prompt.provider.anthropic", "3. Anthropic"},
        {"init.prompt.provider.local", "4. Local"},
        {"init.prompt.provider.selected", "Selected: {0}"},
        {"init.prompt.apikey.title", "API Key"},
        {"init.prompt.apikey.detected", "Detected {0}"},
        {"init.prompt.apikey.reference", "Ref {0}"},
        {"init.prompt.apikey.use_env", "Use env? (Y/n)"},
        {"init.prompt.apikey.use_env.invalid", "Invalid input"},
        {"init.prompt.apikey.manual", "Enter manually"},
        {"init.prompt.apikey.env_hint", "Env: {0}"},
        {"init.prompt.apikey.not_set", "Not set"},
        {"init.prompt.apikey.set", "Set"},
        {"init.prompt.model.title", "Model"},
        {"init.prompt.model.using_default", "Using default"},
        {"init.prompt.docs.title", "Docs Output"},
        {"init.prompt.docs.hint", "hint"},
        {"init.success.saved", "Saved to {0}"},
        {"init.success.next_steps", "Next steps"},
        {"init.success.run_generate", "Run generate"},
        {"init.success.run_generate_cmd", "cmd"},
        {"init.success.run_specific", "Run specific"},
        {"init.success.run_specific_cmd", "cmd"},
        {"init.success.customize", "Customize"},
        {"init.config.exists", "Config exists at {0}"},
        {"init.config.use_force", "Use --force to overwrite"},
        {"init.error.create_failed", "Create failed: {0}"}
      };
    }
  }
}
