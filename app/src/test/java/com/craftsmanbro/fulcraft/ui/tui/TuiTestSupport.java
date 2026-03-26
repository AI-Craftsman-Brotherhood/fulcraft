package com.craftsmanbro.fulcraft.ui.tui;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.graphics.TextGraphics;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.mockito.Mockito;

final class TuiTestSupport {

  private TuiTestSupport() {}

  static TextGraphicsCapture captureTextGraphics() {
    TextGraphics textGraphics = mock(TextGraphics.class);
    List<PutStringCall> calls = new ArrayList<>();
    Mockito.doAnswer(
            invocation -> {
              int column = invocation.getArgument(0);
              int row = invocation.getArgument(1);
              String text = invocation.getArgument(2);
              calls.add(new PutStringCall(column, row, text));
              return null;
            })
        .when(textGraphics)
        .putString(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyString());
    return new TextGraphicsCapture(textGraphics, calls);
  }

  static void injectScreen(TuiApplication app, TextGraphics textGraphics, TerminalSize size)
      throws ReflectiveOperationException {
    com.googlecode.lanterna.screen.Screen screen =
        mock(com.googlecode.lanterna.screen.Screen.class);
    when(screen.newTextGraphics()).thenReturn(textGraphics);
    when(screen.getTerminalSize()).thenReturn(size);
    setField(app, "lanternaScreen", screen);
  }

  private static void setField(Object target, String fieldName, Object value)
      throws ReflectiveOperationException {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }

  record PutStringCall(int column, int row, String text) {}

  record TextGraphicsCapture(TextGraphics textGraphics, List<PutStringCall> calls) {}
}
