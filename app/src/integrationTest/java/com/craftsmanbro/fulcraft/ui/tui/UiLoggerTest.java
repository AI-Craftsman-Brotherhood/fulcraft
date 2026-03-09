package com.craftsmanbro.fulcraft.ui.tui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UiLoggerTest {

  private final PrintStream originalOut = System.out;
  private final PrintStream originalErr = System.err;
  private boolean originalJsonMode;
  private boolean originalColorEnabled;
  private ByteArrayOutputStream out;
  private ByteArrayOutputStream err;

  @BeforeEach
  void setUp() {
    originalJsonMode = Logger.isJsonMode();
    originalColorEnabled = Logger.isColorEnabled();
    out = new ByteArrayOutputStream();
    err = new ByteArrayOutputStream();
    UiLogger.setOutput(
        new PrintStream(out, true, StandardCharsets.UTF_8),
        new PrintStream(err, true, StandardCharsets.UTF_8));
    UiLogger.setJsonMode(false);
    UiLogger.setColorEnabled(false);
  }

  @AfterEach
  void tearDown() {
    UiLogger.setOutput(originalOut, originalErr);
    UiLogger.setJsonMode(originalJsonMode);
    UiLogger.setColorEnabled(originalColorEnabled);
    Logger.clearContext();
  }

  @Test
  void utilityClassPatternIsPreserved() throws Exception {
    Constructor<UiLogger> constructor = UiLogger.class.getDeclaredConstructor();
    assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
    assertThat(Modifier.isFinal(UiLogger.class.getModifiers())).isTrue();

    constructor.setAccessible(true);
    UiLogger instance = constructor.newInstance();
    assertThat(instance).isNotNull();
  }

  @Test
  void outputMethodsWriteToConfiguredStreams() {
    UiLogger.stdout("hello-ui");
    UiLogger.stdoutInline("inline-ui");
    UiLogger.stdoutWarn("warn-ui");
    UiLogger.stderr("stderr-ui");

    String stdout = out.toString(StandardCharsets.UTF_8);
    String stderr = err.toString(StandardCharsets.UTF_8);
    assertThat(stdout).contains("hello-ui").contains("inline-ui").contains("warn-ui");
    assertThat(stderr).contains("stderr-ui");
  }

  @Test
  void loggingLevelMethodsDelegateWithoutThrowing() {
    RuntimeException failure = new RuntimeException("boom");
    assertThatCode(
            () -> {
              UiLogger.debug("debug-ui");
              UiLogger.info("info-ui");
              UiLogger.warn("warn-ui");
              UiLogger.warn("warn-ui-with-exception", failure);
              UiLogger.error("error-ui");
              UiLogger.error("error-ui-with-exception", failure);
            })
        .doesNotThrowAnyException();
  }

  @Test
  void configurationMethodsDelegateToLogger(@TempDir Path tempDir) {
    Config config = new Config();
    Config.LogConfig logConfig = new Config.LogConfig();
    logConfig.setFormat("json");
    logConfig.setOutput("both");
    logConfig.setFilePath("custom.log");
    logConfig.setEnableMdc(true);
    config.setLog(logConfig);

    UiLogger.initialize(config);
    assertThat(Logger.isJsonMode()).isTrue();

    UiLogger.setJsonMode(false);
    assertThat(Logger.isJsonMode()).isFalse();

    UiLogger.setColorEnabled(true);
    assertThat(Logger.isColorEnabled()).isTrue();
    UiLogger.setColorEnabled(false);
    assertThat(Logger.isColorEnabled()).isFalse();

    UiLogger.configureRunLogging(config, tempDir, "run-123");
    assertThat(Files.exists(tempDir.resolve("logs"))).isTrue();
    assertThat(config.getLog().getFilePath())
        .isEqualTo(tempDir.resolve("logs").resolve("custom.log").toString());
    assertThat(Logger.getRunId()).isEqualTo("run-123");
  }

  @Test
  void nullInputsAreHandledGracefully() {
    assertThatCode(() -> UiLogger.initialize(null)).doesNotThrowAnyException();
    assertThatCode(() -> UiLogger.configureRunLogging(null, null, null)).doesNotThrowAnyException();
    assertThatCode(() -> UiLogger.setOutput(null, null)).doesNotThrowAnyException();
    assertThatCode(() -> UiLogger.stdout(null)).doesNotThrowAnyException();
    assertThatCode(() -> UiLogger.warn("warn", null)).doesNotThrowAnyException();
    assertThatCode(() -> UiLogger.error("error", null)).doesNotThrowAnyException();
  }
}
