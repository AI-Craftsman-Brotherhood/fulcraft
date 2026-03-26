package com.craftsmanbro.fulcraft.ui.tui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.craftsmanbro.fulcraft.ui.tui.config.ConfigEditor;
import com.craftsmanbro.fulcraft.ui.tui.config.ConfigValidationService;
import com.craftsmanbro.fulcraft.ui.tui.config.MetadataRegistry;
import com.craftsmanbro.fulcraft.ui.tui.controller.ConfigController;
import com.craftsmanbro.fulcraft.ui.tui.i18n.TuiMessageSource;
import com.craftsmanbro.fulcraft.ui.tui.state.State;
import com.craftsmanbro.fulcraft.ui.tui.state.StateMachine;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigEditorScreenTest {
  private static final TuiMessageSource MSG = TuiMessageSource.getDefault();

  @Test
  void constructorRejectsNullState() {
    assertThatThrownBy(() -> new ConfigEditorScreen(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("state must not be null");
  }

  @Test
  void getStateReturnsConfiguredState() {
    ConfigEditorScreen screen = new ConfigEditorScreen(State.CONFIG_EDIT);

    assertThat(screen.getState()).isEqualTo(State.CONFIG_EDIT);
  }

  @Test
  void drawShowsControllerNotInitializedWhenControllerMissing() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    TuiApplication app = mock(TuiApplication.class);
    when(app.getConfigController()).thenReturn(null);
    ConfigEditorScreen screen = new ConfigEditorScreen(State.CONFIG_CATEGORY);

    screen.draw(app, capture.textGraphics(), new TerminalSize(80, 20), 0);

    assertThat(lines(capture)).contains("Config controller not initialized");
  }

  @Test
  void drawShowsUnsupportedMessageForNonConfigState() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    TuiApplication app = mock(TuiApplication.class);
    when(app.getConfigController()).thenReturn(mock(ConfigController.class));
    ConfigEditorScreen screen = new ConfigEditorScreen(State.HOME);

    screen.draw(app, capture.textGraphics(), new TerminalSize(80, 20), 0);

    assertThat(lines(capture)).contains("Unsupported config screen: HOME");
  }

  @Test
  void handleInputFallsBackToCurrentStateWhenInputHandlerMissing() throws IOException {
    TuiApplication app = mock(TuiApplication.class);
    StateMachine stateMachine = new StateMachine(State.CHAT_INPUT);
    when(app.getTuiInputHandler()).thenReturn(null);
    when(app.getStateMachine()).thenReturn(stateMachine);
    ConfigEditorScreen screen = new ConfigEditorScreen(State.CONFIG_CATEGORY);

    State next = screen.handleInput(app, new KeyStroke(KeyType.Enter));

    assertThat(next).isEqualTo(State.CHAT_INPUT);
  }

  @Test
  void handleInputDelegatesCategoryStateToInputHandler() throws IOException {
    TuiInputHandler inputHandler = mock(TuiInputHandler.class);
    TuiApplication app = mock(TuiApplication.class);
    StateMachine stateMachine = new StateMachine(State.CONFIG_CATEGORY);
    when(app.getTuiInputHandler()).thenReturn(inputHandler);
    when(app.getStateMachine()).thenReturn(stateMachine);
    ConfigEditorScreen screen = new ConfigEditorScreen(State.CONFIG_CATEGORY);
    KeyStroke keyStroke = new KeyStroke(KeyType.Enter);

    State next = screen.handleInput(app, keyStroke);

    verify(inputHandler).handleConfigCategoryInput(keyStroke);
    assertThat(next).isEqualTo(State.CONFIG_CATEGORY);
  }

  @Test
  void handleInputDelegatesItemsStateToInputHandler() throws IOException {
    TuiInputHandler inputHandler = mock(TuiInputHandler.class);
    TuiApplication app = mock(TuiApplication.class);
    StateMachine stateMachine = new StateMachine(State.CONFIG_ITEMS);
    when(app.getTuiInputHandler()).thenReturn(inputHandler);
    when(app.getStateMachine()).thenReturn(stateMachine);
    ConfigEditorScreen screen = new ConfigEditorScreen(State.CONFIG_ITEMS);
    KeyStroke keyStroke = new KeyStroke(KeyType.Enter);

    State next = screen.handleInput(app, keyStroke);

    verify(inputHandler).handleConfigItemsInput(keyStroke);
    assertThat(next).isEqualTo(State.CONFIG_ITEMS);
  }

  @Test
  void handleInputDelegatesEditStateToInputHandler() throws IOException {
    TuiInputHandler inputHandler = mock(TuiInputHandler.class);
    TuiApplication app = mock(TuiApplication.class);
    StateMachine stateMachine = new StateMachine(State.CONFIG_EDIT);
    when(app.getTuiInputHandler()).thenReturn(inputHandler);
    when(app.getStateMachine()).thenReturn(stateMachine);
    ConfigEditorScreen screen = new ConfigEditorScreen(State.CONFIG_EDIT);
    KeyStroke keyStroke = new KeyStroke(KeyType.Enter);

    State next = screen.handleInput(app, keyStroke);

    verify(inputHandler).handleConfigEditInput(keyStroke);
    assertThat(next).isEqualTo(State.CONFIG_EDIT);
  }

  @Test
  void handleInputDelegatesValidationStateToInputHandler() throws IOException {
    TuiInputHandler inputHandler = mock(TuiInputHandler.class);
    TuiApplication app = mock(TuiApplication.class);
    StateMachine stateMachine = new StateMachine(State.CONFIG_VALIDATE);
    when(app.getTuiInputHandler()).thenReturn(inputHandler);
    when(app.getStateMachine()).thenReturn(stateMachine);
    ConfigEditorScreen screen = new ConfigEditorScreen(State.CONFIG_VALIDATE);
    KeyStroke keyStroke = new KeyStroke(KeyType.Enter);

    State next = screen.handleInput(app, keyStroke);

    verify(inputHandler).handleConfigValidationInput(keyStroke);
    assertThat(next).isEqualTo(State.CONFIG_VALIDATE);
  }

  @Test
  void handleInputSkipsDelegationForUnsupportedState() throws IOException {
    TuiInputHandler inputHandler = mock(TuiInputHandler.class);
    TuiApplication app = mock(TuiApplication.class);
    StateMachine stateMachine = new StateMachine(State.HOME);
    when(app.getTuiInputHandler()).thenReturn(inputHandler);
    when(app.getStateMachine()).thenReturn(stateMachine);
    ConfigEditorScreen screen = new ConfigEditorScreen(State.HOME);

    State next = screen.handleInput(app, new KeyStroke(KeyType.Enter));

    verifyNoInteractions(inputHandler);
    assertThat(next).isEqualTo(State.HOME);
  }

  @Test
  void drawCategoryShowsMissingEditorMessage() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    ConfigEditorScreen screen = new ConfigEditorScreen();
    ConfigController controller =
        new ConfigController(
            new StateMachine(State.CHAT_INPUT),
            MetadataRegistry.getDefault(),
            new ConfigValidationService());

    screen.drawCategory(capture.textGraphics(), 0, new TerminalSize(80, 20), controller);

    String expected =
        TuiMessageSource.getDefault().getMessage(TuiMessageSource.CONFIG_EDITOR_NOT_INIT);
    List<String> lines = capture.calls().stream().map(TuiTestSupport.PutStringCall::text).toList();
    assertThat(lines).contains(expected);
  }

  @Test
  void drawCategoryHighlightsErrorStatusAndShowsExitConfirmation() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    ConfigEditorScreen screen = new ConfigEditorScreen();
    ConfigEditor editor = mock(ConfigEditor.class);
    ConfigController controller = mock(ConfigController.class);
    when(controller.getStatusMessage()).thenReturn("invalid category");
    when(controller.getConfigEditor()).thenReturn(editor);
    when(controller.getCategories()).thenReturn(List.of("project"));
    when(controller.formatCategorySummary(eq("project"), anyInt())).thenReturn(" (1 keys)");
    when(controller.isExitConfirmation()).thenReturn(true);
    when(editor.isDirty()).thenReturn(false);

    screen.drawCategory(capture.textGraphics(), 0, new TerminalSize(60, 20), controller);

    verify(capture.textGraphics()).setForegroundColor(TextColor.ANSI.RED);
    verify(capture.textGraphics()).setForegroundColor(TextColor.ANSI.DEFAULT);
    assertThat(lines(capture)).contains(MSG.getMessage(TuiMessageSource.CONFIG_EXIT_CONFIRM));
    assertThat(lines(capture))
        .doesNotContain(MSG.getMessage(TuiMessageSource.CONFIG_ENTER_CATEGORY));
  }

  @Test
  void drawCategoryMarksLocalizedErrorMessagesAsErrors() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    ConfigEditorScreen screen = new ConfigEditorScreen();
    ConfigEditor editor = mock(ConfigEditor.class);
    ConfigController controller = mock(ConfigController.class);
    when(controller.getStatusMessage())
        .thenReturn(MSG.getMessage(TuiMessageSource.CONFIG_ERROR_NO_PATH));
    when(controller.getConfigEditor()).thenReturn(editor);
    when(controller.getCategories()).thenReturn(List.of());
    when(controller.isExitConfirmation()).thenReturn(false);
    when(editor.isDirty()).thenReturn(false);

    screen.drawCategory(capture.textGraphics(), 0, new TerminalSize(60, 20), controller);

    verify(capture.textGraphics()).setForegroundColor(TextColor.ANSI.RED);
  }

  @Test
  void drawCategoryKeepsNormalStatusUncolored() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    ConfigEditorScreen screen = new ConfigEditorScreen();
    ConfigEditor editor = mock(ConfigEditor.class);
    ConfigController controller = mock(ConfigController.class);
    when(controller.getStatusMessage()).thenReturn("Saved configuration.");
    when(controller.getConfigEditor()).thenReturn(editor);
    when(controller.getCategories()).thenReturn(List.of());
    when(controller.isExitConfirmation()).thenReturn(false);
    when(editor.isDirty()).thenReturn(false);

    screen.drawCategory(capture.textGraphics(), 0, new TerminalSize(60, 20), controller);

    verify(capture.textGraphics(), never()).setForegroundColor(TextColor.ANSI.RED);
  }

  @Test
  void drawCategoryShowsDirtyKeysWithLimitAndTruncation() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    ConfigEditorScreen screen = new ConfigEditorScreen();
    ConfigEditor editor = mock(ConfigEditor.class);
    ConfigController controller = mock(ConfigController.class);
    when(controller.getStatusMessage()).thenReturn("");
    when(controller.getConfigEditor()).thenReturn(editor);
    when(controller.getCategories()).thenReturn(List.of());
    when(controller.isExitConfirmation()).thenReturn(false);
    when(editor.isDirty()).thenReturn(true);
    when(editor.getDirtyKeys())
        .thenReturn(List.of("abcdefghijklmnopqrstuvwxyz", "k2", "k3", "k4", "k5", "k6"));

    screen.drawCategory(capture.textGraphics(), 0, new TerminalSize(16, 20), controller);

    assertThat(lines(capture)).contains(MSG.getMessage(TuiMessageSource.CONFIG_CHANGED_KEYS));
    assertThat(lines(capture)).contains("- abcdefg...");
    assertThat(lines(capture)).contains("... and 1 more");
  }

  @Test
  void drawItemsShowsEmptyEntriesAndBufferedNumberPrompt() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    ConfigEditorScreen screen = new ConfigEditorScreen(State.CONFIG_ITEMS);
    ConfigEditor editor = mock(ConfigEditor.class);
    ConfigController controller = mock(ConfigController.class);
    when(controller.getConfigEditor()).thenReturn(editor);
    when(controller.getStatusMessage()).thenReturn("");
    when(controller.isExitConfirmation()).thenReturn(true);
    when(controller.getPathLabel()).thenReturn("llm");
    when(controller.getCurrentItems()).thenReturn(List.of());
    when(controller.getNumberBuffer()).thenReturn(bufferOf("12"));
    when(editor.isDirty()).thenReturn(false);

    screen.drawItems(capture.textGraphics(), 0, new TerminalSize(80, 20), controller);

    assertThat(lines(capture)).contains(MSG.getMessage(TuiMessageSource.CONFIG_NO_ENTRIES));
    assertThat(lines(capture))
        .contains(MSG.getMessage(TuiMessageSource.CONFIG_SELECT_NUMBER) + " 12");
    assertThat(lines(capture)).contains(MSG.getMessage(TuiMessageSource.CONFIG_EXIT_CONFIRM));
    assertThat(lines(capture)).doesNotContain(MSG.getMessage(TuiMessageSource.CONFIG_ENTER_ITEM));
  }

  @Test
  void drawItemsShowsEntriesAndDirtyKeys() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    ConfigEditorScreen screen = new ConfigEditorScreen(State.CONFIG_ITEMS);
    ConfigEditor editor = mock(ConfigEditor.class);
    ConfigController controller = mock(ConfigController.class);
    List<ConfigEditor.PathSegment> itemPath =
        List.of(ConfigEditor.PathSegment.key("llm"), ConfigEditor.PathSegment.key("model_name"));
    ConfigEditor.ConfigItem item = new ConfigEditor.ConfigItem("model_name", "gemini", itemPath);
    when(controller.getConfigEditor()).thenReturn(editor);
    when(controller.getStatusMessage()).thenReturn("");
    when(controller.isExitConfirmation()).thenReturn(false);
    when(controller.getPathLabel()).thenReturn("llm");
    when(controller.getCurrentItems()).thenReturn(List.of(item));
    when(controller.getNumberBuffer()).thenReturn(bufferOf(""));
    when(editor.summarizeValue(eq(itemPath), eq("gemini"), anyInt())).thenReturn("\"gemini\"");
    when(editor.isDirty()).thenReturn(true);
    when(editor.getDirtyKeys()).thenReturn(List.of("llm.model_name"));

    screen.drawItems(capture.textGraphics(), 0, new TerminalSize(80, 20), controller);

    assertThat(lines(capture)).contains("1. model_name = \"gemini\"");
    assertThat(lines(capture)).contains(MSG.getMessage(TuiMessageSource.CONFIG_CHANGED_KEYS));
    assertThat(lines(capture)).contains(MSG.getMessage(TuiMessageSource.CONFIG_ENTER_ITEM));
  }

  @Test
  void drawEditShowsNoSelectionWhenEditorMissing() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    ConfigEditorScreen screen = new ConfigEditorScreen(State.CONFIG_EDIT);
    ConfigController controller = mock(ConfigController.class);
    when(controller.getConfigEditor()).thenReturn(null);
    when(controller.getStatusMessage()).thenReturn("");
    when(controller.isExitConfirmation()).thenReturn(false);
    when(controller.getEditPath()).thenReturn(List.of());
    when(controller.getEditMode()).thenReturn(ConfigController.EditMode.SCALAR);
    when(controller.getEditValueType()).thenReturn(ConfigEditor.ValueType.STRING);
    when(controller.getEnumOptions()).thenReturn(List.of());
    when(controller.getNumberBuffer()).thenReturn(bufferOf(""));
    when(controller.getValueBuffer()).thenReturn(bufferOf(""));
    when(controller.getListEditIndex()).thenReturn(-1);
    when(controller.getCurrentList()).thenReturn(List.of());

    screen.drawEdit(capture.textGraphics(), 0, new TerminalSize(80, 20), controller);

    assertThat(lines(capture)).contains(MSG.getMessage(TuiMessageSource.CONFIG_NO_SELECTION));
  }

  @Test
  void drawEditBooleanShowsCurrentValueAndExitConfirmation() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    ConfigEditorScreen screen = new ConfigEditorScreen(State.CONFIG_EDIT);
    ConfigController controller = mockEditController(ConfigController.EditMode.BOOLEAN);
    ConfigEditor editor = controller.getConfigEditor();
    when(controller.isExitConfirmation()).thenReturn(true);
    when(editor.getValue(anyList())).thenReturn(true);

    screen.drawEdit(capture.textGraphics(), 0, new TerminalSize(80, 20), controller);

    assertThat(lines(capture)).contains(MSG.getMessage(TuiMessageSource.CONFIG_TYPE) + " boolean");
    assertThat(lines(capture)).contains(MSG.getMessage(TuiMessageSource.CONFIG_CURRENT) + " true");
    assertThat(lines(capture)).contains(MSG.getMessage(TuiMessageSource.CONFIG_SPACE_TOGGLE));
    assertThat(lines(capture)).contains(MSG.getMessage(TuiMessageSource.CONFIG_EXIT_CONFIRM));
  }

  @Test
  void drawEditEnumMarksSelectedOptionIgnoringCase() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    ConfigEditorScreen screen = new ConfigEditorScreen(State.CONFIG_EDIT);
    ConfigController controller = mockEditController(ConfigController.EditMode.ENUM);
    ConfigEditor editor = controller.getConfigEditor();
    when(controller.getEnumOptions()).thenReturn(List.of("safe", "fast"));
    when(controller.getNumberBuffer()).thenReturn(bufferOf("2"));
    when(editor.getValue(anyList())).thenReturn("FAST");

    screen.drawEdit(capture.textGraphics(), 0, new TerminalSize(80, 20), controller);

    assertThat(lines(capture)).contains("2. * fast");
    assertThat(lines(capture))
        .contains(MSG.getMessage(TuiMessageSource.CONFIG_SELECT_NUMBER) + " 2");
  }

  @Test
  void drawEditListShowsItemsAndActions() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    ConfigEditorScreen screen = new ConfigEditorScreen(State.CONFIG_EDIT);
    ConfigController controller = mockEditController(ConfigController.EditMode.LIST);
    ConfigEditor editor = controller.getConfigEditor();
    when(controller.getCurrentList()).thenReturn(List.of("first", "second"));
    when(controller.getNumberBuffer()).thenReturn(bufferOf("1"));
    when(editor.summarizeValue(anyList(), any(), anyInt())).thenReturn("\"entry\"");

    screen.drawEdit(capture.textGraphics(), 0, new TerminalSize(80, 20), controller);

    assertThat(lines(capture)).contains("1. \"entry\"");
    assertThat(lines(capture)).contains("2. \"entry\"");
    assertThat(lines(capture)).contains("A: add, D: delete, Enter: edit item");
  }

  @Test
  void drawEditListItemShowsIndexedPathAndEditPrompt() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    ConfigEditorScreen screen = new ConfigEditorScreen(State.CONFIG_EDIT);
    ConfigController controller = mockEditController(ConfigController.EditMode.LIST_ITEM);
    when(controller.getListEditIndex()).thenReturn(2);
    when(controller.getValueBuffer()).thenReturn(bufferOf("abc"));

    screen.drawEdit(capture.textGraphics(), 0, new TerminalSize(80, 20), controller);

    assertThat(lines(capture)).contains(MSG.getMessage(TuiMessageSource.CONFIG_PATH) + " llm[2]");
    assertThat(lines(capture)).contains("Edit item");
    assertThat(lines(capture)).contains(MSG.getMessage(TuiMessageSource.CONFIG_INPUT) + " abc_");
  }

  @Test
  void drawEditListAddShowsAddPrompt() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    ConfigEditorScreen screen = new ConfigEditorScreen(State.CONFIG_EDIT);
    ConfigController controller = mockEditController(ConfigController.EditMode.LIST_ADD);

    screen.drawEdit(capture.textGraphics(), 0, new TerminalSize(80, 20), controller);

    assertThat(lines(capture)).contains("Add item");
    assertThat(lines(capture)).contains(MSG.getMessage(TuiMessageSource.CONFIG_ENTER_APPLY_BACK));
  }

  @Test
  void drawEditScalarFallsBackToGenericTypeLabelWhenNull() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    ConfigEditorScreen screen = new ConfigEditorScreen(State.CONFIG_EDIT);
    ConfigController controller = mockEditController(ConfigController.EditMode.SCALAR);
    when(controller.getEditValueType()).thenReturn(null);
    when(controller.getValueBuffer()).thenReturn(bufferOf("v"));

    screen.drawEdit(capture.textGraphics(), 0, new TerminalSize(80, 20), controller);

    assertThat(lines(capture)).contains(MSG.getMessage(TuiMessageSource.CONFIG_TYPE) + " value");
    assertThat(lines(capture)).contains(MSG.getMessage(TuiMessageSource.CONFIG_INPUT) + " v_");
  }

  @Test
  void drawEditShowsUnsupportedMessageWhenEditModeMissing() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    ConfigEditorScreen screen = new ConfigEditorScreen(State.CONFIG_EDIT);
    ConfigController controller = mockEditController(null);

    screen.drawEdit(capture.textGraphics(), 0, new TerminalSize(80, 20), controller);

    assertThat(lines(capture)).contains(MSG.getMessage(TuiMessageSource.CONFIG_UNSUPPORTED_MODE));
  }

  @Test
  void drawValidationShowsNoErrorsMessage() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    ConfigEditorScreen screen = new ConfigEditorScreen();
    ConfigController controller =
        new ConfigController(
            new StateMachine(State.CHAT_INPUT),
            MetadataRegistry.getDefault(),
            new ConfigValidationService());

    screen.drawValidation(capture.textGraphics(), 0, new TerminalSize(80, 20), controller);

    String expected =
        TuiMessageSource.getDefault().getMessage(TuiMessageSource.CONFIG_NO_VALIDATION_ERRORS);
    List<String> lines = capture.calls().stream().map(TuiTestSupport.PutStringCall::text).toList();
    assertThat(lines).contains(expected);
  }

  @Test
  void drawValidationShowsNoErrorsMessageWhenIssuesAreNull() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    ConfigEditorScreen screen = new ConfigEditorScreen();

    ConfigController controller =
        new ConfigController(
            new StateMachine(State.CHAT_INPUT),
            MetadataRegistry.getDefault(),
            new ConfigValidationService()) {
          @Override
          public List<ConfigValidationService.ValidationIssue> getValidationIssues() {
            return null;
          }
        };

    screen.drawValidation(capture.textGraphics(), 0, new TerminalSize(80, 20), controller);

    assertThat(lines(capture))
        .contains(MSG.getMessage(TuiMessageSource.CONFIG_NO_VALIDATION_ERRORS));
    assertThat(lines(capture)).contains("Press B/Q to return.");
  }

  @Test
  void drawValidationFormatsIssuesWithIndexes() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    ConfigEditorScreen screen = new ConfigEditorScreen();
    List<ConfigValidationService.ValidationIssue> issues =
        List.of(new ConfigValidationService.ValidationIssue("path.key", "message"));

    ConfigController controller =
        new ConfigController(
            new StateMachine(State.CHAT_INPUT),
            MetadataRegistry.getDefault(),
            new ConfigValidationService()) {
          @Override
          public List<ConfigValidationService.ValidationIssue> getValidationIssues() {
            return issues;
          }
        };

    screen.drawValidation(capture.textGraphics(), 0, new TerminalSize(40, 10), controller);

    assertThat(lines(capture)).anyMatch(line -> line.startsWith("1. path.key"));
  }

  @Test
  void drawValidationTruncatesLongLinesAndShowsRemainingCount() {
    TuiTestSupport.TextGraphicsCapture capture = TuiTestSupport.captureTextGraphics();
    ConfigEditorScreen screen = new ConfigEditorScreen(State.CONFIG_VALIDATE);
    String longMessage = "message-that-is-long-enough-to-be-truncated";
    List<ConfigValidationService.ValidationIssue> issues =
        List.of(
            new ConfigValidationService.ValidationIssue("", null),
            new ConfigValidationService.ValidationIssue("path.alpha", longMessage),
            new ConfigValidationService.ValidationIssue("path.beta", "tail"));

    ConfigController controller = mock(ConfigController.class);
    when(controller.getStatusMessage()).thenReturn("");
    when(controller.getValidationIssues()).thenReturn(issues);
    when(controller.getNumberBuffer()).thenReturn(bufferOf("2"));

    screen.drawValidation(capture.textGraphics(), 0, new TerminalSize(20, 8), controller);

    assertThat(lines(capture)).contains(MSG.getMessage(TuiMessageSource.CONFIG_ERRORS_LABEL));
    assertThat(lines(capture)).anyMatch(line -> line.startsWith("1. (general"));
    assertThat(lines(capture)).anyMatch(line -> line.endsWith("..."));
    assertThat(lines(capture)).contains("... and 1 more");
    assertThat(lines(capture)).contains(MSG.getMessage(TuiMessageSource.CONFIG_JUMP_ERROR));
  }

  private static List<String> lines(TuiTestSupport.TextGraphicsCapture capture) {
    return capture.calls().stream().map(TuiTestSupport.PutStringCall::text).toList();
  }

  private static MutableTextBuffer bufferOf(final String value) {
    final MutableTextBuffer buffer = new MutableTextBuffer();
    if (value != null && !value.isEmpty()) {
      buffer.append(value);
    }
    return buffer;
  }

  private static ConfigController mockEditController(ConfigController.EditMode editMode) {
    ConfigController controller = mock(ConfigController.class);
    ConfigEditor editor = mock(ConfigEditor.class);
    List<ConfigEditor.PathSegment> path = List.of(ConfigEditor.PathSegment.key("llm"));
    when(controller.getConfigEditor()).thenReturn(editor);
    when(controller.getStatusMessage()).thenReturn("");
    when(controller.isExitConfirmation()).thenReturn(false);
    when(controller.getEditPath()).thenReturn(path);
    when(controller.getEditMode()).thenReturn(editMode);
    when(controller.getEditValueType()).thenReturn(ConfigEditor.ValueType.STRING);
    when(controller.getEnumOptions()).thenReturn(List.of("default"));
    when(controller.getNumberBuffer()).thenReturn(bufferOf(""));
    when(controller.getValueBuffer()).thenReturn(bufferOf(""));
    when(controller.getListEditIndex()).thenReturn(-1);
    when(controller.getCurrentList()).thenReturn(List.of());
    when(editor.formatPathForDisplay(path)).thenReturn("llm");
    when(editor.isDirty()).thenReturn(false);
    return controller;
  }
}
