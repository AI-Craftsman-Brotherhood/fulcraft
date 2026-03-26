package com.craftsmanbro.fulcraft.ui.tui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import com.craftsmanbro.fulcraft.ui.tui.execution.ExecutionSession;
import com.craftsmanbro.fulcraft.ui.tui.i18n.TuiMessageSource;
import com.craftsmanbro.fulcraft.ui.tui.state.State;
import com.craftsmanbro.fulcraft.ui.tui.state.StateMachine;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SummaryScreenTest {

  @Test
  void getStateReturnsSummary() {
    SummaryScreen screen = new SummaryScreen();

    assertThat(screen.getState()).isEqualTo(State.SUMMARY);
  }

  @Test
  void drawUsesApplicationExecutionSession() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    TuiApplication app = new TuiApplication(new StateMachine(State.SUMMARY));
    ExecutionSession session = app.getExecutionSession();
    session.setStatus(ExecutionSession.Status.COMPLETED);
    session.setTotalStages(1);
    session.incrementCompletedStages();

    SummaryScreen screen = new SummaryScreen();
    screen.draw(app, capture.textGraphics(), new TerminalSize(80, 30), 0);

    String success = TuiMessageSource.getDefault().getMessage(TuiMessageSource.SUMMARY_SUCCESS_MSG);
    assertThat(lines(capture)).contains(success);
  }

  @Test
  void handleInputReturnsCurrentStateForNonCharacterKey() throws IOException {
    TuiApplication app = new TuiApplication(new StateMachine(State.SUMMARY));
    SummaryScreen screen = new SummaryScreen();

    State next = screen.handleInput(app, new KeyStroke(KeyType.Enter));

    assertThat(next).isEqualTo(State.SUMMARY);
  }

  @Test
  void drawReturnsImmediatelyWhenTerminalTooSmall() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    ExecutionSession session = new ExecutionSession();
    SummaryScreen screen = new SummaryScreen();

    screen.draw(capture.textGraphics(), 0, new TerminalSize(2, 30), session);

    verifyNoInteractions(capture.textGraphics());
  }

  @Test
  void drawReturnsImmediatelyWhenStartRowIsOutOfBounds() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    ExecutionSession session = new ExecutionSession();
    SummaryScreen screen = new SummaryScreen();

    screen.draw(capture.textGraphics(), 5, new TerminalSize(80, 5), session);

    verifyNoInteractions(capture.textGraphics());
  }

  @Test
  void drawShowsFailureMessageAndErrorDetails() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    ExecutionSession session = new ExecutionSession();
    session.setStatus(ExecutionSession.Status.FAILED);
    session.setErrorMessage("boom");
    session.setTotalStages(5);
    session.incrementCompletedStages();

    SummaryScreen screen = new SummaryScreen();
    screen.draw(capture.textGraphics(), 0, new TerminalSize(80, 30), session);

    List<String> lines = capture.calls().stream().map(TuiTestSupport.PutStringCall::text).toList();
    TuiMessageSource msg = TuiMessageSource.getDefault();
    assertThat(lines).contains(msg.getMessage(TuiMessageSource.SUMMARY_FAILED_MSG));
    assertThat(lines).anyMatch(line -> line.contains("boom"));
  }

  @Test
  void drawShowsFailureWithoutErrorPrefixWhenNoErrorMessage() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    ExecutionSession session = new ExecutionSession();
    session.setStatus(ExecutionSession.Status.FAILED);
    session.setErrorMessage("");

    SummaryScreen screen = new SummaryScreen();
    screen.draw(capture.textGraphics(), 0, new TerminalSize(80, 30), session);

    TuiMessageSource msg = TuiMessageSource.getDefault();
    List<String> lines = lines(capture);
    assertThat(lines).contains(msg.getMessage(TuiMessageSource.SUMMARY_FAILED_MSG));
    assertThat(lines)
        .noneMatch(line -> line.contains(msg.getMessage(TuiMessageSource.SUMMARY_ERROR_PREFIX)));
  }

  @Test
  void drawShowsCancelledAndNoGeneratedFilesMessages() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    ExecutionSession session = new ExecutionSession();
    session.setStatus(ExecutionSession.Status.CANCELLED);
    session.setTotalStages(2);
    session.incrementCompletedStages();
    SummaryScreen screen = new SummaryScreen();

    screen.draw(capture.textGraphics(), 0, new TerminalSize(80, 30), session);

    TuiMessageSource msg = TuiMessageSource.getDefault();
    List<String> lines = lines(capture);
    assertThat(lines).contains(msg.getMessage(TuiMessageSource.SUMMARY_CANCELLED_MSG));
    assertThat(lines).contains(msg.getMessage(TuiMessageSource.SUMMARY_NO_FILES));
  }

  @Test
  void drawShowsGenericStatusForRunningState() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    ExecutionSession session = new ExecutionSession();
    session.setStatus(ExecutionSession.Status.RUNNING);
    SummaryScreen screen = new SummaryScreen();

    screen.draw(capture.textGraphics(), 0, new TerminalSize(80, 30), session);

    String prefix =
        TuiMessageSource.getDefault().getMessage(TuiMessageSource.SUMMARY_STATUS_PREFIX);
    assertThat(lines(capture))
        .anyMatch(
            line -> line.contains(prefix) && line.contains(ExecutionSession.Status.RUNNING.name()));
  }

  @Test
  void drawSkipsFileLinesWhenFileColumnIsOutOfBounds() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    ExecutionSession session = new ExecutionSession();
    session.setStatus(ExecutionSession.Status.COMPLETED);
    session.addGeneratedFile("abcdefghijklmn");
    SummaryScreen screen = new SummaryScreen();

    screen.draw(capture.textGraphics(), 0, new TerminalSize(3, 30), session);

    assertThat(capture.calls()).noneMatch(call -> call.column() == 4);
  }

  @Test
  void drawTruncatesFileNameToTailWhenNarrow() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    ExecutionSession session = new ExecutionSession();
    session.setStatus(ExecutionSession.Status.COMPLETED);
    session.addGeneratedFile("abcdefghijklmn");
    SummaryScreen screen = new SummaryScreen();

    screen.draw(capture.textGraphics(), 0, new TerminalSize(11, 30), session);

    assertThat(lines(capture)).anyMatch(line -> line.endsWith("lmn"));
    assertThat(lines(capture)).noneMatch(line -> line.contains("abcdefghijklmn"));
  }

  @Test
  void drawTruncatesFileNameWithEllipsisWhenWidthIsModerate() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    ExecutionSession session = new ExecutionSession();
    session.setStatus(ExecutionSession.Status.COMPLETED);
    session.addGeneratedFile("abcdefghijklmn");
    SummaryScreen screen = new SummaryScreen();

    screen.draw(capture.textGraphics(), 0, new TerminalSize(14, 30), session);

    assertThat(lines(capture)).anyMatch(line -> line.contains("...lmn"));
  }

  @Test
  void drawHandlesNegativeStartRow() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    ExecutionSession session = new ExecutionSession();
    session.setStatus(ExecutionSession.Status.COMPLETED);
    SummaryScreen screen = new SummaryScreen();

    screen.draw(capture.textGraphics(), -1, new TerminalSize(80, 30), session);

    String success = TuiMessageSource.getDefault().getMessage(TuiMessageSource.SUMMARY_SUCCESS_MSG);
    assertThat(lines(capture)).contains(success);
  }

  @Test
  void drawShowsMoreFilesIndicatorWhenManyFiles() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    ExecutionSession session = new ExecutionSession();
    session.setStatus(ExecutionSession.Status.COMPLETED);
    session.setTotalStages(3);
    session.incrementCompletedStages();
    session.incrementCompletedStages();
    session.incrementCompletedStages();

    List<String> files = new ArrayList<>();
    for (int i = 0; i < 12; i++) {
      files.add("src/test/java/com/example/Test" + i + "Test.java");
    }
    files.forEach(session::addGeneratedFile);

    SummaryScreen screen = new SummaryScreen();
    screen.draw(capture.textGraphics(), 0, new TerminalSize(80, 30), session);

    List<String> lines = capture.calls().stream().map(TuiTestSupport.PutStringCall::text).toList();
    assertThat(lines).anyMatch(line -> line.contains("... and 2 more"));
  }

  private static List<String> lines(TuiTestSupport.TextGraphicsCapture capture) {
    return capture.calls().stream().map(TuiTestSupport.PutStringCall::text).toList();
  }
}
