package com.craftsmanbro.fulcraft.ui.tui.execution;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.kernel.pipeline.PipelineNodeIds;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.kernel.pipeline.stage.Stage;
import com.craftsmanbro.fulcraft.plugins.reporting.model.TaskRecord;
import com.craftsmanbro.fulcraft.ui.tui.conflict.IssueCategory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExecutionOrchestratorTest {

  @TempDir private Path tempDir;

  @Test
  @DisplayName("startExecution logs and returns when not READY")
  void startExecutionLogsWhenNotReady() {
    ExecutionSession session = new ExecutionSession();
    session.setStatus(ExecutionSession.Status.RUNNING);

    ExecutionOrchestrator orchestrator =
        new ExecutionOrchestrator(session, tempDir, Config.createDefault());

    orchestrator.startExecution();

    List<String> logs = session.getLogLines();
    assertFalse(logs.isEmpty());
    assertTrue(logs.get(logs.size() - 1).contains("Cannot start"));
    assertEquals(ExecutionSession.Status.RUNNING, session.getStatus());
    orchestrator.shutdown();
  }

  @Test
  @DisplayName("requestCancel sets flag and logs")
  void requestCancelSetsFlagAndLogs() {
    ExecutionSession session = new ExecutionSession();
    ExecutionOrchestrator orchestrator =
        new ExecutionOrchestrator(session, tempDir, Config.createDefault());

    orchestrator.requestCancel();

    assertTrue(session.isCancelRequested());
    assertTrue(session.getLogLines().get(session.getLogLines().size() - 1).contains("Cancel"));
    orchestrator.shutdown();
  }

  @Test
  @DisplayName("waitForCompletion returns true when no execution started")
  void waitForCompletionReturnsTrueWhenNoExecution() {
    ExecutionSession session = new ExecutionSession();
    ExecutionOrchestrator orchestrator =
        new ExecutionOrchestrator(session, tempDir, Config.createDefault());

    assertTrue(orchestrator.waitForCompletion(1));
    orchestrator.shutdown();
  }

  @Test
  @DisplayName("create falls back to default config when config files are missing")
  void createFallsBackToDefaultConfigWhenConfigFilesAreMissing() {
    ExecutionSession session = new ExecutionSession();

    ExecutionOrchestrator orchestrator =
        assertDoesNotThrow(() -> ExecutionOrchestrator.create(session, tempDir));

    assertEquals(session, orchestrator.getSession());
    orchestrator.shutdown();
  }

  @Test
  @DisplayName("startExecution runs analyze-report-document flow")
  void startExecutionRunsAnalyzeReportDocumentFlow() {
    ExecutionSession session = new ExecutionSession();
    Config config = Config.createDefault();
    config.getProject().setId("tui-run");
    config.getProject().setRoot(tempDir.toString());

    try {
      prepareSourceFixture(tempDir);
    } catch (Exception e) {
      throw new AssertionError("Failed to prepare source fixture", e);
    }

    ExecutionOrchestrator orchestrator = new ExecutionOrchestrator(session, tempDir, config);
    try {
      orchestrator.startExecution();

      assertTrue(orchestrator.waitForCompletion(60));
      assertEquals(ExecutionSession.Status.COMPLETED, session.getStatus());
      assertEquals(3, session.getTotalStages());
      assertEquals(3, session.getCompletedStages());
      assertTrue(
          session.getLogLines().stream()
              .anyMatch(line -> line.contains("Stage") && line.contains("ANALYZE")));
      assertTrue(
          session.getLogLines().stream()
              .anyMatch(line -> line.contains("Stage") && line.contains("REPORT")));
      assertTrue(
          session.getLogLines().stream()
              .anyMatch(line -> line.contains("Stage") && line.contains("DOCUMENT")));
    } finally {
      orchestrator.shutdown();
    }
  }

  @Test
  @DisplayName("create prefers project config.json over .ful config.json")
  void createPrefersProjectConfigOverFulConfig() throws Exception {
    writeValidConfig(tempDir.resolve("config.json"), "project-root");
    Files.createDirectories(tempDir.resolve(".ful"));
    writeValidConfig(tempDir.resolve(".ful/config.json"), "ful-config");

    ExecutionSession session = new ExecutionSession();
    ExecutionOrchestrator orchestrator = ExecutionOrchestrator.create(session, tempDir);
    try {
      Config loaded = getPrivateField(orchestrator, "config", Config.class);
      assertEquals("project-root", loaded.getProject().getId());
    } finally {
      orchestrator.shutdown();
    }
  }

  @Test
  @DisplayName("create loads .ful config when project config is absent")
  void createLoadsFulConfigWhenProjectConfigIsAbsent() throws Exception {
    Files.createDirectories(tempDir.resolve(".ful"));
    writeValidConfig(tempDir.resolve(".ful/config.json"), "ful-config");

    ExecutionSession session = new ExecutionSession();
    ExecutionOrchestrator orchestrator = ExecutionOrchestrator.create(session, tempDir);
    try {
      Config loaded = getPrivateField(orchestrator, "config", Config.class);
      assertEquals("ful-config", loaded.getProject().getId());
    } finally {
      orchestrator.shutdown();
    }
  }

  @Test
  @DisplayName("collectGeneratedFiles formats output and skips incomplete tasks")
  void collectGeneratedFilesFormatsOutputAndSkipsIncompleteTasks() throws Exception {
    ExecutionSession session = new ExecutionSession();
    ExecutionOrchestrator orchestrator =
        new ExecutionOrchestrator(session, tempDir, Config.createDefault());
    try {
      RunContext context = new RunContext(tempDir, Config.createDefault(), "run-collect");
      TaskRecord withTestClass = new TaskRecord();
      withTestClass.setClassFqn("com.example.Target");
      withTestClass.setTestClassName("com.example.TargetGeneratedTest");
      TaskRecord withClassOnly = new TaskRecord();
      withClassOnly.setClassFqn("com.example.Fallback");
      withClassOnly.setTestClassName("");
      TaskRecord empty = new TaskRecord();
      context.putMetadata("tasks.generated", List.of(withTestClass, withClassOnly, empty));

      invokePrivateVoid(
          orchestrator, "collectGeneratedFiles", new Class<?>[] {RunContext.class}, context);

      List<String> generated = session.getGeneratedFiles();
      assertEquals(2, generated.size());
      assertTrue(generated.get(0).contains("com.example.TargetGeneratedTest"));
      assertTrue(generated.get(0).contains("from com.example.Target"));
      assertEquals("Generated: com.example.FallbackTest", generated.get(1));
    } finally {
      orchestrator.shutdown();
    }
  }

  @Test
  @DisplayName("completeExecution handles cancel success and non-zero exit code")
  void completeExecutionHandlesCancelSuccessAndFailure() throws Exception {
    RunContext context = new RunContext(tempDir, Config.createDefault(), "run-complete");

    ExecutionSession cancelSession = new ExecutionSession();
    ExecutionOrchestrator cancelOrchestrator =
        new ExecutionOrchestrator(cancelSession, tempDir, Config.createDefault());
    try {
      cancelSession.requestCancel();
      invokePrivateVoid(
          cancelOrchestrator,
          "completeExecution",
          new Class<?>[] {RunContext.class, int.class},
          context,
          0);
      assertEquals(ExecutionSession.Status.CANCELLED, cancelSession.getStatus());
      assertTrue(
          cancelSession.getLogLines().stream()
              .anyMatch(line -> line.contains("cancelled by user")));
    } finally {
      cancelOrchestrator.shutdown();
    }

    ExecutionSession successSession = new ExecutionSession();
    ExecutionOrchestrator successOrchestrator =
        new ExecutionOrchestrator(successSession, tempDir, Config.createDefault());
    try {
      invokePrivateVoid(
          successOrchestrator,
          "completeExecution",
          new Class<?>[] {RunContext.class, int.class},
          new RunContext(tempDir, Config.createDefault(), "run-success"),
          0);
      assertEquals(ExecutionSession.Status.COMPLETED, successSession.getStatus());
    } finally {
      successOrchestrator.shutdown();
    }

    ExecutionSession failedSession = new ExecutionSession();
    ExecutionOrchestrator failedOrchestrator =
        new ExecutionOrchestrator(failedSession, tempDir, Config.createDefault());
    try {
      invokePrivateVoid(
          failedOrchestrator,
          "completeExecution",
          new Class<?>[] {RunContext.class, int.class},
          new RunContext(tempDir, Config.createDefault(), "run-failed"),
          3);
      assertEquals(ExecutionSession.Status.FAILED, failedSession.getStatus());
      assertTrue(
          failedSession.getLogLines().stream().anyMatch(line -> line.contains("exit code: 3")));
    } finally {
      failedOrchestrator.shutdown();
    }
  }

  @Test
  @DisplayName("notifyIssue handles handler null error null and stage name fallback")
  void notifyIssueHandlesNullsAndStageFallback() throws Exception {
    ExecutionSession session = new ExecutionSession();
    ExecutionOrchestrator orchestrator =
        new ExecutionOrchestrator(session, tempDir, Config.createDefault());
    try {
      invokePrivateVoid(
          orchestrator,
          "notifyIssue",
          new Class<?>[] {Stage.class, Throwable.class},
          null,
          new IllegalStateException("ignored"));

      AtomicReference<ExecutionIssue> captured = new AtomicReference<>();
      orchestrator.setIssueHandler(captured::set);
      invokePrivateVoid(
          orchestrator, "notifyIssue", new Class<?>[] {Stage.class, Throwable.class}, null, null);
      assertNull(captured.get());

      session.setCurrentStage("REPORT");
      invokePrivateVoid(
          orchestrator,
          "notifyIssue",
          new Class<?>[] {Stage.class, Throwable.class},
          null,
          new IllegalArgumentException("boom"));
      assertNotNull(captured.get());
      assertEquals(IssueCategory.EXCEPTION, captured.get().category());
      assertEquals("REPORT", captured.get().stageName());
      assertEquals("REPORT", captured.get().targetIdentifier());

      session.setCurrentStage("");
      Stage blankNameStage =
          new Stage() {
            @Override
            public String getNodeId() {
              return PipelineNodeIds.ANALYZE;
            }

            @Override
            public void execute(RunContext context) {}

            @Override
            public String getName() {
              return " ";
            }
          };

      invokePrivateVoid(
          orchestrator,
          "notifyIssue",
          new Class<?>[] {Stage.class, Throwable.class},
          blankNameStage,
          new RuntimeException("x"));
      assertNotNull(captured.get());
      assertEquals("PIPELINE", captured.get().stageName());
      assertEquals("PIPELINE", captured.get().targetIdentifier());
    } finally {
      orchestrator.shutdown();
    }
  }

  @Test
  @DisplayName("startExecution marks session cancelled when cancellation is requested beforehand")
  void startExecutionMarksCancelledWhenCancellationRequestedBeforehand() throws Exception {
    ExecutionSession session = new ExecutionSession();
    Config config = Config.createDefault();
    config.getProject().setId("cancel-run");
    config.getProject().setRoot(tempDir.toString());
    prepareSourceFixture(tempDir);

    ExecutionOrchestrator orchestrator = new ExecutionOrchestrator(session, tempDir, config);
    try {
      session.requestCancel();
      orchestrator.startExecution();

      assertTrue(orchestrator.waitForCompletion(60));
      assertEquals(ExecutionSession.Status.CANCELLED, session.getStatus());
      assertTrue(
          session.getLogLines().stream()
              .anyMatch(line -> line.contains("Execution cancelled by user")));
    } finally {
      orchestrator.shutdown();
    }
  }

  @Test
  @DisplayName("shutdown keeps interrupted status when awaitTermination is interrupted")
  void shutdownKeepsInterruptedStatusWhenAwaitTerminationInterrupted() {
    ExecutionSession session = new ExecutionSession();
    ExecutionOrchestrator orchestrator =
        new ExecutionOrchestrator(session, tempDir, Config.createDefault());

    Thread.currentThread().interrupt();
    try {
      orchestrator.shutdown();
      assertTrue(Thread.currentThread().isInterrupted());
    } finally {
      Thread.interrupted();
    }
  }

  private static void prepareSourceFixture(Path root) throws Exception {
    Path sourceDir = root.resolve("src/main/java/com/example");
    Files.createDirectories(sourceDir);
    Files.writeString(
        sourceDir.resolve("SampleService.java"),
        """
        package com.example;

        public class SampleService {
          public int sum(int a, int b) {
            return a + b;
          }
        }
        """);
  }

  private static <T> T getPrivateField(Object target, String name, Class<T> type) throws Exception {
    var field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return type.cast(field.get(target));
  }

  private static void writeValidConfig(Path path, String projectId) throws Exception {
    String json =
        """
        {
          "project": { "id": "%s" },
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
        """
            .formatted(projectId);
    Files.writeString(path, json);
  }

  private static void invokePrivateVoid(
      Object target, String methodName, Class<?>[] parameterTypes, Object... args)
      throws Exception {
    var method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
    method.setAccessible(true);
    method.invoke(target, args);
  }
}
