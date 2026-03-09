package com.craftsmanbro.fulcraft.ui.tui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.ui.banner.StartupBannerSupport;
import com.craftsmanbro.fulcraft.ui.tui.conflict.ConflictCandidate;
import com.craftsmanbro.fulcraft.ui.tui.conflict.ConflictPolicy;
import com.craftsmanbro.fulcraft.ui.tui.conflict.IssueCategory;
import com.craftsmanbro.fulcraft.ui.tui.execution.ExecutionIssue;
import com.craftsmanbro.fulcraft.ui.tui.execution.ExecutionOrchestrator;
import com.craftsmanbro.fulcraft.ui.tui.execution.ExecutionSession;
import com.craftsmanbro.fulcraft.ui.tui.plan.Plan;
import com.craftsmanbro.fulcraft.ui.tui.session.SessionMetadata;
import com.craftsmanbro.fulcraft.ui.tui.state.State;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

class TuiApplicationTest {

  @Test
  void restoreStateFromSession_startsFromHome() {
    try (TuiApplication app = new TuiApplication()) {
      SessionMetadata metadata = new SessionMetadata("session-1", ".");
      metadata.setCurrentState(State.SUMMARY);
      metadata.setConflictPolicy(ConflictPolicy.OVERWRITE);

      app.restoreStateFromSession(metadata);

      assertThat(app.getStateMachine().getCurrentState()).isEqualTo(State.HOME);
      assertThat(app.getExecutionContext().getConflictPolicy()).contains(ConflictPolicy.OVERWRITE);
    }
  }

  @Test
  void setProjectRootIgnoresNull() {
    try (TuiApplication app = new TuiApplication()) {
      Object before = app.getConflictDetector();
      app.setProjectRoot(null);
      assertThat(app.getConflictDetector()).isSameAs(before);
    }
  }

  @Test
  void setResumeSessionIdStoresValue() throws Exception {
    try (TuiApplication app = new TuiApplication()) {
      app.setResumeSessionId("session-123");
      assertThat(getField(app, "resumeSessionId")).isEqualTo("session-123");
    }
  }

  @Test
  void handleChatInputEnterBuildsPlanAndTransitions() throws Exception {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    try (TuiApplication app = new TuiApplication()) {
      TuiTestSupport.injectScreen(app, capture.textGraphics(), new TerminalSize(80, 20));

      app.setInputBuffer("generate tests");
      app.getStateMachine().transitionTo(State.CHAT_INPUT);

      app.handleChatInput(new KeyStroke(KeyType.Enter));

      assertThat(app.getInputBuffer()).isEmpty();
      assertThat(app.getCurrentPlan()).isNotNull();
      assertThat(app.getStateMachine().getCurrentState()).isEqualTo(State.PLAN_REVIEW);
    }
  }

  @Test
  void handleChatInputBackspaceDeletesLastCharacter() throws Exception {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    try (TuiApplication app = new TuiApplication()) {
      TuiTestSupport.injectScreen(app, capture.textGraphics(), new TerminalSize(80, 20));
      app.setInputBuffer("abc");

      app.handleChatInput(new KeyStroke(KeyType.Backspace));

      assertThat(app.getInputBuffer()).isEqualTo("ab");
    }
  }

  @Test
  void handleChatInputCharacterQStopsWhenBufferIsEmpty() throws Exception {
    try (TuiApplication app = new TuiApplication()) {
      setField(app, "running", true);
      app.setInputBuffer("");

      app.handleChatInput(new KeyStroke('q', false, false));

      assertThat(app.isRunning()).isFalse();
    }
  }

  @Test
  void handleChatInputCharacterQAppendsWhenBufferIsNotEmpty() throws Exception {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    try (TuiApplication app = new TuiApplication()) {
      TuiTestSupport.injectScreen(app, capture.textGraphics(), new TerminalSize(80, 20));
      setField(app, "running", true);
      app.setInputBuffer("x");

      app.handleChatInput(new KeyStroke('q', false, false));

      assertThat(app.isRunning()).isTrue();
      assertThat(app.getInputBuffer()).isEqualTo("xq");
    }
  }

