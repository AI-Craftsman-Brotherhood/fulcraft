package com.craftsmanbro.fulcraft.ui.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link UiLogger}.
 *
 * <p>UiLogger is a facade that delegates to the infrastructure Logger. These tests verify:
 *
 * <ul>
 *   <li>The class follows utility class conventions (private constructor)
 *   <li>Methods can be invoked without throwing exceptions
 *   <li>Output methods write to the configured streams
 * </ul>
 */
class UiLoggerTest {

  private final PrintStream originalStdout = System.out;
  private final PrintStream originalStderr = System.err;
  private boolean originalJsonMode;
  private boolean originalColorEnabled;
  private ByteArrayOutputStream stdoutCapture;
  private ByteArrayOutputStream stderrCapture;

  @BeforeEach
  void setUp() {
    originalJsonMode = Logger.isJsonMode();
    originalColorEnabled = Logger.isColorEnabled();
    stdoutCapture = new ByteArrayOutputStream();
    stderrCapture = new ByteArrayOutputStream();
    UiLogger.setOutput(new PrintStream(stdoutCapture), new PrintStream(stderrCapture));
    UiLogger.setColorEnabled(false);
    UiLogger.setJsonMode(false);
  }

  @AfterEach
  void tearDown() {
    // Restore original streams
    UiLogger.setOutput(originalStdout, originalStderr);
    UiLogger.setJsonMode(originalJsonMode);
    UiLogger.setColorEnabled(originalColorEnabled);
    Logger.clearContext();
  }

  @Nested
  @DisplayName("Utility class conventions")
  class UtilityClassConventions {

    @Test
    @DisplayName("should have private constructor")
    void shouldHavePrivateConstructor() throws NoSuchMethodException {
      Constructor<UiLogger> constructor = UiLogger.class.getDeclaredConstructor();
      assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
    }

    @Test
    @DisplayName(
        "private constructor can be invoked via reflection but is meant for utility class pattern")
    void privateConstructorCanBeInvokedViaReflection() throws Exception {
      Constructor<UiLogger> constructor = UiLogger.class.getDeclaredConstructor();
      constructor.setAccessible(true);

      // Private constructor pattern for utility class - it can be invoked via
      // reflection
      // but the private modifier signals that it should not be instantiated
      UiLogger instance = constructor.newInstance();
      assertThat(instance).isNotNull();
    }

    @Test
    @DisplayName("should be final class")
    void shouldBeFinalClass() {
      assertThat(Modifier.isFinal(UiLogger.class.getModifiers())).isTrue();
    }
  }

  @Nested
  @DisplayName("Output configuration")
  class OutputConfiguration {

