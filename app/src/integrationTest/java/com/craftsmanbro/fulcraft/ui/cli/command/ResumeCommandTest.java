package com.craftsmanbro.fulcraft.ui.cli.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.config.ConfigLoaderPort;
import com.craftsmanbro.fulcraft.ui.tui.TuiApplication;
import com.craftsmanbro.fulcraft.ui.tui.session.SessionMetadata;
import com.craftsmanbro.fulcraft.ui.tui.session.SessionStore;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.ListResourceBundle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import picocli.CommandLine;

@Isolated
class ResumeCommandTest {

  private static final String MINIMAL_VALID_CONFIG_JSON =
      """
      {
        "AppName": "demo-app",
        "version": "1.2.3",
        "project": { "id": "demo" },
        "selection_rules": {
          "class_min_loc": 1,
          "class_min_method_count": 1,
          "method_min_loc": 1,
          "method_max_loc": 2,
          "max_methods_per_class": 1,
          "exclude_getters_setters": true
        },
        "llm": { "provider": "mock" }
      }
      """;
  private static final String EXISTING_SESSION_ID = "20260101_010101_111";
  private static final String MISSING_SESSION_ID = "20260101_010101_999";
  private static final String INVALID_SESSION_ID = "../outside";

  @TempDir Path tempDir;

  @Test
  void constructor_defaultCanBeCreated() {
    assertThat(new ResumeCommand()).isNotNull();
  }

  @Test
  void doCall_printsMessage_whenNoSessions() {
    SessionStore store = mock(SessionStore.class);
    when(store.listSessions()).thenReturn(Collections.emptyList());

    ResumeCommand command = new ResumeCommand(store);
    command.setResourceBundle(new TestBundle());

    Config config = new Config();
    int exitCode = command.doCall(config, Path.of("."));

    assertThat(exitCode).isEqualTo(CommandLine.ExitCode.OK);
  }

  @Test
  void doCall_listsSessions_whenListOnly() {
    SessionStore store = mock(SessionStore.class);
    SessionMetadata session = new SessionMetadata(EXISTING_SESSION_ID, "test");
    when(store.listSessions()).thenReturn(List.of(session));

    ResumeCommand command = new ResumeCommand(store);
    command.setResourceBundle(new TestBundle());

    CommandLine cmd = new CommandLine(command);
    cmd.parseArgs("--list");

    Config config = new Config();
    int exitCode = command.doCall(config, Path.of("."));

    assertThat(exitCode).isEqualTo(CommandLine.ExitCode.OK);
  }

  @Test
  void doCall_returnsOk_whenInteractiveInputIsQuit() {
    SessionStore store = mock(SessionStore.class);
    SessionMetadata session = new SessionMetadata(EXISTING_SESSION_ID, "test");
    when(store.listSessions()).thenReturn(List.of(session));

    ResumeCommand command = new ResumeCommand(store);
    command.setResourceBundle(new TestBundle());

    int exitCode = runWithInput(command, "q\n");

    assertThat(exitCode).isEqualTo(CommandLine.ExitCode.OK);
  }

  @Test
  void doCall_returnsUsage_whenInteractiveInputIsNotNumber() {
    SessionStore store = mock(SessionStore.class);
    SessionMetadata session = new SessionMetadata(EXISTING_SESSION_ID, "test");
    when(store.listSessions()).thenReturn(List.of(session));

    ResumeCommand command = new ResumeCommand(store);
    command.setResourceBundle(new TestBundle());

    int exitCode = runWithInput(command, "abc\n");

    assertThat(exitCode).isEqualTo(CommandLine.ExitCode.USAGE);
  }

  @Test
  void doCall_returnsUsage_whenInteractiveSelectionIsOutOfRange() {
    SessionStore store = mock(SessionStore.class);
    SessionMetadata session = new SessionMetadata(EXISTING_SESSION_ID, "test");
    when(store.listSessions()).thenReturn(List.of(session));

    ResumeCommand command = new ResumeCommand(store);
    command.setResourceBundle(new TestBundle());

    int exitCode = runWithInput(command, "2\n");

    assertThat(exitCode).isEqualTo(CommandLine.ExitCode.USAGE);
  }

