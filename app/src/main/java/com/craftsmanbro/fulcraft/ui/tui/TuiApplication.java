package com.craftsmanbro.fulcraft.ui.tui;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.config.impl.ConfigLoaderImpl;
import com.craftsmanbro.fulcraft.ui.banner.StartupBannerSupport;
import com.craftsmanbro.fulcraft.ui.tui.command.CommandDispatcher;
import com.craftsmanbro.fulcraft.ui.tui.command.CommandResult;
import com.craftsmanbro.fulcraft.ui.tui.config.ConfigCommandService;
import com.craftsmanbro.fulcraft.ui.tui.config.ConfigEditor;
import com.craftsmanbro.fulcraft.ui.tui.config.ConfigValidationService;
import com.craftsmanbro.fulcraft.ui.tui.config.MetadataRegistry;
import com.craftsmanbro.fulcraft.ui.tui.conflict.ConflictDetector;
import com.craftsmanbro.fulcraft.ui.tui.conflict.ConflictPolicy;
import com.craftsmanbro.fulcraft.ui.tui.conflict.IssueHandlingOption;
import com.craftsmanbro.fulcraft.ui.tui.controller.ConfigController;
import com.craftsmanbro.fulcraft.ui.tui.execution.ExecutionOrchestrator;
import com.craftsmanbro.fulcraft.ui.tui.execution.ExecutionSession;
import com.craftsmanbro.fulcraft.ui.tui.i18n.TuiMessageSource;
import com.craftsmanbro.fulcraft.ui.tui.plan.Plan;
import com.craftsmanbro.fulcraft.ui.tui.plan.PlanBuilder;
import com.craftsmanbro.fulcraft.ui.tui.session.SessionMetadata;
import com.craftsmanbro.fulcraft.ui.tui.session.SessionStore;
import com.craftsmanbro.fulcraft.ui.tui.session.TranscriptEntry;
import com.craftsmanbro.fulcraft.ui.tui.state.ExecutionContext;
import com.craftsmanbro.fulcraft.ui.tui.state.State;
import com.craftsmanbro.fulcraft.ui.tui.state.StateMachine;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import java.io.IOException;
import java.util.List;

/**
 * Main TUI application using Lanterna.
 *
 * <p>This class manages the terminal screen lifecycle and main event loop. It ensures proper
 * terminal cleanup even on exceptions or interrupts.
 *
 * <p>Design principles:
 *
 * <ul>
 *   <li>Minimal UI: Compact header and state-specific content
 *   <li>Reliable cleanup: Terminal is restored in all cases
 *   <li>Simple key handling: Q to quit
 * </ul>
 */
public class TuiApplication implements AutoCloseable {

  private static final int HEADER_LINE = 0;

  private static final int CONTENT_START_LINE = 2;

  private static final int STARTUP_BOX_WIDTH = 53;

  private static final int STARTUP_TIP_INDENT = 2;

  private static final int STARTUP_TIP_ROW = 7;

  private static final int HOME_CONTENT_START_LINE = 9;

  private static final int ORCHESTRATOR_SHUTDOWN_TIMEOUT_SECONDS = 10;

  enum ConfigEditMode {
    BOOLEAN,
    ENUM,
    SCALAR,
    LIST,
    LIST_ITEM,
    LIST_ADD
  }

  private enum ShutdownReason {
    SIGINT,
    UNCAUGHT_EXCEPTION
  }

  private com.googlecode.lanterna.terminal.Terminal terminal;

  private com.googlecode.lanterna.screen.Screen lanternaScreen;

  private final StateMachine stateMachine;

  private final ScreenManager screenManager;

  private volatile boolean running;

  private final java.util.concurrent.atomic.AtomicBoolean shutdownInitiated =
      new java.util.concurrent.atomic.AtomicBoolean(false);

  // Input buffer for CHAT_INPUT
  private final MutableTextBuffer inputBuffer = new MutableTextBuffer();

  // Current plan for PLAN_REVIEW
  private Plan currentPlan;

  // Command dispatcher
  private final CommandDispatcher commandDispatcher;

  private final MetadataRegistry metadataRegistry;

  private final ConfigValidationService configValidationService;

  // Plan builder
  private final PlanBuilder planBuilder;

  // Command result to display (temporary)
  private CommandResult lastCommandResult;

  // Execution context for carrying state between screens
  private final ExecutionContext executionContext;

  // Conflict detector
  private ConflictDetector conflictDetector;

  // State for CONFLICT_POLICY confirmation flow
  private boolean awaitingOverwriteConfirmation;

  // Execution management for EXECUTION_RUNNING state
  private final ExecutionSession executionSession;

  private ExecutionOrchestrator orchestrator;

  private java.nio.file.Path projectRoot;

  // Log display scroll offset
  private int logScrollOffset;

  // Session persistence
  private final SessionStore sessionStore;

  // Refactored controllers
  private final ConfigController configControllerDelegated;

  private final TuiInputHandler tuiInputHandler;

  private final ConfigCommandService configCommandService;

  private String resumeSessionId;

  /** Creates a new TuiApplication with a fresh StateMachine. */
  public TuiApplication() {
    this(new StateMachine());
  }

