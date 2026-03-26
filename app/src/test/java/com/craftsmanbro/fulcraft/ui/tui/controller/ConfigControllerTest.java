package com.craftsmanbro.fulcraft.ui.tui.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.craftsmanbro.fulcraft.ui.tui.config.ConfigValidationService;
import com.craftsmanbro.fulcraft.ui.tui.config.MetadataRegistry;
import com.craftsmanbro.fulcraft.ui.tui.i18n.TuiMessageSource;
import com.craftsmanbro.fulcraft.ui.tui.state.State;
import com.craftsmanbro.fulcraft.ui.tui.state.StateMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ConfigController}.
 *
 * <p>ConfigController manages the state and logic for the Config Editor in TUI. These tests focus
 * on:
 *
 * <ul>
 *   <li>Constructor validation
 *   <li>State accessors
 *   <li>Number buffer operations
 *   <li>Lifecycle and exit confirmation
 * </ul>
 */
class ConfigControllerTest {

  private StateMachine stateMachine;
  private MetadataRegistry metadataRegistry;
  private ConfigValidationService validationService;
  private TuiMessageSource messageSource;
  private ConfigController controller;

  @BeforeEach
  void setUp() {
    stateMachine = new StateMachine(State.CHAT_INPUT);
    metadataRegistry = MetadataRegistry.getDefault();
    validationService = new ConfigValidationService();
    messageSource = TuiMessageSource.getDefault();
    controller =
        new ConfigController(stateMachine, metadataRegistry, validationService, messageSource);
  }

  @Nested
  @DisplayName("Constructor validation")
  class ConstructorValidation {

    @Test
    @DisplayName("should throw NullPointerException when stateMachine is null")
    void shouldThrowWhenStateMachineIsNull() {
      assertThatThrownBy(
              () -> new ConfigController(null, metadataRegistry, validationService, messageSource))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("stateMachine");
    }

    @Test
    @DisplayName("should throw NullPointerException when metadataRegistry is null")
    void shouldThrowWhenMetadataRegistryIsNull() {
      assertThatThrownBy(
              () -> new ConfigController(stateMachine, null, validationService, messageSource))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("metadataRegistry");
    }

    @Test
    @DisplayName("should throw NullPointerException when validationService is null")
    void shouldThrowWhenValidationServiceIsNull() {
      assertThatThrownBy(
              () -> new ConfigController(stateMachine, metadataRegistry, null, messageSource))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("validationService");
    }

