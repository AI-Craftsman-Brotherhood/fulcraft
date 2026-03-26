package com.craftsmanbro.fulcraft.ui.tui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.craftsmanbro.fulcraft.ui.tui.conflict.IssueCategory;
import com.craftsmanbro.fulcraft.ui.tui.conflict.IssueHandlingOption;
import com.craftsmanbro.fulcraft.ui.tui.execution.ExecutionIssue;
import com.craftsmanbro.fulcraft.ui.tui.i18n.TuiMessageSource;
import com.craftsmanbro.fulcraft.ui.tui.state.ExecutionContext;
import com.craftsmanbro.fulcraft.ui.tui.state.State;
import com.craftsmanbro.fulcraft.ui.tui.state.StateMachine;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.input.KeyStroke;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class IssueHandlingScreenTest {

  @Test
  void getStateReturnsIssueHandling() {
    IssueHandlingScreen screen = new IssueHandlingScreen();

    assertThat(screen.getState()).isEqualTo(State.ISSUE_HANDLING);
  }

  @Test
  void drawShowsNoIssueMessageWhenNonePresent() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    ExecutionContext context = new ExecutionContext();

    IssueHandlingScreen screen = new IssueHandlingScreen();
    screen.draw(capture.textGraphics(), 0, new TerminalSize(80, 20), context);

    String expected = TuiMessageSource.getDefault().getMessage(TuiMessageSource.ISSUE_NO_ISSUE);
    List<String> lines = capture.calls().stream().map(TuiTestSupport.PutStringCall::text).toList();
    assertThat(lines).contains(expected);
  }

  @Test
  void drawShowsIssueOptionsWhenIssuePresent() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    ExecutionContext context = new ExecutionContext();
    context.setCurrentIssue(new ExecutionIssue(IssueCategory.TEST_FAILURE, "Foo", "Cause", "RUN"));

    IssueHandlingScreen screen = new IssueHandlingScreen();
    screen.draw(capture.textGraphics(), 0, new TerminalSize(80, 20), context);

    List<String> lines = capture.calls().stream().map(TuiTestSupport.PutStringCall::text).toList();
    assertThat(lines).contains(IssueHandlingOption.SAFE_FIX.getMenuLabel());
  }

  @Test
  void drawSplitsCauseOnPlatformIndependentLineBreaks() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    ExecutionContext context = new ExecutionContext();
    context.setCurrentIssue(
        new ExecutionIssue(IssueCategory.TEST_FAILURE, "Foo", "first\r\nsecond\r\nthird", "RUN"));

    IssueHandlingScreen screen = new IssueHandlingScreen();
    screen.draw(capture.textGraphics(), 0, new TerminalSize(80, 20), context);

    List<String> lines = capture.calls().stream().map(TuiTestSupport.PutStringCall::text).toList();
    assertThat(lines).contains("first", "second", "third");
    assertThat(lines).noneMatch(line -> line.contains("\r"));
  }

  @Test
  void drawWithApplicationUsesExecutionContextFromApp() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    ExecutionContext context = new ExecutionContext();
    TuiApplication app = mock(TuiApplication.class);
    when(app.getExecutionContext()).thenReturn(context);

    IssueHandlingScreen screen = new IssueHandlingScreen();
    screen.draw(app, capture.textGraphics(), new TerminalSize(80, 20), 0);

    List<String> lines = capture.calls().stream().map(TuiTestSupport.PutStringCall::text).toList();
    String noIssueMessage =
        TuiMessageSource.getDefault().getMessage(TuiMessageSource.ISSUE_NO_ISSUE);
    assertThat(lines).contains(noIssueMessage);
    verify(app).getExecutionContext();
  }

  @Test
  void handleInputDelegatesToApplicationAndReturnsCurrentState() throws Exception {
    IssueHandlingScreen screen = new IssueHandlingScreen();
    KeyStroke keyStroke = new KeyStroke('1', false, false);

    TuiApplication app = mock(TuiApplication.class);
    StateMachine stateMachine = mock(StateMachine.class);
    when(app.getStateMachine()).thenReturn(stateMachine);
    when(stateMachine.getCurrentState()).thenReturn(State.EXECUTION_RUNNING);

    State state = screen.handleInput(app, keyStroke);

    verify(app).handleIssueHandlingInput(keyStroke);
    assertThat(state).isEqualTo(State.EXECUTION_RUNNING);
  }

  @Test
  void drawReturnsEarlyWhenContextHasIssueButIssueIsMissing() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    ExecutionContext context = mock(ExecutionContext.class);
    when(context.hasIssue()).thenReturn(true);
    when(context.getCurrentIssue()).thenReturn(Optional.empty());

    IssueHandlingScreen screen = new IssueHandlingScreen();
    screen.draw(capture.textGraphics(), 3, new TerminalSize(80, 20), context);

    List<String> lines = capture.calls().stream().map(TuiTestSupport.PutStringCall::text).toList();
    String title = TuiMessageSource.getDefault().getMessage(TuiMessageSource.ISSUE_TITLE_DETECTED);
    assertThat(lines).contains(title).hasSize(2);
  }

  @Test
  void privateHelpersCoverTruncationAndSeparatorBranches() throws Exception {
    Method truncateLine =
        IssueHandlingScreen.class.getDeclaredMethod("truncateLine", String.class, int.class);
    truncateLine.setAccessible(true);
    Method buildSeparator =
        IssueHandlingScreen.class.getDeclaredMethod("buildSeparator", int.class);
    buildSeparator.setAccessible(true);

    assertThat((String) truncateLine.invoke(null, null, 10)).isEmpty();
    assertThat((String) truncateLine.invoke(null, "abc", 0)).isEmpty();
    assertThat((String) truncateLine.invoke(null, "abc", 5)).isEqualTo("abc");
    assertThat((String) truncateLine.invoke(null, "abcdef", 3)).isEqualTo("abc");
    assertThat((String) truncateLine.invoke(null, "abcdef", 5)).isEqualTo("ab...");

    assertThat((String) buildSeparator.invoke(null, -1)).isEmpty();
    assertThat(((String) buildSeparator.invoke(null, 80))).hasSize(60);
  }
}