  /**
   * Creates a new TuiApplication with the given StateMachine.
   *
   * @param stateMachine the state machine to use
   */
  public TuiApplication(final StateMachine stateMachine) {
    this.stateMachine = stateMachine;
    this.running = false;
    this.commandDispatcher = new CommandDispatcher();
    this.metadataRegistry = MetadataRegistry.getDefault();
    this.configValidationService = new ConfigValidationService();
    this.planBuilder = new PlanBuilder();
    this.executionContext = new ExecutionContext();
    this.projectRoot = java.nio.file.Path.of(".").toAbsolutePath();
    this.conflictDetector = ConflictDetector.createDefault(this.projectRoot);
    this.awaitingOverwriteConfirmation = false;
    this.executionSession = new ExecutionSession();
    this.sessionStore = new SessionStore();
    // Initialize refactored controllers
    this.configControllerDelegated =
        new ConfigController(stateMachine, metadataRegistry, configValidationService);
    this.configCommandService = new ConfigCommandService(metadataRegistry);
    this.tuiInputHandler = new TuiInputHandler(stateMachine, configControllerDelegated, this::draw);
    this.screenManager =
        new ScreenManager(
            stateMachine.getCurrentState(),
            List.of(
                new MainMenuScreen(),
                new ChatInputScreen(),
                new StepSelectionScreen(),
                new ConfigEditorScreen(State.CONFIG_CATEGORY),
                new ConfigEditorScreen(State.CONFIG_ITEMS),
                new ConfigEditorScreen(State.CONFIG_EDIT),
                new ConfigEditorScreen(State.CONFIG_VALIDATE),
                new ConflictPolicyScreen(),
                new ExecutionRunningScreen(),
                new IssueHandlingScreen(),
                new SummaryScreen()));
  }

  /**
   * Sets the session ID to resume.
   *
   * @param sessionId the session ID
   */
  public void setResumeSessionId(final String sessionId) {
    this.resumeSessionId = sessionId;
  }

  /**
   * Sets the project root directory for execution.
   *
   * @param projectRoot the project root path
   */
  public void setProjectRoot(final java.nio.file.Path projectRoot) {
    if (projectRoot != null) {
      this.projectRoot = projectRoot.toAbsolutePath();
      this.conflictDetector = ConflictDetector.createDefault(this.projectRoot);
    }
  }

  /**
   * Initializes the terminal and screen.
   *
   * @throws IOException if terminal initialization fails
   */
  public void init() throws IOException {
    final com.googlecode.lanterna.terminal.DefaultTerminalFactory terminalFactory =
        new com.googlecode.lanterna.terminal.DefaultTerminalFactory();
    terminal = terminalFactory.createTerminal();
    lanternaScreen = new com.googlecode.lanterna.screen.TerminalScreen(terminal);
    lanternaScreen.startScreen();
    // Hide cursor
    lanternaScreen.setCursorPosition(null);
    initSession();
  }

  /**
   * Runs the main event loop.
   *
   * <p>This method blocks until the user quits (Q key) or an error occurs.
   *
   * @throws IOException if an I/O error occurs
   */
  public void run() throws IOException {
    running = true;
    configureStateChangeListener();
    screenManager.setCurrentState(stateMachine.getCurrentState());
    // Initial draw
    draw();
    // Track last log count for execution state auto-refresh
    int lastLogCount = 0;
    // Main event loop
    while (running) {
      final KeyStroke keyStroke = lanternaScreen.pollInput();
      if (keyStroke != null) {
        handleInput(keyStroke);
      }
      // Auto-refresh during execution to show log updates
      if (stateMachine.getCurrentState() == State.EXECUTION_RUNNING) {
        lastLogCount = refreshExecutionState(lastLogCount);
      }
      if (isConfigState(stateMachine.getCurrentState())
          && configControllerDelegated.pollExternalChanges()) {
        draw();
      }
      sleepBriefly();
    }
  }

  private void configureStateChangeListener() {
    // Set up state change listener to trigger redraw and save state
    stateMachine.addListener(
        (prev, next) -> {
          screenManager.setCurrentState(next);
          requestRedraw();
          if (sessionStore != null) {
            try {
              sessionStore.updateMetadata(meta -> meta.setCurrentState(next));
            } catch (IOException e) {
              UiLogger.warn(
                  MessageSource.getMessage("tui.app.session_state_update_failed", e.getMessage()));
            }
          }
        });
  }

  private int refreshExecutionState(final int lastLogCount) throws IOException {
    int updatedLogCount = lastLogCount;
    final int currentLogCount = executionSession.getLogLines().size();
    if (currentLogCount != updatedLogCount) {
      updatedLogCount = currentLogCount;
      draw();
    }
    syncPendingIssue();
    if (executionContext.hasIssue()) {
      stateMachine.transitionTo(State.ISSUE_HANDLING);
      draw();
      return updatedLogCount;
    }
    // Also check if execution finished
    if (executionSession.isFinished()) {
      stateMachine.transitionTo(State.SUMMARY);
      draw();
    }
    return updatedLogCount;
  }

  private void syncPendingIssue() {
    executionSession
        .getPendingIssue()
        .ifPresent(
            issue -> {
              if (!executionContext.hasIssue()) {
                executionContext.setCurrentIssue(issue);
              }
            });
  }

  private void sleepBriefly() {
    // Small sleep to avoid busy-waiting
    try {
      Thread.sleep(50);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      running = false;
    }
  }

  private boolean isConfigState(final State state) {
    return state == State.CONFIG_CATEGORY
        || state == State.CONFIG_ITEMS
        || state == State.CONFIG_EDIT
        || state == State.CONFIG_VALIDATE;
  }