    @Test
    @DisplayName("should throw NullPointerException when messageSource is null")
    void shouldThrowWhenMessageSourceIsNull() {
      assertThatThrownBy(
              () -> new ConfigController(stateMachine, metadataRegistry, validationService, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("messageSource");
    }

    @Test
    @DisplayName("should create instance with three-arg constructor using default messageSource")
    void shouldCreateWithThreeArgConstructor() {
      assertThatCode(() -> new ConfigController(stateMachine, metadataRegistry, validationService))
          .doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("Initial state accessors")
  class InitialStateAccessors {

    @Test
    @DisplayName("getConfigEditor should return null initially")
    void getConfigEditorShouldReturnNullInitially() {
      assertThat(controller.getConfigEditor()).isNull();
    }

    @Test
    @DisplayName("getCategory should return null initially")
    void getCategoryShouldReturnNullInitially() {
      assertThat(controller.getCategory()).isNull();
    }

    @Test
    @DisplayName("getPath should return empty list initially")
    void getPathShouldReturnEmptyListInitially() {
      assertThat(controller.getPath()).isEmpty();
    }

    @Test
    @DisplayName("getEditPath should return empty list initially")
    void getEditPathShouldReturnEmptyListInitially() {
      assertThat(controller.getEditPath()).isEmpty();
    }

    @Test
    @DisplayName("getEditMode should return null initially")
    void getEditModeShouldReturnNullInitially() {
      assertThat(controller.getEditMode()).isNull();
    }

    @Test
    @DisplayName("getEditValueType should return null initially")
    void getEditValueTypeShouldReturnNullInitially() {
      assertThat(controller.getEditValueType()).isNull();
    }

    @Test
    @DisplayName("getEnumOptions should return empty list initially")
    void getEnumOptionsShouldReturnEmptyListInitially() {
      assertThat(controller.getEnumOptions()).isEmpty();
    }

    @Test
    @DisplayName("getListEditIndex should return -1 initially")
    void getListEditIndexShouldReturnMinusOneInitially() {
      assertThat(controller.getListEditIndex()).isEqualTo(-1);
    }

    @Test
    @DisplayName("getStatusMessage should return null initially")
    void getStatusMessageShouldReturnNullInitially() {
      assertThat(controller.getStatusMessage()).isNull();
    }

    @Test
    @DisplayName("isExitConfirmation should return false initially")
    void isExitConfirmationShouldReturnFalseInitially() {
      assertThat(controller.isExitConfirmation()).isFalse();
    }

    @Test
    @DisplayName("getReturnState should return CHAT_INPUT initially")
    void getReturnStateShouldReturnChatInputInitially() {
      assertThat(controller.getReturnState()).isEqualTo(State.CHAT_INPUT);
    }

    @Test
    @DisplayName("getValidationReturnState should return CONFIG_CATEGORY initially")
    void getValidationReturnStateShouldReturnConfigCategoryInitially() {
      assertThat(controller.getValidationReturnState()).isEqualTo(State.CONFIG_CATEGORY);
    }

    @Test
    @DisplayName("getValidationIssues should return empty list initially")
    void getValidationIssuesShouldReturnEmptyListInitially() {
      assertThat(controller.getValidationIssues()).isEmpty();
    }

    @Test
    @DisplayName("getCurrentList should return empty list when configEditor is null")
    void getCurrentListShouldReturnEmptyListWhenConfigEditorIsNull() {
      assertThat(controller.getCurrentList()).isEmpty();
    }

    @Test
    @DisplayName("getCurrentItems should return empty list when configEditor is null")
    void getCurrentItemsShouldReturnEmptyListWhenConfigEditorIsNull() {
      assertThat(controller.getCurrentItems()).isEmpty();
    }
  }

  @Nested
  @DisplayName("Status message operations")
  class StatusMessageOperations {

    @Test
    @DisplayName("setStatusMessage should update status message")
    void setStatusMessageShouldUpdateStatusMessage() {
      controller.setStatusMessage("test message");
      assertThat(controller.getStatusMessage()).isEqualTo("test message");
    }

    @Test
    @DisplayName("setStatusMessage should accept null")
    void setStatusMessageShouldAcceptNull() {
      controller.setStatusMessage("test");
      controller.setStatusMessage(null);
      assertThat(controller.getStatusMessage()).isNull();
    }
  }

  @Nested
  @DisplayName("Number buffer operations")
  class NumberBufferOperations {

    @Test
    @DisplayName("getNumberBuffer should return empty buffer initially")
    void getNumberBufferShouldReturnEmptyBufferInitially() {
      assertThat(controller.getNumberBuffer().toString()).isEmpty();
    }

    @Test
    @DisplayName("appendNumber should add digit to buffer")
    void appendNumberShouldAddDigitToBuffer() {
      controller.appendNumber('1');
      controller.appendNumber('2');
      controller.appendNumber('3');
      assertThat(controller.getNumberBuffer().toString()).isEqualTo("123");
    }

    @Test
    @DisplayName("appendNumber should ignore non-digit characters")
    void appendNumberShouldIgnoreNonDigitCharacters() {
      controller.appendNumber('1');
      controller.appendNumber('a');
      controller.appendNumber('2');
      controller.appendNumber('!');
      assertThat(controller.getNumberBuffer().toString()).isEqualTo("12");
    }

    @Test
    @DisplayName("trimNumber should remove last digit")
    void trimNumberShouldRemoveLastDigit() {
      controller.appendNumber('1');
      controller.appendNumber('2');
      controller.appendNumber('3');
      controller.trimNumber();
      assertThat(controller.getNumberBuffer().toString()).isEqualTo("12");
    }

    @Test
    @DisplayName("trimNumber should do nothing on empty buffer")
    void trimNumberShouldDoNothingOnEmptyBuffer() {
      controller.trimNumber();
      assertThat(controller.getNumberBuffer().toString()).isEmpty();
    }

    @Test
    @DisplayName("consumeNumber should return parsed integer and clear buffer")
    void consumeNumberShouldReturnParsedIntegerAndClearBuffer() {
      controller.appendNumber('4');
      controller.appendNumber('2');

      Integer result = controller.consumeNumber();

      assertThat(result).isEqualTo(42);
      assertThat(controller.getNumberBuffer().toString()).isEmpty();
    }

    @Test
    @DisplayName("consumeNumber should return null for empty buffer")
    void consumeNumberShouldReturnNullForEmptyBuffer() {
      Integer result = controller.consumeNumber();
      assertThat(result).isNull();
    }

    @Test
    @DisplayName("consumeNumber should clear buffer even after returning result")
    void consumeNumberShouldClearBufferAfterReturning() {
      controller.appendNumber('9');
      controller.appendNumber('9');
      controller.consumeNumber();

      // Second call should return null since buffer was cleared
      Integer result = controller.consumeNumber();
      assertThat(result).isNull();
    }
  }

  @Nested
  @DisplayName("Value buffer operations")
  class ValueBufferOperations {

    @Test
    @DisplayName("getValueBuffer should return empty buffer initially")
    void getValueBufferShouldReturnEmptyBufferInitially() {
      assertThat(controller.getValueBuffer().toString()).isEmpty();
    }

    @Test
    @DisplayName("getValueBuffer should be modifiable")
    void getValueBufferShouldBeModifiable() {
      controller.getValueBuffer().append("test value");
      assertThat(controller.getValueBuffer().toString()).isEqualTo("test value");
    }
  }

  @Nested
  @DisplayName("Exit confirmation")
  class ExitConfirmation {

    @Test
    @DisplayName("attemptExit should exit directly when configEditor is null")
    void attemptExitShouldExitDirectlyWhenConfigEditorIsNull() {
      StateMachine mockStateMachine = mock(StateMachine.class);
      when(mockStateMachine.getCurrentState()).thenReturn(State.HOME);
      ConfigController ctrl =
          new ConfigController(
              mockStateMachine, metadataRegistry, validationService, messageSource);

      ctrl.attemptExit();

      assertThat(ctrl.isExitConfirmation()).isFalse();
      verify(mockStateMachine).transitionTo(State.CHAT_INPUT);
    }

    @Test
    @DisplayName("cancelExit should reset exitConfirmation flag")
    void cancelExitShouldResetExitConfirmationFlag() {
      controller.cancelExit();
      assertThat(controller.isExitConfirmation()).isFalse();
    }

    @Test
    @DisplayName("exit should reset exitConfirmation and transition to returnState")
    void exitShouldResetExitConfirmationAndTransitionToReturnState() {
      StateMachine mockStateMachine = mock(StateMachine.class);
      when(mockStateMachine.getCurrentState()).thenReturn(State.HOME);
      ConfigController ctrl =
          new ConfigController(
              mockStateMachine, metadataRegistry, validationService, messageSource);

      ctrl.exit();

      assertThat(ctrl.isExitConfirmation()).isFalse();
      verify(mockStateMachine).transitionTo(State.CHAT_INPUT);
    }
  }

  @Nested
  @DisplayName("Category selection")
  class CategorySelection {

    @Test
    @DisplayName("selectCategory with invalid selection should set error status")
    void selectCategoryWithInvalidSelectionShouldSetErrorStatus() {
      controller.selectCategory(0);
      assertThat(controller.getStatusMessage()).isNotNull();
    }

    @Test
    @DisplayName("selectCategory with out-of-range selection should set error status")
    void selectCategoryWithOutOfRangeSelectionShouldSetErrorStatus() {
      controller.selectCategory(999);
      assertThat(controller.getStatusMessage()).isNotNull();
    }

    @Test
    @DisplayName("selectCategory with valid selection should clear status and transition")
    void selectCategoryWithValidSelectionShouldClearStatusAndTransition() {
      // Note: ConfigEditor.CATEGORIES has items, valid selections are 1-based
      controller.selectCategory(1);

      assertThat(controller.getStatusMessage()).isNull();
      assertThat(controller.getCategory()).isNotNull();
      assertThat(stateMachine.getCurrentState()).isEqualTo(State.CONFIG_ITEMS);
    }
  }

  @Nested
  @DisplayName("Validation operations")
  class ValidationOperations {

    @Test
    @DisplayName("runValidation should set error when configEditor is null")
    void runValidationShouldSetErrorWhenConfigEditorIsNull() {
      boolean result = controller.runValidation(State.CONFIG_CATEGORY, "Failed");

      assertThat(result).isFalse();
      assertThat(controller.getStatusMessage()).isNotNull();
    }

    @Test
    @DisplayName("exitValidation should transition to validationReturnState")
    void exitValidationShouldTransitionToValidationReturnState() {
      controller.exitValidation();
      assertThat(stateMachine.getCurrentState()).isEqualTo(State.CONFIG_CATEGORY);
    }

    @Test
    @DisplayName("jumpToIssue should return false when validationIssues is empty")
    void jumpToIssueShouldReturnFalseWhenValidationIssuesIsEmpty() {
      boolean result = controller.jumpToIssue(1);
      assertThat(result).isFalse();
    }
  }

  @Nested
  @DisplayName("Save and discard operations")
  class SaveAndDiscardOperations {

    @Test
    @DisplayName("save should set error when configEditor is null")
    void saveShouldSetErrorWhenConfigEditorIsNull() throws Exception {
      controller.save();
      assertThat(controller.getStatusMessage()).isNotNull();
    }

    @Test
    @DisplayName("discard should set success message and reset exitConfirmation")
    void discardShouldSetSuccessMessageAndResetExitConfirmation() {
      controller.discard();

      assertThat(controller.getStatusMessage()).isNotNull();
      assertThat(controller.isExitConfirmation()).isFalse();
    }
  }

  @Nested
  @DisplayName("EditMode enum")
  class EditModeEnum {

    @Test
    @DisplayName("should have all expected values")
    void shouldHaveAllExpectedValues() {
      assertThat(ConfigController.EditMode.values())
          .containsExactlyInAnyOrder(
              ConfigController.EditMode.BOOLEAN,
              ConfigController.EditMode.ENUM,
              ConfigController.EditMode.SCALAR,
              ConfigController.EditMode.LIST,
              ConfigController.EditMode.LIST_ITEM,
              ConfigController.EditMode.LIST_ADD);
    }

    @Test
    @DisplayName("valueOf should return correct enum for valid name")
    void valueOfShouldReturnCorrectEnumForValidName() {
      assertThat(ConfigController.EditMode.valueOf("BOOLEAN"))
          .isEqualTo(ConfigController.EditMode.BOOLEAN);
      assertThat(ConfigController.EditMode.valueOf("SCALAR"))
          .isEqualTo(ConfigController.EditMode.SCALAR);
    }
  }

  @Nested
  @DisplayName("Item selection")
  class ItemSelection {

    @Test
    @DisplayName("selectItem with invalid selection should set error status")
    void selectItemWithInvalidSelectionShouldSetErrorStatus() {
      // Without entering config first, getCurrentItems returns empty
      controller.selectItem(1);
      assertThat(controller.getStatusMessage()).isNotNull();
    }

    @Test
    @DisplayName("selectItem with out-of-range selection should set error status")
    void selectItemWithOutOfRangeSelectionShouldSetErrorStatus() {
      controller.selectItem(0);
      assertThat(controller.getStatusMessage()).isNotNull();
    }
  }

  @Nested
  @DisplayName("List operations")
  class ListOperationsTest {

    @Test
    @DisplayName("selectListItem with invalid selection should set error status")
    void selectListItemWithInvalidSelectionShouldSetErrorStatus() {
      controller.selectListItem(1);
      assertThat(controller.getStatusMessage()).isNotNull();
    }

    @Test
    @DisplayName("beginListAdd should set edit mode to LIST_ADD")
    void beginListAddShouldSetEditModeToListAdd() {
      controller.beginListAdd();
      assertThat(controller.getEditMode()).isEqualTo(ConfigController.EditMode.LIST_ADD);
      assertThat(controller.getListEditIndex()).isEqualTo(-1);
    }

    @Test
    @DisplayName("deleteListItem without number should set error status")
    void deleteListItemWithoutNumberShouldSetErrorStatus() {
      controller.deleteListItem();
      assertThat(controller.getStatusMessage()).isNotNull();
    }
  }

  @Nested
  @DisplayName("openAtPath operations")
  class OpenAtPathOperations {

    @Test
    @DisplayName("openAtPath with invalid path should return false and set error")
    void openAtPathWithInvalidPathShouldReturnFalseAndSetError() {
      boolean result = controller.openAtPath("");
      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("openAtPath without loaded editor should return false and set status message")
    void openAtPathWithoutLoadedEditorShouldReturnFalseAndSetStatusMessage() {
      boolean result = controller.openAtPath("project");

      assertThat(result).isFalse();
      assertThat(controller.getStatusMessage())
          .isEqualTo(messageSource.getMessage(TuiMessageSource.CONFIG_ERROR_EDITOR_NOT_AVAILABLE));
    }
  }

  @Nested
  @DisplayName("Edit operations")
  class EditOperationsTest {

    @Test
    @DisplayName("toggleBoolean should do nothing when editPath is null")
    void toggleBooleanShouldDoNothingWhenEditPathIsNull() {
      assertThatCode(() -> controller.toggleBoolean()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("backFromEdit should transition to CONFIG_ITEMS")
    void backFromEditShouldTransitionToConfigItems() {
      controller.backFromEdit();
      assertThat(stateMachine.getCurrentState()).isEqualTo(State.CONFIG_ITEMS);
    }
  }
}