    @Test
    @DisplayName("setOutput should not throw")
    void setOutputShouldNotThrow() {
      assertThatCode(() -> UiLogger.setOutput(System.out, System.err)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("setJsonMode should not throw")
    void setJsonModeShouldNotThrow() {
      assertThatCode(() -> UiLogger.setJsonMode(true)).doesNotThrowAnyException();
      assertThatCode(() -> UiLogger.setJsonMode(false)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("isJsonMode should reflect current json mode")
    void isJsonModeShouldReflectCurrentJsonMode() {
      UiLogger.setJsonMode(true);
      assertThat(UiLogger.isJsonMode()).isTrue();

      UiLogger.setJsonMode(false);
      assertThat(UiLogger.isJsonMode()).isFalse();
    }

    @Test
    @DisplayName("setColorEnabled should not throw")
    void setColorEnabledShouldNotThrow() {
      assertThatCode(() -> UiLogger.setColorEnabled(true)).doesNotThrowAnyException();
      assertThatCode(() -> UiLogger.setColorEnabled(false)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("setColorEnabled should affect Logger color behavior")
    void setColorEnabledShouldAffectLoggerColorBehavior() {
      UiLogger.setColorEnabled(true);
      assertThat(Logger.green("test")).contains(Logger.ANSI_GREEN);

      UiLogger.setColorEnabled(false);
      assertThat(Logger.green("test")).isEqualTo("test");
    }
  }

  @Nested
  @DisplayName("Standard output methods")
  class StandardOutputMethods {

    @Test
    @DisplayName("stdout should write message to stdout stream")
    void stdoutShouldWriteToStdout() {
      UiLogger.stdout("test message");

      String output = stdoutCapture.toString();
      assertThat(output).contains("test message");
    }

    @Test
    @DisplayName("stdoutInline should write message to stdout stream")
    void stdoutInlineShouldWriteToStdout() {
      UiLogger.stdoutInline("inline message");

      String output = stdoutCapture.toString();
      assertThat(output).contains("inline message");
    }

    @Test
    @DisplayName("stdoutWarn should write warning to stdout stream")
    void stdoutWarnShouldWriteToStdout() {
      UiLogger.stdoutWarn("warning message");

      String output = stdoutCapture.toString();
      assertThat(output).contains("warning message");
    }

    @Test
    @DisplayName("stderr should write message to stderr stream")
    void stderrShouldWriteToStderr() {
      UiLogger.stderr("error output");

      String output = stderrCapture.toString();
      assertThat(output).contains("error output");
    }

    @Test
    @DisplayName("stdout with null message should not throw")
    void stdoutWithNullShouldNotThrow() {
      assertThatCode(() -> UiLogger.stdout(null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("stdout with empty message should not throw")
    void stdoutWithEmptyMessageShouldNotThrow() {
      assertThatCode(() -> UiLogger.stdout("")).doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("Logging level methods")
  class LoggingLevelMethods {

    @Test
    @DisplayName("debug should not throw with message")
    void debugShouldNotThrow() {
      assertThatCode(() -> UiLogger.debug("debug message")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("info should not throw with message")
    void infoShouldNotThrow() {
      assertThatCode(() -> UiLogger.info("info message")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("warn should not throw with message")
    void warnShouldNotThrow() {
      assertThatCode(() -> UiLogger.warn("warn message")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("warn with throwable should not throw")
    void warnWithThrowableShouldNotThrow() {
      Exception testException = new RuntimeException("test exception");
      assertThatCode(() -> UiLogger.warn("warn with exception", testException))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("error should not throw with message")
    void errorShouldNotThrow() {
      assertThatCode(() -> UiLogger.error("error message")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("error with throwable should not throw")
    void errorWithThrowableShouldNotThrow() {
      Exception testException = new RuntimeException("test exception");
      assertThatCode(() -> UiLogger.error("error with exception", testException))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("logging methods with null message should not throw")
    void loggingMethodsWithNullMessageShouldNotThrow() {
      assertThatCode(() -> UiLogger.debug(null)).doesNotThrowAnyException();
      assertThatCode(() -> UiLogger.info(null)).doesNotThrowAnyException();
      assertThatCode(() -> UiLogger.warn(null)).doesNotThrowAnyException();
      assertThatCode(() -> UiLogger.error(null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("logging methods with null throwable should not throw")
    void loggingMethodsWithNullThrowableShouldNotThrow() {
      assertThatCode(() -> UiLogger.warn("message", null)).doesNotThrowAnyException();
      assertThatCode(() -> UiLogger.error("message", null)).doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("Initialize and configure methods")
  class InitializeAndConfigureMethods {

    @Test
    @DisplayName("initialize with null config should handle gracefully")
    void initializeWithNullConfig() {
      // Logger implementation handles null defensively
      assertThatCode(() -> UiLogger.initialize(null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("initialize should apply log format settings from config")
    void initializeShouldApplyLogFormatSettingsFromConfig() {
      Config config = new Config();
      Config.LogConfig logConfig = new Config.LogConfig();
      logConfig.setFormat("json");
      config.setLog(logConfig);

      UiLogger.initialize(config);

      assertThat(UiLogger.isJsonMode()).isTrue();
    }

    @Test
    @DisplayName("configureRunLogging with null parameters should handle gracefully")
    void configureRunLoggingWithNullParameters() {
      // Logger implementation handles null defensively
      assertThatCode(() -> UiLogger.configureRunLogging(null, null, null))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("configureRunLogging should update run log path and run id")
    void configureRunLoggingShouldUpdateRunLogPathAndRunId(@TempDir Path tempDir) {
      Config config = new Config();
      Config.LogConfig logConfig = new Config.LogConfig();
      logConfig.setOutput("both");
      logConfig.setFilePath("custom.log");
      config.setLog(logConfig);

      UiLogger.configureRunLogging(config, tempDir, "run-123");

      assertThat(Files.exists(tempDir.resolve("logs"))).isTrue();
      assertThat(config.getLog().getFilePath())
          .isEqualTo(tempDir.resolve("logs").resolve("custom.log").toString());
      assertThat(Logger.getRunId()).isEqualTo("run-123");
    }
  }
}
