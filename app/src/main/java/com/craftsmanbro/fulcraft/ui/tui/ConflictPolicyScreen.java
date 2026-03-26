package com.craftsmanbro.fulcraft.ui.tui;

import com.craftsmanbro.fulcraft.ui.tui.conflict.ConflictCandidate;
import com.craftsmanbro.fulcraft.ui.tui.conflict.ConflictDetector;
import com.craftsmanbro.fulcraft.ui.tui.i18n.TuiMessageSource;
import com.craftsmanbro.fulcraft.ui.tui.state.ExecutionContext;
import com.craftsmanbro.fulcraft.ui.tui.state.State;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import java.io.IOException;

public final class ConflictPolicyScreen implements Screen {

  private final TuiMessageSource msg = TuiMessageSource.getDefault();

  private static final String WARNING_BOX_TOP =
      "╔════════════════════════════════════════════════╗";

  private static final int WARNING_BOX_INNER_WIDTH = WARNING_BOX_TOP.length() - 2;

  @Override
  public State getState() {
    return State.CONFLICT_POLICY;
  }

  @Override
  public void draw(
      final TuiApplication app,
      final TextGraphics tg,
      final TerminalSize size,
      final int startRow) {
    draw(
        tg,
        startRow,
        size,
        app.getExecutionContext(),
        app.getConflictDetector(),
        app.isAwaitingOverwriteConfirmation());
  }

  @Override
  public State handleInput(final TuiApplication app, final KeyStroke keyStroke) throws IOException {
    app.handleConflictPolicyInput(keyStroke);
    return app.getStateMachine().getCurrentState();
  }

  public void draw(
      final TextGraphics tg,
      final int startRow,
      final ExecutionContext executionContext,
      final ConflictDetector conflictDetector,
      final boolean awaitingOverwriteConfirmation) {
    draw(
        tg,
        startRow,
        new TerminalSize(Integer.MAX_VALUE, Integer.MAX_VALUE),
        executionContext,
        conflictDetector,
        awaitingOverwriteConfirmation);
  }

  public void draw(
      final TextGraphics tg,
      final int startRow,
      final TerminalSize size,
      final ExecutionContext executionContext,
      final ConflictDetector conflictDetector,
      final boolean awaitingOverwriteConfirmation) {
    int row = startRow;
    if (!executionContext.isConflictScanDone()) {
      executionContext.setConflictCandidates(conflictDetector.detectConflicts());
    }
    tg.putString(2, row++, msg.getMessage(TuiMessageSource.CONFLICT_SELECTION_TITLE));
    tg.putString(2, row++, "");
    final int conflictCount = executionContext.getConflictCount();
    final int totalCount = conflictDetector.getTotalFileCount();
    if (conflictCount == 0) {
      tg.setForegroundColor(TextColor.ANSI.GREEN);
      tg.putString(2, row++, msg.getMessage(TuiMessageSource.CONFLICT_NO_CONFLICTS, totalCount));
      tg.setForegroundColor(TextColor.ANSI.DEFAULT);
    } else {
      tg.setForegroundColor(TextColor.ANSI.YELLOW);
      tg.putString(
          2,
          row++,
          msg.getMessage(TuiMessageSource.CONFLICT_CONFLICTS_FOUND, conflictCount, totalCount));
      tg.setForegroundColor(TextColor.ANSI.DEFAULT);
      tg.putString(2, row++, "");
      tg.putString(2, row++, msg.getMessage(TuiMessageSource.CONFLICT_OVERWRITE_LIST));
      final int displayLimit = Math.min(conflictCount, 10);
      for (int i = 0; i < displayLimit; i++) {
        final ConflictCandidate candidate = executionContext.getConflictCandidates().get(i);
        tg.putString(4, row++, "- " + candidate.fileName());
      }
      if (conflictCount > 10) {
        tg.putString(4, row++, msg.getMessage("tui.common.more_count", conflictCount - 10));
      }
    }
    tg.putString(2, row++, "");
    if (awaitingOverwriteConfirmation) {
      drawOverwriteConfirmation(tg, row, size, executionContext);
    } else {
      drawPolicyOptions(tg, row);
    }
  }

  private void drawPolicyOptions(final TextGraphics tg, final int startRow) {
    int row = startRow;
    tg.putString(2, row++, msg.getMessage(TuiMessageSource.CONFLICT_SELECT_POLICY));
    tg.putString(2, row++, "");
    tg.setForegroundColor(TextColor.ANSI.GREEN);
    tg.putString(4, row++, msg.getMessage(TuiMessageSource.CONFLICT_SAFE_LABEL));
    tg.setForegroundColor(TextColor.ANSI.DEFAULT);
    tg.putString(6, row++, msg.getMessage(TuiMessageSource.CONFLICT_SAFE_DESC));
    tg.putString(2, row++, "");
    tg.setForegroundColor(TextColor.ANSI.YELLOW);
    tg.putString(4, row++, msg.getMessage(TuiMessageSource.CONFLICT_SKIP_LABEL));
    tg.setForegroundColor(TextColor.ANSI.DEFAULT);
    tg.putString(6, row++, msg.getMessage(TuiMessageSource.CONFLICT_SKIP_DESC));
    tg.putString(2, row++, "");
    tg.setForegroundColor(TextColor.ANSI.RED);
    tg.putString(4, row++, msg.getMessage(TuiMessageSource.CONFLICT_OVERWRITE_LABEL));
    tg.setForegroundColor(TextColor.ANSI.DEFAULT);
    tg.putString(6, row++, msg.getMessage(TuiMessageSource.CONFLICT_OVERWRITE_DESC1));
    tg.putString(6, row++, msg.getMessage(TuiMessageSource.CONFLICT_OVERWRITE_DESC2));
    tg.putString(2, row++, "");
    tg.putString(2, row++, msg.getMessage(TuiMessageSource.CONFLICT_OTHER_OPTIONS));
    tg.putString(4, row++, msg.getMessage(TuiMessageSource.CONFLICT_BACK));
    tg.putString(4, row, msg.getMessage(TuiMessageSource.CONFLICT_QUIT));
  }