  @Test
  void doCall_returnsSoftware_whenSessionListingThrows() {
    SessionStore store = mock(SessionStore.class);
    when(store.listSessions()).thenThrow(new RuntimeException("listing failed"));

    ResumeCommand command = new ResumeCommand(store);
    command.setResourceBundle(new TestBundle());

    int exitCode = command.doCall(new Config(), Path.of("."));

    assertThat(exitCode).isEqualTo(CommandLine.ExitCode.SOFTWARE);
  }

  @Test
  void doCall_returnsSoftware_whenSessionIdProvidedAndPreFlightFails() {
    SessionStore store = mock(SessionStore.class);
    when(store.listSessions())
        .thenReturn(List.of(new SessionMetadata(EXISTING_SESSION_ID, "test")));

    ResumeCommand command = new ResumeCommand(store);
    command.setResourceBundle(new TestBundle());

    new CommandLine(command).parseArgs("--id", EXISTING_SESSION_ID);

    int exitCode = command.doCall(new Config(), tempDir.resolve("missing-root"));

    assertThat(exitCode).isEqualTo(CommandLine.ExitCode.SOFTWARE);
  }

  @Test
  void doCall_returnsUsage_whenSessionIdIsNotFound() {
    SessionStore store = mock(SessionStore.class);
    when(store.listSessions())
        .thenReturn(List.of(new SessionMetadata(EXISTING_SESSION_ID, "test")));

    ResumeCommand command = new ResumeCommand(store);
    command.setResourceBundle(new TestBundle());
    new CommandLine(command).parseArgs("--id", MISSING_SESSION_ID);

    int exitCode = command.doCall(new Config(), Path.of("."));

    assertThat(exitCode).isEqualTo(CommandLine.ExitCode.USAGE);
  }

  @Test
  void doCall_returnsUsage_whenSessionIdFormatIsInvalid() {
    SessionStore store = mock(SessionStore.class);
    when(store.listSessions())
        .thenReturn(List.of(new SessionMetadata(EXISTING_SESSION_ID, "test")));

    ResumeCommand command = new ResumeCommand(store);
    command.setResourceBundle(new TestBundle());
    new CommandLine(command).parseArgs("--id", INVALID_SESSION_ID);

    int exitCode = command.doCall(new Config(), Path.of("."));

    assertThat(exitCode).isEqualTo(CommandLine.ExitCode.USAGE);
  }

  @Test
  void doCall_returnsUsage_whenSessionIdIsProvidedButNoSessionsExist() {
    SessionStore store = mock(SessionStore.class);
    when(store.listSessions()).thenReturn(Collections.emptyList());

    ResumeCommand command = new ResumeCommand(store);
    command.setResourceBundle(new TestBundle());
    new CommandLine(command).parseArgs("--id", MISSING_SESSION_ID);

    int exitCode = command.doCall(new Config(), Path.of("."));

    assertThat(exitCode).isEqualTo(CommandLine.ExitCode.USAGE);
  }

  @Test
  void doCall_returnsOk_whenInteractiveInputIsEof() {
    SessionStore store = mock(SessionStore.class);
    when(store.listSessions())
        .thenReturn(List.of(new SessionMetadata(EXISTING_SESSION_ID, "test")));

    ResumeCommand command = new ResumeCommand(store);
    command.setResourceBundle(new TestBundle());

    int exitCode = runWithInput(command, "");

    assertThat(exitCode).isEqualTo(CommandLine.ExitCode.OK);
  }

