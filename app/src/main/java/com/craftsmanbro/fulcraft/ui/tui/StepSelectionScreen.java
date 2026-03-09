package com.craftsmanbro.fulcraft.ui.tui;

import com.craftsmanbro.fulcraft.ui.tui.i18n.TuiMessageSource;
import com.craftsmanbro.fulcraft.ui.tui.plan.Plan;
import com.craftsmanbro.fulcraft.ui.tui.state.State;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import java.io.IOException;

public final class StepSelectionScreen implements Screen {

  private final TuiMessageSource messageSource = TuiMessageSource.getDefault();

  @Override
  public State getState() {
    return State.PLAN_REVIEW;
  }

  @Override
  public void draw(
      final TuiApplication application,
      final TextGraphics graphics,
      final TerminalSize terminalSize,
      final int startRow) {
    draw(graphics, startRow, terminalSize, application.getCurrentPlan());
  }

  @Override
  public State handleInput(final TuiApplication application, final KeyStroke keyStroke)
      throws IOException {
    application.handlePlanReviewInput(keyStroke);
    return application.getStateMachine().getCurrentState();
  }

  public void draw(final TextGraphics graphics, final int startRow, final Plan plan) {
    draw(graphics, startRow, new TerminalSize(Integer.MAX_VALUE, Integer.MAX_VALUE), plan);
  }

  public void draw(
      final TextGraphics graphics,
      final int startRow,
      final TerminalSize terminalSize,
      final Plan plan) {
    final int sectionColumn = 2;
    final int contentColumn = 4;
    int currentRow = startRow;
    drawString(
        graphics,
        terminalSize,
        sectionColumn,
        currentRow++,
        messageSource.getMessage(TuiMessageSource.PLAN_TITLE));
    drawString(graphics, terminalSize, sectionColumn, currentRow++, "");
    if (plan != null) {
      graphics.setForegroundColor(TextColor.ANSI.CYAN);
      drawString(
          graphics,
          terminalSize,
          sectionColumn,
          currentRow++,
          messageSource.getMessage("tui.plan.label.goal"));
      graphics.setForegroundColor(TextColor.ANSI.DEFAULT);
      currentRow = drawLines(graphics, terminalSize, contentColumn, currentRow, plan.goal());
      drawString(graphics, terminalSize, sectionColumn, currentRow++, "");
      graphics.setForegroundColor(TextColor.ANSI.CYAN);
      drawString(
          graphics,
          terminalSize,
          sectionColumn,
          currentRow++,
          messageSource.getMessage("tui.plan.label.steps"));
      graphics.setForegroundColor(TextColor.ANSI.DEFAULT);
      int stepNumber = 1;
      for (final String planStep : plan.steps()) {
        currentRow =
            drawLines(
                graphics, terminalSize, contentColumn, currentRow, stepNumber + ". " + planStep);
        stepNumber++;
      }
      drawString(graphics, terminalSize, sectionColumn, currentRow++, "");
      graphics.setForegroundColor(TextColor.ANSI.CYAN);
      drawString(
          graphics,
          terminalSize,
          sectionColumn,
          currentRow++,
          messageSource.getMessage("tui.plan.label.impact"));
      graphics.setForegroundColor(TextColor.ANSI.DEFAULT);
      currentRow = drawLines(graphics, terminalSize, contentColumn, currentRow, plan.impact());
      drawString(graphics, terminalSize, sectionColumn, currentRow++, "");
      graphics.setForegroundColor(TextColor.ANSI.CYAN);
      drawString(
          graphics,
          terminalSize,
          sectionColumn,
          currentRow++,
          messageSource.getMessage("tui.plan.label.risks"));
      graphics.setForegroundColor(TextColor.ANSI.DEFAULT);
      if (plan.risks().isEmpty()) {
        currentRow =
            drawLines(
                graphics,
                terminalSize,
                contentColumn,
                currentRow,
                messageSource.getMessage("tui.plan.label.no_risks"));
      } else {
        for (final String planRisk : plan.risks()) {
          currentRow =
              drawLines(graphics, terminalSize, contentColumn, currentRow, "- " + planRisk);
        }
      }
      drawString(graphics, terminalSize, sectionColumn, currentRow++, "");
    } else {
      currentRow =
          drawLines(
              graphics,
              terminalSize,
              contentColumn,
              currentRow,
              messageSource.getMessage("tui.plan.no_plan_available"));
      drawString(graphics, terminalSize, sectionColumn, currentRow++, "");
    }
    drawString(
        graphics,
        terminalSize,
        sectionColumn,
        currentRow++,
        messageSource.getMessage("tui.plan.actions"));
    graphics.setForegroundColor(TextColor.ANSI.GREEN);
    drawString(
        graphics,
        terminalSize,
        contentColumn,
        currentRow++,
        messageSource.getMessage(TuiMessageSource.PLAN_APPROVE));
    graphics.setForegroundColor(TextColor.ANSI.YELLOW);
    drawString(
        graphics,
        terminalSize,
        contentColumn,
        currentRow++,
        messageSource.getMessage(TuiMessageSource.PLAN_EDIT));
    graphics.setForegroundColor(TextColor.ANSI.RED);
    drawString(
        graphics,
        terminalSize,
        contentColumn,
        currentRow,
        messageSource.getMessage(TuiMessageSource.PLAN_QUIT));
    graphics.setForegroundColor(TextColor.ANSI.DEFAULT);
  }

  private int drawLines(
      final TextGraphics graphics,
      final TerminalSize terminalSize,
      final int column,
      final int row,
      final String text) {
    if (text == null) {
      return row;
    }
    int currentRow = row;
    final String[] textLines = text.split("\\R", -1);
    for (final String textLine : textLines) {
      drawString(graphics, terminalSize, column, currentRow, textLine);
      currentRow++;
    }
    return currentRow;
  }

  private void drawString(
      final TextGraphics graphics,
      final TerminalSize terminalSize,
      final int column,
      final int row,
      final String text) {
    if (text == null) {
      return;
    }
    if (row < 0
        || row >= terminalSize.getRows()
        || column < 0
        || column >= terminalSize.getColumns()) {
      return;
    }
    final int maxWidth = terminalSize.getColumns() - column;
    if (maxWidth <= 0) {
      return;
    }
    String clippedText = text;
    if (clippedText.length() > maxWidth) {
      clippedText = clippedText.substring(0, maxWidth);
    }
    graphics.putString(column, row, clippedText);
  }
}