  @Test
  void handleChatInputEnterWithBlankInputClearsBufferOnly() throws Exception {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    try (TuiApplication app = new TuiApplication()) {
      TuiTestSupport.injectScreen(app, capture.textGraphics(), new TerminalSize(80, 20));
      app.getStateMachine().transitionTo(State.CHAT_INPUT);
      app.setInputBuffer("   ");

      app.handleChatInput(new KeyStroke(KeyType.Enter));

      assertThat(app.getInputBuffer()).isEmpty();
      assertThat(app.getCurrentPlan()).isNull();
      assertThat(app.getStateMachine().getCurrentState()).isEqualTo(State.CHAT_INPUT);
    }
  }

  @Test
  void handleChatInputSlashCommandStoresResult() throws Exception {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    try (TuiApplication app = new TuiApplication()) {
      TuiTestSupport.injectScreen(app, capture.textGraphics(), new TerminalSize(80, 20));
      app.getStateMachine().transitionTo(State.CHAT_INPUT);
      app.setInputBuffer("/status");

      app.handleChatInput(new KeyStroke(KeyType.Enter));

      assertThat(app.getLastCommandResult()).isNotNull();
      assertThat(app.getLastCommandResult().success()).isTrue();
      assertThat(app.getStateMachine().getCurrentState()).isEqualTo(State.CHAT_INPUT);
    }
  }

  @Test
  void handleChatInputUnknownConfigSubcommandReturnsError() throws Exception {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    try (TuiApplication app = new TuiApplication()) {
      TuiTestSupport.injectScreen(app, capture.textGraphics(), new TerminalSize(80, 20));
      app.getStateMachine().transitionTo(State.CHAT_INPUT);
      app.setInputBuffer("/config unknown");

      app.handleChatInput(new KeyStroke(KeyType.Enter));

      assertThat(app.getLastCommandResult()).isNotNull();
      assertThat(app.getLastCommandResult().success()).isFalse();
      assertThat(app.getLastCommandResult().errorMessage()).contains("Unknown /config subcommand");
    }
  }

  @Test
  void handlePlanReviewInputApprovesPlan() throws Exception {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    try (TuiApplication app = new TuiApplication()) {
      TuiTestSupport.injectScreen(app, capture.textGraphics(), new TerminalSize(80, 20));

      Plan plan = new Plan("Goal", List.of("Step"), "Impact", List.of());
      app.setCurrentPlan(plan);
      app.getStateMachine().transitionTo(State.PLAN_REVIEW);

      app.handlePlanReviewInput(new KeyStroke('A', false, false));

      assertThat(app.getExecutionContext().getPlan()).contains(plan);
      assertThat(app.getStateMachine().getCurrentState()).isEqualTo(State.CONFLICT_POLICY);
    }
  }

  @Test
  void handlePlanReviewInputEditReturnsToChatInput() throws Exception {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    try (TuiApplication app = new TuiApplication()) {
      TuiTestSupport.injectScreen(app, capture.textGraphics(), new TerminalSize(80, 20));
      app.getStateMachine().transitionTo(State.PLAN_REVIEW);

      app.handlePlanReviewInput(new KeyStroke('E', false, false));

      assertThat(app.getStateMachine().getCurrentState()).isEqualTo(State.CHAT_INPUT);
    }
  }

  @Test
  void handlePlanReviewInputQuitStopsApplication() throws Exception {
    try (TuiApplication app = new TuiApplication()) {
      setField(app, "running", true);
      app.getStateMachine().transitionTo(State.PLAN_REVIEW);

      app.handlePlanReviewInput(new KeyStroke('Q', false, false));

      assertThat(app.isRunning()).isFalse();
    }
  }

  @Test
  void handlePlanReviewInputIgnoresNonCharacterKey() throws Exception {
    try (TuiApplication app = new TuiApplication()) {
      app.getStateMachine().transitionTo(State.PLAN_REVIEW);

      app.handlePlanReviewInput(new KeyStroke(KeyType.ArrowDown));

      assertThat(app.getStateMachine().getCurrentState()).isEqualTo(State.PLAN_REVIEW);
    }
  }

