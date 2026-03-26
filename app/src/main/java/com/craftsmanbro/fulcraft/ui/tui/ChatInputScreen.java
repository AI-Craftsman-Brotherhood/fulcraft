package com.craftsmanbro.fulcraft.ui.tui;

import com.craftsmanbro.fulcraft.ui.tui.command.CommandResult;
import com.craftsmanbro.fulcraft.ui.tui.i18n.TuiMessageSource;
import com.craftsmanbro.fulcraft.ui.tui.state.State;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import java.io.IOException;

public final class ChatInputScreen implements Screen {

  private final TuiMessageSource msg = TuiMessageSource.getDefault();

  @Override
  public State getState() {
    return State.CHAT_INPUT;
  }

  @Override
  public void draw(
      final TuiApplication app,
      final TextGraphics tg,
      final TerminalSize size,
      final int startRow) {
    draw(tg, startRow, app.getLastCommandResult(), app.getInputBuffer());
  }

  @Override
  public State handleInput(final TuiApplication app, final KeyStroke keyStroke) throws IOException {
    app.handleChatInput(keyStroke);
    return app.getStateMachine().getCurrentState();
  }

  public void draw(
      final TextGraphics tg,
      final int startRow,
      final CommandResult lastCommandResult,
      final String inputBuffer) {
    final int headerColumn = 2;
    final int contentColumn = 4;
    int currentRow = startRow;
    tg.putString(headerColumn, currentRow++, msg.getMessage(TuiMessageSource.CHAT_TITLE));
    tg.putString(headerColumn, currentRow++, "");
    if (lastCommandResult != null) {
      if (lastCommandResult.success()) {
        for (final String outputLine : lastCommandResult.outputLines()) {
          tg.putString(contentColumn, currentRow++, outputLine);
        }
      } else {
        tg.setForegroundColor(TextColor.ANSI.RED);
        final String errorLabelPrefix = msg.getMessage(TuiMessageSource.COMMON_ERROR) + ": ";
        final String[] errorLines = lastCommandResult.errorMessage().split("\\R");
        for (int errorLineIndex = 0; errorLineIndex < errorLines.length; errorLineIndex++) {
          // Indent continuation lines so the error text aligns after the prefix.
          final String renderedErrorLine =
              errorLineIndex == 0
                  ? errorLabelPrefix + errorLines[errorLineIndex]
                  : " ".repeat(errorLabelPrefix.length()) + errorLines[errorLineIndex];
          tg.putString(contentColumn, currentRow++, renderedErrorLine);
        }
        tg.setForegroundColor(TextColor.ANSI.DEFAULT);
      }
      tg.putString(headerColumn, currentRow++, "");
    }
    tg.putString(headerColumn, currentRow++, msg.getMessage(TuiMessageSource.CHAT_HINT));
    tg.putString(headerColumn, currentRow++, msg.getMessage(TuiMessageSource.CHAT_COMMANDS_HINT));
    tg.putString(headerColumn, currentRow++, "");
    final String normalizedInputBuffer = inputBuffer == null ? "" : inputBuffer;
    tg.putString(headerColumn, currentRow, "> " + normalizedInputBuffer + "_");
  }
}
