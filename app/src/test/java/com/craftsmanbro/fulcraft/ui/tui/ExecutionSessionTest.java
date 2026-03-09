package com.craftsmanbro.fulcraft.ui.tui;

import static org.junit.jupiter.api.Assertions.*;

import com.craftsmanbro.fulcraft.ui.tui.execution.ExecutionSession;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests for ExecutionSession. */
class ExecutionSessionTest {

  private ExecutionSession session;

  @BeforeEach
  void setUp() {
    session = new ExecutionSession();
  }

  @Nested
  @DisplayName("Initial state tests")
  class InitialStateTests {

    @Test
    @DisplayName("should start in READY status")
    void shouldStartInReadyStatus() {
      assertEquals(ExecutionSession.Status.READY, session.getStatus());
    }

    @Test
    @DisplayName("should have empty current stage")
    void shouldHaveEmptyCurrentStage() {
      assertEquals("", session.getCurrentStage());
    }

    @Test
    @DisplayName("should not be running initially")
    void shouldNotBeRunningInitially() {
      assertFalse(session.isRunning());
    }

    @Test
    @DisplayName("should not be finished initially")
    void shouldNotBeFinishedInitially() {
      assertFalse(session.isFinished());
    }

    @Test
    @DisplayName("should have empty log lines")
    void shouldHaveEmptyLogLines() {
      assertTrue(session.getLogLines().isEmpty());
    }
  }

  @Nested
  @DisplayName("Status management tests")
  class StatusManagementTests {

    @Test
    @DisplayName("should transition to RUNNING")
    void shouldTransitionToRunning() {
      session.setStatus(ExecutionSession.Status.RUNNING);
      assertEquals(ExecutionSession.Status.RUNNING, session.getStatus());
      assertTrue(session.isRunning());
    }

    @Test
    @DisplayName("should report isFinished for COMPLETED")
    void shouldReportFinishedForCompleted() {
      session.setStatus(ExecutionSession.Status.COMPLETED);
      assertTrue(session.isFinished());
    }

    @Test
    @DisplayName("should report isFinished for CANCELLED")
    void shouldReportFinishedForCancelled() {
      session.setStatus(ExecutionSession.Status.CANCELLED);
      assertTrue(session.isFinished());
    }

    @Test
    @DisplayName("should report isFinished for FAILED")
    void shouldReportFinishedForFailed() {
      session.setStatus(ExecutionSession.Status.FAILED);
      assertTrue(session.isFinished());
    }
  }

  @Nested
  @DisplayName("Log collection tests")
  class LogCollectionTests {

    @Test
    @DisplayName("should append log lines")
    void shouldAppendLogLines() {
      session.appendLog("Line 1");
      session.appendLog("Line 2");

      List<String> logs = session.getLogLines();
      assertEquals(2, logs.size());
      assertEquals("Line 1", logs.get(0));
      assertEquals("Line 2", logs.get(1));
    }

    @Test
    @DisplayName("should ignore null log lines")
    void shouldIgnoreNullLogLines() {
      session.appendLog("Line 1");
      session.appendLog(null);

      assertEquals(1, session.getLogLines().size());
    }

    @Test
    @DisplayName("should return last N log lines")
    void shouldReturnLastNLogLines() {
      session.appendLog("Line 1");
      session.appendLog("Line 2");
      session.appendLog("Line 3");

      List<String> last2 = session.getLastLogLines(2);
      assertEquals(2, last2.size());
      assertEquals("Line 2", last2.get(0));
      assertEquals("Line 3", last2.get(1));
    }

    @Test
    @DisplayName("should clear logs")
    void shouldClearLogs() {
      session.appendLog("Line 1");
      session.clearLogs();

      assertTrue(session.getLogLines().isEmpty());
    }
  }

  @Nested
  @DisplayName("Cancellation tests")
  class CancellationTests {

    @Test
    @DisplayName("should not have cancel requested initially")
    void shouldNotHaveCancelRequestedInitially() {
      assertFalse(session.isCancelRequested());
    }

    @Test
    @DisplayName("should request cancel")
    void shouldRequestCancel() {
      session.requestCancel();
      assertTrue(session.isCancelRequested());
    }
  }

  @Nested
  @DisplayName("Progress tracking tests")
  class ProgressTrackingTests {

    @Test
    @DisplayName("should track current stage")
    void shouldTrackCurrentStage() {
      session.setCurrentStage("ANALYZE");
      assertEquals("ANALYZE", session.getCurrentStage());
    }

    @Test
    @DisplayName("should track progress message")
    void shouldTrackProgressMessage() {
      session.setProgress("5/10 files");
      assertEquals("5/10 files", session.getProgress());
    }

    @Test
    @DisplayName("should track completed stages")
    void shouldTrackCompletedStages() {
      session.setTotalStages(5);
      session.incrementCompletedStages();
      session.incrementCompletedStages();

      assertEquals(5, session.getTotalStages());
      assertEquals(2, session.getCompletedStages());
    }
  }

  @Nested
  @DisplayName("Reset tests")
  class ResetTests {

    @Test
    @DisplayName("should reset all state")
    void shouldResetAllState() {
      // Set various state
      session.setStatus(ExecutionSession.Status.RUNNING);
      session.setCurrentStage("ANALYZE");
      session.setProgress("50%");
      session.appendLog("A log");
      session.requestCancel();
      session.addGeneratedFile("test.java");
      session.setErrorMessage("error");

      // Reset
      session.reset();

      // Verify reset
      assertEquals(ExecutionSession.Status.READY, session.getStatus());
      assertEquals("", session.getCurrentStage());
      assertEquals("", session.getProgress());
      assertTrue(session.getLogLines().isEmpty());
      assertFalse(session.isCancelRequested());
      assertTrue(session.getGeneratedFiles().isEmpty());
      assertNull(session.getErrorMessage());
    }
  }

  @Nested
  @DisplayName("Generated files tests")
  class GeneratedFilesTests {

    @Test
    @DisplayName("should add generated files")
    void shouldAddGeneratedFiles() {
      session.addGeneratedFile("Test1.java");
      session.addGeneratedFile("Test2.java");

      List<String> files = session.getGeneratedFiles();
      assertEquals(2, files.size());
      assertTrue(files.contains("Test1.java"));
      assertTrue(files.contains("Test2.java"));
    }

    @Test
    @DisplayName("should ignore null files")
    void shouldIgnoreNullFiles() {
      session.addGeneratedFile("Test1.java");
      session.addGeneratedFile(null);

      assertEquals(1, session.getGeneratedFiles().size());
    }
  }
}