  /**
   * Handles keyboard input.
   *
   * @param keyStroke the key stroke to handle
   */
  private void handleInput(final KeyStroke keyStroke) throws IOException {
    // Handle special keys
    if (keyStroke.getKeyType() == KeyType.EOF) {
      running = false;
      return;
    }
    if (keyStroke.getKeyType() == KeyType.Escape) {
      if (isExecutionRunning()) {
        executionSession.appendLog(MessageSource.getMessage("tui.app.exit_blocked"));
        if (lanternaScreen != null && stateMachine.getCurrentState() == State.EXECUTION_RUNNING) {
          draw();
        }
        return;
      }
      running = false;
      return;
    }
    screenManager.handleInput(this, keyStroke);
  }

  private boolean isExecutionRunning() {
    return orchestrator != null && executionSession.isRunning();
  }

  /** Handles input in CHAT_INPUT state. */
  void handleChatInput(final KeyStroke keyStroke) throws IOException {
    final KeyType keyType = keyStroke.getKeyType();
    if (keyType == KeyType.Enter) {
      // Submit input
      final String input = inputBuffer.toString().trim();
      if (!input.isEmpty()) {
        processUserInput(input);
      }
      inputBuffer.setLength(0);
      draw();
    } else if (keyType == KeyType.Backspace && !inputBuffer.isEmpty()) {
      // Delete last character
      inputBuffer.deleteCharAt(inputBuffer.length() - 1);
      draw();
    } else if (keyType == KeyType.Character) {
      final char c = keyStroke.getCharacter();
      // Allow quit with Q only when buffer is empty
      if ((c == 'q' || c == 'Q') && inputBuffer.isEmpty()) {
        running = false;
        return;
      }
      inputBuffer.append(c);
      draw();
    }
  }

  /** Processes user input after Enter is pressed. */
  private void processUserInput(final String input) throws IOException {
    lastCommandResult = null;
    if (commandDispatcher.isCommand(input)) {
      final ConfigCommandService.ParsedCommand configCommand =
          configCommandService.parseCommand(input);
      if (configCommand != null) {
        handleConfigCommand(input, configCommand);
        return;
      }
      // Handle non-config slash command
      sessionStore.appendTranscript(
          TranscriptEntry.command(input, MessageSource.getMessage("tui.app.executing")));
      lastCommandResult = commandDispatcher.dispatch(input);
      logCommandResult();
    } else {
      processNaturalLanguageInput(input);
    }
    draw();
  }

  private void handleConfigCommand(
      final String input, final ConfigCommandService.ParsedCommand configCommand)
      throws IOException {
    switch (configCommand.type()) {
      case OPEN -> handleConfigOpenCommand(input);
      case GET -> handleConfigGetCommand(input, configCommand);
      case SET -> handleConfigSetCommand(input, configCommand);
      case SEARCH -> handleConfigSearchCommand(input, configCommand);
      case VALIDATE -> handleConfigValidateCommand(input);
      case UNKNOWN -> handleConfigUnknownCommand(input, configCommand);
    }
  }

  private void handleConfigOpenCommand(final String input) throws IOException {
    sessionStore.appendTranscript(
        TranscriptEntry.command(input, MessageSource.getMessage("tui.app.config_open")));
    enterConfigEditor();
    draw();
  }

  private void handleConfigSetCommand(
      final String input, final ConfigCommandService.ParsedCommand configCommand)
      throws IOException {
    sessionStore.appendTranscript(
        TranscriptEntry.command(input, MessageSource.getMessage("tui.app.executing")));
    lastCommandResult = configCommandService.applySet(configCommand.args(), resolveConfigPath());
    logCommandResult();
    draw();
  }

  private void handleConfigGetCommand(
      final String input, final ConfigCommandService.ParsedCommand configCommand)
      throws IOException {
    sessionStore.appendTranscript(
        TranscriptEntry.command(input, MessageSource.getMessage("tui.app.executing")));
    lastCommandResult = configCommandService.applyGet(configCommand.args(), resolveConfigPath());
    logCommandResult();
    draw();
  }

  private void handleConfigSearchCommand(
      final String input, final ConfigCommandService.ParsedCommand configCommand)
      throws IOException {
    sessionStore.appendTranscript(
        TranscriptEntry.command(input, MessageSource.getMessage("tui.app.config_search")));
    final ConfigCommandService.SearchResult searchResult =
        configCommandService.applySearch(configCommand.args(), resolveConfigPath());
    if (searchResult.shouldOpenEditor()) {
      openConfigEditorAtMetadata(searchResult.singleMatch());
    } else if (searchResult.commandResult() != null) {
      lastCommandResult = searchResult.commandResult();
      logCommandResult();
    }
    draw();
  }

  private void handleConfigValidateCommand(final String input) throws IOException {
    sessionStore.appendTranscript(
        TranscriptEntry.command(input, MessageSource.getMessage("tui.app.config_validate")));
    enterConfigEditor();
    configControllerDelegated.runValidation(
        stateMachine.getCurrentState(),
        TuiMessageSource.getDefault().getMessage(TuiMessageSource.CONFIG_VALIDATION_FAILED_MSG));
    draw();
  }

  private void handleConfigUnknownCommand(
      final String input, final ConfigCommandService.ParsedCommand configCommand)
      throws IOException {
    sessionStore.appendTranscript(
        TranscriptEntry.command(input, MessageSource.getMessage("tui.app.executing")));
    lastCommandResult =
        CommandResult.error(
            MessageSource.getMessage("tui.app.config_unknown", configCommand.args()));
    logCommandResult();
    draw();
  }

