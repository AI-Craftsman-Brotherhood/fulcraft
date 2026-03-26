package com.craftsmanbro.fulcraft.ui.tui.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.ui.tui.conflict.IssueCategory;
import com.craftsmanbro.fulcraft.ui.tui.conflict.IssueHandlingOption;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExecutionSessionTest {

  @Test
  @DisplayName("setStatus rejects null")
  void setStatusRejectsNull() {
    ExecutionSession session = new ExecutionSession();

    assertThrows(NullPointerException.class, () -> session.setStatus(null));
  }

  @Test
  @DisplayName("setCurrentStage and setProgress normalize null to empty string")
  void setStageAndProgressNormalizeNull() {
    ExecutionSession session = new ExecutionSession();
    session.setCurrentStage(null);
    session.setProgress(null);

    assertEquals("", session.getCurrentStage());
    assertEquals("", session.getProgress());
  }

  @Test
  @DisplayName("isRunning reflects RUNNING status only")
  void isRunningReflectsRunningStatusOnly() {
    ExecutionSession session = new ExecutionSession();
    assertFalse(session.isRunning());

    session.setStatus(ExecutionSession.Status.RUNNING);
    assertTrue(session.isRunning());

    session.setStatus(ExecutionSession.Status.COMPLETED);
    assertFalse(session.isRunning());
  }

  @Test
  @DisplayName("isFinished is true only for completed cancelled failed")
  void isFinishedReturnsTrueOnlyForTerminalStates() {
    ExecutionSession session = new ExecutionSession();

    session.setStatus(ExecutionSession.Status.READY);
    assertFalse(session.isFinished());
    session.setStatus(ExecutionSession.Status.RUNNING);
    assertFalse(session.isFinished());

    session.setStatus(ExecutionSession.Status.COMPLETED);
    assertTrue(session.isFinished());
    session.setStatus(ExecutionSession.Status.CANCELLED);
    assertTrue(session.isFinished());
    session.setStatus(ExecutionSession.Status.FAILED);
    assertTrue(session.isFinished());
  }

  @Test
  @DisplayName("appendLog keeps only the most recent 500 lines")
  void appendLogKeepsMostRecent500Lines() {
    ExecutionSession session = new ExecutionSession();
    for (int i = 1; i <= 510; i++) {
      session.appendLog("line-" + i);
    }

    List<String> logs = session.getLogLines();
    assertEquals(500, logs.size());
    assertEquals("line-11", logs.get(0));
    assertEquals("line-510", logs.get(logs.size() - 1));
  }

  @Test
  @DisplayName("getLastLogLines returns all logs when request exceeds size")
  void getLastLogLinesReturnsAllWhenRequestExceedsSize() {
    ExecutionSession session = new ExecutionSession();
    session.appendLog("line-1");
    session.appendLog("line-2");

    assertEquals(List.of("line-1", "line-2"), session.getLastLogLines(10));
  }

  @Test
  @DisplayName("getLastLogLines returns tail when request is smaller than size")
  void getLastLogLinesReturnsTailWhenRequestIsSmaller() {
    ExecutionSession session = new ExecutionSession();
    session.appendLog("line-1");
    session.appendLog("line-2");
    session.appendLog("line-3");

    assertEquals(List.of("line-2", "line-3"), session.getLastLogLines(2));
  }

  @Test
  @DisplayName("appendLog ignores null input")
  void appendLogIgnoresNullInput() {
    ExecutionSession session = new ExecutionSession();
    session.appendLog(null);

    assertTrue(session.getLogLines().isEmpty());
  }

  @Test
  @DisplayName("awaitIssueDecision returns null when no issue is pending")
  void awaitIssueDecisionReturnsNullWhenNoIssuePending() throws InterruptedException {
    ExecutionSession session = new ExecutionSession();

    assertNull(session.awaitIssueDecision());
  }

  @Test
  @DisplayName("requestIssueHandling and resolveIssue coordinate between threads")
  void requestIssueHandlingAndResolveIssueCoordinateBetweenThreads() throws InterruptedException {
    ExecutionSession session = new ExecutionSession();
    ExecutionIssue issue = new ExecutionIssue(IssueCategory.EXCEPTION, "target", "cause", "RUN");
    session.requestIssueHandling(issue);

    AtomicReference<IssueHandlingOption> decision = new AtomicReference<>();
    AtomicReference<Throwable> error = new AtomicReference<>();
    Thread waitingThread =
        new Thread(
            () -> {
              try {
                decision.set(session.awaitIssueDecision());
              } catch (Throwable t) {
                error.set(t);
              }
            });

    waitingThread.start();
    Thread.sleep(50);
    assertTrue(waitingThread.isAlive());

    session.resolveIssue(IssueHandlingOption.SAFE_FIX);
    waitingThread.join(1000);

    assertFalse(waitingThread.isAlive());
    assertNull(error.get());
    assertEquals(IssueHandlingOption.SAFE_FIX, decision.get());
    assertTrue(session.getPendingIssue().isEmpty());
  }

  @Test
  @DisplayName("resolveIssue rejects null option")
  void resolveIssueRejectsNullOption() {
    ExecutionSession session = new ExecutionSession();

    assertThrows(NullPointerException.class, () -> session.resolveIssue(null));
  }

  @Test
  @DisplayName("reset clears pending issue state and runtime state")
  void resetClearsIssueAndRuntimeState() {
    ExecutionSession session = new ExecutionSession();
    session.setStatus(ExecutionSession.Status.RUNNING);
    session.setCurrentStage("ANALYZE");
    session.setProgress("1/3");
    session.appendLog("line");
    session.requestIssueHandling(
        new ExecutionIssue(IssueCategory.EXCEPTION, "target", "cause", "RUN"));
    session.requestCancel();
    session.addGeneratedFile("GeneratedTest.java");
    session.setErrorMessage("boom");

    session.reset();

    assertEquals(ExecutionSession.Status.READY, session.getStatus());
    assertEquals("", session.getCurrentStage());
    assertEquals("", session.getProgress());
    assertFalse(session.isCancelRequested());
    assertTrue(session.getLogLines().isEmpty());
    assertTrue(session.getGeneratedFiles().isEmpty());
    assertTrue(session.getPendingIssue().isEmpty());
    assertNull(session.getErrorMessage());
  }

  @Test
  @DisplayName("reset wakes threads waiting for issue decisions")
  void resetWakesThreadsWaitingForIssueDecisions() throws InterruptedException {
    ExecutionSession session = new ExecutionSession();
    session.requestIssueHandling(
        new ExecutionIssue(IssueCategory.EXCEPTION, "target", "cause", "RUN"));

    AtomicReference<IssueHandlingOption> decision = new AtomicReference<>();
    AtomicReference<Throwable> error = new AtomicReference<>();
    Thread waitingThread =
        new Thread(
            () -> {
              try {
                decision.set(session.awaitIssueDecision());
              } catch (Throwable t) {
                error.set(t);
              }
            });

    waitingThread.start();
    Thread.sleep(50);
    assertTrue(waitingThread.isAlive());

    session.reset();
    waitingThread.join(1000);

    assertFalse(waitingThread.isAlive());
    assertNull(error.get());
    assertNull(decision.get());
    assertTrue(session.getPendingIssue().isEmpty());
  }

  @Test
  @DisplayName("addGeneratedFile ignores null path")
  void addGeneratedFileIgnoresNullPath() {
    ExecutionSession session = new ExecutionSession();
    session.addGeneratedFile(null);

    assertTrue(session.getGeneratedFiles().isEmpty());
  }
}