  @Test
  void handleConflictPolicyInputAwaitsOverwriteConfirmationWhenConflictsExist() throws Exception {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    try (TuiApplication app = new TuiApplication()) {
      TuiTestSupport.injectScreen(app, capture.textGraphics(), new TerminalSize(80, 20));

      app.getExecutionContext()
          .setConflictCandidates(List.of(ConflictCandidate.of("src/test/java/FooTest.java")));
      app.getStateMachine().transitionTo(State.CONFLICT_POLICY);

      app.handleConflictPolicyInput(new KeyStroke('3', false, false));

      assertThat(app.isAwaitingOverwriteConfirmation()).isTrue();
      assertThat(app.getStateMachine().getCurrentState()).isEqualTo(State.CONFLICT_POLICY);
    }
  }

  @Test
  void handleConflictPolicyInputBackMovesToPlanReview() throws Exception {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    try (TuiApplication app = new TuiApplication()) {
      TuiTestSupport.injectScreen(app, capture.textGraphics(), new TerminalSize(80, 20));
      app.getStateMachine().transitionTo(State.CONFLICT_POLICY);

      app.handleConflictPolicyInput(new KeyStroke('B', false, false));

      assertThat(app.getStateMachine().getCurrentState()).isEqualTo(State.PLAN_REVIEW);
    }
  }

  @Test
  void handleConflictPolicyInputOverwriteConfirmationNoReturnsToSelection() throws Exception {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    try (TuiApplication app = new TuiApplication()) {
      TuiTestSupport.injectScreen(app, capture.textGraphics(), new TerminalSize(80, 20));
      app.getExecutionContext()
          .setConflictCandidates(List.of(ConflictCandidate.of("src/test/java/FooTest.java")));
      app.getStateMachine().transitionTo(State.CONFLICT_POLICY);

      app.handleConflictPolicyInput(new KeyStroke('3', false, false));
      app.handleConflictPolicyInput(new KeyStroke('N', false, false));

      assertThat(app.isAwaitingOverwriteConfirmation()).isFalse();
      assertThat(app.getStateMachine().getCurrentState()).isEqualTo(State.CONFLICT_POLICY);
    }
  }

  @Test
  void handleConflictPolicyInputIgnoresNonCharacterKey() throws Exception {
    try (TuiApplication app = new TuiApplication()) {
      app.getStateMachine().transitionTo(State.CONFLICT_POLICY);

      app.handleConflictPolicyInput(new KeyStroke(KeyType.Enter));

      assertThat(app.getStateMachine().getCurrentState()).isEqualTo(State.CONFLICT_POLICY);
      assertThat(app.isAwaitingOverwriteConfirmation()).isFalse();
    }
  }

  @Test
  void handleExecutionRunningInputScrollsUpAndDown() throws Exception {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    try (TuiApplication app = new TuiApplication()) {
      TuiTestSupport.injectScreen(app, capture.textGraphics(), new TerminalSize(80, 20));
      app.getStateMachine().transitionTo(State.EXECUTION_RUNNING);
      app.setLogScrollOffset(1);

      app.handleExecutionRunningInput(new KeyStroke(KeyType.ArrowUp));
      app.handleExecutionRunningInput(new KeyStroke(KeyType.ArrowUp));
      app.handleExecutionRunningInput(new KeyStroke(KeyType.ArrowDown));

      assertThat(app.getLogScrollOffset()).isEqualTo(1);
    }
  }

  @Test
  void handleExecutionRunningInputTransitionsToIssueHandlingWhenIssueExists() throws Exception {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    try (TuiApplication app = new TuiApplication()) {
      TuiTestSupport.injectScreen(app, capture.textGraphics(), new TerminalSize(80, 20));
      app.getStateMachine().transitionTo(State.EXECUTION_RUNNING);
      app.getExecutionContext()
          .setCurrentIssue(
              new ExecutionIssue(IssueCategory.TEST_FAILURE, "Foo#bar", "fail", "RUN"));

      app.handleExecutionRunningInput(new KeyStroke(KeyType.Enter));

      assertThat(app.getStateMachine().getCurrentState()).isEqualTo(State.ISSUE_HANDLING);
    }
  }