  @Test
  void doCall_returnsSoftware_whenInteractiveSelectionResumesButPreFlightFails() {
    SessionStore store = mock(SessionStore.class);
    when(store.listSessions())
        .thenReturn(List.of(new SessionMetadata(EXISTING_SESSION_ID, "test")));

    ResumeCommand command = new ResumeCommand(store);
    command.setResourceBundle(new TestBundle());

    int exitCode = runWithInput(command, "1\n", tempDir.resolve("missing-root"));

    assertThat(exitCode).isEqualTo(CommandLine.ExitCode.SOFTWARE);
  }

  @Test
  void doCall_returnsSoftware_whenInteractiveInputReadFails() {
    SessionStore store = mock(SessionStore.class);
    when(store.listSessions())
        .thenReturn(List.of(new SessionMetadata(EXISTING_SESSION_ID, "test")));

    ResumeCommand command = new ResumeCommand(store);
    command.setResourceBundle(new TestBundle());

    InputStream brokenInput =
        new InputStream() {
          @Override
          public int read() throws IOException {
            throw new IOException("input failed");
          }
        };

    int exitCode = runWithInputStream(command, brokenInput, Path.of("."));

    assertThat(exitCode).isEqualTo(CommandLine.ExitCode.SOFTWARE);
  }

  @Test
  void loadConfig_returnsDefault_whenConfigFilesDoNotExist() {
    ResumeCommand command = new ResumeCommand(mock(SessionStore.class));

    Config config = command.loadConfig(tempDir);

    assertThat(config.getProject().getId()).isEqualTo("default");
  }

  @Test
  void loadConfig_loadsProjectConfigJson_whenPresent() throws Exception {
    ResumeCommand command = new ResumeCommand(mock(SessionStore.class));
    Path configFile = tempDir.resolve("config.json");
    Files.writeString(configFile, MINIMAL_VALID_CONFIG_JSON);

    Config config = command.loadConfig(tempDir);

    assertThat(config.getAppName()).isEqualTo("demo-app");
    assertThat(config.getProject().getId()).isEqualTo("demo");
  }

  @Test
  void loadConfig_loadsFulFallbackConfigJson_whenProjectConfigMissing() throws Exception {
    ResumeCommand command = new ResumeCommand(mock(SessionStore.class));
    Path fallbackConfig = tempDir.resolve(".ful").resolve("config.json");
    Files.createDirectories(fallbackConfig.getParent());
    Files.writeString(fallbackConfig, MINIMAL_VALID_CONFIG_JSON);

    Config config = command.loadConfig(tempDir);

    assertThat(config.getAppName()).isEqualTo("demo-app");
    assertThat(config.getProject().getId()).isEqualTo("demo");
  }

  @Test
  void loadConfig_usesCurrentDirectory_whenProjectRootIsNull() {
    ResumeCommand command = new ResumeCommand(mock(SessionStore.class));

    Config config = command.loadConfig(null);

    assertThat(config).isNotNull();
  }

  @Test
  void runPreFlight_returnsFalse_whenProjectRootDoesNotExist() throws Exception {
    ResumeCommand command = new ResumeCommand(mock(SessionStore.class));
    command.setResourceBundle(new TestBundle());

    boolean result = invokeRunPreFlight(command, tempDir.resolve("missing-root"));

    assertThat(result).isFalse();
  }

  @Test
  void runPreFlight_returnsTrue_whenProjectRootIsValid() throws Exception {
    ResumeCommand command = new ResumeCommand(mock(SessionStore.class));
    command.setResourceBundle(new TestBundle());
    Path projectRoot = tempDir.resolve("valid-project");
    Files.createDirectories(projectRoot.resolve("src/main/java"));
    Files.writeString(projectRoot.resolve("build.gradle"), "plugins { id 'java' }");

    boolean result = invokeRunPreFlight(command, projectRoot);

    assertThat(result).isTrue();
  }

  @Test
  void runPreFlight_returnsFalse_whenProjectRootIsNull() throws Exception {
    ResumeCommand command = new ResumeCommand(mock(SessionStore.class));
    command.setResourceBundle(new TestBundle());

    boolean result = invokeRunPreFlight(command, null);

    assertThat(result).isFalse();
  }

