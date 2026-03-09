package com.craftsmanbro.fulcraft;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/**
 * E2E tests for configuration loading behavior through the real CLI process.
 *
 * <p>These tests execute {@code com.craftsmanbro.fulcraft.Main} in a forked JVM to verify config
 * resolution and schema validation from the user's perspective.
 */
class ConfigurationE2ETest {

  private static final long PROCESS_TIMEOUT_SECONDS = 120L;

  private record CliProcessResult(int exitCode, String stdout, String stderr) {}

  @Test
  @DisplayName("explicit --config with valid schema allows analyze --dry-run")
  void explicitConfig_allowsAnalyzeDryRun(@TempDir Path tempDir) throws Exception {
    Path workDir = tempDir.resolve("work");
    Files.createDirectories(workDir);
    Path projectDir = createAnalyzableProject(tempDir.resolve("project"));

    Path configFile = workDir.resolve("explicit-config.json");
    writeMinimumValidConfig(configFile, "explicit-e2e", "explicit-model", null);

    CliProcessResult result =
        runCli(
            workDir,
            "--config",
            configFile.toString(),
            "analyze",
            "--dry-run",
            projectDir.toString());

    assertThat(result.exitCode()).isEqualTo(0);
    assertThat(result.stdout()).contains("explicit-model");
    assertThat(result.stderr()).doesNotContain("Configuration file not found");
  }

  @Test
  @DisplayName("explicit --config fails when file does not exist")
  void explicitMissingConfig_fails(@TempDir Path tempDir) throws Exception {
    Path workDir = tempDir.resolve("work");
    Files.createDirectories(workDir);
    Path projectDir = createAnalyzableProject(tempDir.resolve("project"));

    Path missingConfig = workDir.resolve("missing-config.json");
    CliProcessResult result =
        runCli(
            workDir,
            "--config",
            missingConfig.toString(),
            "analyze",
            "--dry-run",
            projectDir.toString());

    assertThat(result.exitCode()).isEqualTo(CommandLine.ExitCode.SOFTWARE);
    assertThat(result.stderr()).contains("Configuration file not found");
  }

  @Test
  @DisplayName("schema validation errors are surfaced for invalid config")
  void invalidConfig_reportsSchemaValidationError(@TempDir Path tempDir) throws Exception {
    Path workDir = tempDir.resolve("work");
    Files.createDirectories(workDir);
    Path projectDir = createAnalyzableProject(tempDir.resolve("project"));

    Path configFile = workDir.resolve("invalid-config.json");
    Files.writeString(
        configFile,
        """
                {
                  "project": {
                    "id": "invalid-e2e"
                  },
                  "llm": {
                    "provider": "mock",
                    "modelName": "should_fail"
                  }
                }
                """,
        StandardCharsets.UTF_8);

    CliProcessResult result =
        runCli(
            workDir,
            "--config",
            configFile.toString(),
            "analyze",
            "--dry-run",
            projectDir.toString());

    assertThat(result.exitCode()).isEqualTo(CommandLine.ExitCode.SOFTWARE);
    assertThat(result.stderr()).contains("Config schema validation failed");
    assertThat(result.stderr()).contains("selection_rules");
  }

  @Test
  @DisplayName(".ful/config.json fallback is used when default config.json is missing")
  void dotFulFallbackConfig_isUsed(@TempDir Path tempDir) throws Exception {
    Path workDir = tempDir.resolve("work");
    Files.createDirectories(workDir);
    Path projectDir = createAnalyzableProject(tempDir.resolve("project"));

    Path fallbackConfig = workDir.resolve(".ful").resolve("config.json");
    Files.createDirectories(fallbackConfig.getParent());
    writeMinimumValidConfig(fallbackConfig, "fallback-e2e", "fallback-model", null);

    CliProcessResult result = runCli(workDir, "analyze", "--dry-run", projectDir.toString());

    assertThat(result.exitCode()).isEqualTo(0);
    assertThat(result.stdout()).contains("fallback-model");
  }

  @Test
  @DisplayName("default config.json is used when no explicit --config is provided")
  void defaultConfig_isUsed(@TempDir Path tempDir) throws Exception {
    Path workDir = tempDir.resolve("work");
    Files.createDirectories(workDir);
    Path projectDir = createAnalyzableProject(tempDir.resolve("project"));

    Path defaultConfig = workDir.resolve("config.json");
    writeMinimumValidConfig(defaultConfig, "default-e2e", "default-model", null);

    CliProcessResult result = runCli(workDir, "analyze", "--dry-run", projectDir.toString());

    assertThat(result.exitCode()).isEqualTo(0);
    assertThat(result.stdout()).contains("default-model");
  }