  @Test
  void handleExecutionRunningInputTransitionsToSummaryWhenFinished() throws Exception {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    try (TuiApplication app = new TuiApplication()) {
      TuiTestSupport.injectScreen(app, capture.textGraphics(), new TerminalSize(80, 20));
      app.getStateMachine().transitionTo(State.EXECUTION_RUNNING);
      app.getExecutionSession().setStatus(ExecutionSession.Status.COMPLETED);

      app.handleExecutionRunningInput(new KeyStroke(KeyType.Enter));

      assertThat(app.getStateMachine().getCurrentState()).isEqualTo(State.SUMMARY);
    }
  }

  @Test
  void handleExecutionRunningInputQuitStopsOnlyAfterFinish() throws Exception {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    try (TuiApplication app = new TuiApplication()) {
      TuiTestSupport.injectScreen(app, capture.textGraphics(), new TerminalSize(80, 20));
      app.getStateMachine().transitionTo(State.EXECUTION_RUNNING);
      setField(app, "running", true);

      app.getExecutionSession().setStatus(ExecutionSession.Status.RUNNING);
      app.handleExecutionRunningInput(new KeyStroke('Q', false, false));
      assertThat(app.isRunning()).isTrue();

      app.getExecutionSession().setStatus(ExecutionSession.Status.COMPLETED);
      app.handleExecutionRunningInput(new KeyStroke('Q', false, false));
      assertThat(app.isRunning()).isFalse();
    }
  }

  @Test
  void handleExecutionRunningInputDelegatesCancelOnlyToOrchestrator() throws Exception {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    try (TuiApplication app = new TuiApplication()) {
      TuiTestSupport.injectScreen(app, capture.textGraphics(), new TerminalSize(80, 20));
      app.getStateMachine().transitionTo(State.EXECUTION_RUNNING);
      ExecutionOrchestrator orchestrator = mock(ExecutionOrchestrator.class);
      setField(app, "orchestrator", orchestrator);

      app.handleExecutionRunningInput(new KeyStroke('C', false, false));
      verify(orchestrator).requestCancel();

      app.handleExecutionRunningInput(new KeyStroke('P', false, false));
      verifyNoMoreInteractions(orchestrator);
    }
  }

  @Test
  void handleInputEscapeDoesNotStopApplicationWhileExecutionRunning() throws Exception {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    try (TuiApplication app = new TuiApplication()) {
      TuiTestSupport.injectScreen(app, capture.textGraphics(), new TerminalSize(80, 20));
      setField(app, "running", true);
      app.getStateMachine().transitionTo(State.EXECUTION_RUNNING);
      app.getExecutionSession().setStatus(ExecutionSession.Status.RUNNING);
      ExecutionOrchestrator orchestrator = mock(ExecutionOrchestrator.class);
      setField(app, "orchestrator", orchestrator);

      invokePrivate(
          app, "handleInput", new Class<?>[] {KeyStroke.class}, new KeyStroke(KeyType.Escape));

      assertThat(app.isRunning()).isTrue();
      assertThat(app.getExecutionSession().getLogLines())
          .anyMatch(line -> line.contains("Press C to cancel before exiting."));
      verifyNoMoreInteractions(orchestrator);
    }
  }

  @Test
  void handleInputEscapeStopsApplicationWhenExecutionIsNotRunning() throws Exception {
    try (TuiApplication app = new TuiApplication()) {
      setField(app, "running", true);

      invokePrivate(
          app, "handleInput", new Class<?>[] {KeyStroke.class}, new KeyStroke(KeyType.Escape));

      assertThat(app.isRunning()).isFalse();
    }
  }

  @Test
  void closeRequestsCancelAndWaitBeforeShutdownWhenExecutionRunning() throws Exception {
    TuiApplication app = new TuiApplication();
    try {
      ExecutionOrchestrator orchestrator = mock(ExecutionOrchestrator.class);
      setField(app, "orchestrator", orchestrator);
      app.getExecutionSession().setStatus(ExecutionSession.Status.RUNNING);

      app.close();

      InOrder inOrder = inOrder(orchestrator);
      inOrder.verify(orchestrator).requestCancel();
      inOrder.verify(orchestrator).waitForCompletion(anyInt());
      inOrder.verify(orchestrator).shutdown();
    } finally {
      app.close();
    }
  }

