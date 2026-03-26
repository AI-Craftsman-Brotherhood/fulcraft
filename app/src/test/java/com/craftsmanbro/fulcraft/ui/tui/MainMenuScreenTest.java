package com.craftsmanbro.fulcraft.ui.tui;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.ui.tui.i18n.TuiMessageSource;
import com.craftsmanbro.fulcraft.ui.tui.state.State;
import java.util.List;
import org.junit.jupiter.api.Test;

class MainMenuScreenTest {

  @Test
  void drawOutputsMenuEntries() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    MainMenuScreen screen = new MainMenuScreen();

    screen.draw(capture.textGraphics(), 1);

    List<String> lines = capture.calls().stream().map(TuiTestSupport.PutStringCall::text).toList();
    TuiMessageSource msg = TuiMessageSource.getDefault();

    assertThat(lines).contains(msg.getMessage(TuiMessageSource.HOME_WELCOME));
    assertThat(lines).contains(msg.getMessage(TuiMessageSource.HOME_MENU_HINT));
    assertThat(lines).anyMatch(line -> line.contains("1: " + State.HOME.getDisplayName()));
    assertThat(lines).anyMatch(line -> line.contains("7: " + State.SUMMARY.getDisplayName()));
  }
}
