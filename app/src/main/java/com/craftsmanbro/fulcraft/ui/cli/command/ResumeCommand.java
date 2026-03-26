package com.craftsmanbro.fulcraft.ui.cli.command;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.config.ConfigLoaderPort;
import com.craftsmanbro.fulcraft.infrastructure.validation.contract.PreFlightCheckPort;
import com.craftsmanbro.fulcraft.infrastructure.validation.impl.PreFlightChecker;
import com.craftsmanbro.fulcraft.ui.cli.UiLogger;
import com.craftsmanbro.fulcraft.ui.cli.command.support.CommandMessageSupport;
import com.craftsmanbro.fulcraft.ui.cli.command.support.TuiCommandSupport;
import com.craftsmanbro.fulcraft.ui.tui.TuiApplication;
import com.craftsmanbro.fulcraft.ui.tui.session.SessionMetadata;
import com.craftsmanbro.fulcraft.ui.tui.session.SessionStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ResourceBundle;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * CLI command for resuming a previous TUI session.
 *
 * <p>This command lists available sessions and allows the user to select one to resume. Sessions
 * are stored in {@code .ful/sessions/} and contain saved state, transcripts, and plans.
 *
 * <p>Example usage:
 *
 * <pre>
 *   ful resume            # Interactive session selection
 *   ful resume --list     # List sessions without prompt
 *   ful resume --id SESSION_ID   # Resume specific session directly
 * </pre>
 */
@Command(
    name = "resume",
    description = "${command.resume.description}",
    footer = "${command.resume.footer}",
    resourceBundle = "messages",
    mixinStandardHelpOptions = true)
@Category("other")
public class ResumeCommand extends BaseCliCommand {

  @Option(
      names = {"-l", "--list"},
      descriptionKey = "option.resume.list")
  private boolean listOnly;

  @Option(
      names = {"-i", "--id"},
      descriptionKey = "option.resume.id")
  private String sessionId;

  @Option(
      names = {"-p", "--project-root"},
      descriptionKey = "option.resume.project_root")
  private Path projectRoot;

  private final SessionStore sessionStore;

  private ResourceBundle resourceBundle;

  /** Creates ResumeCommand with default SessionStore. */
  public ResumeCommand() {
    this(new SessionStore());
  }

  /**
   * Creates ResumeCommand with custom SessionStore (for testing).
   *
   * @param sessionStore the session store to use
   */
  ResumeCommand(final SessionStore sessionStore) {
    this.sessionStore = sessionStore;
  }

  /**
   * Sets the resource bundle (injected by Picocli).
   *
   * @param resourceBundle the resource bundle to set
   */
  public void setResourceBundle(final ResourceBundle resourceBundle) {
    this.resourceBundle = resourceBundle;
  }

  private String msg(final String key, final Object... args) {
    resourceBundle = CommandMessageSupport.resolve(resourceBundle);
    return CommandMessageSupport.message(resourceBundle, key, args);
  }

  @Override
  protected Integer doCall(final Config config, final Path projectRoot) {
    try {
      final List<SessionMetadata> sessions = sessionStore.listSessions();
      if (listOnly) {
        if (sessions.isEmpty()) {
          UiLogger.stdout(msg("resume.no_sessions"));
          UiLogger.stdout(msg("resume.start_new_hint"));
          return CommandLine.ExitCode.OK;
        }
        displaySessionList(sessions);
        return CommandLine.ExitCode.OK;
      }
      final String requestedSessionId = sessionId == null ? null : sessionId.trim();
      if (requestedSessionId != null && !requestedSessionId.isBlank()) {
        if (!SessionStore.isValidSessionId(requestedSessionId)) {
          UiLogger.error(msg("resume.session_not_found", requestedSessionId));
          return CommandLine.ExitCode.USAGE;
        }
        if (!containsSessionId(sessions, requestedSessionId)) {
          UiLogger.error(msg("resume.session_not_found", requestedSessionId));
          return CommandLine.ExitCode.USAGE;
        }
        return resumeSession(requestedSessionId, projectRoot);
      }
      if (sessions.isEmpty()) {
        UiLogger.stdout(msg("resume.no_sessions"));
        UiLogger.stdout(msg("resume.start_new_hint"));
        return CommandLine.ExitCode.OK;
      }
      // Interactive selection
      return interactiveSelection(sessions, projectRoot);
    } catch (Exception e) {
      UiLogger.error(msg("resume.failed", e.getMessage()));
      return CommandLine.ExitCode.SOFTWARE;
    }
  }

  /** Displays the list of available sessions. */
  private void displaySessionList(final List<SessionMetadata> sessions) {
    UiLogger.stdout("");
    UiLogger.stdout(msg("resume.list.header"));
    UiLogger.stdout("─".repeat(80));
    for (int i = 0; i < sessions.size(); i++) {
      final SessionMetadata session = sessions.get(i);
      UiLogger.stdout(
          String.format("  %2d. %s", i + 1, SessionStore.formatSessionForDisplay(session)));
    }
    UiLogger.stdout("─".repeat(80));
    UiLogger.stdout(msg("resume.list.total", sessions.size()));
    UiLogger.stdout("");
  }