  @Test
  void closeShutsDownExistingOrchestratorOnlyOnce() throws Exception {
    TuiApplication app = new TuiApplication();
    try {
      ExecutionOrchestrator orchestrator = mock(ExecutionOrchestrator.class);
      setField(app, "orchestrator", orchestrator);

      app.close();
      app.close();

      verify(orchestrator, times(1)).shutdown();
    } finally {
      app.close();
    }
  }

  @Test
  void handleConflictPolicyInputShutsDownPreviousOrchestratorBeforeCreatingNewOne(
      @TempDir Path tempDir) throws Exception {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    try (TuiApplication app = new TuiApplication()) {
      app.setProjectRoot(tempDir);
      TuiTestSupport.injectScreen(app, capture.textGraphics(), new TerminalSize(80, 20));
      app.getStateMachine().transitionTo(State.CONFLICT_POLICY);

      ExecutionOrchestrator previous = mock(ExecutionOrchestrator.class);
      ExecutionOrchestrator replacement = mock(ExecutionOrchestrator.class);
      AtomicBoolean previousShutdown = new AtomicBoolean(false);
      doAnswer(
              invocation -> {
                previousShutdown.set(true);
                return null;
              })
          .when(previous)
          .shutdown();
      setField(app, "orchestrator", previous);

      try (MockedStatic<ExecutionOrchestrator> orchestratorFactory =
          mockStatic(ExecutionOrchestrator.class)) {
        orchestratorFactory
            .when(
                () ->
                    ExecutionOrchestrator.create(
                        app.getExecutionSession(), tempDir.toAbsolutePath()))
            .thenAnswer(
                invocation -> {
                  assertThat(previousShutdown.get()).isTrue();
                  return replacement;
                });

        app.handleConflictPolicyInput(new KeyStroke('1', false, false));
      }

      verify(previous).shutdown();
      verify(replacement).setIssueHandler(any());
      verify(replacement).startExecution();
      assertThat(app.getStateMachine().getCurrentState()).isEqualTo(State.EXECUTION_RUNNING);
    }
  }

  @Test
  void handleIssueHandlingInputAppliesSafeFixAndReturnsToExecution() throws Exception {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    try (TuiApplication app = new TuiApplication()) {
      TuiTestSupport.injectScreen(app, capture.textGraphics(), new TerminalSize(80, 20));
      app.getStateMachine().transitionTo(State.ISSUE_HANDLING);
      app.getExecutionContext()
          .setCurrentIssue(
              new ExecutionIssue(IssueCategory.TEST_FAILURE, "Foo#bar", "fail", "RUN"));

      app.handleIssueHandlingInput(new KeyStroke('1', false, false));

      assertThat(app.getStateMachine().getCurrentState()).isEqualTo(State.EXECUTION_RUNNING);
      assertThat(app.getExecutionContext().hasIssue()).isFalse();
      assertThat(app.getExecutionSession().getLogLines())
          .anyMatch(line -> line.contains("User selected: Safe fix"))
          .anyMatch(line -> line.contains("Applying safe fix for: Foo#bar"));
    }
  }

  @Test
  void handleIssueHandlingInputIgnoresUnknownOption() throws Exception {
    try (TuiApplication app = new TuiApplication()) {
      app.getStateMachine().transitionTo(State.ISSUE_HANDLING);

      app.handleIssueHandlingInput(new KeyStroke('9', false, false));

      assertThat(app.getStateMachine().getCurrentState()).isEqualTo(State.ISSUE_HANDLING);
    }
  }

  @Test
  void handleIssueHandlingInputQuitStopsApplication() throws Exception {
    try (TuiApplication app = new TuiApplication()) {
      setField(app, "running", true);

      app.handleIssueHandlingInput(new KeyStroke('Q', false, false));

      assertThat(app.isRunning()).isFalse();
    }
  }

