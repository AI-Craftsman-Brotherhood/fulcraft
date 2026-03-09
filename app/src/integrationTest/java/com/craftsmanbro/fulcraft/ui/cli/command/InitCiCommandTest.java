package com.craftsmanbro.fulcraft.ui.cli.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.ui.cli.UiLogger;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ListResourceBundle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InitCiCommandTest {

  @Test
  void call_inDryRun_replacesTemplatePlaceholders() throws Exception {
    CapturingInitCiCommand command = new CapturingInitCiCommand();
    setField(command, "githubActions", true);
    setField(command, "dryRun", true);
    setField(command, "comment", false);
    setField(command, "qualityGate", false);
    setField(command, "coverageThreshold", 85);

    int exitCode = command.call();

    assertThat(exitCode).isEqualTo(0);

    String output = String.join("\n", command.lines());
    assertThat(output).contains("Disabled by init-ci --no-comment");
    assertThat(output).contains("Disabled by init-ci --no-quality-gate");
    assertThat(output).contains("coverage_threshold: 0.85");
  }

  @Test
  void doCall_returnsError_whenGithubActionsFlagIsNotSet() {
    CapturingInitCiCommand command = new CapturingInitCiCommand();

    int exitCode = command.doCall(new Config(), Path.of("."));

    assertThat(exitCode).isEqualTo(1);
  }

  @Test
  void doCall_returnsError_whenOutputFileExistsWithoutForce(@TempDir Path tempDir)
      throws Exception {
    Path output = tempDir.resolve("ful-quality-gate.yml");
    Files.writeString(output, "existing");

    CapturingInitCiCommand command = new CapturingInitCiCommand();
    setField(command, "githubActions", true);
    setField(command, "outputPath", output);

    int exitCode = command.doCall(new Config(), tempDir);

    assertThat(exitCode).isEqualTo(1);
    assertThat(Files.readString(output)).isEqualTo("existing");
  }

  @Test
  void doCall_overwritesOutputFile_whenForceEnabled(@TempDir Path tempDir) throws Exception {
    Path output = tempDir.resolve("ful-quality-gate.yml");
    Files.writeString(output, "outdated");

    CapturingInitCiCommand command = new CapturingInitCiCommand();
    setField(command, "githubActions", true);
    setField(command, "outputPath", output);
    setField(command, "force", true);
    setField(command, "comment", false);
    setField(command, "qualityGate", false);
    setField(command, "coverageThreshold", 90);

    int exitCode = command.doCall(new Config(), tempDir);

    assertThat(exitCode).isEqualTo(0);
    String updated = Files.readString(output);
    assertThat(updated).contains("Disabled by init-ci --no-comment");
    assertThat(updated).contains("Disabled by init-ci --no-quality-gate");
    assertThat(updated).contains("coverage_threshold: 0.9");
  }

  @Test
  void doCall_writesEnabledQualityGateSettings_whenToolsProvided(@TempDir Path tempDir)
      throws Exception {
    Path output = tempDir.resolve("ci/ful-quality-gate.yml");

    CapturingInitCiCommand command = new CapturingInitCiCommand();
    setField(command, "githubActions", true);
    setField(command, "outputPath", output);
    setField(command, "comment", true);
    setField(command, "qualityGate", true);
    setField(command, "coverageTool", "cobertura");
    setField(command, "staticAnalysisTools", List.of("spotbugs", "checkstyle"));

    int exitCode = command.doCall(new Config(), tempDir);

    assertThat(exitCode).isEqualTo(0);
    String generated = Files.readString(output);
    assertThat(generated).contains("if: github.event_name == 'pull_request' && always()");
    assertThat(generated).doesNotContain("Disabled by init-ci --no-comment");
    assertThat(generated).doesNotContain("Disabled by init-ci --no-quality-gate");

    String console = String.join("\n", command.lines());
    assertThat(console).contains("Coverage Tool: cobertura");
    assertThat(console).contains("Static Analysis: spotbugs, checkstyle");
    assertThat(console).contains("Quality Gate: enabled");
    assertThat(console).doesNotContain("Coverage Threshold:");
  }

  @Test
  void doCall_printsDefaultStaticAnalysis_whenToolListIsEmpty(@TempDir Path tempDir)
      throws Exception {
    Path output = tempDir.resolve("ci/ful-quality-gate.yml");

    CapturingInitCiCommand command = new CapturingInitCiCommand();
    setField(command, "githubActions", true);
    setField(command, "outputPath", output);
    setField(command, "qualityGate", true);
    setField(command, "staticAnalysisTools", List.of());

    int exitCode = command.doCall(new Config(), tempDir);

    assertThat(exitCode).isEqualTo(0);
    String console = String.join("\n", command.lines());
    assertThat(console).contains("Static Analysis: spotbugs, pmd (default)");
  }

  @Test
  void doCall_returnsError_whenWriteFailsForDirectoryOutput(@TempDir Path tempDir)
      throws Exception {
    CapturingInitCiCommand command = new CapturingInitCiCommand();
    setField(command, "githubActions", true);
    setField(command, "outputPath", tempDir);
    setField(command, "force", true);

    int exitCode = command.doCall(new Config(), tempDir);

    assertThat(exitCode).isEqualTo(1);
  }

  @Test
  void setResourceBundle_appliesInjectedBundle() {
    InitCiCommand command = new InitCiCommand();
    command.setResourceBundle(new InitCiTestBundle());

    int exitCode = command.doCall(new Config(), Path.of("."));

    assertThat(exitCode).isEqualTo(1);
  }

  @Test
  void print_handlesNullAndPlainText() {
    InitCiCommand command = new InitCiCommand();
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();
    UiLogger.setOutput(
        new PrintStream(stdout, true, StandardCharsets.UTF_8),
        new PrintStream(stderr, true, StandardCharsets.UTF_8));
    try {
      command.print(null);
      command.print("hello");
    } finally {
      UiLogger.setOutput(System.out, System.err);
    }

    String output = stdout.toString(StandardCharsets.UTF_8);
    assertThat(output).contains(System.lineSeparator());
    assertThat(output).contains("hello");
  }

  private static final class InitCiTestBundle extends ListResourceBundle {
    @Override
    protected Object[][] getContents() {
      return new Object[][] {{"init-ci.error.specify_github_actions", "missing flag"}};
    }
  }

  private static void setField(Object target, String name, Object value) throws Exception {
    Class<?> type = target.getClass();
    while (type != null) {
      try {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
        return;
      } catch (NoSuchFieldException ignored) {
        type = type.getSuperclass();
      }
    }
    throw new NoSuchFieldException(name);
  }

  private static final class CapturingInitCiCommand extends InitCiCommand {
    private final List<String> lines = new ArrayList<>();

    @Override
    protected void print(String message) {
      lines.add(message);
    }

    List<String> lines() {
      return lines;
    }
  }
}
