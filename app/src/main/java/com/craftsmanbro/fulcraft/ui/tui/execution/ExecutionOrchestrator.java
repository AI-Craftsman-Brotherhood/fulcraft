package com.craftsmanbro.fulcraft.ui.tui.execution;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.config.ConfigLoaderPort;
import com.craftsmanbro.fulcraft.config.ConfigPathResolver;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.config.impl.ConfigLoaderImpl;
import com.craftsmanbro.fulcraft.infrastructure.fs.impl.RunIdGenerator;
import com.craftsmanbro.fulcraft.infrastructure.telemetry.contract.TelemetryPort;
import com.craftsmanbro.fulcraft.infrastructure.telemetry.impl.Telemetry;
import com.craftsmanbro.fulcraft.kernel.pipeline.PipelineNodeIds;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.kernel.pipeline.stage.Stage;
import com.craftsmanbro.fulcraft.plugins.reporting.contract.ReportWriteException;
import com.craftsmanbro.fulcraft.plugins.reporting.model.TaskRecord;
import com.craftsmanbro.fulcraft.ui.cli.command.run.RunAnalysisReportExecutor;
import com.craftsmanbro.fulcraft.ui.cli.wiring.DefaultServiceFactory;
import com.craftsmanbro.fulcraft.ui.tui.TuiLogRedirector;
import com.craftsmanbro.fulcraft.ui.tui.UiLogger;
import io.opentelemetry.api.trace.Tracer;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Orchestrates pipeline execution from the TUI.
 *
 * <p>This class bridges the TUI and the existing pipeline infrastructure, providing:
 *
 * <ul>
 *   <li>Asynchronous execution in a background thread
 *   <li>Progress tracking via ExecutionSession
 *   <li>Log capture for TUI display
 *   <li>Cancellation support
 *   <li>Generated file tracking
 * </ul>
 */
public class ExecutionOrchestrator {

  @FunctionalInterface
  public interface IssueHandler {

    void onIssue(ExecutionIssue issue);
  }

  private static final String DEFAULT_ENGINE_TYPE = "composite";

  private static final String META_START_TIME = "startTime";

  private static final String GENERATED_TASKS_KEY = "tasks.generated";

  private static final String CANCELLED_BY_USER_KEY = "tui.exec.cancelled";

  private static final List<String> ANALYSIS_REPORT_DOCUMENT_STEPS =
      List.of(PipelineNodeIds.ANALYZE, PipelineNodeIds.REPORT, PipelineNodeIds.DOCUMENT);

  private final ExecutionSession session;

  private final Path projectRoot;

  private final Config config;

  private final TelemetryPort telemetry;

  private final ExecutorService executor;

  private final AtomicReference<IssueHandler> issueHandler = new AtomicReference<>();

  private final AtomicReference<Future<?>> executionFuture = new AtomicReference<>();

  private TuiLogRedirector logRedirector;

  /**
   * Creates an ExecutionOrchestrator.
   *
   * @param session the execution session for state tracking
   * @param projectRoot the project root directory
   * @param config the application configuration
   */
  public ExecutionOrchestrator(
      final ExecutionSession session, final Path projectRoot, final Config config) {
    this.session = Objects.requireNonNull(session, "session must not be null");
    this.projectRoot = Objects.requireNonNull(projectRoot, "projectRoot must not be null");
    this.config = Objects.requireNonNull(config, "config must not be null");
    this.telemetry = Telemetry.getInstance();
    this.executor =
        Executors.newSingleThreadExecutor(
            r -> {
              final Thread t = new Thread(r, "TUI-Pipeline-Executor");
              t.setDaemon(true);
              return t;
            });
  }

  /**
   * Creates an ExecutionOrchestrator with default configuration.
   *
   * @param session the execution session
   * @param projectRoot the project root directory
   * @return a new orchestrator instance
   */
  public static ExecutionOrchestrator create(
      final ExecutionSession session, final Path projectRoot) {
    return create(session, projectRoot, new ConfigLoaderImpl());
  }

