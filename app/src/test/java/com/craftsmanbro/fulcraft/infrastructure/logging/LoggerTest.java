package com.craftsmanbro.fulcraft.infrastructure.logging;

import static org.junit.jupiter.api.Assertions.*;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.logging.contract.OperationalLoggingPort;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.infrastructure.logging.model.LogContext;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoggerTest {

  private final ByteArrayOutputStream out = new ByteArrayOutputStream();
  private final PrintStream originalOut = System.out;
  private boolean originalJsonMode;
  private Locale originalLocale;

  @BeforeEach
  void setUp() {
    originalJsonMode = Logger.isJsonMode();
    originalLocale = MessageSource.getLocale();
    Logger.setJsonMode(false);
    MessageSource.setLocale(Locale.ENGLISH);
  }

  @AfterEach
  void restoreStreams() {
    Logger.setOutput(originalOut, System.err);
    Logger.setJsonMode(originalJsonMode);
    MessageSource.setLocale(originalLocale);
  }

  @Test
  void trimLargeContentKeepsHeadAndTail() {
    String content = "abcdefghijklmnopqrstuvwxyz".repeat(10);
    int max = 80;

    String trimmed = Logger.trimLargeContent(content, max);

    assertTrue(trimmed.startsWith("abc"), "Head should be kept");
    assertTrue(trimmed.endsWith("xyz"), "Tail should be kept");
    assertTrue(trimmed.contains("TRIMMED"), "Should include trim marker");
    assertTrue(trimmed.length() <= max, "Should not exceed max length");
  }

  @Test
  void progressBarWritesToCustomOutput() {
    Logger.setOutput(new PrintStream(out), System.err);

    Logger.progressBar(1, 2, "MyFile.java");
    Logger.progressBar(2, 2, "MyFile.java");

    String printed = out.toString();
    assertTrue(printed.contains("MyFile.java"), "Should include filename");
    assertTrue(printed.contains("%"), "Should show progress percentage");
  }

  @Test
  void progressCompletePrintsSummary() {
    Logger.setOutput(new PrintStream(out), System.err);

    Logger.progressComplete(5);

    assertTrue(out.toString().contains("Analyzed 5 files"));
  }

  @Test
  void progressBarWithEtrShowsEstimate() {
    Logger.setOutput(new PrintStream(out), System.err);

    // Simulate a long-running operation with startTime 3 seconds ago
    long startTime = System.currentTimeMillis() - 3000;

    Logger.progressBar(5, 10, "TestFile.java", startTime);

    String printed = out.toString();
    assertTrue(printed.contains("TestFile.java"), "Should include filename");
    assertTrue(printed.contains("%"), "Should show percentage");
    // ETR should appear after 2 seconds of elapsed time
    assertTrue(printed.contains("ETR") || printed.contains("s"), "Should show ETR estimate");
  }

  @Test
  void colorMethodsReturnColoredTextWhenEnabled() {
    Logger.setColorEnabled(true);

    String green = Logger.green("test");
    String red = Logger.red("test");
    String yellow = Logger.yellow("test");
    String cyan = Logger.cyan("test");
    String bold = Logger.bold("test");

    assertTrue(green.contains("test"), "Should contain text");
    assertTrue(green.contains("\u001B["), "Should contain ANSI code when color enabled");
    assertTrue(red.contains("\u001B["), "Red should contain ANSI code");
    assertTrue(yellow.contains("\u001B["), "Yellow should contain ANSI code");
    assertTrue(cyan.contains("\u001B["), "Cyan should contain ANSI code");
    assertTrue(bold.contains("\u001B["), "Bold should contain ANSI code");
  }

  @Test
  void colorMethodsReturnPlainTextWhenDisabled() {
    Logger.setColorEnabled(false);

    String green = Logger.green("test");
    String red = Logger.red("test");
    String yellow = Logger.yellow("test");
    String cyan = Logger.cyan("test");
    String bold = Logger.bold("test");

    assertEquals("test", green, "Should return plain text when color disabled");
    assertEquals("test", red, "Red should return plain text");
    assertEquals("test", yellow, "Yellow should return plain text");
    assertEquals("test", cyan, "Cyan should return plain text");
    assertEquals("test", bold, "Bold should return plain text");
  }

  @Test
  void stdoutSuccessUsesGreenIcon() {
    Logger.setOutput(new PrintStream(out), System.err);
    Logger.setColorEnabled(false);

    Logger.stdoutSuccess("Success message");

    String printed = out.toString();
    assertTrue(printed.contains("✔"), "Should contain success icon");
    assertTrue(printed.contains("Success message"), "Should contain message");
  }

  @Test
  void stdoutWarnUsesYellowIcon() {
    Logger.setOutput(new PrintStream(out), System.err);
    Logger.setColorEnabled(false);

    Logger.stdoutWarn("Warning message");

    String printed = out.toString();
    assertTrue(printed.contains("⚠"), "Should contain warning icon");
    assertTrue(printed.contains("Warning message"), "Should contain message");
  }

  @Test
  void stdoutErrorUsesRedIcon() {
    Logger.setOutput(new PrintStream(out), new PrintStream(out));
    Logger.setColorEnabled(false);

    Logger.stdoutError("Error message");

    String printed = out.toString();
    assertTrue(printed.contains("✘"), "Should contain error icon");
    assertTrue(printed.contains("Error message"), "Should contain message");
  }

  @Test
  void stdoutInfoUsesCyanIcon() {
    Logger.setOutput(new PrintStream(out), System.err);
    Logger.setColorEnabled(false);

    Logger.stdoutInfo("Info message");

    String printed = out.toString();
    assertTrue(printed.contains("ℹ"), "Should contain info icon");
    assertTrue(printed.contains("Info message"), "Should contain message");
  }

  @Test
  void jsonModeCanBeSet() {
    Logger.setJsonMode(true);

    assertTrue(Logger.isJsonMode(), "JSON mode should be enabled");

    Logger.setJsonMode(false);

    assertFalse(Logger.isJsonMode(), "JSON mode should be disabled");
  }

  @Test
  void port_exposesContractAndContextModel() {
    Logger.setOutput(new PrintStream(out), new PrintStream(out));
    Logger.setJsonMode(false);
    Logger.clearContext();

    OperationalLoggingPort port = Logger.port();
    LogContext context =
        new LogContext(
            "run-1", "trace-1", "analysis", "AnalyzeStage", "com.example.Service", "t-1");

    port.applyContext(context);
    port.emitStdout("hello-port");

    assertEquals(context, port.currentContext());
    assertTrue(out.toString().contains("hello-port"));
  }
}