  @Test
  @DisplayName("--config takes precedence over config.json and .ful/config.json")
  void explicitConfig_takesPrecedence(@TempDir Path tempDir) throws Exception {
    Path workDir = tempDir.resolve("work");
    Files.createDirectories(workDir);
    Path projectDir = createAnalyzableProject(tempDir.resolve("project"));

    Path defaultConfig = workDir.resolve("config.json");
    writeMinimumValidConfig(defaultConfig, "default-e2e", "default-model", null);

    Path fallbackConfig = workDir.resolve(".ful").resolve("config.json");
    Files.createDirectories(fallbackConfig.getParent());
    writeMinimumValidConfig(fallbackConfig, "fallback-e2e", "fallback-model", null);

    Path explicitConfig = workDir.resolve("explicit-config.json");
    writeMinimumValidConfig(explicitConfig, "explicit-e2e", "explicit-model", null);

    CliProcessResult result =
        runCli(
            workDir,
            "--config",
            explicitConfig.toString(),
            "analyze",
            "--dry-run",
            projectDir.toString());

    assertThat(result.exitCode()).isEqualTo(0);
    assertThat(result.stdout()).contains("explicit-model");
    assertThat(result.stdout()).doesNotContain("default-model");
    assertThat(result.stdout()).doesNotContain("fallback-model");
  }

  @Test
  @DisplayName("CLI project root argument overrides config project.root")
  void cliProjectRoot_overridesConfigProjectRoot(@TempDir Path tempDir) throws Exception {
    Path workDir = tempDir.resolve("work");
    Files.createDirectories(workDir);
    Path projectDir = createAnalyzableProject(tempDir.resolve("project"));

    Path configFile = workDir.resolve("override-config.json");
    writeMinimumValidConfig(configFile, "override-e2e", "override-model", "/path/does/not/exist");

    CliProcessResult result =
        runCli(
            workDir,
            "--config",
            configFile.toString(),
            "analyze",
            "--dry-run",
            projectDir.toString());

    assertThat(result.exitCode()).isEqualTo(0);
    assertThat(result.stderr()).doesNotContain("Project root directory does not exist");
  }

  private CliProcessResult runCli(Path workDir, String... args) throws Exception {
    Path javaExecutable = resolveJavaExecutable();
    String classpath = System.getProperty("java.class.path");

    ProcessBuilder builder = new ProcessBuilder();
    builder.command(buildCommand(javaExecutable, classpath, args));
    builder.directory(workDir.toFile());
    builder.redirectErrorStream(false);

    Process process = builder.start();
    ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
    ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();

    Thread stdoutPump = pumpAsync(process.getInputStream(), stdoutBuffer);
    Thread stderrPump = pumpAsync(process.getErrorStream(), stderrBuffer);

    boolean finished = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    if (!finished) {
      process.destroyForcibly();
      throw new AssertionError("CLI process timed out");
    }

    stdoutPump.join(TimeUnit.SECONDS.toMillis(5));
    stderrPump.join(TimeUnit.SECONDS.toMillis(5));

    return new CliProcessResult(
        process.exitValue(),
        stdoutBuffer.toString(StandardCharsets.UTF_8),
        stderrBuffer.toString(StandardCharsets.UTF_8));
  }

  private String[] buildCommand(Path javaExecutable, String classpath, String... args) {
    String[] command = new String[4 + args.length];
    command[0] = javaExecutable.toString();
    command[1] = "-cp";
    command[2] = classpath;
    command[3] = "com.craftsmanbro.fulcraft.Main";
    System.arraycopy(args, 0, command, 4, args.length);
    return command;
  }

  private Thread pumpAsync(InputStream input, ByteArrayOutputStream output) {
    Thread thread =
        new Thread(
            () -> {
              byte[] buffer = new byte[8192];
              int read;
              try (input) {
                while ((read = input.read(buffer)) != -1) {
                  output.write(buffer, 0, read);
                }
              } catch (IOException ignored) {
                // Process stream may close during teardown.
              }
            },
            "configuration-e2e-stream-pump");
    thread.setDaemon(true);
    thread.start();
    return thread;
  }

  private Path resolveJavaExecutable() {
    String javaHome = System.getProperty("java.home");
    String javaBinary =
        System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
            ? "java.exe"
            : "java";
    Path candidate = Path.of(javaHome, "bin", javaBinary);
    return Files.isExecutable(candidate) ? candidate : Path.of(javaBinary);
  }

  private Path createAnalyzableProject(Path projectDir) throws IOException {
    Path source = projectDir.resolve("src/main/java/com/example/fixture/Sample.java");
    Files.createDirectories(source.getParent());
    Files.writeString(
        source,
        """
                package com.example.fixture;

                public class Sample {
                    public int plusOne(int value) {
                        return value + 1;
                    }
                }
                """,
        StandardCharsets.UTF_8);
    Files.writeString(
        projectDir.resolve("build.gradle"),
        """
                plugins {
                    id 'java'
                }
                """,
        StandardCharsets.UTF_8);
    return projectDir;
  }

  private void writeMinimumValidConfig(
      Path configFile, String projectId, String modelName, String projectRoot) throws IOException {
    String rootField =
        projectRoot == null ? "" : ",\n    \"root\": \"" + escapeJson(projectRoot) + "\"";
    String modelField =
        modelName == null ? "" : ",\n    \"model_name\": \"" + escapeJson(modelName) + "\"";
    Files.writeString(
        configFile,
        """
                {
                  "project": {
                    "id": "%s"%s
                  },
                  "selection_rules": {
                    "class_min_loc": 0,
                    "class_min_method_count": 0,
                    "method_min_loc": 0,
                    "method_max_loc": 300
                  },
                  "llm": {
                    "provider": "mock"%s
                  }
                }
                """
            .formatted(escapeJson(projectId), rootField, modelField),
        StandardCharsets.UTF_8);
  }

  private String escapeJson(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
