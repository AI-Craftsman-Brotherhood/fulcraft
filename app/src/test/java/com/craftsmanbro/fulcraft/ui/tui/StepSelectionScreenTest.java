package com.craftsmanbro.fulcraft.ui.tui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.ui.tui.plan.Plan;
import com.craftsmanbro.fulcraft.ui.tui.state.State;
import com.craftsmanbro.fulcraft.ui.tui.state.StateMachine;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.input.KeyStroke;
import java.util.List;
import org.junit.jupiter.api.Test;

class StepSelectionScreenTest {

  @Test
  void getStateReturnsPlanReview() {
    StepSelectionScreen screen = new StepSelectionScreen();

    assertThat(screen.getState()).isEqualTo(State.PLAN_REVIEW);
  }

  @Test
  void handleInputDelegatesToApplicationAndReturnsCurrentState() throws Exception {
    StepSelectionScreen screen = new StepSelectionScreen();
    TuiApplication app = mock(TuiApplication.class);
    StateMachine stateMachine = mock(StateMachine.class);
    KeyStroke keyStroke = new KeyStroke('A', false, false);
    when(app.getStateMachine()).thenReturn(stateMachine);
    when(stateMachine.getCurrentState()).thenReturn(State.CONFLICT_POLICY);

    State next = screen.handleInput(app, keyStroke);

    verify(app).handlePlanReviewInput(keyStroke);
    assertThat(next).isEqualTo(State.CONFLICT_POLICY);
  }

  @Test
  void drawWithApplicationUsesCurrentPlan() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    StepSelectionScreen screen = new StepSelectionScreen();
    TuiApplication app = mock(TuiApplication.class);
    Plan plan = new Plan("Goal", List.of("First step"), "Impact", List.of("Risk"));
    when(app.getCurrentPlan()).thenReturn(plan);

    screen.draw(app, capture.textGraphics(), new TerminalSize(80, 20), 2);

    List<String> lines = capture.calls().stream().map(TuiTestSupport.PutStringCall::text).toList();
    assertThat(capture.calls().getFirst().row()).isEqualTo(2);
    assertThat(lines)
        .contains(
            MessageSource.getMessage("tui.plan.label.goal"),
            MessageSource.getMessage("tui.plan.label.impact"),
            MessageSource.getMessage("tui.plan.label.risks"));
    assertThat(lines).anyMatch(line -> line.contains("1. First step"));
    verify(app).getCurrentPlan();
  }

  @Test
  void drawShowsFallbackWhenNoPlan() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    StepSelectionScreen screen = new StepSelectionScreen();

    screen.draw(capture.textGraphics(), 0, null);

    List<String> lines = capture.calls().stream().map(TuiTestSupport.PutStringCall::text).toList();
    assertThat(lines).contains(MessageSource.getMessage("tui.plan.no_plan_available"));
  }

  @Test
  void drawShowsPlanSteps() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    StepSelectionScreen screen = new StepSelectionScreen();
    Plan plan = new Plan("Goal", List.of("First step", "Second step"), "Impact", List.of("Risk"));

    screen.draw(capture.textGraphics(), 0, plan);

    List<String> lines = capture.calls().stream().map(TuiTestSupport.PutStringCall::text).toList();
    assertThat(lines).anyMatch(line -> line.contains("1. First step"));
    assertThat(lines).anyMatch(line -> line.contains("2. Second step"));
  }

  @Test
  void drawShowsNoneIdentifiedWhenRisksAreEmpty() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    StepSelectionScreen screen = new StepSelectionScreen();
    Plan plan = new Plan("Goal", List.of("Only step"), "Impact", List.of());

    screen.draw(capture.textGraphics(), 0, plan);

    List<String> lines = capture.calls().stream().map(TuiTestSupport.PutStringCall::text).toList();
    assertThat(lines).contains(MessageSource.getMessage("tui.plan.label.no_risks"));
  }

  @Test
  void drawSplitsMultilinePlanFields() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    StepSelectionScreen screen = new StepSelectionScreen();
    Plan plan =
        new Plan(
            "Goal line 1\nGoal line 2",
            List.of("Step one\nStep one detail"),
            "Impact line 1\nImpact line 2",
            List.of("Risk one\nRisk one detail"));

    screen.draw(capture.textGraphics(), 0, plan);

    List<String> lines = capture.calls().stream().map(TuiTestSupport.PutStringCall::text).toList();
    assertThat(lines)
        .contains(
            "Goal line 1",
            "Goal line 2",
            "1. Step one",
            "Step one detail",
            "Impact line 1",
            "Impact line 2",
            "- Risk one",
            "Risk one detail");
  }

  @Test
  void drawSkipsOutOfBoundsRowsAndColumns() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    StepSelectionScreen screen = new StepSelectionScreen();
    Plan plan = new Plan("Goal", List.of("Step"), "Impact", List.of("Risk"));

    screen.draw(capture.textGraphics(), -3, new TerminalSize(2, 6), plan);

    assertThat(capture.calls()).isEmpty();
  }

  @Test
  void drawTruncatesOutputToTerminalWidth() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    StepSelectionScreen screen = new StepSelectionScreen();

    screen.draw(capture.textGraphics(), 0, new TerminalSize(6, 8), (Plan) null);

    List<String> lines = capture.calls().stream().map(TuiTestSupport.PutStringCall::text).toList();
    assertThat(lines).isNotEmpty();
    assertThat(lines).allMatch(line -> line.length() <= 4);
    assertThat(lines).noneMatch(line -> line.equals(MessageSource.getMessage("tui.plan.title")));
  }
}