  public static ExecutionOrchestrator create(
      final ExecutionSession session, final Path projectRoot, final ConfigLoaderPort configLoader) {
    Objects.requireNonNull(session, "session must not be null");
    Objects.requireNonNull(projectRoot, "projectRoot must not be null");
    Objects.requireNonNull(configLoader, "configLoader must not be null");
    final Path configFile = ConfigPathResolver.resolveFromProjectRoot(projectRoot);
    final Config config =
        configFile != null ? configLoader.load(configFile) : Config.createDefault();
    UiLogger.initialize(config);
    return new ExecutionOrchestrator(session, projectRoot, config);
  }

  /**
   * Starts the pipeline execution asynchronously.
   *
   * <p>The execution runs in a background thread, updating the session with progress and logs.
   */
  public void startExecution() {
    if (session.getStatus() != ExecutionSession.Status.READY) {
      session.appendLog(MessageSource.getMessage("tui.exec.cannot_start"));
      return;
    }
    session.setStatus(ExecutionSession.Status.RUNNING);
    session.appendLog(MessageSource.getMessage("tui.exec.starting_pipeline"));
    // Set up log redirection
    logRedirector = new TuiLogRedirector(session);
    UiLogger.setOutput(logRedirector.getStdout(), logRedirector.getStderr());
    final Future<?> future = executor.submit(this::runPipeline);
    executionFuture.set(future);
  }

  public void setIssueHandler(final IssueHandler issueHandler) {
    this.issueHandler.set(issueHandler);
  }

  /** Runs the pipeline (called in background thread). */
  private void runPipeline() {
    try {
      final RunContext context = createContext();
      session.setTotalStages(ANALYSIS_REPORT_DOCUMENT_STEPS.size());
      final int exitCode = runAnalyzeReportDocument(context);
      completeExecution(context, exitCode);
    } catch (CancellationException e) {
      markCancelled();
    } catch (IOException | ReportWriteException | RuntimeException e) {
      if (session.isCancelRequested()) {
        markCancelled();
      } else {
        notifyIssue(null, e);
        session.setStatus(ExecutionSession.Status.FAILED);
        session.setErrorMessage(e.getMessage());
        session.appendLog(MessageSource.getMessage("tui.exec.failed_message", e.getMessage()));
      }
    } finally {
      // Restore original output streams
      if (logRedirector != null) {
        UiLogger.setOutput(logRedirector.getOriginalStdout(), logRedirector.getOriginalStderr());
      }
    }
  }

  private int runAnalyzeReportDocument(final RunContext context)
      throws IOException, ReportWriteException {
    final Tracer tracer = telemetry.getTracer();
    final DefaultServiceFactory services = new DefaultServiceFactory(tracer);
    final RunAnalysisReportExecutor analysisExecutor =
        new RunAnalysisReportExecutor(services, DEFAULT_ENGINE_TYPE);
    return analysisExecutor.execute(
        context,
        true,
        new RunAnalysisReportExecutor.StageListener() {

          @Override
          public void onStageStarted(final String nodeId) {
            if (session.isCancelRequested()) {
              throw new CancellationException(MessageSource.getMessage(CANCELLED_BY_USER_KEY));
            }
            final String display = nodeId.toUpperCase(Locale.ROOT);
            session.setCurrentStage(display);
            session.setProgress(MessageSource.getMessage("tui.exec.running_stage", display));
            session.appendLog(MessageSource.getMessage("tui.exec.stage_started", display));
          }

          @Override
          public void onStageCompleted(final String nodeId) {
            final String display = nodeId.toUpperCase(Locale.ROOT);
            session.incrementCompletedStages();
            session.appendLog(MessageSource.getMessage("tui.exec.stage_completed", display));
            session.setProgress(
                MessageSource.getMessage(
                    "tui.exec.stages_done",
                    session.getCompletedStages(),
                    session.getTotalStages()));
          }

          @Override
          public void onStageSkipped(final String nodeId) {
            final String display = nodeId.toUpperCase(Locale.ROOT);
            session.incrementCompletedStages();
            session.appendLog(MessageSource.getMessage("tui.exec.stage_skipped", display));
            session.setProgress(
                MessageSource.getMessage(
                    "tui.exec.stages_done",
                    session.getCompletedStages(),
                    session.getTotalStages()));
          }
        });
  }