  private void drawOverwriteConfirmation(
      final TextGraphics tg,
      final int startRow,
      final TerminalSize size,
      final ExecutionContext context) {
    int row = startRow;
    final int warningBoxInnerWidth = resolveWarningBoxInnerWidth(size);
    tg.setForegroundColor(TextColor.ANSI.RED);
    tg.putString(2, row++, buildBoxBorder('╔', '═', '╗', warningBoxInnerWidth));
    tg.putString(
        2,
        row++,
        formatBoxLine(
            centerText(
                msg.getMessage(TuiMessageSource.CONFLICT_WARNING_BOX_TITLE), warningBoxInnerWidth),
            warningBoxInnerWidth));
    tg.putString(2, row++, buildBoxBorder('╠', '═', '╣', warningBoxInnerWidth));
    tg.putString(2, row++, formatBoxLine("", warningBoxInnerWidth));
    tg.putString(
        2,
        row++,
        formatBoxLine(
            "  "
                + msg.getMessage(
                    TuiMessageSource.CONFLICT_WARNING_BOX_MESSAGE, context.getConflictCount())
                + "  ",
            warningBoxInnerWidth));
    tg.putString(2, row++, formatBoxLine("", warningBoxInnerWidth));
    tg.putString(
        2,
        row++,
        formatBoxLine(
            "  " + msg.getMessage(TuiMessageSource.CONFLICT_WARNING_BOX_DESC1) + "  ",
            warningBoxInnerWidth));
    tg.putString(
        2,
        row++,
        formatBoxLine(
            "  " + msg.getMessage(TuiMessageSource.CONFLICT_WARNING_BOX_DESC2) + "  ",
            warningBoxInnerWidth));
    tg.putString(2, row++, formatBoxLine("", warningBoxInnerWidth));
    tg.putString(2, row++, buildBoxBorder('╚', '═', '╝', warningBoxInnerWidth));
    tg.setForegroundColor(TextColor.ANSI.DEFAULT);
    tg.putString(2, row++, "");
    tg.setForegroundColor(TextColor.ANSI.WHITE);
    tg.putString(2, row++, msg.getMessage(TuiMessageSource.CONFLICT_CONFIRM_QUESTION));
    tg.putString(2, row++, "");
    tg.setForegroundColor(TextColor.ANSI.GREEN);
    tg.putString(4, row++, msg.getMessage(TuiMessageSource.CONFLICT_CONFIRM_YES));
    tg.setForegroundColor(TextColor.ANSI.YELLOW);
    tg.putString(4, row, msg.getMessage(TuiMessageSource.CONFLICT_CONFIRM_NO));
    tg.setForegroundColor(TextColor.ANSI.DEFAULT);
  }

  private static String formatBoxLine(final String content, final int innerWidth) {
    String safeContent = content != null ? content : "";
    if (safeContent.length() > innerWidth) {
      safeContent = safeContent.substring(0, innerWidth);
    }
    final StringBuilder line = new StringBuilder(innerWidth + 2);
    line.append('║');
    line.append(padRight(safeContent, innerWidth));
    line.append('║');
    return line.toString();
  }

  private static String centerText(final String text, final int innerWidth) {
    final String safeText = text != null ? text : "";
    if (safeText.length() >= innerWidth) {
      return safeText.substring(0, innerWidth);
    }
    final int padding = innerWidth - safeText.length();
    final int left = padding / 2;
    final int right = padding - left;
    return " ".repeat(left) + safeText + " ".repeat(right);
  }

  private static String buildBoxBorder(
      final char leftBorder, final char fill, final char rightBorder, final int innerWidth) {
    return leftBorder + String.valueOf(fill).repeat(innerWidth) + rightBorder;
  }

  private static int resolveWarningBoxInnerWidth(final TerminalSize size) {
    if (size == null) {
      return WARNING_BOX_INNER_WIDTH;
    }
    final int availableWidth = Math.max(0, size.getColumns() - 2);
    return Math.clamp(availableWidth - 2, 0, WARNING_BOX_INNER_WIDTH);
  }

  private static String padRight(final String text, final int width) {
    if (text.length() >= width) {
      return text;
    }
    final StringBuilder padded = new StringBuilder(width);
    padded.append(text);
    while (padded.length() < width) {
      padded.append(' ');
    }
    return padded.toString();
  }
}