  @Test
  void handleDefaultInputTransitionsAcrossMenuStates() throws Exception {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    try (TuiApplication app = new TuiApplication()) {
      TuiTestSupport.injectScreen(app, capture.textGraphics(), new TerminalSize(80, 20));

      app.handleDefaultInput(new KeyStroke('1', false, false));
      assertThat(app.getStateMachine().getCurrentState()).isEqualTo(State.HOME);

      app.handleDefaultInput(new KeyStroke('3', false, false));
      assertThat(app.getStateMachine().getCurrentState()).isEqualTo(State.PLAN_REVIEW);

      app.handleDefaultInput(new KeyStroke('4', false, false));
      assertThat(app.getStateMachine().getCurrentState()).isEqualTo(State.CONFLICT_POLICY);

      app.handleDefaultInput(new KeyStroke('5', false, false));
      assertThat(app.getStateMachine().getCurrentState()).isEqualTo(State.EXECUTION_RUNNING);

      app.handleDefaultInput(new KeyStroke('6', false, false));
      assertThat(app.getStateMachine().getCurrentState()).isEqualTo(State.ISSUE_HANDLING);

      app.handleDefaultInput(new KeyStroke('7', false, false));
      assertThat(app.getStateMachine().getCurrentState()).isEqualTo(State.SUMMARY);
    }
  }

  @Test
  void handleDefaultInputStateTwoClearsInputAndLastCommandResult() throws Exception {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    try (TuiApplication app = new TuiApplication()) {
      TuiTestSupport.injectScreen(app, capture.textGraphics(), new TerminalSize(80, 20));
      app.getStateMachine().transitionTo(State.CHAT_INPUT);
      app.setInputBuffer("/status");
      app.handleChatInput(new KeyStroke(KeyType.Enter));
      assertThat(app.getLastCommandResult()).isNotNull();

      app.setInputBuffer("keep-me");
      app.handleDefaultInput(new KeyStroke('2', false, false));

      assertThat(app.getStateMachine().getCurrentState()).isEqualTo(State.CHAT_INPUT);
      assertThat(app.getInputBuffer()).isEmpty();
      assertThat(app.getLastCommandResult()).isNull();
    }
  }

  @Test
  void handleDefaultInputQuitStopsApplication() throws Exception {
    try (TuiApplication app = new TuiApplication()) {
      setField(app, "running", true);

      app.handleDefaultInput(new KeyStroke('Q', false, false));

      assertThat(app.isRunning()).isFalse();
    }
  }

  @Test
  void drawAtHomeWithTinyWidthFallsBackToCompactHeader() throws Exception {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    try (TuiApplication app = new TuiApplication()) {
      TuiTestSupport.injectScreen(app, capture.textGraphics(), new TerminalSize(3, 20));

      invokeDraw(app);

      List<String> lines =
          capture.calls().stream().map(TuiTestSupport.PutStringCall::text).toList();
      assertThat(lines).noneMatch(line -> line.contains("╭"));
    }
  }

  @Test
  void drawAtHomeRendersCodexStyleStartupBanner(@TempDir Path tempDir) throws Exception {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    try (TuiApplication app = new TuiApplication()) {
      writeConfig(tempDir, "test-model-x");
      app.setProjectRoot(tempDir);
      TuiTestSupport.injectScreen(app, capture.textGraphics(), new TerminalSize(100, 30));

      invokeDraw(app);

      List<String> lines =
          capture.calls().stream().map(TuiTestSupport.PutStringCall::text).toList();
      final String modelLine =
          MessageSource.getMessage("tui.app.startup.model_label")
              + " "
              + "test-model-x   "
              + MessageSource.getMessage("tut.startup.model_hint");
      assertThat(lines)
          .anyMatch(line -> line.contains(">_ ful (v"))
          .anyMatch(line -> line.contains(modelLine))
          .anyMatch(
              line ->
                  line.contains(
                      MessageSource.getMessage("tui.app.startup.directory_label")
                          + " "
                          + StartupBannerSupport.formatDirectory(tempDir)))
          .anyMatch(line -> line.contains(MessageSource.getMessage("tut.startup.tip")));
    }
  }