  private void completeExecution(final RunContext context, final int exitCode) {
    collectGeneratedFiles(context);
    if (session.isCancelRequested()) {
      markCancelled();
    } else if (exitCode == 0) {
      session.setStatus(ExecutionSession.Status.COMPLETED);
      session.appendLog(MessageSource.getMessage("tui.exec.completed_success"));
    } else {
      markFailedWithExitCode(exitCode);
    }
  }

  private void markCancelled() {
    session.setStatus(ExecutionSession.Status.CANCELLED);
    session.appendLog(MessageSource.getMessage(CANCELLED_BY_USER_KEY));
  }

  private void markFailedWithExitCode(final int exitCode) {
    session.setStatus(ExecutionSession.Status.FAILED);
    session.appendLog(MessageSource.getMessage("tui.exec.failed_exit_code", exitCode));
  }

  /** Creates the run context. */
  private RunContext createContext() {
    final String runId = RunIdGenerator.newRunId();
    final RunContext context =
        new RunContext(projectRoot.toAbsolutePath(), config, runId)
            .withDryRun(false)
            .withFailFast(false)
            .withShowSummary(true);
    UiLogger.configureRunLogging(config, context.getRunDirectory(), context.getRunId());
    context.putMetadata(META_START_TIME, Instant.now().toEpochMilli());
    return context;
  }

  /** Collects generated file paths from the context. */
  private void collectGeneratedFiles(final RunContext context) {
    final List<TaskRecord> generatedTests = getTasksFromMetadata(context, GENERATED_TASKS_KEY);
    for (final TaskRecord task : generatedTests) {
      // Build the expected test file path from class info
      final String classFqn = task.getClassFqn();
      final String testClassName = task.getTestClassName();
      if (testClassName != null && !testClassName.isEmpty()) {
        session.addGeneratedFile(
            MessageSource.getMessage("tui.exec.generated_test_from", testClassName, classFqn));
      } else if (classFqn != null) {
        session.addGeneratedFile(
            MessageSource.getMessage("tui.exec.generated_test", classFqn + "Test"));
      }
    }
  }

  private static List<TaskRecord> getTasksFromMetadata(final RunContext context, final String key) {
    return context.getMetadataList(key, TaskRecord.class).orElse(List.of());
  }

  /** Requests cancellation of the execution. */
  public void requestCancel() {
    session.requestCancel();
    session.appendLog(MessageSource.getMessage("tui.exec.cancel_requested"));
  }

  /**
   * Waits for execution to complete.
   *
   * @param timeoutSeconds maximum time to wait
   * @return true if execution completed within timeout
   */
  public boolean waitForCompletion(final int timeoutSeconds) {
    final Future<?> future = executionFuture.get();
    if (future == null) {
      return true;
    }
    try {
      future.get(timeoutSeconds, TimeUnit.SECONDS);
      return true;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Returns the execution session.
   *
   * @return the session
   */
  public ExecutionSession getSession() {
    return session;
  }

  /** Shuts down the executor. */
  public void shutdown() {
    executor.shutdown();
    try {
      if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
        executor.shutdownNow();
      }
    } catch (InterruptedException e) {
      executor.shutdownNow();
      Thread.currentThread().interrupt();
    } finally {
      telemetry.shutdown();
    }
  }

  private void notifyIssue(final Stage stage, final Throwable error) {
    final IssueHandler handler = issueHandler.get();
    if (handler == null || error == null) {
      return;
    }
    String stageName = stage != null ? stage.getName() : session.getCurrentStage();
    if (stageName == null || stageName.isBlank()) {
      stageName = "PIPELINE";
    }
    final ExecutionIssue issue = ExecutionIssue.fromException(stageName, stageName, error);
    handler.onIssue(issue);
  }

  /** Exception thrown when execution is cancelled. */
  private static class CancellationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    CancellationException(final String message) {
      super(message);
    }
  }
}