  private void logCommandResult() {
    if (lastCommandResult == null) {
      return;
    }
    final String result =
        lastCommandResult.success()
            ? MessageSource.getMessage("tui.app.result_success")
            : MessageSource.getMessage("tui.app.result_error", lastCommandResult.errorMessage());
    sessionStore.appendTranscript(TranscriptEntry.systemResponse(result));
  }

  private void processNaturalLanguageInput(final String input) {
    sessionStore.appendTranscript(TranscriptEntry.userInput(input));
    // Generate plan and transition to PLAN_REVIEW
    currentPlan = planBuilder.build(input);
    // Save plan
    try {
      sessionStore.savePlan(currentPlan);
      sessionStore.appendTranscript(
          TranscriptEntry.systemResponse(
              MessageSource.getMessage("tui.app.plan_generated", currentPlan.goal())));
    } catch (IOException e) {
      UiLogger.warn(MessageSource.getMessage("tui.app.plan_save_failed", e.getMessage()));
    }
    stateMachine.transitionTo(State.PLAN_REVIEW);
    sessionStore.appendTranscript(
        TranscriptEntry.stateChange(State.CHAT_INPUT.name(), State.PLAN_REVIEW.name()));
  }

  private void enterConfigEditor() {
    // Delegate to ConfigController
    configControllerDelegated.enter(projectRoot);
  }

  private java.nio.file.Path resolveConfigPath() {
    return projectRoot.resolve("config.json");
  }

  private void openConfigEditorAtMetadata(final MetadataRegistry.ConfigKeyMetadata metadata) {
    enterConfigEditor();
    configControllerDelegated.openAtPath(metadata.path());
  }

  /** Handles input in PLAN_REVIEW state. */
  void handlePlanReviewInput(final KeyStroke keyStroke) throws IOException {
    if (keyStroke.getKeyType() == KeyType.Character) {
      final char c = Character.toUpperCase(keyStroke.getCharacter());
      switch (c) {
        case 'A':
          // Approve - store plan and transition to conflict policy
          if (currentPlan != null) {
            executionContext.setPlan(currentPlan);
          }
          stateMachine.transitionTo(State.CONFLICT_POLICY);
          draw();
          break;
        case 'E':
          // Edit - go back to chat input
          stateMachine.transitionTo(State.CHAT_INPUT);
          draw();
          break;
        case 'Q':
          // Quit
          running = false;
          break;
        default:
          // Ignore other keys
          break;
      }
    }
  }

  // Config input handlers have been moved to TuiInputHandler
  // Dead config helper methods have been removed (moved to ConfigController)
  /**
   * Handles input in CONFLICT_POLICY state.
   *
   * <p>Supports the following inputs:
   *
   * <ul>
   *   <li>1 - Select Safe policy (new file names)
   *   <li>2 - Select Skip policy (skip conflicting files)
   *   <li>3 - Select Overwrite policy (requires confirmation)
   *   <li>Y/N - Confirm/cancel overwrite when awaiting confirmation
   *   <li>Q - Quit
   * </ul>
   */
  void handleConflictPolicyInput(final KeyStroke keyStroke) throws IOException {
    if (keyStroke.getKeyType() != KeyType.Character) {
      return;
    }
    final char c = Character.toUpperCase(keyStroke.getCharacter());
    if (awaitingOverwriteConfirmation) {
      // Handle confirmation for overwrite
      handleOverwriteConfirmation(c);
    } else {
      // Handle policy selection
      handlePolicySelection(c);
    }
  }

  /** Handles policy selection (1, 2, or 3). */
  private void handlePolicySelection(final char c) throws IOException {
    switch (c) {
      case '1':
        // Safe: new file name
        applyConflictPolicy(ConflictPolicy.SAFE);
        proceedToExecution();
        break;
      case '2':
        // Skip
        applyConflictPolicy(ConflictPolicy.SKIP);
        proceedToExecution();
        break;
      case '3':
        // Overwrite - requires confirmation
        if (executionContext.hasConflicts()) {
          awaitingOverwriteConfirmation = true;
          draw();
        } else {
          // No conflicts, just proceed
          applyConflictPolicy(ConflictPolicy.OVERWRITE);
          proceedToExecution();
        }
        break;
      case 'Q':
        running = false;
        break;
      case 'B':
        // Back to PLAN_REVIEW
        stateMachine.transitionTo(State.PLAN_REVIEW);
        draw();
        break;
      default:
        // Ignore other keys
        break;
    }
  }

  /** Handles Y/N confirmation for overwrite. */
  private void handleOverwriteConfirmation(final char c) throws IOException {
    switch (c) {
      case 'Y':
        // Confirmed overwrite
        applyConflictPolicy(ConflictPolicy.OVERWRITE);
        executionContext.setOverwriteConfirmed(true);
        awaitingOverwriteConfirmation = false;
        proceedToExecution();
        break;
      case 'N':
        // Cancelled - return to policy selection
        awaitingOverwriteConfirmation = false;
        draw();
        break;
      default:
        // Ignore other keys during confirmation
        break;
    }
  }

  private void applyConflictPolicy(final ConflictPolicy policy) {
    executionContext.setConflictPolicy(policy);
    if (sessionStore != null) {
      try {
        sessionStore.updateMetadata(meta -> meta.setConflictPolicy(policy));
      } catch (IOException e) {
        UiLogger.warn(
            MessageSource.getMessage("tui.app.conflict_policy_update_failed", e.getMessage()));
      }
    }
  }