  @Test
  void drawAtHomeRendersStartupBannerOnShortTerminal(@TempDir Path tempDir) throws Exception {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    try (TuiApplication app = new TuiApplication()) {
      writeConfig(tempDir, "compact-model");
      app.setProjectRoot(tempDir);
      TuiTestSupport.injectScreen(app, capture.textGraphics(), new TerminalSize(100, 8));

      invokeDraw(app);

      List<String> lines =
          capture.calls().stream().map(TuiTestSupport.PutStringCall::text).toList();
      final String modelLine =
          MessageSource.getMessage("tui.app.startup.model_label")
              + " "
              + "compact-model   "
              + MessageSource.getMessage("tut.startup.model_hint");
      assertThat(lines)
          .anyMatch(line -> line.contains(">_ ful (v"))
          .anyMatch(line -> line.contains(modelLine))
          .noneMatch(line -> line.contains(MessageSource.getMessage("tut.startup.tip")));
    }
  }

  @Test
  void drawAtHomeRendersConfiguredApplicationMetadata(@TempDir Path tempDir) throws Exception {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    try (TuiApplication app = new TuiApplication()) {
      writeConfig(tempDir, "configured-model", "custom-ful", "2.3.4");
      app.setProjectRoot(tempDir);
      TuiTestSupport.injectScreen(app, capture.textGraphics(), new TerminalSize(100, 30));

      invokeDraw(app);

      List<String> lines =
          capture.calls().stream().map(TuiTestSupport.PutStringCall::text).toList();
      assertThat(lines).anyMatch(line -> line.contains(">_ custom-ful (v2.3.4)"));
    }
  }

  @Test
  void handleInterruptRendersShutdownAndIsIdempotent() throws Exception {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    try (TuiApplication app = new TuiApplication()) {
      TuiTestSupport.injectScreen(app, capture.textGraphics(), new TerminalSize(80, 20));
      setField(app, "running", true);

      app.handleInterrupt();

      assertThat(app.isRunning()).isFalse();
      List<String> lines =
          capture.calls().stream().map(TuiTestSupport.PutStringCall::text).toList();
      assertThat(lines)
          .contains(MessageSource.getMessage("tui.app.title"))
          .contains(MessageSource.getMessage("tui.app.shutdown_interrupted"));

      int renderedLineCount = capture.calls().size();
      app.handleInterrupt();
      assertThat(capture.calls()).hasSize(renderedLineCount);
    }
  }

  @Test
  void handleFatalErrorRendersErrorSummaryAndDetail() throws Exception {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    try (TuiApplication app = new TuiApplication()) {
      TuiTestSupport.injectScreen(app, capture.textGraphics(), new TerminalSize(80, 20));

      app.handleFatalError(new IllegalArgumentException("boom"));

      List<String> lines =
          capture.calls().stream().map(TuiTestSupport.PutStringCall::text).toList();
      assertThat(lines)
          .contains(MessageSource.getMessage("tui.app.shutdown_unexpected"))
          .contains("IllegalArgumentException: boom");
    }
  }

  @Test
  void privateHelpersBuildAndFormatShutdownMessages() throws Exception {
    try (TuiApplication app = new TuiApplication()) {
      List<String> lines =
          (List<String>)
              invokePrivate(
                  app,
                  "buildShutdownLines",
                  new Class<?>[] {String.class, String.class, String.class},
                  "summary",
                  "detail",
                  "/tmp/session");
      assertThat(lines)
          .containsExactly(
              "summary",
              "detail",
              MessageSource.getMessage("tui.app.session_saved_to", "/tmp/session"));

      List<String> empty =
          (List<String>)
              invokePrivate(
                  app,
                  "buildShutdownLines",
                  new Class<?>[] {String.class, String.class, String.class},
                  null,
                  " ",
                  "");
      assertThat(empty).isEmpty();

      String detailWithMessage =
          (String)
              invokePrivate(
                  app,
                  "formatErrorDetail",
                  new Class<?>[] {Throwable.class},
                  new IllegalStateException("x"));
      assertThat(detailWithMessage).isEqualTo("IllegalStateException: x");

      String detailWithoutMessage =
          (String)
              invokePrivate(
                  app,
                  "formatErrorDetail",
                  new Class<?>[] {Throwable.class},
                  new IllegalStateException());
      assertThat(detailWithoutMessage).isEqualTo("IllegalStateException");
    }
  }

