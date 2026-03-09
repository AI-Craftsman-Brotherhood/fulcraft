package com.craftsmanbro.fulcraft.ui.tui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.craftsmanbro.fulcraft.ui.tui.conflict.ConflictCandidate;
import com.craftsmanbro.fulcraft.ui.tui.conflict.ConflictDetector;
import com.craftsmanbro.fulcraft.ui.tui.i18n.TuiMessageSource;
import com.craftsmanbro.fulcraft.ui.tui.state.ExecutionContext;
import com.craftsmanbro.fulcraft.ui.tui.state.State;
import com.craftsmanbro.fulcraft.ui.tui.state.StateMachine;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConflictPolicyScreenTest {

  @Test
  void getStateReturnsConflictPolicy() {
    ConflictPolicyScreen screen = new ConflictPolicyScreen();

    assertThat(screen.getState()).isEqualTo(State.CONFLICT_POLICY);
  }

  @Test
  void drawWithApplicationUsesContextAndDetector() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    ExecutionContext context = new ExecutionContext();
    context.setConflictCandidates(List.of());

    ConflictDetector detector = mock(ConflictDetector.class);
    when(detector.getTotalFileCount()).thenReturn(3);

    TuiApplication app = mock(TuiApplication.class);
    when(app.getExecutionContext()).thenReturn(context);
    when(app.getConflictDetector()).thenReturn(detector);
    when(app.isAwaitingOverwriteConfirmation()).thenReturn(false);

    ConflictPolicyScreen screen = new ConflictPolicyScreen();
    screen.draw(app, capture.textGraphics(), new TerminalSize(80, 20), 4);

    assertThat(capture.calls().getFirst().row()).isEqualTo(4);
    verify(app).getExecutionContext();
    verify(app).getConflictDetector();
    verify(app).isAwaitingOverwriteConfirmation();
  }

  @Test
  void handleInputDelegatesToApplicationAndReturnsCurrentState() throws Exception {
    ConflictPolicyScreen screen = new ConflictPolicyScreen();
    TuiApplication app = mock(TuiApplication.class);
    StateMachine stateMachine = mock(StateMachine.class);
    KeyStroke keyStroke = new KeyStroke(KeyType.Enter);
    when(app.getStateMachine()).thenReturn(stateMachine);
    when(stateMachine.getCurrentState()).thenReturn(State.ISSUE_HANDLING);

    State next = screen.handleInput(app, keyStroke);

    verify(app).handleConflictPolicyInput(keyStroke);
    assertThat(next).isEqualTo(State.ISSUE_HANDLING);
  }

  @Test
  void drawShowsNoConflictsMessage() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    ExecutionContext context = new ExecutionContext();
    context.setConflictCandidates(List.of());

    ConflictDetector detector = mock(ConflictDetector.class);
    when(detector.getTotalFileCount()).thenReturn(3);

    ConflictPolicyScreen screen = new ConflictPolicyScreen();
    screen.draw(capture.textGraphics(), 0, context, detector, false);

    String expected =
        TuiMessageSource.getDefault().getMessage(TuiMessageSource.CONFLICT_NO_CONFLICTS, 3);
    List<String> lines = capture.calls().stream().map(TuiTestSupport.PutStringCall::text).toList();
    assertThat(lines).contains(expected);
  }

  @Test
  void drawTriggersConflictDetectionWhenNotScanned() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    ExecutionContext context = new ExecutionContext();

    ConflictDetector detector = mock(ConflictDetector.class);
    when(detector.detectConflicts())
        .thenReturn(List.of(ConflictCandidate.of("src/test/java/com/example/FooTest.java")));
    when(detector.getTotalFileCount()).thenReturn(1);

    ConflictPolicyScreen screen = new ConflictPolicyScreen();
    screen.draw(capture.textGraphics(), 0, context, detector, false);

    verify(detector).detectConflicts();
    assertThat(context.getConflictCount()).isEqualTo(1);
  }

  @Test
  void drawSkipsConflictDetectionWhenScanAlreadyDone() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    ExecutionContext context = new ExecutionContext();
    context.setConflictCandidates(
        List.of(ConflictCandidate.of("src/test/java/com/example/FooTest.java")));

    ConflictDetector detector = mock(ConflictDetector.class);
    when(detector.getTotalFileCount()).thenReturn(1);

    ConflictPolicyScreen screen = new ConflictPolicyScreen();
    screen.draw(capture.textGraphics(), 0, context, detector, false);

    verify(detector, never()).detectConflicts();
  }

  @Test
  void drawShowsOnlyFirstTenConflictsAndOverflowCount() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    ExecutionContext context = new ExecutionContext();
    List<ConflictCandidate> candidates =
        List.of(
            ConflictCandidate.of("src/test/java/com/example/Foo0Test.java"),
            ConflictCandidate.of("src/test/java/com/example/Foo1Test.java"),
            ConflictCandidate.of("src/test/java/com/example/Foo2Test.java"),
            ConflictCandidate.of("src/test/java/com/example/Foo3Test.java"),
            ConflictCandidate.of("src/test/java/com/example/Foo4Test.java"),
            ConflictCandidate.of("src/test/java/com/example/Foo5Test.java"),
            ConflictCandidate.of("src/test/java/com/example/Foo6Test.java"),
            ConflictCandidate.of("src/test/java/com/example/Foo7Test.java"),
            ConflictCandidate.of("src/test/java/com/example/Foo8Test.java"),
            ConflictCandidate.of("src/test/java/com/example/Foo9Test.java"),
            ConflictCandidate.of("src/test/java/com/example/Foo10Test.java"));
    context.setConflictCandidates(candidates);

    ConflictDetector detector = mock(ConflictDetector.class);
    when(detector.getTotalFileCount()).thenReturn(11);

    ConflictPolicyScreen screen = new ConflictPolicyScreen();
    screen.draw(capture.textGraphics(), 0, context, detector, false);

    List<String> lines = capture.calls().stream().map(TuiTestSupport.PutStringCall::text).toList();
    assertThat(lines).contains("- Foo0Test.java", "- Foo9Test.java", "... and 1 more");
    assertThat(lines).noneMatch(line -> line.equals("- Foo10Test.java"));
  }

  @Test
  void drawShowsOverwriteConfirmationWhenAwaiting() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    ExecutionContext context = new ExecutionContext();
    context.setConflictCandidates(List.of(ConflictCandidate.of("src/test/java/FooTest.java")));

    ConflictDetector detector = mock(ConflictDetector.class);
    when(detector.getTotalFileCount()).thenReturn(1);

    ConflictPolicyScreen screen = new ConflictPolicyScreen();
    screen.draw(capture.textGraphics(), 0, context, detector, true);

    String expected =
        TuiMessageSource.getDefault().getMessage(TuiMessageSource.CONFLICT_CONFIRM_QUESTION);
    List<String> lines = capture.calls().stream().map(TuiTestSupport.PutStringCall::text).toList();
    assertThat(lines).contains(expected);
  }

  @Test
  void drawWithApplicationClampsOverwriteConfirmationToTerminalWidth() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    ExecutionContext context = new ExecutionContext();
    context.setConflictCandidates(List.of(ConflictCandidate.of("src/test/java/FooTest.java")));

    ConflictDetector detector = mock(ConflictDetector.class);
    when(detector.getTotalFileCount()).thenReturn(1);

    TuiApplication app = mock(TuiApplication.class);
    when(app.getExecutionContext()).thenReturn(context);
    when(app.getConflictDetector()).thenReturn(detector);
    when(app.isAwaitingOverwriteConfirmation()).thenReturn(true);

    ConflictPolicyScreen screen = new ConflictPolicyScreen();
    screen.draw(app, capture.textGraphics(), new TerminalSize(20, 20), 0);

    List<String> boxLines =
        capture.calls().stream()
            .map(TuiTestSupport.PutStringCall::text)
            .filter(
                line ->
                    line.startsWith("╔")
                        || line.startsWith("╠")
                        || line.startsWith("╚")
                        || line.startsWith("║"))
            .toList();
    assertThat(boxLines).isNotEmpty();
    assertThat(boxLines).allSatisfy(line -> assertThat(line).hasSizeLessThanOrEqualTo(18));
  }

  @Test
  void privateHelpersHandleNullAndTruncationBranches() throws Exception {
    Field innerWidthField = ConflictPolicyScreen.class.getDeclaredField("WARNING_BOX_INNER_WIDTH");
    innerWidthField.setAccessible(true);
    int innerWidth = innerWidthField.getInt(null);

    Method formatBoxLine =
        ConflictPolicyScreen.class.getDeclaredMethod("formatBoxLine", String.class, int.class);
    formatBoxLine.setAccessible(true);
    Method centerText =
        ConflictPolicyScreen.class.getDeclaredMethod("centerText", String.class, int.class);
    centerText.setAccessible(true);
    Method padRight =
        ConflictPolicyScreen.class.getDeclaredMethod("padRight", String.class, int.class);
    padRight.setAccessible(true);
    Method buildBoxBorder =
        ConflictPolicyScreen.class.getDeclaredMethod(
            "buildBoxBorder", char.class, char.class, char.class, int.class);
    buildBoxBorder.setAccessible(true);

    String nullBoxLine = (String) formatBoxLine.invoke(null, null, innerWidth);
    String longBoxLine =
        (String) formatBoxLine.invoke(null, "x".repeat(innerWidth + 10), innerWidth);
    String centeredNull = (String) centerText.invoke(null, null, innerWidth);
    String centeredLong = (String) centerText.invoke(null, "y".repeat(innerWidth + 5), innerWidth);
    String padded = (String) padRight.invoke(null, "ab", 5);
    String unchanged = (String) padRight.invoke(null, "abcdef", 3);
    String border = (String) buildBoxBorder.invoke(null, '╔', '═', '╗', 3);

    assertThat(nullBoxLine)
        .startsWith("║")
        .endsWith("║")
        .hasSize(innerWidth + 2)
        .isEqualTo("║" + " ".repeat(innerWidth) + "║");
    assertThat(longBoxLine).isEqualTo("║" + "x".repeat(innerWidth) + "║");
    assertThat(centeredNull).hasSize(innerWidth).isEqualTo(" ".repeat(innerWidth));
    assertThat(centeredLong).isEqualTo("y".repeat(innerWidth));
    assertThat(padded).isEqualTo("ab   ");
    assertThat(unchanged).isEqualTo("abcdef");
    assertThat(border).isEqualTo("╔═══╗");
  }
}
