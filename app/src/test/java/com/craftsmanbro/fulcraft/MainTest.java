package com.craftsmanbro.fulcraft;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.ui.banner.StartupBannerSupport;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class MainTest {

  private StringWriter outWriter;
  private StringWriter errWriter;
  private Locale originalLocale;

  @BeforeEach
  void setUp() {
    outWriter = new StringWriter();
    errWriter = new StringWriter();
    originalLocale = Locale.getDefault();
  }

  @AfterEach
  void tearDown() {
    Locale.setDefault(originalLocale);
  }

  @Test
  void testHelpMessage_English() {
    Locale.setDefault(Locale.ENGLISH);
    CommandLine cmd = Main.createCommandLine();
    assertThat(cmd.getSubcommands().containsKey("run")).as("Run command check").isTrue();
    assertThat(cmd.getSubcommands().containsKey("explore")).as("Explore command check").isTrue();
    cmd.setOut(new PrintWriter(outWriter));
    cmd.setErr(new PrintWriter(errWriter));

    int exitCode = cmd.execute("--help");

    assertThat(exitCode).isEqualTo(CommandLine.ExitCode.OK);
    String output = outWriter.toString();
    assertThat(output)
        .as("Output: " + output)
        .contains("Tools for generating unit tests for Java legacy code"); // cli.description
    assertThat(output).as("Output: " + output).contains("run"); // run command listed
  }

  @Test
  void testHelpMessage_Japanese() {
    Locale.setDefault(Locale.JAPANESE);
    CommandLine cmd = Main.createCommandLine();
    cmd.setOut(new PrintWriter(outWriter));
    cmd.setErr(new PrintWriter(errWriter));

    int exitCode = cmd.execute("--help");

    assertThat(exitCode).isEqualTo(CommandLine.ExitCode.OK);
    String output = outWriter.toString();
    assertThat(output)
        .as("Output: " + output)
        .contains("Javaレガシーコード向けのユニットテスト自動生成ツールです"); // cli.description
    assertThat(output).as("Output: " + output).contains("run"); // run command listed
  }

  @Test
  void testRunCommandHelp_English() {
    Locale.setDefault(Locale.ENGLISH);
    CommandLine cmd = Main.createCommandLine();
    cmd.setOut(new PrintWriter(outWriter));
    cmd.setErr(new PrintWriter(errWriter));

    int exitCode = cmd.execute("run", "--help");

    assertThat(exitCode).isEqualTo(CommandLine.ExitCode.OK);
    String output = outWriter.toString();
    assertThat(output).as("Output: " + output).contains("Run workflow-based pipeline nodes.");
    assertThat(output).as("Output: " + output).contains("Example Usage:");
    assertThat(output).as("Output: " + output).contains("ful run -p /path/to/project");
  }

  @Test
  void testReportHelp_Japanese_LocalizesStandardHelpOptions() {
    Locale.setDefault(Locale.ENGLISH);
    CommandLine cmd = Main.createCommandLine();
    cmd.setOut(new PrintWriter(outWriter));
    cmd.setErr(new PrintWriter(errWriter));

    int exitCode = cmd.execute("--lang", "ja", "report", "--help");

    assertThat(exitCode).isEqualTo(CommandLine.ExitCode.OK);
    String output = outWriter.toString();
    assertThat(output).as("Output: " + output).contains("このヘルプメッセージを表示して終了します。");
    assertThat(output).as("Output: " + output).contains("バージョン情報を表示して終了します。");
  }

  @Test
  void testVersionOutput_UsesResolvedApplicationVersion() {
    Locale.setDefault(Locale.ENGLISH);
    CommandLine cmd = Main.createCommandLine();
    cmd.setOut(new PrintWriter(outWriter));
    cmd.setErr(new PrintWriter(errWriter));

    int exitCode = cmd.execute("--version");

    assertThat(exitCode).isEqualTo(CommandLine.ExitCode.OK);
    assertThat(outWriter.toString().trim())
        .isEqualTo("ful " + StartupBannerSupport.resolveApplicationVersion());
  }

  @Test
  void testUnknownCommand() {
    CommandLine cmd = Main.createCommandLine();
    cmd.setOut(new PrintWriter(outWriter));
    cmd.setErr(new PrintWriter(errWriter));

    int exitCode = cmd.execute("rum"); // Unknown command

    assertThat(exitCode).isNotEqualTo(0);
    String output = errWriter.toString();
    assertThat(output).as("Output: " + output).contains("Unmatched argument");
  }

  @Test
  void testParseError_Japanese_WithLangOption() {
    Locale.setDefault(Locale.ENGLISH);
    CommandLine cmd = Main.createCommandLine("--lang", "ja", "--invalid-option");
    cmd.setOut(new PrintWriter(outWriter));
    cmd.setErr(new PrintWriter(errWriter));

    int exitCode = cmd.execute("--lang", "ja", "--invalid-option");

    assertThat(exitCode).isEqualTo(CommandLine.ExitCode.USAGE);
    String output = errWriter.toString();
    assertThat(output).as("Output: " + output).contains("--invalid-option");
    assertThat(output).as("Output: " + output).contains("このヘルプメッセージを表示して終了します。");
  }
}