  @Test
  void privateHelpersFormatUiLines() throws Exception {
    try (TuiApplication app = new TuiApplication()) {
      String withNullContent =
          (String)
              invokePrivate(
                  app, "formatBoxLine", new Class<?>[] {String.class, int.class}, null, 4);
      assertThat(withNullContent).isEqualTo("│    │");

      String clipped =
          (String)
              invokePrivate(
                  app, "formatBoxLine", new Class<?>[] {String.class, int.class}, "abcdef", 4);
      assertThat(clipped).isEqualTo("│abcd│");

      String nullText =
          (String)
              invokePrivate(
                  app, "truncateForScreen", new Class<?>[] {String.class, int.class}, null, 10);
      assertThat(nullText).isEmpty();

      String noLimit =
          (String)
              invokePrivate(
                  app, "truncateForScreen", new Class<?>[] {String.class, int.class}, "abcdef", 0);
      assertThat(noLimit).isEqualTo("abcdef");

      String shortened =
          (String)
              invokePrivate(
                  app, "truncateForScreen", new Class<?>[] {String.class, int.class}, "abcdef", 4);
      assertThat(shortened).isEqualTo("a...");
    }
  }

  @Test
  void sleepBrieflyHandlesInterruptedThread() throws Exception {
    try (TuiApplication app = new TuiApplication()) {
      setField(app, "running", true);

      Thread.currentThread().interrupt();
      invokePrivate(app, "sleepBriefly", new Class<?>[0]);

      assertThat(app.isRunning()).isFalse();
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
      Thread.interrupted();
    }
  }

  @Test
  void accessorsReturnNonNullComponents() {
    try (TuiApplication app = new TuiApplication()) {
      assertThat(app.getCommandDispatcher()).isNotNull();
      assertThat(app.getExecutionSession()).isNotNull();
      assertThat(app.getTuiInputHandler()).isNotNull();
      assertThat(app.getConfigController()).isNotNull();
    }
  }

  private static void invokeDraw(TuiApplication app) throws ReflectiveOperationException {
    Method drawMethod = TuiApplication.class.getDeclaredMethod("draw");
    drawMethod.setAccessible(true);
    drawMethod.invoke(app);
  }

  private static Object invokePrivate(
      TuiApplication app, String methodName, Class<?>[] parameterTypes, Object... args)
      throws ReflectiveOperationException {
    Method method = TuiApplication.class.getDeclaredMethod(methodName, parameterTypes);
    method.setAccessible(true);
    return method.invoke(app, args);
  }

  private static void setField(Object target, String fieldName, Object value)
      throws ReflectiveOperationException {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }

  private static Object getField(Object target, String fieldName)
      throws ReflectiveOperationException {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    return field.get(target);
  }

  private static void writeConfig(Path projectRoot, String modelName) throws Exception {
    writeConfig(projectRoot, modelName, null, null);
  }

  private static void writeConfig(
      Path projectRoot, String modelName, String appName, String version) throws Exception {
    String normalizedRoot = projectRoot.toString().replace('\\', '/');
    String appNameSection = appName == null ? "" : "  \"AppName\": \"" + appName + "\",\n";
    String versionSection = version == null ? "" : "  \"version\": \"" + version + "\",\n";
    String json =
        """
                {
        %s%s  "execution": { "per_task_isolation": false },
                  "llm": {
                    "provider": "openai",
                    "api_key": "dummy",
                    "fix_retries": 2,
                    "max_retries": 3,
                    "model_name": "%s"
                  },
                  "project": { "id": "test-project", "root": "%s" },
                  "selection_rules": {
                    "class_min_loc": 10,
                    "class_min_method_count": 1,
                    "exclude_getters_setters": true,
                    "method_max_loc": 2000,
                    "method_min_loc": 3
                  }
                }
                """
            .formatted(appNameSection, versionSection, modelName, normalizedRoot);
    Files.writeString(projectRoot.resolve("config.json"), json);
  }
}
