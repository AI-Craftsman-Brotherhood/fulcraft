package com.craftsmanbro.fulcraft.ui.tui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.craftsmanbro.fulcraft.ui.tui.execution.ExecutionSession;
import com.craftsmanbro.fulcraft.ui.tui.i18n.TuiMessageSource;
import com.craftsmanbro.fulcraft.ui.tui.state.State;
import com.craftsmanbro.fulcraft.ui.tui.state.StateMachine;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExecutionRunningScreenTest {

  @Test
  void getStateReturnsExecutionRunning() {
    ExecutionRunningScreen screen = new ExecutionRunningScreen();
    assertThat(screen.getState()).isEqualTo(State.EXECUTION_RUNNING);
  }

  @Test
  void drawReturnsNonNegativeOffsetOnTooSmallTerminal() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    ExecutionSession session = new ExecutionSession();
    ExecutionRunningScreen screen = new ExecutionRunningScreen();

    int newOffset = screen.draw(capture.textGraphics(), 2, new TerminalSize(2, 20), session, -5);

    assertThat(newOffset).isZero();
    assertThat(capture.calls()).isEmpty();
  }

  @Test
  void drawReturnsCurrentOffsetWhenRowsDoNotFitStartRow() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    ExecutionSession session = new ExecutionSession();
    ExecutionRunningScreen screen = new ExecutionRunningScreen();

    int newOffset = screen.draw(capture.textGraphics(), 5, new TerminalSize(40, 5), session, 3);

    assertThat(newOffset).isEqualTo(3);
    assertThat(capture.calls()).isEmpty();
  }

  @Test
  void drawClampsScrollOffset() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    ExecutionSession session = new ExecutionSession();
    session.setStatus(ExecutionSession.Status.RUNNING);
    session.appendLog("line-1");
    session.appendLog("line-2");
    session.appendLog("line-3");

    ExecutionRunningScreen screen = new ExecutionRunningScreen();
    TerminalSize size = new TerminalSize(80, 20);

    int newOffset = screen.draw(capture.textGraphics(), 2, size, session, 5);

    assertThat(newOffset).isZero();
  }

  @Test
  void drawShowsStageProgressAndControlsHintWhileRunning() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    ExecutionSession session = new ExecutionSession();
    session.setStatus(ExecutionSession.Status.RUNNING);
    session.setCurrentStage("ANALYZE");
    session.setProgress("1/3");

    ExecutionRunningScreen screen = new ExecutionRunningScreen();
    TerminalSize size = new TerminalSize(80, 20);

    screen.draw(capture.textGraphics(), 2, size, session, 0);

    List<String> lines = capture.calls().stream().map(TuiTestSupport.PutStringCall::text).toList();
    TuiMessageSource msg = TuiMessageSource.getDefault();
    assertThat(lines).contains("● RUNNING");
    assertThat(lines).contains(msg.getMessage(TuiMessageSource.EXEC_STAGE) + " ANALYZE");
    assertThat(lines).contains(msg.getMessage(TuiMessageSource.EXEC_PROGRESS) + " 1/3");
    assertThat(lines).contains(msg.getMessage(TuiMessageSource.EXEC_CONTROLS_HINT));
  }

  @Test
  void drawShowsStatusTextForAllNonCompletedStates() {
    assertStatusText(ExecutionSession.Status.READY, "○ READY");
    assertStatusText(ExecutionSession.Status.CANCELLED, "○ CANCELLED");
    assertStatusText(ExecutionSession.Status.FAILED, "✗ FAILED");
  }

  @Test
  void drawShowsFinishHintWhenCompleted() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    ExecutionSession session = new ExecutionSession();
    session.setStatus(ExecutionSession.Status.COMPLETED);

    ExecutionRunningScreen screen = new ExecutionRunningScreen();
    TerminalSize size = new TerminalSize(80, 20);

    screen.draw(capture.textGraphics(), 2, size, session, 0);

    List<String> lines = capture.calls().stream().map(TuiTestSupport.PutStringCall::text).toList();
    String hint = TuiMessageSource.getDefault().getMessage(TuiMessageSource.EXEC_FINISH_HINT);
    assertThat(lines).contains(hint);
  }

  @Test
  void drawTruncatesLongLogLinesWithEllipsisWhenWidthIsLargeEnough() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    ExecutionSession session = new ExecutionSession();
    session.setStatus(ExecutionSession.Status.RUNNING);
    session.appendLog("0123456789ABC");

    ExecutionRunningScreen screen = new ExecutionRunningScreen();
    screen.draw(capture.textGraphics(), 2, new TerminalSize(14, 20), session, 0);

    List<String> lines = capture.calls().stream().map(TuiTestSupport.PutStringCall::text).toList();
    assertThat(lines).contains("0123456...");
  }

  @Test
  void drawTruncatesLongLogLinesWithoutEllipsisWhenWidthIsThreeOrLess() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    ExecutionSession session = new ExecutionSession();
    session.setStatus(ExecutionSession.Status.RUNNING);
    session.appendLog("abcdef");

    ExecutionRunningScreen screen = new ExecutionRunningScreen();
    screen.draw(capture.textGraphics(), 2, new TerminalSize(6, 20), session, 0);

    List<String> lines = capture.calls().stream().map(TuiTestSupport.PutStringCall::text).toList();
    assertThat(lines).contains("ab");
  }

  @Test
  void drawUsingApplicationUpdatesScrollOffset() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    ExecutionRunningScreen screen = new ExecutionRunningScreen();
    ExecutionSession session = new ExecutionSession();
    session.setStatus(ExecutionSession.Status.RUNNING);
    session.appendLog("line-1");

    TuiApplication app = mock(TuiApplication.class);
    when(app.getExecutionSession()).thenReturn(session);
    when(app.getLogScrollOffset()).thenReturn(3);

    screen.draw(app, capture.textGraphics(), new TerminalSize(80, 20), 2);

    verify(app).setLogScrollOffset(0);
  }

  @Test
  void drawStopsLogRenderingWhenFooterAreaIsReached() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    ExecutionSession session = new ExecutionSession();
    session.setStatus(ExecutionSession.Status.RUNNING);
    for (int i = 0; i < 30; i++) {
      session.appendLog("line-" + i);
    }

    ExecutionRunningScreen screen = new ExecutionRunningScreen();
    screen.draw(capture.textGraphics(), 2, new TerminalSize(80, 20), session, 0);

    long renderedLogLines =
        capture.calls().stream()
            .map(TuiTestSupport.PutStringCall::text)
            .filter(line -> line.startsWith("line-"))
            .count();
    assertThat(renderedLogLines).isEqualTo(9);
  }

  @Test
  void handleInputDelegatesToApplicationAndReturnsCurrentState() throws Exception {
    ExecutionRunningScreen screen = new ExecutionRunningScreen();
    KeyStroke keyStroke = new KeyStroke('P', false, false);

    TuiApplication app = mock(TuiApplication.class);
    StateMachine stateMachine = mock(StateMachine.class);
    when(app.getStateMachine()).thenReturn(stateMachine);
    when(stateMachine.getCurrentState()).thenReturn(State.ISSUE_HANDLING);

    State state = screen.handleInput(app, keyStroke);

    verify(app).handleExecutionRunningInput(keyStroke);
    assertThat(state).isEqualTo(State.ISSUE_HANDLING);
  }

  @Test
  void privateHelpersHandleNullAndGuardBranches() throws Exception {
    ExecutionRunningScreen screen = new ExecutionRunningScreen();
    Method putString =
        ExecutionRunningScreen.class.getDeclaredMethod(
            "putString",
            TextGraphics.class,
            TerminalSize.class,
            int.class,
            int.class,
            String.class);
    putString.setAccessible(true);
    Method truncateLine =
        ExecutionRunningScreen.class.getDeclaredMethod("truncateLine", String.class, int.class);
    truncateLine.setAccessible(true);

    TextGraphics textGraphics = mock(TextGraphics.class);

    putString.invoke(screen, textGraphics, new TerminalSize(20, 5), 2, 1, null);
    putString.invoke(screen, textGraphics, new TerminalSize(20, 5), 2, -1, "text");

    TerminalSize inconsistentSize = mock(TerminalSize.class);
    when(inconsistentSize.getRows()).thenReturn(5);
    // 1st call: bounds check (column is valid), 2nd call: maxWidth becomes 0.
    when(inconsistentSize.getColumns()).thenReturn(20, 2);
    putString.invoke(screen, textGraphics, inconsistentSize, 2, 1, "text");

    verifyNoInteractions(textGraphics);
    assertThat((String) truncateLine.invoke(screen, null, 10)).isEmpty();
  }

  private static void assertStatusText(ExecutionSession.Status status, String expectedStatusText) {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    ExecutionSession session = new ExecutionSession();
    session.setStatus(status);

    new ExecutionRunningScreen()
        .draw(capture.textGraphics(), 2, new TerminalSize(80, 20), session, 0);

    List<String> lines = capture.calls().stream().map(TuiTestSupport.PutStringCall::text).toList();
    assertThat(lines).contains(expectedStatusText);
  }
}
