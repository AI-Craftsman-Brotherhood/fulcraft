package com.craftsmanbro.fulcraft.ui.tui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.craftsmanbro.fulcraft.ui.tui.command.CommandResult;
import com.craftsmanbro.fulcraft.ui.tui.i18n.TuiMessageSource;
import com.craftsmanbro.fulcraft.ui.tui.state.State;
import com.craftsmanbro.fulcraft.ui.tui.state.StateMachine;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class ChatInputScreenTest {

  @Test
  void getStateReturnsChatInput() {
    ChatInputScreen screen = new ChatInputScreen();

    assertThat(screen.getState()).isEqualTo(State.CHAT_INPUT);
  }

  @Test
  void drawUsesApplicationBufferAndResult() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    ChatInputScreen screen = new ChatInputScreen();
    TuiApplication app = mock(TuiApplication.class);
    when(app.getLastCommandResult()).thenReturn(CommandResult.success(List.of("ok")));
    when(app.getInputBuffer()).thenReturn("buffer");

    screen.draw(app, capture.textGraphics(), new TerminalSize(80, 20), 3);

    List<String> lines = capture.calls().stream().map(TuiTestSupport.PutStringCall::text).toList();
    assertThat(capture.calls().getFirst().row()).isEqualTo(3);
    assertThat(lines).contains("ok");
    assertThat(lines).anyMatch(line -> line.contains("> buffer_"));
  }

  @Test
  void handleInputDelegatesToAppAndReturnsCurrentState() throws Exception {
    ChatInputScreen screen = new ChatInputScreen();
    TuiApplication app = mock(TuiApplication.class);
    StateMachine stateMachine = mock(StateMachine.class);
    KeyStroke keyStroke = mock(KeyStroke.class);
    when(app.getStateMachine()).thenReturn(stateMachine);
    when(stateMachine.getCurrentState()).thenReturn(State.PLAN_REVIEW);

    State next = screen.handleInput(app, keyStroke);

    verify(app).handleChatInput(keyStroke);
    assertThat(next).isEqualTo(State.PLAN_REVIEW);
  }

  @Test
  void drawShowsErrorMessageAndPrompt() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    ChatInputScreen screen = new ChatInputScreen();

    screen.draw(capture.textGraphics(), 0, CommandResult.error("boom"), "input");

    List<String> lines = capture.calls().stream().map(TuiTestSupport.PutStringCall::text).toList();
    TuiMessageSource msg = TuiMessageSource.getDefault();

    assertThat(lines)
        .anyMatch(line -> line.contains(msg.getMessage(TuiMessageSource.COMMON_ERROR)));
    assertThat(lines).anyMatch(line -> line.contains("boom"));
    assertThat(lines).anyMatch(line -> line.contains("> input_"));
  }

  @Test
  void drawShowsSuccessOutputLines() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    ChatInputScreen screen = new ChatInputScreen();

    screen.draw(capture.textGraphics(), 0, CommandResult.success(List.of("ok", "done")), "");

    List<String> lines = capture.calls().stream().map(TuiTestSupport.PutStringCall::text).toList();
    assertThat(lines).contains("ok", "done");
  }

  @Test
  void drawShowsHintsAndEmptyPromptWhenNoResultAndNullInput() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    ChatInputScreen screen = new ChatInputScreen();
    TuiMessageSource msg = TuiMessageSource.getDefault();

    screen.draw(capture.textGraphics(), 2, null, null);

    List<String> lines = capture.calls().stream().map(TuiTestSupport.PutStringCall::text).toList();
    assertThat(capture.calls().getFirst().row()).isEqualTo(2);
    assertThat(lines).contains(msg.getMessage(TuiMessageSource.CHAT_HINT));
    assertThat(lines).contains(msg.getMessage(TuiMessageSource.CHAT_COMMANDS_HINT));
    assertThat(lines).anyMatch(line -> line.contains("> _"));
  }

  @Test
  void drawFormatsMultilineErrorAndResetsColor() {
    ChatInputScreen screen = new ChatInputScreen();
    TextGraphics tg = mock(TextGraphics.class);
    TuiMessageSource msg = TuiMessageSource.getDefault();
    String prefix = msg.getMessage(TuiMessageSource.COMMON_ERROR) + ": ";

    screen.draw(tg, 0, CommandResult.error("boom\ntrace"), "");

    InOrder inOrder = inOrder(tg);
    inOrder.verify(tg).setForegroundColor(TextColor.ANSI.RED);
    inOrder.verify(tg).putString(eq(4), anyInt(), eq(prefix + "boom"));
    inOrder.verify(tg).putString(eq(4), anyInt(), eq(" ".repeat(prefix.length()) + "trace"));
    inOrder.verify(tg).setForegroundColor(TextColor.ANSI.DEFAULT);
  }
}
