package com.craftsmanbro.fulcraft.infrastructure.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class LoggerCoverageTest {

  private final ByteArrayOutputStream out = new ByteArrayOutputStream();
  private final ByteArrayOutputStream err = new ByteArrayOutputStream();
  private final PrintStream originalOut = System.out;
  private final PrintStream originalErr = System.err;
  private boolean originalJsonMode;
  private boolean originalColorEnabled;

  @BeforeEach
  void setUp() {
    originalJsonMode = Logger.isJsonMode();
    originalColorEnabled = Logger.isColorEnabled();
    Logger.setJsonMode(false);
    Logger.setColorEnabled(false);
    Logger.setOutput(new PrintStream(out), new PrintStream(err));
    Logger.resetInfoOnceKeys();
    Logger.resetWarnOnceKeys();
    Logger.clearContext();
  }

  @AfterEach
  void tearDown() {
    Logger.setOutput(originalOut, originalErr);
    Logger.setJsonMode(originalJsonMode);
    Logger.setColorEnabled(originalColorEnabled);
    Logger.resetInfoOnceKeys();
    Logger.resetWarnOnceKeys();
    Logger.clearContext();
  }

  @Test
  void suppressProgressOutput_suppressesUntilScopeClosed() {
    try (var ignored = Logger.suppressProgressOutput()) {
      Logger.progressBar(1, 2, "Suppressed.java");
      Logger.progressComplete(2);
      Logger.progressCompleteOnce(2);
    }

    assertEquals("", out.toString());

    Logger.progressBar(1, 2, "Visible.java");
    assertTrue(out.toString().contains("Visible.java"));
  }

  @Test
  void suppressProgressOutput_nestedScopesRestorePreviousState() {
    try (var outer = Logger.suppressProgressOutput()) {
      try (var inner = Logger.suppressProgressOutput()) {
        Logger.progressBar(1, 1, "Inner.java");
      }
      Logger.progressBar(1, 1, "StillSuppressed.java");
      assertEquals("", out.toString());
    }

    Logger.progressBar(1, 1, "ShownAfterClose.java");
    String printed = out.toString();
    assertFalse(printed.contains("Inner.java"));
    assertFalse(printed.contains("StillSuppressed.java"));
    assertTrue(printed.contains("ShownAfterClose.java"));
  }

  @Test
  void infoOnceAndWarnOnce_emitOnlyFirstMessagePerKey() {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    ch.qos.logback.classic.Logger targetLogger = attachAppender(appender);
    Level previousLevel = targetLogger.getLevel();
    targetLogger.setLevel(Level.INFO);

    try {
      Logger.infoOnce("info-key", "first info");
      Logger.infoOnce("info-key", "second info");
      Logger.infoOnce(null, "ignored info");

      Logger.warnOnce("warn-key", "first warn");
      Logger.warnOnce("warn-key", "second warn");
      Logger.warnOnce(null, "ignored warn");

      Logger.resetInfoOnceKeys();
      Logger.infoOnce("info-key", "info reset");

      assertEquals(
          2,
          appender.list.stream()
              .filter(
                  event ->
                      event.getLevel() == Level.INFO
                          && event.getFormattedMessage().contains("info"))
              .count());
      assertEquals(
          1,
          appender.list.stream()
              .filter(
                  event ->
                      event.getLevel() == Level.WARN
                          && event.getFormattedMessage().contains("warn"))
              .count());
    } finally {
      detachAppender(targetLogger, appender, previousLevel);
    }
  }

  @Test
  void progressCompleteOnce_printsOnlyOncePerTotal() {
    Logger.progressCompleteOnce(3);
    String first = out.toString();

    Logger.progressCompleteOnce(3);
    assertEquals(first, out.toString());

    Logger.progressCompleteOnce(4);
    assertTrue(out.toString().length() > first.length());
  }

  @Test
  void progressCompleteOnce_logsOnlyOncePerTotalInJsonMode() {
    Logger.setJsonMode(true);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    ch.qos.logback.classic.Logger targetLogger = attachAppender(appender);
    Level previousLevel = targetLogger.getLevel();
    targetLogger.setLevel(Level.INFO);

    try {
      Logger.progressCompleteOnce(3);
      Logger.progressCompleteOnce(3);
      Logger.progressCompleteOnce(4);

      assertEquals(2, appender.list.size());
    } finally {
      detachAppender(targetLogger, appender, previousLevel);
    }
  }

  @Test
  void stdioMethods_maskNullToEmptyString() {
    Logger.stdout(null);
    Logger.stdoutInline(null);
    Logger.stderr(null);

    String printedOut = out.toString();
    String printedErr = err.toString();
    assertFalse(printedOut.contains("null"));
    assertFalse(printedErr.contains("null"));
  }

  @Test
  void setDebugEnabled_switchesRootLevelBetweenDebugAndInfo() {
    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    ch.qos.logback.classic.Logger root = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    Level previousLevel = root.getLevel();
    try {
      Logger.setDebugEnabled(true);
      assertEquals(Level.DEBUG, root.getLevel());

      Logger.setDebugEnabled(false);
      assertEquals(Level.INFO, root.getLevel());
    } finally {
      root.setLevel(previousLevel);
    }
  }

  @Test
  void trimLargeContent_usesConfiguredMaxWhenNonPositiveLimitPassed() {
    Config config = new Config();
    Config.LogConfig logConfig = new Config.LogConfig();
    logConfig.setMaxMessageLength(90);
    config.setLog(logConfig);
    Logger.initialize(config);

    String content = "abcdefghijklmnopqrstuvwxyz".repeat(6);
    String trimmed = Logger.trimLargeContent(content, 0);

    assertTrue(trimmed.length() < content.length());
    assertTrue(trimmed.contains("TRIMMED"));
  }

  @Test
  void trimLargeContent_returnsOriginalWhenInitializedAndNoMaxConfigured() {
    Config config = new Config();
    Config.LogConfig logConfig = new Config.LogConfig();
    logConfig.setMaxMessageLength(0);
    config.setLog(logConfig);
    Logger.initialize(config);

    String content = "short-content";
    assertEquals(content, Logger.trimLargeContent(content, 0));
  }

  @Test
  void privateHelpers_buildPatternsAndOutputDetectionBehaveAsExpected() throws Exception {
    assertTrue(
        invokeBoolean("isConsoleOutput", new Class<?>[] {String.class}, new Object[] {null}));
    assertTrue(
        invokeBoolean("isConsoleOutput", new Class<?>[] {String.class}, new Object[] {"console"}));
    assertTrue(invokeBoolean("isConsoleOutput", new Class<?>[] {String.class}, "both"));
    assertFalse(invokeBoolean("isConsoleOutput", new Class<?>[] {String.class}, "file"));

    assertTrue(invokeBoolean("isFileOutput", new Class<?>[] {String.class}, "file"));
    assertTrue(invokeBoolean("isFileOutput", new Class<?>[] {String.class}, "both"));
    assertFalse(invokeBoolean("isFileOutput", new Class<?>[] {String.class}, "console"));
    assertFalse(invokeBoolean("isFileOutput", new Class<?>[] {String.class}, new Object[] {null}));

    Config.LogConfig verbose = new Config.LogConfig();
    verbose.setIncludeTimestamp(true);
    verbose.setIncludeThread(true);
    verbose.setIncludeLogger(true);
    verbose.setEnableMdc(true);
    String consolePatternWithLevel =
        invokeString(
            "buildConsolePattern",
            new Class<?>[] {Config.LogConfig.class, boolean.class},
            verbose,
            true);
    assertTrue(consolePatternWithLevel.contains("%d{HH:mm:ss}"));
    assertTrue(consolePatternWithLevel.contains("[%thread]"));
    assertTrue(consolePatternWithLevel.contains("%logger{36} - "));
    assertTrue(consolePatternWithLevel.contains("[%level]"));

    Config.LogConfig minimal = new Config.LogConfig();
    minimal.setIncludeTimestamp(false);
    minimal.setIncludeThread(false);
    minimal.setIncludeLogger(false);
    minimal.setEnableMdc(false);
    String consolePatternWithoutLevel =
        invokeString(
            "buildConsolePattern",
            new Class<?>[] {Config.LogConfig.class, boolean.class},
            minimal,
            false);
    assertEquals("%msg%n", consolePatternWithoutLevel);

    String filePatternWithMdc =
        invokeString("buildFilePattern", new Class<?>[] {Config.LogConfig.class}, verbose);
    assertTrue(filePatternWithMdc.contains("[%X{runId:-}]"));
    assertTrue(filePatternWithMdc.contains("%logger{36} - "));

    String filePatternWithoutMdc =
        invokeString("buildFilePattern", new Class<?>[] {Config.LogConfig.class}, minimal);
    assertFalse(filePatternWithoutMdc.contains("[%X{runId:-}]"));
    assertFalse(filePatternWithoutMdc.contains("%logger{36} - "));

    assertEquals(
        "logs/ful.%d{yyyy-MM-dd}.log",
        invokeString("buildTimeBasedPattern", new Class<?>[] {String.class}, "logs/ful.log"));
    assertEquals(
        "logs/ful.%d{yyyy-MM-dd}",
        invokeString("buildTimeBasedPattern", new Class<?>[] {String.class}, "logs/ful"));
    assertEquals(
        "logs/ful.%d{yyyy-MM-dd}.%i.log",
        invokeString("buildSizeAndTimePattern", new Class<?>[] {String.class}, "logs/ful.log"));
    assertEquals(
        "logs/ful.%d{yyyy-MM-dd}.%i",
        invokeString("buildSizeAndTimePattern", new Class<?>[] {String.class}, "logs/ful"));
  }

  private ch.qos.logback.classic.Logger attachAppender(ListAppender<ILoggingEvent> appender) {
    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    ch.qos.logback.classic.Logger targetLogger = context.getLogger("utgenerator");
    appender.setContext(context);
    appender.start();
    targetLogger.addAppender(appender);
    return targetLogger;
  }

  private void detachAppender(
      ch.qos.logback.classic.Logger targetLogger,
      ListAppender<ILoggingEvent> appender,
      Level previousLevel) {
    targetLogger.detachAppender(appender);
    targetLogger.setLevel(previousLevel);
    appender.stop();
  }

  private boolean invokeBoolean(String methodName, Class<?>[] parameterTypes, Object... args)
      throws Exception {
    Method method = Logger.class.getDeclaredMethod(methodName, parameterTypes);
    method.setAccessible(true);
    return (boolean) method.invoke(null, args);
  }

  private String invokeString(String methodName, Class<?>[] parameterTypes, Object... args)
      throws Exception {
    Method method = Logger.class.getDeclaredMethod(methodName, parameterTypes);
    method.setAccessible(true);
    return (String) method.invoke(null, args);
  }
}
