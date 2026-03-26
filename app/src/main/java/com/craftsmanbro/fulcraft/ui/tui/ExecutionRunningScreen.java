package com.craftsmanbro.fulcraft.ui.tui;

import com.craftsmanbro.fulcraft.ui.tui.execution.ExecutionSession;
import com.craftsmanbro.fulcraft.ui.tui.i18n.TuiMessageSource;
import com.craftsmanbro.fulcraft.ui.tui.state.State;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import java.io.IOException;
import java.util.List;

public final class ExecutionRunningScreen implements Screen {

  private final TuiMessageSource msg = TuiMessageSource.getDefault();

  @Override
  public State getState() {
    return State.EXECUTION_RUNNING;
  }

  @Override
  public void draw(
      final TuiApplication app,
      final TextGraphics tg,
      final TerminalSize size,
      final int startRow) {
    final int newOffset =
        draw(tg, startRow, size, app.getExecutionSession(), app.getLogScrollOffset());
    app.setLogScrollOffset(newOffset);
  }

  @Override
  public State handleInput(final TuiApplication app, final KeyStroke keyStroke) throws IOException {
    app.handleExecutionRunningInput(keyStroke);
    return app.getStateMachine().getCurrentState();
  }

  public int draw(
      final TextGraphics tg,
      final int startRow,
      final TerminalSize size,
      final ExecutionSession executionSession,
      final int logScrollOffset) {
    int row = startRow;
    if (size.getColumns() <= 2 || size.getRows() <= startRow) {
      return Math.max(0, logScrollOffset);
    }
    final int maxWidth = Math.max(0, size.getColumns() - 4);
    final int maxLogLines = Math.max(0, size.getRows() - startRow - 8);
    tg.setForegroundColor(TextColor.ANSI.CYAN);
    putString(tg, size, 2, row++, msg.getMessage(TuiMessageSource.EXEC_TITLE_RUNNING));
    tg.setForegroundColor(TextColor.ANSI.DEFAULT);
    putString(tg, size, 2, row++, "");
    final ExecutionSession.Status status = executionSession.getStatus();
    final String stageName = executionSession.getCurrentStage();
    final String progress = executionSession.getProgress();
    final String statusText =
        switch (status) {
          case RUNNING -> msg.getMessage("tui.exec.status.running");
          case CANCELLED -> msg.getMessage("tui.exec.status.cancelled");
          case COMPLETED -> msg.getMessage("tui.exec.status.completed");
          case FAILED -> msg.getMessage("tui.exec.status.failed");
          default -> msg.getMessage("tui.exec.status.unknown", status.name());
        };
    switch (status) {
      case RUNNING -> tg.setForegroundColor(TextColor.ANSI.GREEN);
      case CANCELLED, FAILED -> tg.setForegroundColor(TextColor.ANSI.RED);
      case COMPLETED -> tg.setForegroundColor(TextColor.ANSI.CYAN);
      default -> tg.setForegroundColor(TextColor.ANSI.DEFAULT);
    }
    putString(tg, size, 2, row++, statusText);
    tg.setForegroundColor(TextColor.ANSI.DEFAULT);
    if (!stageName.isEmpty()) {
      putString(tg, size, 2, row++, msg.getMessage(TuiMessageSource.EXEC_STAGE) + " " + stageName);
    }
    if (!progress.isEmpty()) {
      putString(
          tg, size, 2, row++, msg.getMessage(TuiMessageSource.EXEC_PROGRESS) + " " + progress);
    }
    putString(tg, size, 2, row++, "");
    final int separatorWidth = Math.min(50, maxWidth);
    putString(tg, size, 2, row++, "─".repeat(separatorWidth));
    final List<String> logLines = executionSession.getLogLines();
    final int totalLogs = logLines.size();
    int normalizedLogScrollOffset = Math.max(0, logScrollOffset);
    final int maxOffset = Math.max(0, totalLogs - maxLogLines);
    if (normalizedLogScrollOffset > maxOffset) {
      normalizedLogScrollOffset = maxOffset;
    }
    final int displayStart = Math.max(0, totalLogs - maxLogLines - normalizedLogScrollOffset);
    final int displayEnd = Math.min(totalLogs, displayStart + maxLogLines);
    for (int i = displayStart; i < displayEnd && row < size.getRows() - 4; i++) {
      final String line = truncateLine(logLines.get(i), maxWidth);
      putString(tg, size, 2, row++, line);
    }
    while (row < size.getRows() - 4) {
      row++;
    }
    row = size.getRows() - 3;
    putString(tg, size, 2, row++, "─".repeat(separatorWidth));
    if (executionSession.isFinished()) {
      tg.setForegroundColor(TextColor.ANSI.GREEN);
      putString(tg, size, 2, row, msg.getMessage(TuiMessageSource.EXEC_FINISH_HINT));
    } else {
      tg.setForegroundColor(TextColor.ANSI.YELLOW);
      putString(tg, size, 2, row, msg.getMessage(TuiMessageSource.EXEC_CONTROLS_HINT));
    }
    tg.setForegroundColor(TextColor.ANSI.DEFAULT);
    return normalizedLogScrollOffset;
  }

  private void putString(
      final TextGraphics tg,
      final TerminalSize size,
      final int column,
      final int row,
      final String text) {
    if (text == null) {
      return;
    }
    if (row < 0 || row >= size.getRows() || column < 0 || column >= size.getColumns()) {
      return;
    }
    final int maxWidth = size.getColumns() - column;
    if (maxWidth <= 0) {
      return;
    }
    String safeText = text;
    if (safeText.length() > maxWidth) {
      safeText = safeText.substring(0, maxWidth);
    }
    tg.putString(column, row, safeText);
  }

  private String truncateLine(final String line, final int maxWidth) {
    if (line == null) {
      return "";
    }
    if (maxWidth <= 0 || line.length() <= maxWidth) {
      return line;
    }
    if (maxWidth <= 3) {
      return line.substring(0, maxWidth);
    }
    return line.substring(0, maxWidth - 3) + "...";
  }
}
