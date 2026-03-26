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

public final class SummaryScreen implements Screen {

  private final TuiMessageSource msg = TuiMessageSource.getDefault();

  @Override
  public State getState() {
    return State.SUMMARY;
  }

  @Override
  public void draw(
      final TuiApplication app,
      final TextGraphics tg,
      final TerminalSize size,
      final int startRow) {
    draw(tg, startRow, size, app.getExecutionSession());
  }

  @Override
  public State handleInput(final TuiApplication app, final KeyStroke keyStroke) throws IOException {
    app.handleDefaultInput(keyStroke);
    return app.getStateMachine().getCurrentState();
  }

  public void draw(
      final TextGraphics tg,
      final int startRow,
      final TerminalSize size,
      final ExecutionSession executionSession) {
    final int contentColumn = 2;
    final int detailColumn = 4;
    final int maxVisibleFiles = 10;
    int currentRow = startRow;
    if (size.getColumns() <= contentColumn || size.getRows() <= startRow) {
      return;
    }
    final int contentWidth = Math.max(0, size.getColumns() - (contentColumn * 2));
    tg.setForegroundColor(TextColor.ANSI.CYAN);
    putClippedString(
        tg, size, contentColumn, currentRow++, msg.getMessage(TuiMessageSource.SUMMARY_TITLE));
    tg.setForegroundColor(TextColor.ANSI.DEFAULT);
    putClippedString(tg, size, contentColumn, currentRow++, "");
    final ExecutionSession.Status sessionStatus = executionSession.getStatus();
    switch (sessionStatus) {
      case COMPLETED -> {
        tg.setForegroundColor(TextColor.ANSI.GREEN);
        putClippedString(
            tg,
            size,
            contentColumn,
            currentRow++,
            msg.getMessage(TuiMessageSource.SUMMARY_SUCCESS_MSG));
      }
      case CANCELLED -> {
        tg.setForegroundColor(TextColor.ANSI.YELLOW);
        putClippedString(
            tg,
            size,
            contentColumn,
            currentRow++,
            msg.getMessage(TuiMessageSource.SUMMARY_CANCELLED_MSG));
      }
      case FAILED -> {
        tg.setForegroundColor(TextColor.ANSI.RED);
        putClippedString(
            tg,
            size,
            contentColumn,
            currentRow++,
            msg.getMessage(TuiMessageSource.SUMMARY_FAILED_MSG));
        final String errorMessage = executionSession.getErrorMessage();
        if (errorMessage != null && !errorMessage.isEmpty()) {
          putClippedString(
              tg,
              size,
              detailColumn,
              currentRow++,
              msg.getMessage(TuiMessageSource.SUMMARY_ERROR_PREFIX) + " " + errorMessage);
        }
      }
      default -> {
        tg.setForegroundColor(TextColor.ANSI.DEFAULT);
        putClippedString(
            tg,
            size,
            contentColumn,
            currentRow++,
            msg.getMessage(TuiMessageSource.SUMMARY_STATUS_PREFIX) + " " + sessionStatus.name());
      }
    }
    tg.setForegroundColor(TextColor.ANSI.DEFAULT);
    putClippedString(tg, size, contentColumn, currentRow++, "");
    final int completedStages = executionSession.getCompletedStages();
    final int totalStages = executionSession.getTotalStages();
    putClippedString(
        tg,
        size,
        contentColumn,
        currentRow++,
        msg.getMessage(TuiMessageSource.SUMMARY_STAGES_COMPLETED, completedStages, totalStages));
    putClippedString(tg, size, contentColumn, currentRow++, "");
    final List<String> generatedFiles = executionSession.getGeneratedFiles();
    if (generatedFiles.isEmpty()) {
      putClippedString(
          tg, size, contentColumn, currentRow++, msg.getMessage(TuiMessageSource.SUMMARY_NO_FILES));
    } else {
      tg.setForegroundColor(TextColor.ANSI.CYAN);
      putClippedString(
          tg,
          size,
          contentColumn,
          currentRow++,
          msg.getMessage(TuiMessageSource.SUMMARY_GENERATED_FILES_LABEL, generatedFiles.size()));
      tg.setForegroundColor(TextColor.ANSI.DEFAULT);
      final int visibleFileCount = Math.min(generatedFiles.size(), maxVisibleFiles);
      final int fileTextWidth = Math.max(0, contentWidth - detailColumn);
      for (int i = 0; i < visibleFileCount; i++) {
        final String generatedFile = generatedFiles.get(i);
        final String visibleFile = truncateKeepingTail(generatedFile, fileTextWidth);
        putClippedString(tg, size, detailColumn, currentRow++, "• " + visibleFile);
      }
      if (generatedFiles.size() > maxVisibleFiles) {
        putClippedString(
            tg,
            size,
            detailColumn,
            currentRow++,
            msg.getMessage("tui.common.more_count", generatedFiles.size() - maxVisibleFiles));
      }
    }
    putClippedString(tg, size, contentColumn, currentRow++, "");
    tg.setForegroundColor(TextColor.ANSI.YELLOW);
    putClippedString(
        tg, size, contentColumn, currentRow++, msg.getMessage(TuiMessageSource.SUMMARY_TIP1));
    putClippedString(
        tg, size, contentColumn, currentRow++, msg.getMessage(TuiMessageSource.SUMMARY_TIP2));
    tg.setForegroundColor(TextColor.ANSI.DEFAULT);
    putClippedString(tg, size, contentColumn, currentRow++, "");
    putClippedString(
        tg, size, contentColumn, currentRow, msg.getMessage(TuiMessageSource.SUMMARY_FOOTER));
  }

  private void putClippedString(
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
    final int availableWidth = size.getColumns() - column;
    if (availableWidth <= 0) {
      return;
    }
    final String clippedText =
        text.length() > availableWidth ? text.substring(0, availableWidth) : text;
    tg.putString(column, row, clippedText);
  }

  private String truncateKeepingTail(final String text, final int availableWidth) {
    final int ellipsisWidth = 3;
    if (text == null || availableWidth <= 0) {
      return "";
    }
    if (text.length() <= availableWidth) {
      return text;
    }
    // Preserve the tail so filenames remain identifiable in narrow terminal layouts.
    if (availableWidth <= ellipsisWidth) {
      return text.substring(text.length() - availableWidth);
    }
    return "..." + text.substring(text.length() - (availableWidth - ellipsisWidth));
  }
}