  /** Proceeds to the execution state. */
  private void proceedToExecution() throws IOException {
    // Shutdown prior orchestrator instance before replacing it to avoid resource
    // accumulation.
    shutdownOrchestrator();
    // Reset execution session for new run
    executionSession.reset();
    executionContext.clearIssue();
    orchestrator = ExecutionOrchestrator.create(executionSession, projectRoot);
    orchestrator.setIssueHandler(
        issue -> {
          if (!executionContext.hasIssue()) {
            executionContext.setCurrentIssue(issue);
          }
        });
    orchestrator.startExecution();
    stateMachine.transitionTo(State.EXECUTION_RUNNING);
    draw();
  }

  /**
   * Handles input in EXECUTION_RUNNING state.
   *
   * <p>Supports:
   *
   * <ul>
   *   <li>C - Cancel execution and go to Summary
   *   <li>UP/DOWN - Scroll log view
   * </ul>
   */
  void handleExecutionRunningInput(final KeyStroke keyStroke) throws IOException {
    if (!handleExecutionRunningCharacterInput(keyStroke)) {
      handleExecutionRunningScrollInput(keyStroke);
    }
    updateExecutionRunningState();
  }

  private boolean handleExecutionRunningCharacterInput(final KeyStroke keyStroke) {
    if (keyStroke.getKeyType() != KeyType.Character) {
      return false;
    }
    final char c = Character.toUpperCase(keyStroke.getCharacter());
    switch (c) {
      case 'C':
        // Cancel execution
        requestExecutionCancel();
        break;
      case 'Q':
        // Only allow quit if execution is finished
        handleExecutionQuit();
        break;
      default:
        break;
    }
    return true;
  }

  private void requestExecutionCancel() {
    if (orchestrator != null) {
      orchestrator.requestCancel();
    }
  }

  private void handleExecutionQuit() {
    if (executionSession.isFinished()) {
      running = false;
    }
  }

  private void shutdownOrchestrator() {
    if (orchestrator == null) {
      return;
    }
    try {
      if (isExecutionRunning()) {
        orchestrator.requestCancel();
        if (!orchestrator.waitForCompletion(ORCHESTRATOR_SHUTDOWN_TIMEOUT_SECONDS)) {
          UiLogger.warn(MessageSource.getMessage("tui.app.orchestrator_shutdown_timeout"));
        }
      }
      orchestrator.shutdown();
    } catch (RuntimeException e) {
      UiLogger.warn(
          MessageSource.getMessage("tui.app.orchestrator_shutdown_failed", e.getMessage()));
    } finally {
      orchestrator = null;
    }
  }

  private void handleExecutionRunningScrollInput(final KeyStroke keyStroke) {
    if (keyStroke.getKeyType() == KeyType.ArrowUp) {
      // Scroll log up
      if (logScrollOffset > 0) {
        logScrollOffset--;
      }
    } else if (keyStroke.getKeyType() == KeyType.ArrowDown) {
      // Scroll log down
      logScrollOffset++;
    }
  }

  private void updateExecutionRunningState() throws IOException {
    // Check if execution is finished and transition to SUMMARY
    if (executionContext.hasIssue()) {
      stateMachine.transitionTo(State.ISSUE_HANDLING);
      draw();
      return;
    }
    if (executionSession.isFinished()) {
      stateMachine.transitionTo(State.SUMMARY);
    }
    draw();
  }

  /**
   * Handles input in ISSUE_HANDLING state.
   *
   * <p>Supports:
   *
   * <ul>
   *   <li>1 - Safe fix (apply conservative fix and retry)
   *   <li>2 - Propose only (generate proposal without applying)
   *   <li>3 - Skip (skip this target and continue)
   *   <li>Q - Quit
   * </ul>
   */
  void handleIssueHandlingInput(final KeyStroke keyStroke) throws IOException {
    final IssueHandlingOption option = resolveIssueHandlingOption(keyStroke);
    if (option == null) {
      return;
    }
    executionContext.setSelectedOption(option);
    applyIssueHandlingDecision();
  }

  private IssueHandlingOption resolveIssueHandlingOption(final KeyStroke keyStroke) {
    if (keyStroke.getKeyType() != KeyType.Character) {
      return null;
    }
    final char c = Character.toUpperCase(keyStroke.getCharacter());
    if (c == 'Q') {
      running = false;
      return null;
    }
    if (c < '0' || c > '9') {
      return null;
    }
    return IssueHandlingOption.fromKeyNumber(c - '0');
  }

  /**
   * Applies the user's issue handling decision and returns to execution.
   *
   * <p>Based on the selected option:
   *
   * <ul>
   *   <li>SAFE_FIX - Log the decision and return to execution
   *   <li>PROPOSE_ONLY - Log the proposal and continue
   *   <li>SKIP - Skip the current target and continue
   * </ul>
   */
  private void applyIssueHandlingDecision() throws IOException {
    final IssueHandlingOption option = executionContext.getSelectedOption().orElse(null);
    if (option == null) {
      return;
    }
    // Log the decision
    executionContext
        .getCurrentIssue()
        .ifPresent(
            issue -> {
              final String message =
                  switch (option) {
                    case SAFE_FIX ->
                        MessageSource.getMessage(
                            "tui.app.issue_safe_fix", issue.targetIdentifier());
                    case PROPOSE_ONLY ->
                        MessageSource.getMessage(
                            "tui.app.issue_propose_only", issue.targetIdentifier());
                    case SKIP ->
                        MessageSource.getMessage("tui.app.issue_skip", issue.targetIdentifier());
                  };
              executionSession.appendLog(
                  MessageSource.getMessage("tui.app.issue_user_selected", option.getDisplayName()));
              executionSession.appendLog("  " + message);
            });
    executionSession.resolveIssue(option);
    // Clear the issue and return to execution
    executionContext.clearIssue();
    stateMachine.transitionTo(State.EXECUTION_RUNNING);
    draw();
  }