  private boolean containsSessionId(
      final List<SessionMetadata> sessions, final String sessionIdToFind) {
    return sessions.stream().anyMatch(session -> sessionIdToFind.equals(session.getId()));
  }

  /** Interactive session selection with numbered menu. */
  private int interactiveSelection(final List<SessionMetadata> sessions, final Path projectRoot)
      throws IOException {
    displaySessionList(sessions);
    UiLogger.stdout(msg("resume.interactive.prompt"));
    UiLogger.stdout("> ");
    final String input = readInteractiveInput();
    if (input == null || "q".equalsIgnoreCase(input.trim())) {
      UiLogger.stdout(msg("resume.interactive.cancelled"));
      return CommandLine.ExitCode.OK;
    }
    try {
      final int num = Integer.parseInt(input.trim());
      if (num < 1 || num > sessions.size()) {
        UiLogger.error(msg("resume.interactive.invalid_selection", num));
        return CommandLine.ExitCode.USAGE;
      }
      final SessionMetadata selected = sessions.get(num - 1);
      return resumeSession(selected.getId(), projectRoot);
    } catch (NumberFormatException e) {
      UiLogger.error(msg("resume.interactive.invalid_input", input));
      return CommandLine.ExitCode.USAGE;
    }
  }

  /** Resumes a session by ID. */
  private int resumeSession(final String id, final Path projectRoot) {
    UiLogger.info(msg("resume.resuming", id));
    final Path effectiveProjectRoot = projectRoot != null ? projectRoot : Path.of(".");
    if (!runPreFlight(effectiveProjectRoot)) {
      return CommandLine.ExitCode.SOFTWARE;
    }
    return TuiCommandSupport.launch(
        new TuiApplication(),
        app -> {
          app.setProjectRoot(effectiveProjectRoot);
          app.setResumeSessionId(id);
        },
        msg("app.exited_normally"),
        msg("app.error"),
        this::runApplication);
  }

  private void runApplication(final TuiApplication app) throws IOException {
    try {
      app.init();
      app.run();
    } catch (IOException | RuntimeException e) {
      app.handleFatalError(e);
      throw e;
    } catch (Exception e) {
      app.handleFatalError(e);
      throw new IOException(msg("resume.error.unexpected_tui"), e);
    }
  }

  private String readInteractiveInput() throws IOException {
    final java.io.Console console = System.console();
    if (console != null) {
      return console.readLine();
    }
    return readLineFromStdin();
  }

  private String readLineFromStdin() throws IOException {
    final StringBuilder buffer = new StringBuilder();
    while (true) {
      final int value = System.in.read();
      if (value == -1) {
        return buffer.isEmpty() ? null : buffer.toString();
      }
      if (value == '\n') {
        return buffer.toString();
      }
      if (value != '\r') {
        buffer.append((char) value);
      }
    }
  }

  private boolean runPreFlight(final Path projectRoot) {
    final PreFlightCheckPort checker = new PreFlightChecker();
    try {
      final Config config = loadConfig(projectRoot);
      checker.check(projectRoot, config);
      return true;
    } catch (RuntimeException e) {
      UiLogger.stderr(msg("app.preflight_failed"));
      UiLogger.stderr(e.getMessage());
      UiLogger.stderr(msg("app.preflight_fix"));
      UiLogger.error(msg("app.preflight_failed") + ": " + e.getMessage());
      return false;
    }
  }

  @Override
  protected Config loadConfig(final Path projectRoot) {
    final Path effectiveProjectRoot = projectRoot == null ? Path.of(".") : projectRoot;
    final ConfigLoaderPort configLoader = createConfigLoader();
    Path configFile = effectiveProjectRoot.resolve("config.json");
    if (!Files.exists(configFile)) {
      configFile = effectiveProjectRoot.resolve(".ful").resolve("config.json");
    }
    if (Files.exists(configFile)) {
      return configLoader.load(configFile);
    }
    return Config.createDefault();
  }

  @Override
  protected Path resolveConfigPath(final ConfigLoaderPort configLoader, final Path projectRoot) {
    return resolveProjectConfigPath(projectRoot);
  }

  @Override
  protected Path resolveProjectRoot(final Config config, final Path projectRootFromCli) {
    return projectRootFromCli != null ? projectRootFromCli : Path.of(".");
  }

  @Override
  protected boolean shouldApplyProjectRootToConfig() {
    return false;
  }

  @Override
  protected boolean shouldValidateProjectRoot() {
    return false;
  }

  @Override
  protected boolean shouldLoadConfig() {
    return false;
  }

  @Override
  protected Path resolveStartupBannerConfigPath(final Config config, final Path projectRoot) {
    final Path root = projectRoot != null ? projectRoot : Path.of(".");
    return resolveProjectConfigPath(root).toAbsolutePath().normalize();
  }

  @Override
  protected Path getProjectRootOption() {
    return projectRoot;
  }
}