  @Test
  void runApplication_rethrowsIOException_afterFatalHandling() {
    ResumeCommand command = new ResumeCommand(mock(SessionStore.class));
    ThrowingTuiApplication app = new ThrowingTuiApplication(new IOException("io-failure"));

    assertThatThrownBy(() -> invokeRunApplication(command, app))
        .isInstanceOf(IOException.class)
        .hasMessage("io-failure");
    assertThat(app.fatalError).isInstanceOf(IOException.class);
  }

  @Test
  void runApplication_rethrowsRuntimeException_afterFatalHandling() {
    ResumeCommand command = new ResumeCommand(mock(SessionStore.class));
    ThrowingTuiApplication app = new ThrowingTuiApplication(new IllegalStateException("boom"));

    assertThatThrownBy(() -> invokeRunApplication(command, app))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("boom");
    assertThat(app.fatalError).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void runApplication_wrapsCheckedExceptionInIOException() {
    ResumeCommand command = new ResumeCommand(mock(SessionStore.class));
    ThrowingTuiApplication app = new ThrowingTuiApplication(new Exception("checked"));

    assertThatThrownBy(() -> invokeRunApplication(command, app))
        .isInstanceOf(IOException.class)
        .hasMessage("Unexpected error executing TUI")
        .hasCauseInstanceOf(Exception.class);
    assertThat(app.fatalError).isInstanceOf(Exception.class);
  }

  @Test
  void runApplication_completesWithoutFatalError_whenNoException() throws Throwable {
    ResumeCommand command = new ResumeCommand(mock(SessionStore.class));
    ThrowingTuiApplication app = new ThrowingTuiApplication(null);

    invokeRunApplication(command, app);

    assertThat(app.fatalError).isNull();
  }

  @Test
  void resolveProjectRoot_prefersCliProjectRootAndFallsBackToCurrentDirectory() {
    ResumeCommand command = new ResumeCommand(mock(SessionStore.class));

    Path resolvedFromCli = command.resolveProjectRoot(new Config(), Path.of("/tmp/project"));
    Path resolvedFallback = command.resolveProjectRoot(new Config(), null);

    assertThat(resolvedFromCli).isEqualTo(Path.of("/tmp/project"));
    assertThat(resolvedFallback).isEqualTo(Path.of("."));
  }

  @Test
  void resolveConfigPath_prefersFulConfig_whenProjectConfigMissing() throws Exception {
    ResumeCommand command = new ResumeCommand(mock(SessionStore.class));
    Path projectRoot = tempDir.resolve("project");
    Path fallback = projectRoot.resolve(".ful").resolve("config.json");
    Files.createDirectories(fallback.getParent());
    Files.writeString(fallback, MINIMAL_VALID_CONFIG_JSON);

    Path resolved =
        command.resolveConfigPath(org.mockito.Mockito.mock(ConfigLoaderPort.class), projectRoot);

    assertThat(resolved).isEqualTo(fallback);
  }

  @Test
  void resolveStartupBannerConfigPath_returnsAbsoluteNormalizedPath() throws Exception {
    ResumeCommand command = new ResumeCommand(mock(SessionStore.class));
    Path projectRoot = tempDir.resolve("project");
    Path fallback = projectRoot.resolve(".ful").resolve("config.json");
    Files.createDirectories(fallback.getParent());
    Files.writeString(fallback, MINIMAL_VALID_CONFIG_JSON);

    Path resolved = command.resolveStartupBannerConfigPath(new Config(), projectRoot);

    assertThat(resolved).isEqualTo(fallback.toAbsolutePath().normalize());
  }

  @Test
  void resolveStartupBannerConfigPath_defaultsToCurrentDirectory_whenProjectRootIsNull() {
    ResumeCommand command = new ResumeCommand(mock(SessionStore.class));

    Path resolved = command.resolveStartupBannerConfigPath(new Config(), null);

    assertThat(resolved).isNotNull().isAbsolute();
  }

  @Test
  void overrideFlagsReturnExpectedValues() {
    ResumeCommand command = new ResumeCommand(mock(SessionStore.class));

    assertThat(command.shouldApplyProjectRootToConfig()).isFalse();
    assertThat(command.shouldValidateProjectRoot()).isFalse();
    assertThat(command.shouldLoadConfig()).isFalse();
  }

  @Test
  void getProjectRootOption_returnsParsedCliOption() {
    ResumeCommand command = new ResumeCommand(mock(SessionStore.class));
    new CommandLine(command).parseArgs("--project-root", "/tmp/root");

    assertThat(command.getProjectRootOption()).isEqualTo(Path.of("/tmp/root"));
  }

  private static int runWithInput(ResumeCommand command, String input) {
    return runWithInput(command, input, Path.of("."));
  }

  private static int runWithInput(ResumeCommand command, String input, Path projectRoot) {
    InputStream originalIn = System.in;
    try {
      System.setIn(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
      return command.doCall(new Config(), projectRoot);
    } finally {
      System.setIn(originalIn);
    }
  }

  private static int runWithInputStream(
      ResumeCommand command, InputStream input, Path projectRoot) {
    InputStream originalIn = System.in;
    try {
      System.setIn(input);
      return command.doCall(new Config(), projectRoot);
    } finally {
      System.setIn(originalIn);
    }
  }

  private static boolean invokeRunPreFlight(ResumeCommand command, Path projectRoot)
      throws Exception {
    Method method = ResumeCommand.class.getDeclaredMethod("runPreFlight", Path.class);
    method.setAccessible(true);
    return (boolean) method.invoke(command, projectRoot);
  }

  private static void invokeRunApplication(ResumeCommand command, TuiApplication app)
      throws Throwable {
    Method method = ResumeCommand.class.getDeclaredMethod("runApplication", TuiApplication.class);
    method.setAccessible(true);
    try {
      method.invoke(command, app);
    } catch (InvocationTargetException e) {
      throw e.getCause();
    }
  }

  private static final class ThrowingTuiApplication extends TuiApplication {
    private final Throwable throwable;
    private Throwable fatalError;

    private ThrowingTuiApplication(Throwable throwable) {
      this.throwable = throwable;
    }

    @Override
    public void init() throws IOException {
      throwConfiguredThrowable();
    }

    @Override
    public void run() throws IOException {
      // No-op for unit tests: avoids terminal-dependent behavior.
    }

    @Override
    public void handleFatalError(Throwable error) {
      fatalError = error;
    }

    private void throwConfiguredThrowable() throws IOException {
      if (throwable == null) {
        return;
      }
      if (throwable instanceof IOException ioException) {
        throw ioException;
      }
      if (throwable instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      sneakyThrow(throwable);
    }

    private static <T extends Throwable> void sneakyThrow(Throwable throwable) throws T {
      throw (T) throwable;
    }
  }

  static class TestBundle extends ListResourceBundle {
    @Override
    protected Object[][] getContents() {
      return new Object[][] {
        {"resume.no_sessions", "No sessions"},
        {"resume.start_new_hint", "Start new"},
        {"resume.list.header", "Sessions"},
        {"resume.list.total", "Total {0}"},
        {"resume.failed", "Resume failed: {0}"},
        {"resume.interactive.prompt", "Select session number"},
        {"resume.interactive.cancelled", "Cancelled"},
        {"resume.interactive.invalid_selection", "Invalid selection: {0}"},
        {"resume.interactive.invalid_input", "Invalid input: {0}"},
        {"resume.session_not_found", "Session not found: {0}"},
        {"resume.resuming", "Resuming {0}"},
        {"app.preflight_failed", "Preflight failed"},
        {"app.preflight_fix", "Fix project setup"},
        {"app.exited_normally", "Exited"},
        {"app.error", "App error"}
      };
    }
  }
}