  /** Handles input in other states (HOME, etc.) */
  void handleDefaultInput(final KeyStroke keyStroke) throws IOException {
    // Handle character keys
    if (keyStroke.getKeyType() == KeyType.Character) {
      final char c = Character.toUpperCase(keyStroke.getCharacter());
      handleCharacterInput(c);
    }
  }

  private void handleCharacterInput(final char c) throws IOException {
    switch (c) {
      case 'Q':
        running = false;
        break;
      case '1':
        stateMachine.transitionTo(State.HOME);
        draw();
        break;
      case '2':
        inputBuffer.setLength(0);
        lastCommandResult = null;
        stateMachine.transitionTo(State.CHAT_INPUT);
        draw();
        break;
      case '3':
        stateMachine.transitionTo(State.PLAN_REVIEW);
        draw();
        break;
      case '4':
        stateMachine.transitionTo(State.CONFLICT_POLICY);
        draw();
        break;
      case '5':
        stateMachine.transitionTo(State.EXECUTION_RUNNING);
        draw();
        break;
      case '6':
        stateMachine.transitionTo(State.ISSUE_HANDLING);
        draw();
        break;
      case '7':
        stateMachine.transitionTo(State.SUMMARY);
        draw();
        break;
      default:
        // Ignore other keys for now
        break;
    }
  }

  /** Requests a redraw on the next loop iteration. */
  private void requestRedraw() {
    // For now, this is a no-op since we redraw in handleInput
    // Future implementation may use a flag for deferred redraw
  }

  /**
   * Draws the current screen content.
   *
   * @throws IOException if drawing fails
   */
  private void draw() throws IOException {
    lanternaScreen.clear();
    final TextGraphics tg = lanternaScreen.newTextGraphics();
    final TerminalSize size = lanternaScreen.getTerminalSize();
    // Draw header (HOME startup banner or compact state header)
    drawHeader(tg, size);
    // Draw state-specific content
    drawContent(tg, size);
    lanternaScreen.refresh();
  }

  /**
   * Draws the header line.
   *
   * @param tg text graphics context
   * @param size terminal size
   */
  private void drawHeader(final TextGraphics tg, final TerminalSize size) {
    if (size.getColumns() <= 0 || size.getRows() <= HEADER_LINE) {
      return;
    }
    final State state = stateMachine.getCurrentState();
    if (state == State.HOME && drawStartupBanner(tg, size)) {
      return;
    }
    drawCompactHeader(tg, size, state);
  }

  private void drawCompactHeader(
      final TextGraphics tg, final TerminalSize size, final State state) {
    final TuiMessageSource messageSource = TuiMessageSource.getDefault();
    final String headerHint;
    if (state == State.CONFIG_VALIDATE) {
      headerHint = messageSource.getMessage(TuiMessageSource.HEADER_CONFIG_VALIDATE_HINT);
    } else if (isConfigState(state)) {
      headerHint = messageSource.getMessage(TuiMessageSource.HEADER_CONFIG_HINT);
    } else {
      headerHint = messageSource.getMessage(TuiMessageSource.HEADER_QUIT_HINT);
    }
    String headerText =
        messageSource.getMessage(TuiMessageSource.HEADER_TITLE_PREFIX)
            + state.getDisplayName()
            + headerHint;
    if (isConfigState(state)) {
      final ConfigEditor editor = configControllerDelegated.getConfigEditor();
      if (editor != null && editor.isDirty()) {
        headerText += messageSource.getMessage(TuiMessageSource.HEADER_DIRTY);
      }
    }
    if (headerText.length() > size.getColumns()) {
      headerText = headerText.substring(0, size.getColumns());
    }
    // Draw header with inverted colors for visibility
    tg.setForegroundColor(TextColor.ANSI.BLACK);
    tg.setBackgroundColor(TextColor.ANSI.WHITE);
    // Fill header line
    final StringBuilder headerLine = new StringBuilder(headerText);
    while (headerLine.length() < size.getColumns()) {
      headerLine.append(' ');
    }
    tg.putString(0, HEADER_LINE, headerLine.toString());
    // Reset colors
    tg.setForegroundColor(TextColor.ANSI.DEFAULT);
    tg.setBackgroundColor(TextColor.ANSI.DEFAULT);
  }

