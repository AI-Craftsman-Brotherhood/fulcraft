package com.craftsmanbro.fulcraft.ui.tui;

import com.craftsmanbro.fulcraft.ui.tui.conflict.IssueHandlingOption;
import com.craftsmanbro.fulcraft.ui.tui.execution.ExecutionIssue;
import com.craftsmanbro.fulcraft.ui.tui.i18n.TuiMessageSource;
import com.craftsmanbro.fulcraft.ui.tui.state.ExecutionContext;
import com.craftsmanbro.fulcraft.ui.tui.state.State;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import java.io.IOException;

public final class IssueHandlingScreen implements Screen {

  private final TuiMessageSource msg = TuiMessageSource.getDefault();

  @Override
  public State getState() {
    return State.ISSUE_HANDLING;
  }

  @Override
  public void draw(
      final TuiApplication app,
      final TextGraphics tg,
      final TerminalSize size,
      final int startRow) {
    draw(tg, startRow, size, app.getExecutionContext());
  }

  @Override
  public State handleInput(final TuiApplication app, final KeyStroke keyStroke) throws IOException {
    app.handleIssueHandlingInput(keyStroke);
    return app.getStateMachine().getCurrentState();
  }

  public void draw(
      final TextGraphics tg,
      final int startRow,
      final TerminalSize size,
      final ExecutionContext executionContext) {
    int row = startRow;
    final int maxWidth = Math.max(0, size.getColumns() - 4);
    tg.setForegroundColor(TextColor.ANSI.RED);
    tg.putString(2, row++, msg.getMessage(TuiMessageSource.ISSUE_TITLE_DETECTED));
    tg.setForegroundColor(TextColor.ANSI.DEFAULT);
    tg.putString(2, row++, "");
    if (!executionContext.hasIssue()) {
      tg.putString(2, row++, msg.getMessage(TuiMessageSource.ISSUE_NO_ISSUE));
      tg.putString(2, row++, "");
      tg.putString(2, row, msg.getMessage(TuiMessageSource.ISSUE_QUIT));
      return;
    }
    final ExecutionIssue issue = executionContext.getCurrentIssue().orElse(null);
    if (issue == null) {
      return;
    }
    tg.setForegroundColor(TextColor.ANSI.YELLOW);
    tg.putString(2, row++, buildSeparator(maxWidth));
    tg.setForegroundColor(TextColor.ANSI.DEFAULT);
    tg.setForegroundColor(TextColor.ANSI.CYAN);
    tg.putString(
        2,
        row++,
        truncateLine(
            msg.getMessage(TuiMessageSource.ISSUE_CATEGORY)
                + " "
                + issue.category().getDisplayName(),
            maxWidth));
    tg.putString(
        2,
        row++,
        truncateLine(
            msg.getMessage(TuiMessageSource.ISSUE_TARGET) + "   " + issue.targetIdentifier(),
            maxWidth));
    tg.putString(
        2,
        row++,
        truncateLine(
            msg.getMessage(TuiMessageSource.ISSUE_STAGE) + "    " + issue.stageName(), maxWidth));
    tg.setForegroundColor(TextColor.ANSI.DEFAULT);
    tg.putString(2, row++, "");
    tg.setForegroundColor(TextColor.ANSI.WHITE);
    tg.putString(2, row++, msg.getMessage(TuiMessageSource.ISSUE_CAUSE));
    tg.setForegroundColor(TextColor.ANSI.DEFAULT);
    final String[] causeLines = issue.cause().split("\\R", 3);
    final int causeWidth = Math.max(0, maxWidth - 4);
    for (final String line : causeLines) {
      tg.putString(4, row++, truncateLine(line, causeWidth));
    }
    tg.putString(2, row++, "");
    tg.setForegroundColor(TextColor.ANSI.YELLOW);
    tg.putString(2, row++, buildSeparator(maxWidth));
    tg.setForegroundColor(TextColor.ANSI.DEFAULT);
    tg.putString(2, row++, "");
    tg.setForegroundColor(TextColor.ANSI.WHITE);
    tg.putString(2, row++, msg.getMessage(TuiMessageSource.ISSUE_SELECT_OPTION));
    tg.setForegroundColor(TextColor.ANSI.DEFAULT);
    tg.putString(2, row++, "");
    tg.setForegroundColor(TextColor.ANSI.GREEN);
    tg.putString(4, row++, IssueHandlingOption.SAFE_FIX.getMenuLabel());
    tg.setForegroundColor(TextColor.ANSI.DEFAULT);
    tg.putString(6, row++, IssueHandlingOption.SAFE_FIX.getDescription());
    tg.putString(2, row++, "");
    tg.setForegroundColor(TextColor.ANSI.YELLOW);
    tg.putString(4, row++, IssueHandlingOption.PROPOSE_ONLY.getMenuLabel());
    tg.setForegroundColor(TextColor.ANSI.DEFAULT);
    tg.putString(6, row++, IssueHandlingOption.PROPOSE_ONLY.getDescription());
    tg.putString(2, row++, "");
    tg.setForegroundColor(TextColor.ANSI.RED);
    tg.putString(4, row++, IssueHandlingOption.SKIP.getMenuLabel());
    tg.setForegroundColor(TextColor.ANSI.DEFAULT);
    tg.putString(6, row++, IssueHandlingOption.SKIP.getDescription());
    tg.putString(2, row++, "");
    tg.putString(2, row++, "");
    tg.setForegroundColor(TextColor.ANSI.CYAN);
    tg.putString(2, row, msg.getMessage(TuiMessageSource.ISSUE_FOOTER));
    tg.setForegroundColor(TextColor.ANSI.DEFAULT);
  }

  private static String buildSeparator(final int maxWidth) {
    final int ruleWidth = Math.clamp(maxWidth, 0, 60);
    return "─".repeat(ruleWidth);
  }

  private static String truncateLine(final String line, final int maxWidth) {
    if (line == null || maxWidth <= 0) {
      return "";
    }
    if (line.length() <= maxWidth) {
      return line;
    }
    if (maxWidth <= 3) {
      return line.substring(0, maxWidth);
    }
    return line.substring(0, maxWidth - 3) + "...";
  }
}
