package com.craftsmanbro.fulcraft.ui.tui;

import com.craftsmanbro.fulcraft.ui.tui.i18n.TuiMessageSource;
import com.craftsmanbro.fulcraft.ui.tui.state.State;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import java.io.IOException;
import java.util.List;

public final class MainMenuScreen implements Screen {

  private static final List<MenuEntry> MENU_ENTRIES =
      List.of(
          new MenuEntry('1', State.HOME),
          new MenuEntry('2', State.CHAT_INPUT),
          new MenuEntry('3', State.PLAN_REVIEW),
          new MenuEntry('4', State.CONFLICT_POLICY),
          new MenuEntry('5', State.EXECUTION_RUNNING),
          new MenuEntry('6', State.ISSUE_HANDLING),
          new MenuEntry('7', State.SUMMARY));

  private final TuiMessageSource msg = TuiMessageSource.getDefault();

  @Override
  public State getState() {
    return State.HOME;
  }

  @Override
  public void draw(
      final TuiApplication app,
      final TextGraphics tg,
      final TerminalSize size,
      final int startRow) {
    draw(tg, startRow);
  }

  @Override
  public State handleInput(final TuiApplication app, final KeyStroke keyStroke) throws IOException {
    app.handleDefaultInput(keyStroke);
    return app.getStateMachine().getCurrentState();
  }

  public void draw(final TextGraphics tg, final int startRow) {
    final int menuColumn = 2;
    int currentRow = startRow;
    tg.putString(menuColumn, currentRow++, msg.getMessage(TuiMessageSource.HOME_WELCOME));
    tg.putString(menuColumn, currentRow++, "");
    tg.putString(menuColumn, currentRow++, msg.getMessage(TuiMessageSource.HOME_MENU_HINT));
    for (final MenuEntry menuEntry : MENU_ENTRIES) {
      tg.putString(
          menuColumn,
          currentRow++,
          "  " + menuEntry.key() + ": " + menuEntry.state().getDisplayName());
    }
  }

  private record MenuEntry(char key, State state) {}
}
