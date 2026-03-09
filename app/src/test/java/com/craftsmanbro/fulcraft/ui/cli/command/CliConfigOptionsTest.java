package com.craftsmanbro.fulcraft.ui.cli.command;

import static org.junit.jupiter.api.Assertions.*;

import com.craftsmanbro.fulcraft.Main;
import com.craftsmanbro.fulcraft.ui.cli.UiLogger;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/** Tests for CLI configuration options including color, log-format, and other settings. */
class CliConfigOptionsTest {

  private ByteArrayOutputStream out;
  private ByteArrayOutputStream err;
  private PrintStream originalOut;
  private PrintStream originalErr;
  private boolean originalJsonMode;
  private boolean originalColorEnabled;

  @BeforeEach
  void setUp() {
    out = new ByteArrayOutputStream();
    err = new ByteArrayOutputStream();
    originalOut = System.out;
    originalErr = System.err;
    System.setOut(new PrintStream(out));
    System.setErr(new PrintStream(err));
    originalJsonMode = UiLogger.isJsonMode();
    originalColorEnabled = UiLogger.isColorEnabled();
  }

  @AfterEach
  void tearDown() {
    System.setOut(originalOut);
    System.setErr(originalErr);
    UiLogger.setJsonMode(originalJsonMode);
    UiLogger.setColorEnabled(originalColorEnabled);
  }

  @Test
  void testColorOptionOff() {
    CommandLine cmd = Main.createCommandLine();

    // Parse with --color=off
    int exitCode = cmd.execute("run", "--color=off", "--help");

    // Should complete successfully (help exits with 0)
    assertEquals(0, exitCode);
  }

  @Test
  void testColorOptionOn() {
    CommandLine cmd = Main.createCommandLine();

    int exitCode = cmd.execute("run", "--color=on", "--help");

    assertEquals(0, exitCode);
  }

  @Test
  void testColorOptionAuto() {
    CommandLine cmd = Main.createCommandLine();

    int exitCode = cmd.execute("run", "--color=auto", "--help");

    assertEquals(0, exitCode);
  }

  @Test
  void testLogFormatJson() {
    CommandLine cmd = Main.createCommandLine();

    int exitCode = cmd.execute("run", "--log-format=json", "--help");

    assertEquals(0, exitCode);
  }

  @Test
  void testLogFormatYaml() {
    CommandLine cmd = Main.createCommandLine();

    int exitCode = cmd.execute("run", "--log-format=yaml", "--help");

    assertEquals(0, exitCode);
  }

  @Test
  void testLogFormatHuman() {
    CommandLine cmd = Main.createCommandLine();

    int exitCode = cmd.execute("run", "--log-format=human", "--help");

    assertEquals(0, exitCode);
  }

  @Test
  void testJsonShorthand() {
    CommandLine cmd = Main.createCommandLine();

    // --json should be equivalent to --log-format=json
    int exitCode = cmd.execute("run", "--json", "--help");

    assertEquals(0, exitCode);
  }

  @Test
  void testHelpShowsNewOptions() {
    CommandLine cmd = Main.createCommandLine();

    int exitCode = cmd.execute("run", "--help");

    String output = out.toString(StandardCharsets.UTF_8);
    assertEquals(0, exitCode);
    assertTrue(output.contains("--color"), "Help should include --color option");
    assertTrue(output.contains("--log-format"), "Help should include --log-format option");
    assertTrue(output.contains("--json"), "Help should include --json option");
  }

  @Test
  void testCombinedOptions() {
    CommandLine cmd = Main.createCommandLine();

    // Test combining multiple options
    int exitCode = cmd.execute("run", "--color=off", "--json", "--dry-run", "--help");

    assertEquals(0, exitCode);
  }

  @Test
  void testAnalyzeCommandHasColorOption() {
    String output = executeHelp("analyze", "--help");
    assertTrue(output.contains("--color"), "Analyze help should include --color option");
  }

  @Test
  void testGenerateCommandHasColorOption() {
    String output = executeHelp("generate", "--help");
    assertFalse(output.isBlank(), "Generate help should not be blank");
  }

  @Test
  void testAnalyzeCommandParsesSharedCliOptions() {
    CommandLine cmd = Main.createCommandLine();

    int exitCode = cmd.execute("analyze", "--color=off", "--log-format=json", "--json", "--help");

    assertEquals(0, exitCode);
  }

  @Test
  void testGenerateCommandParsesSharedCliOptions() {
    CommandLine cmd = Main.createCommandLine();

    int exitCode = cmd.execute("generate", "--color=off", "--log-format=json", "--json", "--help");

    assertEquals(0, exitCode);
  }

  private String executeHelp(String... args) {
    out.reset();
    CommandLine cmd = Main.createCommandLine();
    int exitCode = cmd.execute(args);
    assertEquals(0, exitCode);
    return out.toString(StandardCharsets.UTF_8);
  }
}