  private boolean drawStartupBanner(final TextGraphics tg, final TerminalSize size) {
    final int columns = size.getColumns();
    final int rows = size.getRows();
    if (columns < 4 || rows < HEADER_LINE + 7) {
      return false;
    }
    final int boxWidth = Math.min(STARTUP_BOX_WIDTH, columns);
    final int innerWidth = boxWidth - 2;
    if (innerWidth <= 0) {
      return false;
    }
    final String horizontal = "─".repeat(innerWidth);
    final ConfigLoaderImpl configLoader = new ConfigLoaderImpl();
    final String startupTitle =
        ">_ "
            + StartupBannerSupport.resolveApplicationName(projectRoot, configLoader)
            + " (v"
            + StartupBannerSupport.resolveApplicationVersion(projectRoot, configLoader)
            + ")";
    final String startupModelLabel =
        MessageSource.getMessage("tui.app.startup.model_label")
            + " "
            + StartupBannerSupport.resolveModelName(projectRoot, configLoader)
            + "   "
            + MessageSource.getMessage("tut.startup.model_hint");
    tg.putString(0, HEADER_LINE, "╭" + horizontal + "╮");
    tg.putString(0, HEADER_LINE + 1, formatBoxLine(" " + startupTitle, innerWidth));
    tg.putString(0, HEADER_LINE + 2, formatBoxLine("", innerWidth));
    tg.putString(0, HEADER_LINE + 3, formatBoxLine(" " + startupModelLabel, innerWidth));
    tg.putString(
        0,
        HEADER_LINE + 4,
        formatBoxLine(
            MessageSource.getMessage("tui.app.startup.directory_label")
                + " "
                + StartupBannerSupport.formatDirectory(projectRoot),
            innerWidth));
    tg.putString(0, HEADER_LINE + 5, "╰" + horizontal + "╯");
    if (rows > HOME_CONTENT_START_LINE) {
      final int availableWidth = Math.max(0, columns - STARTUP_TIP_INDENT);
      tg.putString(
          STARTUP_TIP_INDENT,
          STARTUP_TIP_ROW,
          truncateForScreen(MessageSource.getMessage("tut.startup.tip"), availableWidth));
    }
    return true;
  }

  private String formatBoxLine(final String content, final int innerWidth) {
    final String normalized = content == null ? "" : content;
    final String clipped =
        normalized.length() > innerWidth ? normalized.substring(0, innerWidth) : normalized;
    final StringBuilder builder = new StringBuilder(innerWidth + 2);
    builder.append('│').append(clipped);
    while (builder.length() < innerWidth + 1) {
      builder.append(' ');
    }
    return builder.append('│').toString();
  }

  /**
   * Draws state-specific content.
   *
   * @param tg text graphics context
   */
  private void drawContent(final TextGraphics tg, final TerminalSize size) {
    final int row = resolveContentStartLine(size);
    screenManager.draw(this, tg, size, row);
  }

  private int resolveContentStartLine(final TerminalSize size) {
    final int desiredRow =
        stateMachine.getCurrentState() == State.HOME ? HOME_CONTENT_START_LINE : CONTENT_START_LINE;
    if (size.getRows() <= 0) {
      return 0;
    }
    return Math.min(desiredRow, size.getRows() - 1);
  }

  /** Stops the event loop. */
  public void stop() {
    running = false;
  }

  public void handleFatalError(final Throwable error) {
    handleShutdown(ShutdownReason.UNCAUGHT_EXCEPTION, error);
  }

  public void handleInterrupt() {
    handleShutdown(ShutdownReason.SIGINT, null);
  }

  /** Returns the current StateMachine. */
  public StateMachine getStateMachine() {
    return stateMachine;
  }

  /** Returns true if the application is currently running. */
  public boolean isRunning() {
    return running;
  }

  /** Returns the command dispatcher for testing/extension. */
  public CommandDispatcher getCommandDispatcher() {
    return commandDispatcher;
  }

  /** Returns the current plan for testing. */
  Plan getCurrentPlan() {
    return currentPlan;
  }

  /** Sets the current plan for testing. */
  void setCurrentPlan(final Plan plan) {
    this.currentPlan = plan;
  }

  /** Returns the input buffer for testing. */
  String getInputBuffer() {
    return inputBuffer.toString();
  }

  /** Sets the input buffer for testing. */
  void setInputBuffer(final String content) {
    inputBuffer.setLength(0);
    inputBuffer.append(content);
  }

  /** Returns the last command result for testing. */
  CommandResult getLastCommandResult() {
    return lastCommandResult;
  }

  /** Returns the execution context for testing. */
  ExecutionContext getExecutionContext() {
    return executionContext;
  }

  /** Returns true if awaiting overwrite confirmation for testing. */
  boolean isAwaitingOverwriteConfirmation() {
    return awaitingOverwriteConfirmation;
  }

  ExecutionSession getExecutionSession() {
    return executionSession;
  }

  ConflictDetector getConflictDetector() {
    return conflictDetector;
  }

  int getLogScrollOffset() {
    return logScrollOffset;
  }

  void setLogScrollOffset(final int logScrollOffset) {
    this.logScrollOffset = logScrollOffset;
  }

  /** Returns the TuiInputHandler for delegating input handling. */
  TuiInputHandler getTuiInputHandler() {
    return tuiInputHandler;
  }

  /** Returns the ConfigController for accessing config state. */
  ConfigController getConfigController() {
    return configControllerDelegated;
  }

  /**
   * Closes the terminal and screen, restoring the terminal state.
   *
   * <p>This method is safe to call multiple times and will not throw exceptions.
   */
  @Override
  public void close() {
    shutdownOrchestrator();
    try {
      if (lanternaScreen != null) {
        lanternaScreen.stopScreen();
      }
    } catch (IOException e) {
      // Best effort cleanup, log but don't throw
      UiLogger.warn(MessageSource.getMessage("tui.app.screen_stop_failed", e.getMessage()));
    }
    try {
      if (terminal != null) {
        terminal.close();
      }
    } catch (IOException e) {
      // Best effort cleanup
      UiLogger.warn(MessageSource.getMessage("tui.app.terminal_close_failed", e.getMessage()));
    }
    // Close session
    if (sessionStore != null) {
      final boolean completed = executionSession.getStatus() == ExecutionSession.Status.COMPLETED;
      sessionStore.closeSession(completed);
    }
  }

