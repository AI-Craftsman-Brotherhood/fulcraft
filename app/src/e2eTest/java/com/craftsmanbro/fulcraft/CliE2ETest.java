package com.craftsmanbro.fulcraft;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/**
 * E2E tests for the CLI entry point.
 *
 * <p>These tests run the CLI in a forked JVM process to validate behavior closer to real
 * command-line usage, including exit codes and filesystem artifacts.
 */
class CliE2ETest {

  private static final long PROCESS_TIMEOUT_SECONDS = 120L;
  private static final List<String> DEMO_FIXTURE_NAMES =
      List.of(
          "basic-if-switch",
          "basic-loops",
          "basic-exception",
          "basic-collections",
          "basic-encapsulation",
          "basic-inheritance",
          "oop-abstraction",
          "oop-polymorphism",
          "oop-composition",
          "oop-generics",
          "oop-enum-behavior",
          "oop-record",
          "oop-sealed-hierarchy",
          "oop-default-method",
          "oop-nested-class",
          "oop-builder-pattern",
          "oop-annotation",
          "oop-factory-method",
          "oop-strategy-pattern",
          "oop-state-pattern",
          "oop-observer-pattern");
  private static final String ENGLISH_DESCRIPTION =
      "Tools for generating unit tests for Java legacy code.";
  private static final String JAPANESE_DESCRIPTION = "Javaレガシーコード向けのユニットテスト自動生成ツールです。";

