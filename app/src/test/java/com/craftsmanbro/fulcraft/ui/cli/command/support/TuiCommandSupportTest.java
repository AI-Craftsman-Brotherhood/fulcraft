package com.craftsmanbro.fulcraft.ui.cli.command.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.craftsmanbro.fulcraft.ui.tui.TuiApplication;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.mockito.ArgumentCaptor;
import picocli.CommandLine;

@Isolated
class TuiCommandSupportTest {

  @Test
  void launch_acceptsNullInitializer() throws Exception {
    TuiApplication app = mock(TuiApplication.class);

    int exitCode = TuiCommandSupport.launch(app, null, "Exited normally", "Error occurred");

    assertThat(exitCode).isEqualTo(CommandLine.ExitCode.OK);
    verify(app).init();
    verify(app).run();
    verify(app).close();
  }

  @Test
  void launch_executesDefaultAppRunnerAndClosesApplication() throws Exception {
    TuiApplication app = mock(TuiApplication.class);

    int exitCode = TuiCommandSupport.launch(app, a -> {}, "Exited normally", "Error occurred");

    assertThat(exitCode).isEqualTo(CommandLine.ExitCode.OK);
    verify(app).init();
    verify(app).run();
    verify(app).close();
  }

  @Test
  void launch_handlesRuntimeExceptionFromDefaultRunner() throws Exception {
    TuiApplication app = mock(TuiApplication.class);
    doThrow(new RuntimeException("Simulated error")).when(app).run();

    int exitCode = TuiCommandSupport.launch(app, a -> {}, "Exited normally", "Error occurred");

    assertThat(exitCode).isEqualTo(CommandLine.ExitCode.SOFTWARE);
    verify(app).handleFatalError(any(RuntimeException.class));
    verify(app).close();
  }

  @Test
  void launch_handlesIoExceptionFromDefaultRunner() throws Exception {
    TuiApplication app = mock(TuiApplication.class);
    IOException ioException = new IOException("init failed");
    doThrow(ioException).when(app).init();

    int exitCode = TuiCommandSupport.launch(app, a -> {}, "Exited normally", "Error occurred");

    assertThat(exitCode).isEqualTo(CommandLine.ExitCode.SOFTWARE);
    verify(app).handleFatalError(ioException);
    verify(app, never()).run();
    verify(app).close();
  }

  @Test
  void launch_handlesCheckedExceptionFromDefaultRunner() throws Exception {
    TuiApplication app = mock(TuiApplication.class);
    Exception checkedException = new Exception("checked");
    doAnswer(
            invocation -> {
              sneakyThrow(checkedException);
              return null;
            })
        .when(app)
        .run();

    int exitCode = TuiCommandSupport.launch(app, a -> {}, "Exited normally", "Error occurred");

    assertThat(exitCode).isEqualTo(CommandLine.ExitCode.SOFTWARE);
    ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
    verify(app).handleFatalError(captor.capture());
    assertThat(captor.getValue()).isSameAs(checkedException);
    verify(app).close();
  }

  @Test
  void launch_returnsSoftwareWhenInitializerThrows() throws Exception {
    TuiApplication app = mock(TuiApplication.class);

    int exitCode =
        TuiCommandSupport.launch(
            app,
            ignored -> {
              throw new IllegalStateException("init failed");
            },
            "Exited",
            "Error");

    assertThat(exitCode).isEqualTo(CommandLine.ExitCode.SOFTWARE);
    verify(app, never()).init();
    verify(app).close();
  }

  @Test
  void launch_returnsSoftwareWhenCustomRunnerThrows() throws Exception {
    TuiApplication app = mock(TuiApplication.class);

    int exitCode =
        TuiCommandSupport.launch(
            app,
            ignored -> {},
            "Exited",
            "Error",
            ignored -> {
              throw new IOException("runner failed");
            });

    assertThat(exitCode).isEqualTo(CommandLine.ExitCode.SOFTWARE);
    verify(app, never()).init();
    verify(app).close();
  }

  @Test
  void launch_reinterruptsCurrentThread_whenCustomRunnerIsInterrupted() throws Exception {
    TuiApplication app = mock(TuiApplication.class);
    Thread.interrupted();

    try {
      int exitCode =
          TuiCommandSupport.launch(
              app,
              ignored -> {},
              "Exited",
              "Error",
              ignored -> {
                throw new InterruptedException("runner interrupted");
              });

      assertThat(exitCode).isEqualTo(CommandLine.ExitCode.SOFTWARE);
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
      verify(app, never()).handleFatalError(any());
      verify(app).close();
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void launch_restoresDefaultUncaughtExceptionHandler() throws Exception {
    TuiApplication app = mock(TuiApplication.class);
    Thread.UncaughtExceptionHandler original = Thread.getDefaultUncaughtExceptionHandler();
    Thread.UncaughtExceptionHandler sentinel = (thread, error) -> {};

    Thread.setDefaultUncaughtExceptionHandler(sentinel);
    try {
      TuiCommandSupport.launch(app, ignored -> {}, "Exited", "Error", ignored -> {});
      assertThat(Thread.getDefaultUncaughtExceptionHandler()).isSameAs(sentinel);
    } finally {
      Thread.setDefaultUncaughtExceptionHandler(original);
    }
  }

  @Test
  void launch_runsInitializerBeforeExecution() throws Exception {
    TuiApplication app = mock(TuiApplication.class);
    boolean[] initialized = {false};

    int exitCode =
        TuiCommandSupport.launch(app, ignored -> initialized[0] = true, "Exited", "Error");

    assertThat(exitCode).isEqualTo(CommandLine.ExitCode.OK);
    assertThat(initialized[0]).isTrue();
  }

  @Test
  void launch_forwardsUncaughtExceptionsToPreviousDefaultHandler() throws Exception {
    TuiApplication app = mock(TuiApplication.class);
    doThrow(new IllegalStateException("fatal handler failed")).when(app).handleFatalError(any());

    AtomicInteger previousHandlerInvocations = new AtomicInteger();
    Thread.UncaughtExceptionHandler original = Thread.getDefaultUncaughtExceptionHandler();
    Thread.UncaughtExceptionHandler sentinel =
        (thread, error) -> previousHandlerInvocations.incrementAndGet();

    Thread.setDefaultUncaughtExceptionHandler(sentinel);
    try {
      int exitCode =
          TuiCommandSupport.launch(
              app,
              ignored -> {},
              "Exited",
              "Error",
              ignored -> {
                Thread uncaughtThread =
                    new Thread(
                        () -> {
                          throw new RuntimeException("boom");
                        },
                        "tui-test-uncaught");
                uncaughtThread.start();
                uncaughtThread.join();
              });

      assertThat(exitCode).isEqualTo(CommandLine.ExitCode.OK);
      verify(app).handleFatalError(any(RuntimeException.class));
      assertThat(previousHandlerInvocations.get()).isEqualTo(1);
    } finally {
      Thread.setDefaultUncaughtExceptionHandler(original);
    }
  }

  private static <E extends Throwable> void sneakyThrow(Throwable throwable) throws E {
    throw (E) throwable;
  }
}