  /** Initializes or resumes the session. */
  private void initSession() {
    try {
      if (resumeSessionId != null) {
        final java.util.Optional<SessionMetadata> meta = sessionStore.loadSession(resumeSessionId);
        if (meta.isPresent()) {
          restoreStateFromSession(meta.get());
          return;
        }
        UiLogger.warn(MessageSource.getMessage("tui.app.session_not_found", resumeSessionId));
      }
      final String id = sessionStore.createSession(projectRoot.toString());
      UiLogger.info(MessageSource.getMessage("tui.app.session_created", id));
      // Record initial state
      sessionStore.appendTranscript(TranscriptEntry.stateChange(null, State.HOME.name()));
    } catch (IOException e) {
      UiLogger.error(
          MessageSource.getMessage("tui.app.session_state_update_failed", e.getMessage()));
    }
  }

  /** Restores application data from loaded session metadata. */
  void restoreStateFromSession(final SessionMetadata meta) {
    // Always start from HOME at startup, even when resuming an existing session.
    stateMachine.transitionTo(State.HOME);
    try {
      sessionStore.updateMetadata(stored -> stored.setCurrentState(State.HOME));
    } catch (IOException e) {
      UiLogger.warn(
          MessageSource.getMessage("tui.app.session_state_to_home_failed", e.getMessage()));
    }
    // Restore conflict policy if set
    if (meta.getConflictPolicy() != null) {
      executionContext.setConflictPolicy(meta.getConflictPolicy());
    }
    // Restore plan if available
    final java.util.Optional<Plan> plan = sessionStore.loadPlan();
    plan.ifPresent(
        p -> {
          executionContext.setPlan(p);
          currentPlan = p;
        });
    // Load previous transcript to show history (optional, future improvement)
    // For now just log that we resumed
    sessionStore.appendTranscript(
        TranscriptEntry.systemResponse(
            MessageSource.getMessage("tui.app.session_resumed", meta.getId())));
  }

  private void handleShutdown(final ShutdownReason reason, final Throwable error) {
    if (!shutdownInitiated.compareAndSet(false, true)) {
      return;
    }
    running = false;
    final String summary =
        reason == ShutdownReason.SIGINT
            ? MessageSource.getMessage("tui.app.shutdown_interrupted")
            : MessageSource.getMessage("tui.app.shutdown_unexpected");
    final String detail = formatErrorDetail(error);
    appendShutdownTranscript(reason, summary, detail);
    if (error != null) {
      UiLogger.error(summary, error);
    } else {
      UiLogger.warn(summary);
    }
    final String sessionPath =
        sessionStore.getSessionDir().map(java.nio.file.Path::toString).orElse(null);
    renderShutdownScreen(summary, detail, sessionPath);
    close();
    writeShutdownMessage(summary, detail, sessionPath);
  }

  private void appendShutdownTranscript(
      final ShutdownReason reason, final String summary, final String detail) {
    if (sessionStore == null) {
      return;
    }
    final String message = detail == null || detail.isBlank() ? summary : summary + " " + detail;
    if (reason == ShutdownReason.SIGINT) {
      sessionStore.appendTranscript(TranscriptEntry.systemResponse(message));
    } else {
      sessionStore.appendTranscript(TranscriptEntry.error(message));
    }
  }

  private String formatErrorDetail(final Throwable error) {
    if (error == null) {
      return null;
    }
    final String message = error.getMessage();
    if (message == null || message.isBlank()) {
      return error.getClass().getSimpleName();
    }
    return error.getClass().getSimpleName() + ": " + message;
  }

  private void renderShutdownScreen(
      final String summary, final String detail, final String sessionPath) {
    if (lanternaScreen == null) {
      return;
    }
    try {
      lanternaScreen.clear();
      final TextGraphics tg = lanternaScreen.newTextGraphics();
      final TerminalSize size = lanternaScreen.getTerminalSize();
      int row = 1;
      tg.putString(2, row++, MessageSource.getMessage("tui.app.title"));
      row++;
      for (final String line : buildShutdownLines(summary, detail, sessionPath)) {
        if (row >= size.getRows()) {
          break;
        }
        tg.putString(2, row++, truncateForScreen(line, size.getColumns() - 4));
      }
      lanternaScreen.refresh();
    } catch (IOException e) {
      UiLogger.warn(MessageSource.getMessage("tui.app.shutdown_render_failed", e.getMessage()));
    }
  }

  private void writeShutdownMessage(
      final String summary, final String detail, final String sessionPath) {
    for (final String line : buildShutdownLines(summary, detail, sessionPath)) {
      UiLogger.stderr(line);
    }
  }

  private List<String> buildShutdownLines(
      final String summary, final String detail, final String sessionPath) {
    final List<String> lines = new java.util.ArrayList<>();
    if (summary != null && !summary.isBlank()) {
      lines.add(summary);
    }
    if (detail != null && !detail.isBlank()) {
      lines.add(detail);
    }
    if (sessionPath != null && !sessionPath.isBlank()) {
      lines.add(MessageSource.getMessage("tui.app.session_saved_to", sessionPath));
    }
    return lines;
  }

  private String truncateForScreen(final String text, final int maxWidth) {
    if (text == null) {
      return "";
    }
    if (maxWidth <= 0 || text.length() <= maxWidth) {
      return text;
    }
    return text.substring(0, Math.max(0, maxWidth - 3)) + "...";
  }
}
