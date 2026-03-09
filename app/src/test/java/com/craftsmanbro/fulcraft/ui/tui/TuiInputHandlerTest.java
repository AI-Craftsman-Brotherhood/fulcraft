package com.craftsmanbro.fulcraft.ui.tui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.craftsmanbro.fulcraft.ui.tui.config.ConfigValidationService;
import com.craftsmanbro.fulcraft.ui.tui.controller.ConfigController;
import com.craftsmanbro.fulcraft.ui.tui.controller.ConfigController.EditMode;
import com.craftsmanbro.fulcraft.ui.tui.i18n.TuiMessageSource;
import com.craftsmanbro.fulcraft.ui.tui.state.State;
import com.craftsmanbro.fulcraft.ui.tui.state.StateMachine;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TuiInputHandlerTest {

  @Test
  void handleConfigCategoryInput_withNullControllerDoesNothing() throws IOException {
    StateMachine stateMachine = new StateMachine(State.CONFIG_CATEGORY);
    AtomicInteger redraws = new AtomicInteger();
    TuiInputHandler handler = new TuiInputHandler(stateMachine, null, redraws::incrementAndGet);

    handler.handleConfigCategoryInput(new KeyStroke(KeyType.Enter));

    assertThat(redraws.get()).isZero();
  }

  @Test
  void handleConfigCategoryEnterWithoutSelectionShowsHint() throws IOException {
    ConfigController controller = mock(ConfigController.class);
    when(controller.consumeNumber()).thenReturn(null);

    StateMachine stateMachine = new StateMachine();
    stateMachine.transitionTo(State.CONFIG_CATEGORY);
    AtomicInteger redraws = new AtomicInteger();

    TuiInputHandler handler =
        new TuiInputHandler(stateMachine, controller, redraws::incrementAndGet);

    handler.handleConfigCategoryInput(new KeyStroke(KeyType.Enter));

    TuiMessageSource messageSource = TuiMessageSource.getDefault();
    verify(controller)
        .setStatusMessage(messageSource.getMessage(TuiMessageSource.INPUT_HINT_ENTER_CATEGORY));
    assertThat(redraws.get()).isEqualTo(1);
  }

  @Test
  void handleConfigCategoryEnterWithSelectionSelectsCategory() throws IOException {
    ConfigController controller = mock(ConfigController.class);
    when(controller.consumeNumber()).thenReturn(2);

    StateMachine stateMachine = new StateMachine(State.CONFIG_CATEGORY);
    AtomicInteger redraws = new AtomicInteger();
    TuiInputHandler handler =
        new TuiInputHandler(stateMachine, controller, redraws::incrementAndGet);

    handler.handleConfigCategoryInput(new KeyStroke(KeyType.Enter));

    verify(controller).selectCategory(2);
    assertThat(redraws.get()).isEqualTo(1);
  }

  @Test
  void handleConfigEditBooleanTogglesOnSpace() throws IOException {
    ConfigController controller = mock(ConfigController.class);
    when(controller.getEditMode()).thenReturn(EditMode.BOOLEAN);

    StateMachine stateMachine = new StateMachine();
    stateMachine.transitionTo(State.CONFIG_EDIT);
    AtomicInteger redraws = new AtomicInteger();

    TuiInputHandler handler =
        new TuiInputHandler(stateMachine, controller, redraws::incrementAndGet);

    handler.handleConfigEditInput(new KeyStroke(' ', false, false));

    verify(controller).toggleBoolean();
    assertThat(redraws.get()).isEqualTo(1);
  }

  @Test
  void handleConfigEditBooleanSaveSkipsSaveWhenValidationFails() throws IOException {
    ConfigController controller = mock(ConfigController.class);
    when(controller.getEditMode()).thenReturn(EditMode.BOOLEAN);
    when(controller.runValidation(any(), anyString())).thenReturn(false);

    StateMachine stateMachine = new StateMachine(State.CONFIG_EDIT);
    AtomicInteger redraws = new AtomicInteger();
    TuiInputHandler handler =
        new TuiInputHandler(stateMachine, controller, redraws::incrementAndGet);

    handler.handleConfigEditInput(characterKey('S'));

    verify(controller).runValidation(any(), anyString());
    verify(controller, never()).save();
    assertThat(redraws.get()).isEqualTo(1);
  }

  @Test
  void handleConfigEditBooleanQGoesBackFromEdit() throws IOException {
    ConfigController controller = mock(ConfigController.class);
    when(controller.getEditMode()).thenReturn(EditMode.BOOLEAN);

    StateMachine stateMachine = new StateMachine(State.CONFIG_EDIT);
    AtomicInteger redraws = new AtomicInteger();
    TuiInputHandler handler =
        new TuiInputHandler(stateMachine, controller, redraws::incrementAndGet);

    handler.handleConfigEditInput(characterKey('Q'));

    verify(controller).backFromEdit();
    assertThat(redraws.get()).isEqualTo(1);
  }

  @Test
  void handleConfigEditWithNullEditModeDoesNothing() throws IOException {
    ConfigController controller = mock(ConfigController.class);
    when(controller.getEditMode()).thenReturn(null);

    StateMachine stateMachine = new StateMachine(State.CONFIG_EDIT);
    AtomicInteger redraws = new AtomicInteger();
    TuiInputHandler handler =
        new TuiInputHandler(stateMachine, controller, redraws::incrementAndGet);

    handler.handleConfigEditInput(characterKey('A'));

    verify(controller).getEditMode();
    assertThat(redraws.get()).isZero();
  }

  @Test
  void handleConfigEditValidationHotkeyRunsValidationForListMode() throws IOException {
    ConfigController controller = mock(ConfigController.class);
    when(controller.getEditMode()).thenReturn(EditMode.LIST);

    StateMachine stateMachine = new StateMachine(State.CONFIG_EDIT);
    AtomicInteger redraws = new AtomicInteger();
    TuiInputHandler handler =
        new TuiInputHandler(stateMachine, controller, redraws::incrementAndGet);

    handler.handleConfigEditInput(characterKey('V'));

    verify(controller).runValidation(any(), anyString());
    assertThat(redraws.get()).isEqualTo(1);
  }

  @Test
  void handleConfigEditEnumEnterWithoutSelectionShowsHint() throws IOException {
    ConfigController controller = mock(ConfigController.class);
    when(controller.getEditMode()).thenReturn(EditMode.ENUM);
    when(controller.consumeNumber()).thenReturn(null);

    StateMachine stateMachine = new StateMachine(State.CONFIG_EDIT);
    AtomicInteger redraws = new AtomicInteger();
    TuiInputHandler handler =
        new TuiInputHandler(stateMachine, controller, redraws::incrementAndGet);

    handler.handleConfigEditInput(new KeyStroke(KeyType.Enter));

    verify(controller)
        .setStatusMessage(
            TuiMessageSource.getDefault().getMessage(TuiMessageSource.INPUT_HINT_ENTER_SELECTION));
    assertThat(redraws.get()).isEqualTo(1);
  }

  @Test
  void handleConfigEditEnumEnterWithSelectionAppliesEnumSelection() throws IOException {
    ConfigController controller = mock(ConfigController.class);
    when(controller.getEditMode()).thenReturn(EditMode.ENUM);
    when(controller.consumeNumber()).thenReturn(3);

    StateMachine stateMachine = new StateMachine(State.CONFIG_EDIT);
    AtomicInteger redraws = new AtomicInteger();
    TuiInputHandler handler =
        new TuiInputHandler(stateMachine, controller, redraws::incrementAndGet);

    handler.handleConfigEditInput(new KeyStroke(KeyType.Enter));

    verify(controller).applyEnumSelection(3);
    assertThat(redraws.get()).isEqualTo(1);
  }

  @Test
  void handleConfigEditEnumBackspaceTrimsNumber() throws IOException {
    ConfigController controller = mock(ConfigController.class);
    when(controller.getEditMode()).thenReturn(EditMode.ENUM);

    StateMachine stateMachine = new StateMachine(State.CONFIG_EDIT);
    AtomicInteger redraws = new AtomicInteger();
    TuiInputHandler handler =
        new TuiInputHandler(stateMachine, controller, redraws::incrementAndGet);

    handler.handleConfigEditInput(new KeyStroke(KeyType.Backspace));

    verify(controller).trimNumber();
    assertThat(redraws.get()).isEqualTo(1);
  }

  @Test
  void handleConfigEditEnumDigitAppendsNumber() throws IOException {
    ConfigController controller = mock(ConfigController.class);
    when(controller.getEditMode()).thenReturn(EditMode.ENUM);

    StateMachine stateMachine = new StateMachine(State.CONFIG_EDIT);
    AtomicInteger redraws = new AtomicInteger();
    TuiInputHandler handler =
        new TuiInputHandler(stateMachine, controller, redraws::incrementAndGet);

    handler.handleConfigEditInput(characterKey('8'));

    verify(controller).appendNumber('8');
    assertThat(redraws.get()).isEqualTo(1);
  }

  @Test
  void handleConfigEditScalarEnterAppliesInput() throws IOException {
    ConfigController controller = mock(ConfigController.class);
    when(controller.getEditMode()).thenReturn(EditMode.SCALAR);
    when(controller.getValueBuffer()).thenReturn(bufferOf(""));

    StateMachine stateMachine = new StateMachine(State.CONFIG_EDIT);
    AtomicInteger redraws = new AtomicInteger();
    TuiInputHandler handler =
        new TuiInputHandler(stateMachine, controller, redraws::incrementAndGet);

    handler.handleConfigEditInput(new KeyStroke(KeyType.Enter));

    verify(controller).applyScalarInput();
    assertThat(redraws.get()).isEqualTo(1);
  }

  @Test
  void handleConfigEditScalarBackspaceRemovesLastCharacter() throws IOException {
    ConfigController controller = mock(ConfigController.class);
    when(controller.getEditMode()).thenReturn(EditMode.SCALAR);
    MutableTextBuffer buffer = bufferOf("abc");
    when(controller.getValueBuffer()).thenReturn(buffer);

    StateMachine stateMachine = new StateMachine(State.CONFIG_EDIT);
    AtomicInteger redraws = new AtomicInteger();
    TuiInputHandler handler =
        new TuiInputHandler(stateMachine, controller, redraws::incrementAndGet);

    handler.handleConfigEditInput(new KeyStroke(KeyType.Backspace));

    assertThat(buffer.toString()).isEqualTo("ab");
    assertThat(redraws.get()).isEqualTo(1);
  }

  @Test
  void handleConfigEditScalarBackspaceWithEmptyBufferDoesNothing() throws IOException {
    ConfigController controller = mock(ConfigController.class);
    when(controller.getEditMode()).thenReturn(EditMode.SCALAR);
    when(controller.getValueBuffer()).thenReturn(bufferOf(""));

    StateMachine stateMachine = new StateMachine(State.CONFIG_EDIT);
    AtomicInteger redraws = new AtomicInteger();
    TuiInputHandler handler =
        new TuiInputHandler(stateMachine, controller, redraws::incrementAndGet);

    handler.handleConfigEditInput(new KeyStroke(KeyType.Backspace));

    assertThat(redraws.get()).isZero();
  }

  @Test
  void handleConfigEditScalarBWithEmptyBufferBacksFromEdit() throws IOException {
    ConfigController controller = mock(ConfigController.class);
    when(controller.getEditMode()).thenReturn(EditMode.SCALAR);
    MutableTextBuffer buffer = bufferOf("");
    when(controller.getValueBuffer()).thenReturn(buffer);

    StateMachine stateMachine = new StateMachine(State.CONFIG_EDIT);
    AtomicInteger redraws = new AtomicInteger();
    TuiInputHandler handler =
        new TuiInputHandler(stateMachine, controller, redraws::incrementAndGet);

    handler.handleConfigEditInput(characterKey('B'));

    verify(controller).backFromEdit();
    assertThat(buffer.toString()).isEmpty();
    assertThat(redraws.get()).isEqualTo(1);
  }

  @Test
  void handleConfigEditScalarCharacterAppendsToBuffer() throws IOException {
    ConfigController controller = mock(ConfigController.class);
    when(controller.getEditMode()).thenReturn(EditMode.SCALAR);
    MutableTextBuffer buffer = bufferOf("");
    when(controller.getValueBuffer()).thenReturn(buffer);

    StateMachine stateMachine = new StateMachine(State.CONFIG_EDIT);
    AtomicInteger redraws = new AtomicInteger();
    TuiInputHandler handler =
        new TuiInputHandler(stateMachine, controller, redraws::incrementAndGet);

    handler.handleConfigEditInput(characterKey('x'));

    assertThat(buffer.toString()).isEqualTo("x");
    assertThat(redraws.get()).isEqualTo(1);
  }

  @Test
  void handleConfigEditListEnterWithoutSelectionShowsHint() throws IOException {
    ConfigController controller = mock(ConfigController.class);
    when(controller.getEditMode()).thenReturn(EditMode.LIST);
    when(controller.consumeNumber()).thenReturn(null);

    StateMachine stateMachine = new StateMachine(State.CONFIG_EDIT);
    AtomicInteger redraws = new AtomicInteger();
    TuiInputHandler handler =
        new TuiInputHandler(stateMachine, controller, redraws::incrementAndGet);

    handler.handleConfigEditInput(new KeyStroke(KeyType.Enter));

    verify(controller)
        .setStatusMessage(
            TuiMessageSource.getDefault().getMessage(TuiMessageSource.INPUT_HINT_ENTER_LIST_ITEM));
    assertThat(redraws.get()).isEqualTo(1);
  }

  @Test
  void handleConfigEditListEnterWithSelectionSelectsListItem() throws IOException {
    ConfigController controller = mock(ConfigController.class);
    when(controller.getEditMode()).thenReturn(EditMode.LIST);
    when(controller.consumeNumber()).thenReturn(2);

    StateMachine stateMachine = new StateMachine(State.CONFIG_EDIT);
    AtomicInteger redraws = new AtomicInteger();
    TuiInputHandler handler =
        new TuiInputHandler(stateMachine, controller, redraws::incrementAndGet);

    handler.handleConfigEditInput(new KeyStroke(KeyType.Enter));

    verify(controller).selectListItem(2);
    assertThat(redraws.get()).isEqualTo(1);
  }

  @Test
  void handleConfigEditListHotkeysDispatchActions() throws IOException {
    ConfigController controller = mock(ConfigController.class);
    when(controller.getEditMode()).thenReturn(EditMode.LIST);
    when(controller.runValidation(any(), anyString())).thenReturn(true);

    StateMachine stateMachine = new StateMachine(State.CONFIG_EDIT);
    AtomicInteger redraws = new AtomicInteger();
    TuiInputHandler handler =
        new TuiInputHandler(stateMachine, controller, redraws::incrementAndGet);

    handler.handleConfigEditInput(characterKey('A'));
    handler.handleConfigEditInput(characterKey('D'));
    handler.handleConfigEditInput(characterKey('B'));
    handler.handleConfigEditInput(characterKey('7'));
    handler.handleConfigEditInput(characterKey('S'));

    verify(controller).beginListAdd();
    verify(controller).deleteListItem();
    verify(controller).backFromEdit();
    verify(controller).appendNumber('7');
    verify(controller).save();
    assertThat(redraws.get()).isEqualTo(5);
  }

  @Test
  void handleConfigEditListItemEnterAppliesItemEdit() throws IOException {
    ConfigController controller = mock(ConfigController.class);
    when(controller.getEditMode()).thenReturn(EditMode.LIST_ITEM);
    when(controller.getValueBuffer()).thenReturn(bufferOf(""));

    StateMachine stateMachine = new StateMachine(State.CONFIG_EDIT);
    AtomicInteger redraws = new AtomicInteger();
    TuiInputHandler handler =
        new TuiInputHandler(stateMachine, controller, redraws::incrementAndGet);

    handler.handleConfigEditInput(new KeyStroke(KeyType.Enter));

    verify(controller).applyListItemEdit();
    assertThat(redraws.get()).isEqualTo(1);
  }

  @Test
  void handleConfigEditListAddEnterAppliesListAdd() throws IOException {
    ConfigController controller = mock(ConfigController.class);
    when(controller.getEditMode()).thenReturn(EditMode.LIST_ADD);
    when(controller.getValueBuffer()).thenReturn(bufferOf(""));

    StateMachine stateMachine = new StateMachine(State.CONFIG_EDIT);
    AtomicInteger redraws = new AtomicInteger();
    TuiInputHandler handler =
        new TuiInputHandler(stateMachine, controller, redraws::incrementAndGet);

    handler.handleConfigEditInput(new KeyStroke(KeyType.Enter));

    verify(controller).applyListAdd();
    assertThat(redraws.get()).isEqualTo(1);
  }

  @Test
  void handleConfigEditListItemBackspaceRemovesCharacter() throws IOException {
    ConfigController controller = mock(ConfigController.class);
    when(controller.getEditMode()).thenReturn(EditMode.LIST_ITEM);
    MutableTextBuffer buffer = bufferOf("42");
    when(controller.getValueBuffer()).thenReturn(buffer);

    StateMachine stateMachine = new StateMachine(State.CONFIG_EDIT);
    AtomicInteger redraws = new AtomicInteger();
    TuiInputHandler handler =
        new TuiInputHandler(stateMachine, controller, redraws::incrementAndGet);

    handler.handleConfigEditInput(new KeyStroke(KeyType.Backspace));

    assertThat(buffer.toString()).isEqualTo("4");
    assertThat(redraws.get()).isEqualTo(1);
  }

  @Test
  void handleConfigEditListItemBWithEmptyBufferReturnsToList() throws IOException {
    ConfigController controller = mock(ConfigController.class);
    when(controller.getEditMode()).thenReturn(EditMode.LIST_ITEM);
    when(controller.getValueBuffer()).thenReturn(bufferOf(""));
    when(controller.getEditPath()).thenReturn(List.of());

    StateMachine stateMachine = new StateMachine(State.CONFIG_EDIT);
    AtomicInteger redraws = new AtomicInteger();
    TuiInputHandler handler =
        new TuiInputHandler(stateMachine, controller, redraws::incrementAndGet);

    handler.handleConfigEditInput(characterKey('B'));

    verify(controller).backFromEdit();
    verify(controller).beginEdit(List.of());
    assertThat(redraws.get()).isEqualTo(1);
  }

  @Test
  void handleConfigEditListItemCharacterAppendsBuffer() throws IOException {
    ConfigController controller = mock(ConfigController.class);
    when(controller.getEditMode()).thenReturn(EditMode.LIST_ITEM);
    MutableTextBuffer buffer = bufferOf("");
    when(controller.getValueBuffer()).thenReturn(buffer);

    StateMachine stateMachine = new StateMachine(State.CONFIG_EDIT);
    AtomicInteger redraws = new AtomicInteger();
    TuiInputHandler handler =
        new TuiInputHandler(stateMachine, controller, redraws::incrementAndGet);

    handler.handleConfigEditInput(characterKey('z'));

    assertThat(buffer.toString()).isEqualTo("z");
    assertThat(redraws.get()).isEqualTo(1);
  }

  @Test
  void handleConfigItemsEnterWithoutSelectionShowsHint() throws IOException {
    ConfigController controller = mock(ConfigController.class);
    when(controller.consumeNumber()).thenReturn(null);

    StateMachine stateMachine = new StateMachine(State.CONFIG_ITEMS);
    AtomicInteger redraws = new AtomicInteger();
    TuiInputHandler handler =
        new TuiInputHandler(stateMachine, controller, redraws::incrementAndGet);

    handler.handleConfigItemsInput(new KeyStroke(KeyType.Enter));

    verify(controller).setStatusMessage(message(TuiMessageSource.INPUT_HINT_ENTER_ITEM));
    assertThat(redraws.get()).isEqualTo(1);
  }

  @Test
  void handleConfigItemsEnterWithSelectionSelectsItem() throws IOException {
    ConfigController controller = mock(ConfigController.class);
    when(controller.consumeNumber()).thenReturn(4);

    StateMachine stateMachine = new StateMachine(State.CONFIG_ITEMS);
    AtomicInteger redraws = new AtomicInteger();
    TuiInputHandler handler =
        new TuiInputHandler(stateMachine, controller, redraws::incrementAndGet);

    handler.handleConfigItemsInput(new KeyStroke(KeyType.Enter));

    verify(controller).selectItem(4);
    assertThat(redraws.get()).isEqualTo(1);
  }

  @Test
  void handleConfigItemsBackspaceTrimsNumber() throws IOException {
    ConfigController controller = mock(ConfigController.class);

    StateMachine stateMachine = new StateMachine(State.CONFIG_ITEMS);
    AtomicInteger redraws = new AtomicInteger();
    TuiInputHandler handler =
        new TuiInputHandler(stateMachine, controller, redraws::incrementAndGet);

    handler.handleConfigItemsInput(new KeyStroke(KeyType.Backspace));

    verify(controller).trimNumber();
    assertThat(redraws.get()).isEqualTo(1);
  }

  @Test
  void handleConfigItemsValidationHotkeyRunsValidation() throws IOException {
    ConfigController controller = mock(ConfigController.class);

    StateMachine stateMachine = new StateMachine(State.CONFIG_ITEMS);
    AtomicInteger redraws = new AtomicInteger();
    TuiInputHandler handler =
        new TuiInputHandler(stateMachine, controller, redraws::incrementAndGet);

    handler.handleConfigItemsInput(characterKey('V'));

    verify(controller).runValidation(any(), anyString());
    assertThat(redraws.get()).isEqualTo(1);
  }

  @Test
  void handleConfigItemsBReturnsToCategorySelection() throws IOException {
    ConfigController controller = mock(ConfigController.class);

    StateMachine stateMachine = new StateMachine(State.CONFIG_ITEMS);
    AtomicInteger redraws = new AtomicInteger();
    TuiInputHandler handler =
        new TuiInputHandler(stateMachine, controller, redraws::incrementAndGet);

    handler.handleConfigItemsInput(characterKey('B'));

    verify(controller).backFromItems();
    assertThat(redraws.get()).isEqualTo(1);
  }

  @Test
  void handleConfigItemsDigitAppendsNumber() throws IOException {
    ConfigController controller = mock(ConfigController.class);

    StateMachine stateMachine = new StateMachine(State.CONFIG_ITEMS);
    AtomicInteger redraws = new AtomicInteger();
    TuiInputHandler handler =
        new TuiInputHandler(stateMachine, controller, redraws::incrementAndGet);

    handler.handleConfigItemsInput(characterKey('6'));

    verify(controller).appendNumber('6');
    assertThat(redraws.get()).isEqualTo(1);
  }

  @Test
  void handleConfigValidationEnterWithoutIssuesExitsValidation() throws IOException {
    ConfigController controller = mock(ConfigController.class);
    when(controller.getValidationIssues()).thenReturn(List.of());

    StateMachine stateMachine = new StateMachine();
    stateMachine.transitionTo(State.CONFIG_VALIDATE);
    AtomicInteger redraws = new AtomicInteger();

    TuiInputHandler handler =
        new TuiInputHandler(stateMachine, controller, redraws::incrementAndGet);

    handler.handleConfigValidationInput(new KeyStroke(KeyType.Enter));

    verify(controller).exitValidation();
    assertThat(redraws.get()).isEqualTo(1);
  }

  @Test
  void handleConfigValidationEnterWithIssuesAndNoSelectionJumpsToFirstIssue() throws IOException {
    ConfigController controller = mock(ConfigController.class);
    when(controller.getValidationIssues())
        .thenReturn(List.of(mock(ConfigValidationService.ValidationIssue.class)));
    when(controller.consumeNumber()).thenReturn(null);

    StateMachine stateMachine = new StateMachine(State.CONFIG_VALIDATE);
    AtomicInteger redraws = new AtomicInteger();
    TuiInputHandler handler =
        new TuiInputHandler(stateMachine, controller, redraws::incrementAndGet);

    handler.handleConfigValidationInput(new KeyStroke(KeyType.Enter));

    verify(controller).jumpToIssue(1);
    assertThat(redraws.get()).isEqualTo(1);
  }

  @Test
  void handleConfigValidationEnterWithIssuesAndSelectionJumpsToSelectedIssue() throws IOException {
    ConfigController controller = mock(ConfigController.class);
    when(controller.getValidationIssues())
        .thenReturn(List.of(mock(ConfigValidationService.ValidationIssue.class)));
    when(controller.consumeNumber()).thenReturn(3);

    StateMachine stateMachine = new StateMachine(State.CONFIG_VALIDATE);
    AtomicInteger redraws = new AtomicInteger();
    TuiInputHandler handler =
        new TuiInputHandler(stateMachine, controller, redraws::incrementAndGet);

    handler.handleConfigValidationInput(new KeyStroke(KeyType.Enter));

    verify(controller).jumpToIssue(3);
    assertThat(redraws.get()).isEqualTo(1);
  }

  @Test
  void handleConfigValidationBackspaceTrimsNumber() throws IOException {
    ConfigController controller = mock(ConfigController.class);

    StateMachine stateMachine = new StateMachine(State.CONFIG_VALIDATE);
    AtomicInteger redraws = new AtomicInteger();
    TuiInputHandler handler =
        new TuiInputHandler(stateMachine, controller, redraws::incrementAndGet);

    handler.handleConfigValidationInput(new KeyStroke(KeyType.Backspace));

    verify(controller).trimNumber();
    assertThat(redraws.get()).isEqualTo(1);
  }

  @Test
  void handleConfigValidationQExitsValidation() throws IOException {
    ConfigController controller = mock(ConfigController.class);

    StateMachine stateMachine = new StateMachine(State.CONFIG_VALIDATE);
    AtomicInteger redraws = new AtomicInteger();
    TuiInputHandler handler =
        new TuiInputHandler(stateMachine, controller, redraws::incrementAndGet);

    handler.handleConfigValidationInput(characterKey('Q'));

    verify(controller).exitValidation();
    assertThat(redraws.get()).isEqualTo(1);
  }

  @Test
  void handleConfigValidationDigitAppendsNumber() throws IOException {
    ConfigController controller = mock(ConfigController.class);

    StateMachine stateMachine = new StateMachine(State.CONFIG_VALIDATE);
    AtomicInteger redraws = new AtomicInteger();
    TuiInputHandler handler =
        new TuiInputHandler(stateMachine, controller, redraws::incrementAndGet);

    handler.handleConfigValidationInput(characterKey('9'));

    verify(controller).appendNumber('9');
    assertThat(redraws.get()).isEqualTo(1);
  }

  @Test
  void handleConfigValidationWithNullControllerDoesNothing() throws IOException {
    StateMachine stateMachine = new StateMachine(State.CONFIG_VALIDATE);
    AtomicInteger redraws = new AtomicInteger();
    TuiInputHandler handler = new TuiInputHandler(stateMachine, null, redraws::incrementAndGet);

    handler.handleConfigValidationInput(new KeyStroke(KeyType.Enter));

    assertThat(redraws.get()).isZero();
  }

  @Test
  void handleConfigCategorySaveRunsValidationAndSave() throws IOException {
    ConfigController controller = mock(ConfigController.class);
    when(controller.runValidation(any(), anyString())).thenReturn(true);

    StateMachine stateMachine = new StateMachine();
    stateMachine.transitionTo(State.CONFIG_CATEGORY);
    AtomicInteger redraws = new AtomicInteger();

    TuiInputHandler handler =
        new TuiInputHandler(stateMachine, controller, redraws::incrementAndGet);

    handler.handleConfigCategoryInput(new KeyStroke('S', false, false));

    verify(controller).runValidation(any(), anyString());
    verify(controller).save();
    assertThat(redraws.get()).isEqualTo(1);
  }

  @Test
  void handleConfigCategoryValidationHotkeyRunsValidation() throws IOException {
    ConfigController controller = mock(ConfigController.class);

    StateMachine stateMachine = new StateMachine(State.CONFIG_CATEGORY);
    AtomicInteger redraws = new AtomicInteger();
    TuiInputHandler handler =
        new TuiInputHandler(stateMachine, controller, redraws::incrementAndGet);

    handler.handleConfigCategoryInput(characterKey('V'));

    verify(controller).runValidation(any(), anyString());
    assertThat(redraws.get()).isEqualTo(1);
  }

  @Test
  void handleConfigCategoryQAttemptsExit() throws IOException {
    ConfigController controller = mock(ConfigController.class);

    StateMachine stateMachine = new StateMachine(State.CONFIG_CATEGORY);
    AtomicInteger redraws = new AtomicInteger();
    TuiInputHandler handler =
        new TuiInputHandler(stateMachine, controller, redraws::incrementAndGet);

    handler.handleConfigCategoryInput(characterKey('Q'));

    verify(controller).attemptExit();
    assertThat(redraws.get()).isEqualTo(1);
  }

  @Test
  void handleConfigCategoryDigitAppendsNumber() throws IOException {
    ConfigController controller = mock(ConfigController.class);

    StateMachine stateMachine = new StateMachine(State.CONFIG_CATEGORY);
    AtomicInteger redraws = new AtomicInteger();
    TuiInputHandler handler =
        new TuiInputHandler(stateMachine, controller, redraws::incrementAndGet);

    handler.handleConfigCategoryInput(characterKey('1'));

    verify(controller).appendNumber('1');
    assertThat(redraws.get()).isEqualTo(1);
  }

  @Test
  void handleConfigCategoryExitConfirmationYExitsWhenSavedAndClean() throws IOException {
    ConfigController controller = mock(ConfigController.class);
    when(controller.isExitConfirmation()).thenReturn(true);
    when(controller.saveAll(any(), anyString())).thenReturn(true);
    when(controller.hasDirtyEditors()).thenReturn(false);

    StateMachine stateMachine = new StateMachine(State.CONFIG_CATEGORY);
    AtomicInteger redraws = new AtomicInteger();
    TuiInputHandler handler =
        new TuiInputHandler(stateMachine, controller, redraws::incrementAndGet);

    handler.handleConfigCategoryInput(characterKey('Y'));

    verify(controller).saveAll(any(), anyString());
    verify(controller).exit();
    assertThat(redraws.get()).isEqualTo(1);
  }

  @Test
  void handleConfigCategoryExitConfirmationYDoesNotExitWhenDirty() throws IOException {
    ConfigController controller = mock(ConfigController.class);
    when(controller.isExitConfirmation()).thenReturn(true);
    when(controller.saveAll(any(), anyString())).thenReturn(true);
    when(controller.hasDirtyEditors()).thenReturn(true);

    StateMachine stateMachine = new StateMachine(State.CONFIG_CATEGORY);
    AtomicInteger redraws = new AtomicInteger();
    TuiInputHandler handler =
        new TuiInputHandler(stateMachine, controller, redraws::incrementAndGet);

    handler.handleConfigCategoryInput(characterKey('y'));

    verify(controller).saveAll(any(), anyString());
    verify(controller, never()).exit();
    assertThat(redraws.get()).isEqualTo(1);
  }

  @Test
  void handleConfigCategoryExitConfirmationNDiscardsAndExits() throws IOException {
    ConfigController controller = mock(ConfigController.class);
    when(controller.isExitConfirmation()).thenReturn(true);

    StateMachine stateMachine = new StateMachine(State.CONFIG_CATEGORY);
    AtomicInteger redraws = new AtomicInteger();
    TuiInputHandler handler =
        new TuiInputHandler(stateMachine, controller, redraws::incrementAndGet);

    handler.handleConfigCategoryInput(characterKey('N'));

    verify(controller).discardAll();
    verify(controller).exit();
    assertThat(redraws.get()).isEqualTo(1);
  }

  @Test
  void handleConfigCategoryExitConfirmationBCancelsExit() throws IOException {
    ConfigController controller = mock(ConfigController.class);
    when(controller.isExitConfirmation()).thenReturn(true);

    StateMachine stateMachine = new StateMachine(State.CONFIG_CATEGORY);
    AtomicInteger redraws = new AtomicInteger();
    TuiInputHandler handler =
        new TuiInputHandler(stateMachine, controller, redraws::incrementAndGet);

    handler.handleConfigCategoryInput(characterKey('B'));

    verify(controller).cancelExit();
    assertThat(redraws.get()).isEqualTo(1);
  }

  @Test
  void handleConfigCategoryExitConfirmationNonCharacterIsIgnored() throws IOException {
    ConfigController controller = mock(ConfigController.class);
    when(controller.isExitConfirmation()).thenReturn(true);

    StateMachine stateMachine = new StateMachine(State.CONFIG_CATEGORY);
    AtomicInteger redraws = new AtomicInteger();
    TuiInputHandler handler =
        new TuiInputHandler(stateMachine, controller, redraws::incrementAndGet);

    handler.handleConfigCategoryInput(new KeyStroke(KeyType.Enter));

    verify(controller).isExitConfirmation();
    verify(controller, never()).saveAll(any(), anyString());
    assertThat(redraws.get()).isZero();
  }

  private static KeyStroke characterKey(char c) {
    return new KeyStroke(c, false, false);
  }

  private static MutableTextBuffer bufferOf(final String value) {
    final MutableTextBuffer buffer = new MutableTextBuffer();
    if (value != null && !value.isEmpty()) {
      buffer.append(value);
    }
    return buffer;
  }

  private static String message(String key) {
    return TuiMessageSource.getDefault().getMessage(key);
  }
}