  private record CliProcessResult(int exitCode, String stdout, String stderr) {}

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
            "cli-e2e-stream-pump");
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

  private Path resolveFixtureProject(String fixtureName) {
    Path[] candidates = {
      Path.of("src/e2eTest/resources/projects", fixtureName),
      Path.of("app/src/e2eTest/resources/projects", fixtureName)
    };
    for (Path candidate : candidates) {
      Path normalized = candidate.toAbsolutePath().normalize();
      if (Files.isDirectory(normalized)) {
        return normalized;
      }
    }
    throw new IllegalStateException("Fixture project not found: " + fixtureName);
  }

  private Path prepareAnalyzableFixture(Path tempDir, String fixtureName) throws IOException {
    Path source = resolveFixtureProject(fixtureName);
    Path target = tempDir.resolve(fixtureName);

    try (var stream = Files.walk(source)) {
      for (Path sourcePath : (Iterable<Path>) stream::iterator) {
        Path relative = source.relativize(sourcePath);
        Path normalizedRelative = normalizeFixtureRelativePath(relative);
        Path targetPath = target.resolve(normalizedRelative.toString());
        if (Files.isDirectory(sourcePath)) {
          Files.createDirectories(targetPath);
        } else {
          Files.createDirectories(targetPath.getParent());
          Files.copy(sourcePath, targetPath);
        }
      }
    }

    Files.writeString(
        target.resolve("build.gradle"),
        """
                plugins {
                    id 'java'
                }
                """,
        StandardCharsets.UTF_8);
    return target;
  }

  private Path normalizeFixtureRelativePath(Path relative) {
    String normalized = relative.toString();
    if (normalized.endsWith(".java.txt")) {
      normalized = normalized.substring(0, normalized.length() - ".txt".length());
    }
    return Path.of(normalized);
  }

  private Path writeMinimumValidConfig(Path configFile, String projectId) throws IOException {
    Files.writeString(
        configFile,
        """
                {
                  "project": {
                    "id": "%s"
                  },
                  "selection_rules": {
                    "class_min_loc": 0,
                    "class_min_method_count": 0,
                    "method_min_loc": 0,
                    "method_max_loc": 300
                  },
                  "llm": {
                    "provider": "mock"
                  }
                }
                """
            .formatted(projectId),
        StandardCharsets.UTF_8);
    return configFile;
  }

  private Path resolveSingleRunDirectory(Path runsRoot) throws IOException {
    assertThat(runsRoot).isDirectory();
    try (var stream = Files.list(runsRoot)) {
      return stream.filter(Files::isDirectory).findFirst().orElseThrow();
    }
  }

  private Path resolveTasksFile(Path planDir) {
    List<Path> candidates =
        List.of(
            planDir.resolve("tasks.jsonl"),
            planDir.resolve("tasks.json"),
            planDir.resolve("tasks.yaml"),
            planDir.resolve("tasks.yml"));
    for (Path candidate : candidates) {
      if (Files.isRegularFile(candidate)) {
        return candidate;
      }
    }
    throw new AssertionError("No tasks file found under: " + planDir);
  }

  private void assertAnalysisArtifacts(Path runDir) throws IOException {
    Path analysisDir = runDir.resolve("analysis");
    assertThat(analysisDir).isDirectory();
    assertThat(analysisDir.resolve("type_resolution_summary.json")).exists();
    try (var analysisFiles = Files.walk(analysisDir)) {
      assertThat(
              analysisFiles
                  .filter(Files::isRegularFile)
                  .anyMatch(path -> path.getFileName().toString().startsWith("analysis_")))
          .isTrue();
    }
  }

  private void assertRunPipelineArtifacts(Path runDir) throws IOException {
    assertAnalysisArtifacts(runDir);

    Path reportFile = runDir.resolve("report").resolve("report.md");
    assertThat(reportFile).exists();

    Path docsDir = runDir.resolve("docs");
    assertThat(docsDir).isDirectory();
    try (var docs = Files.walk(docsDir)) {
      assertThat(
              docs.filter(Files::isRegularFile)
                  .anyMatch(
                      path ->
                          path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".html")))
          .isTrue();
    }

    Path exploreDir = runDir.resolve("explore");
    assertThat(exploreDir.resolve("index.html")).exists();
    assertThat(exploreDir.resolve("explore_snapshot.json")).exists();
  }

  @Nested
  @DisplayName("Help and Version")
  class HelpAndVersionTests {

    @Test
    @DisplayName("ful --help returns exit code 0 and prints usage")
    void help_printsUsageAndExitsSuccessfully(@TempDir Path tempDir) throws Exception {
      CliProcessResult result = runCli(tempDir, "--help");

      assertThat(result.exitCode()).isEqualTo(0);
      assertThat(result.stdout()).contains("Usage: ful");
      assertThat(result.stdout()).contains("Commands:");
    }

    @Test
    @DisplayName("ful --version returns exit code 0 and prints version info")
    void version_printsVersionAndExitsSuccessfully(@TempDir Path tempDir) throws Exception {
      CliProcessResult result = runCli(tempDir, "--version");

      assertThat(result.exitCode()).isEqualTo(0);
      assertThat(result.stdout()).isNotBlank();
    }

    @Test
    @DisplayName("ful (no args) prints usage and returns USAGE exit code")
    void noArgs_printsUsageAndReturnsUsageExitCode(@TempDir Path tempDir) throws Exception {
      CliProcessResult result = runCli(tempDir);

      assertThat(result.exitCode()).isEqualTo(CommandLine.ExitCode.USAGE);
      assertThat(result.stdout()).contains("Usage: ful");
    }
  }

  @Nested
  @DisplayName("Invalid Arguments")
  class InvalidArgumentTests {

    @Test
    @DisplayName("ful --unknown-option returns invalid input exit code")
    void unknownOption_returnsInvalidInputExitCode(@TempDir Path tempDir) throws Exception {
      CliProcessResult result = runCli(tempDir, "--this-option-does-not-exist");

      assertThat(result.exitCode()).isEqualTo(CommandLine.ExitCode.USAGE);
      assertThat(result.stderr()).contains("Unknown option");
    }

    @Test
    @DisplayName("ful nonexistent-subcommand returns invalid input exit code")
    void unknownSubcommand_returnsInvalidInputExitCode(@TempDir Path tempDir) throws Exception {
      CliProcessResult result = runCli(tempDir, "nonexistent-subcommand-xyz");

      assertThat(result.exitCode()).isEqualTo(CommandLine.ExitCode.USAGE);
      assertThat(result.stderr()).contains("Unmatched argument");
    }
  }

  @Nested
  @DisplayName("Analyze Command")
  class AnalyzeCommandTests {

    @Test
    @DisplayName("ful analyze --help prints analyze usage")
    void analyzeHelp_printsUsage(@TempDir Path tempDir) throws Exception {
      CliProcessResult result = runCli(tempDir, "analyze", "--help");

      assertThat(result.exitCode()).isEqualTo(0);
      assertThat(result.stdout()).containsIgnoringCase("analyze");
    }

    @Test
    @DisplayName("ful analyze with fixture project succeeds and writes analysis artifacts")
    void analyzeFixtureProject_writesArtifacts(@TempDir Path tempDir) throws Exception {
      Path workDir = tempDir.resolve("work");
      Files.createDirectories(workDir);
      Path configFile = workDir.resolve("config.json");
      writeMinimumValidConfig(configFile, "e2e-project");

      Path fixtureProject = prepareAnalyzableFixture(tempDir, "simple-java");
      CliProcessResult result =
          runCli(workDir, "--config", configFile.toString(), "analyze", fixtureProject.toString());

      assertThat(result.exitCode())
          .withFailMessage(
              "analyze command failed with exit=%s%nstdout:%n%s%n---%nstderr:%n%s",
              result.exitCode(), result.stdout(), result.stderr())
          .isEqualTo(0);
      assertThat(result.stdout()).doesNotContain("Exception in thread");
      assertThat(result.stderr()).doesNotContain("Exception in thread");

      Path runsRoot = workDir.resolve(".ful").resolve("runs");
      Path runDir = resolveSingleRunDirectory(runsRoot);

      Path analysisDir = runDir.resolve("analysis");
      Path shard = analysisDir.resolve("com/example/analysis_Calculator.json");
      Path summary = analysisDir.resolve("type_resolution_summary.json");
      assertThat(analysisDir).isDirectory();
      assertThat(shard).exists();
      assertThat(summary).exists();
      assertThat(Files.readString(shard, StandardCharsets.UTF_8)).contains("Calculator");
    }

    @Test
    @DisplayName("ful analyze succeeds for all demo fixture projects")
    void analyzeDemoFixtures_succeeds(@TempDir Path tempDir) throws Exception {
      for (String fixtureName : DEMO_FIXTURE_NAMES) {
        Path workDir = tempDir.resolve("work-" + fixtureName);
        Files.createDirectories(workDir);
        Path configFile = workDir.resolve("config.json");
        writeMinimumValidConfig(configFile, "e2e-" + fixtureName);

        Path fixtureProject = prepareAnalyzableFixture(tempDir, fixtureName);
        CliProcessResult result =
            runCli(
                workDir, "--config", configFile.toString(), "analyze", fixtureProject.toString());

        assertThat(result.exitCode())
            .withFailMessage(
                "analyze failed for fixture=%s with exit=%s%nstdout:%n%s%n---%nstderr:%n%s",
                fixtureName, result.exitCode(), result.stdout(), result.stderr())
            .isEqualTo(0);
        assertThat(result.stdout()).doesNotContain("Exception in thread");
        assertThat(result.stderr()).doesNotContain("Exception in thread");

        Path runDir = resolveSingleRunDirectory(workDir.resolve(".ful").resolve("runs"));
        assertAnalysisArtifacts(runDir);
      }
    }
  }

  @Nested
  @DisplayName("Run Command")
  class RunCommandTests {

    @Test
    @DisplayName("ful run executes default pipeline and writes report/document/explore artifacts")
    void runDefaultPipeline_writesArtifacts(@TempDir Path tempDir) throws Exception {
      Path workDir = tempDir.resolve("work");
      Files.createDirectories(workDir);
      Path configFile = workDir.resolve("config.json");
      writeMinimumValidConfig(configFile, "run-e2e-project");

      Path fixtureProject = prepareAnalyzableFixture(tempDir, "simple-java");
      CliProcessResult result =
          runCli(workDir, "--config", configFile.toString(), "run", fixtureProject.toString());

      assertThat(result.exitCode())
          .withFailMessage(
              "run command failed with exit=%s%nstdout:%n%s%n---%nstderr:%n%s",
              result.exitCode(), result.stdout(), result.stderr())
          .isEqualTo(0);

      Path runDir = resolveSingleRunDirectory(workDir.resolve(".ful").resolve("runs"));
      assertRunPipelineArtifacts(runDir);
    }

    @Test
    @DisplayName("ful run succeeds for all demo fixture projects")
    void runDemoFixtures_writeArtifacts(@TempDir Path tempDir) throws Exception {
      for (String fixtureName : DEMO_FIXTURE_NAMES) {
        Path workDir = tempDir.resolve("work-run-" + fixtureName);
        Files.createDirectories(workDir);
        Path configFile = workDir.resolve("config.json");
        writeMinimumValidConfig(configFile, "run-e2e-" + fixtureName);

        Path fixtureProject = prepareAnalyzableFixture(tempDir, fixtureName);
        CliProcessResult result =
            runCli(workDir, "--config", configFile.toString(), "run", fixtureProject.toString());

        assertThat(result.exitCode())
            .withFailMessage(
                "run command failed for fixture=%s with exit=%s%nstdout:%n%s%n---%nstderr:%n%s",
                fixtureName, result.exitCode(), result.stdout(), result.stderr())
            .isEqualTo(0);

        Path runDir = resolveSingleRunDirectory(workDir.resolve(".ful").resolve("runs"));
        assertRunPipelineArtifacts(runDir);
      }
    }
  }

  @Nested
  @DisplayName("JUnit Plugin Commands")
  class JunitPluginCommandTests {

    @Test
    @DisplayName("ful junit-select writes tasks file under run plan directory")
    void junitSelect_writesTasksFile(@TempDir Path tempDir) throws Exception {
      Path workDir = tempDir.resolve("work");
      Files.createDirectories(workDir);
      Path configFile = workDir.resolve("config.json");
      writeMinimumValidConfig(configFile, "select-e2e-project");

      Path fixtureProject = prepareAnalyzableFixture(tempDir, "simple-java");
      CliProcessResult result =
          runCli(
              workDir,
              "--config",
              configFile.toString(),
              "junit-select",
              fixtureProject.toString());

      assertThat(result.exitCode())
          .withFailMessage(
              "junit-select failed with exit=%s%nstdout:%n%s%n---%nstderr:%n%s",
              result.exitCode(), result.stdout(), result.stderr())
          .isEqualTo(0);

      Path runDir = resolveSingleRunDirectory(workDir.resolve(".ful").resolve("runs"));
      Path planDir = runDir.resolve("plan");
      Path tasksFile = resolveTasksFile(planDir);
      assertThat(tasksFile).exists();
      assertThat(Files.readString(tasksFile, StandardCharsets.UTF_8)).isNotBlank();
    }

    @Test
    @DisplayName("ful junit-select succeeds for all demo fixture projects")
    void junitSelectDemoFixtures_writesTasksFile(@TempDir Path tempDir) throws Exception {
      for (String fixtureName : DEMO_FIXTURE_NAMES) {
        Path workDir = tempDir.resolve("work-select-" + fixtureName);
        Files.createDirectories(workDir);
        Path configFile = workDir.resolve("config.json");
        writeMinimumValidConfig(configFile, "select-e2e-" + fixtureName);

        Path fixtureProject = prepareAnalyzableFixture(tempDir, fixtureName);
        CliProcessResult result =
            runCli(
                workDir,
                "--config",
                configFile.toString(),
                "junit-select",
                fixtureProject.toString());

        assertThat(result.exitCode())
            .withFailMessage(
                "junit-select failed for fixture=%s with exit=%s%nstdout:%n%s%n---%nstderr:%n%s",
                fixtureName, result.exitCode(), result.stdout(), result.stderr())
            .isEqualTo(0);

        Path runDir = resolveSingleRunDirectory(workDir.resolve(".ful").resolve("runs"));
        Path planDir = runDir.resolve("plan");
        Path tasksFile = resolveTasksFile(planDir);
        assertThat(tasksFile).exists();
        assertThat(Files.readString(tasksFile, StandardCharsets.UTF_8)).isNotBlank();
      }
    }
  }

  @Nested
  @DisplayName("Localization")
  class LocalizationTests {

    @Test
    @DisplayName("ful --lang=ja --help renders Japanese localized description")
    void japaneseLocale_rendersLocalizedHelp(@TempDir Path tempDir) throws Exception {
      CliProcessResult result = runCli(tempDir, "--lang=ja", "--help");

      assertThat(result.exitCode()).isEqualTo(0);
      assertThat(result.stdout()).contains(JAPANESE_DESCRIPTION);
    }

    @Test
    @DisplayName("ful --lang=en --help renders English localized description")
    void englishLocale_rendersLocalizedHelp(@TempDir Path tempDir) throws Exception {
      CliProcessResult result = runCli(tempDir, "--lang=en", "--help");

      assertThat(result.exitCode()).isEqualTo(0);
      assertThat(result.stdout()).contains(ENGLISH_DESCRIPTION);
    }
  }

  @Nested
  @DisplayName("Command Listing")
  class CommandListingTests {

    @Test
    @DisplayName("help output lists key subcommands")
    void help_listsKeySubcommands(@TempDir Path tempDir) throws Exception {
      CliProcessResult result = runCli(tempDir, "--help");

      assertThat(result.exitCode()).isEqualTo(0);
      assertThat(result.stdout()).contains("analyze");
      assertThat(result.stdout()).contains("run");
      assertThat(result.stdout()).contains("explore");
    }
  }
}
