package com.craftsmanbro.fulcraft.ui.tui;

import com.craftsmanbro.fulcraft.ui.tui.config.ConfigEditor;
import com.craftsmanbro.fulcraft.ui.tui.config.ConfigValidationService;
import com.craftsmanbro.fulcraft.ui.tui.controller.ConfigController;
import com.craftsmanbro.fulcraft.ui.tui.i18n.TuiMessageSource;
import com.craftsmanbro.fulcraft.ui.tui.state.State;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ConfigEditorScreen implements Screen {

  private static final int CONFIG_CHANGED_KEYS_LIMIT = 5;

  private static final List<String> ERROR_MESSAGE_KEYS =
      List.of(
          TuiMessageSource.CONFIG_ERROR_INVALID_CATEGORY,
          TuiMessageSource.CONFIG_ERROR_INVALID_ITEM,
          TuiMessageSource.CONFIG_ERROR_INVALID_ENUM,
          TuiMessageSource.CONFIG_ERROR_VALUE_REQUIRED,
          TuiMessageSource.CONFIG_ERROR_ENTER_ITEM_DELETE,
          TuiMessageSource.CONFIG_ERROR_INVALID_LIST_ITEM,
          TuiMessageSource.CONFIG_ERROR_UPDATE_FAILED,
          TuiMessageSource.CONFIG_ERROR_ITEM_UPDATE_FAILED,
          TuiMessageSource.CONFIG_ERROR_ITEM_ADD_FAILED,
          TuiMessageSource.CONFIG_ERROR_ITEM_DELETE_FAILED,
          TuiMessageSource.CONFIG_ERROR_EDITOR_NOT_AVAILABLE,
          TuiMessageSource.CONFIG_VALIDATION_FAILED,
          TuiMessageSource.CONFIG_VALIDATION_FAILED_MSG,
          TuiMessageSource.CONFIG_ERROR_NO_PATH,
          TuiMessageSource.INPUT_ERROR_INVALID_ERROR_NUMBER,
          TuiMessageSource.INPUT_ERROR_VALIDATION_FAILED_SAVE);

  private final State state;

  private final TuiMessageSource msg = TuiMessageSource.getDefault();

  public ConfigEditorScreen() {
    this(State.CONFIG_CATEGORY);
  }

  public ConfigEditorScreen(final State state) {
    this.state = Objects.requireNonNull(state, "state must not be null");
  }

  @Override
  public State getState() {
    return state;
  }

  @Override
  public void draw(
      final TuiApplication app,
      final TextGraphics tg,
      final TerminalSize size,
      final int startRow) {
    final ConfigController ctrl = app.getConfigController();
    if (ctrl == null) {
      tg.putString(2, startRow, msg.getMessage("tui.config_screen.controller_not_initialized"));
      return;
    }
    switch (state) {
      case CONFIG_CATEGORY -> drawCategory(tg, startRow, size, ctrl);
      case CONFIG_ITEMS -> drawItems(tg, startRow, size, ctrl);
      case CONFIG_EDIT -> drawEdit(tg, startRow, size, ctrl);
      case CONFIG_VALIDATE -> drawValidation(tg, startRow, size, ctrl);
      default ->
          tg.putString(2, startRow, msg.getMessage("tui.config_screen.unsupported_state", state));
    }
  }

  @Override
  public State handleInput(final TuiApplication app, final KeyStroke keyStroke) throws IOException {
    final TuiInputHandler inputHandler = app.getTuiInputHandler();
    if (inputHandler == null) {
      // Fallback to legacy handlers if TuiInputHandler is not available
      return app.getStateMachine().getCurrentState();
    }
    switch (state) {
      case CONFIG_CATEGORY -> inputHandler.handleConfigCategoryInput(keyStroke);
      case CONFIG_ITEMS -> inputHandler.handleConfigItemsInput(keyStroke);
      case CONFIG_EDIT -> inputHandler.handleConfigEditInput(keyStroke);
      case CONFIG_VALIDATE -> inputHandler.handleConfigValidationInput(keyStroke);
      default -> {
        // Unsupported state
      }
    }
    return app.getStateMachine().getCurrentState();
  }

  public void drawCategory(
      final TextGraphics tg, int startRow, final TerminalSize size, final ConfigController ctrl) {
    int row = startRow;
    tg.putString(2, row++, msg.getMessage(TuiMessageSource.CONFIG_CATEGORIES));
    tg.putString(2, row++, "");
    row = drawStatusMessage(tg, row, ctrl.getStatusMessage());
    final ConfigEditor configEditor = ctrl.getConfigEditor();
    if (configEditor == null) {
      tg.putString(2, row, msg.getMessage(TuiMessageSource.CONFIG_EDITOR_NOT_INIT));
      return;
    }
    int index = 1;
    for (final String category : ctrl.getCategories()) {
      final String summary = ctrl.formatCategorySummary(category, size.getColumns() - 6);
      tg.putString(2, row++, index + ". " + category + summary);
      index++;
    }
    row = drawChangedKeys(tg, row, size, configEditor);
    if (ctrl.isExitConfirmation()) {
      tg.putString(2, row, msg.getMessage(TuiMessageSource.CONFIG_EXIT_CONFIRM));
      return;
    }
    tg.putString(2, row, msg.getMessage(TuiMessageSource.CONFIG_ENTER_CATEGORY));
  }

  public void drawItems(
      final TextGraphics tg, int startRow, final TerminalSize size, final ConfigController ctrl) {
    final ConfigEditor configEditor = ctrl.getConfigEditor();
    final String statusMessage = ctrl.getStatusMessage();
    final boolean exitConfirmation = ctrl.isExitConfirmation();
    final String pathLabel = ctrl.getPathLabel();
    final List<ConfigEditor.ConfigItem> items = ctrl.getCurrentItems();
    final MutableTextBuffer numberBuffer = ctrl.getNumberBuffer();
    int row = startRow;
    tg.putString(2, row++, msg.getMessage(TuiMessageSource.CONFIG_ITEMS));
    tg.putString(2, row++, "");
    row = drawStatusMessage(tg, row, statusMessage);
    if (configEditor == null) {
      tg.putString(2, row, msg.getMessage(TuiMessageSource.CONFIG_EDITOR_NOT_INIT));
      return;
    }
    tg.putString(2, row++, msg.getMessage(TuiMessageSource.CONFIG_PATH) + " " + pathLabel);
    tg.putString(2, row++, "");
    if (items.isEmpty()) {
      tg.putString(2, row++, msg.getMessage(TuiMessageSource.CONFIG_NO_ENTRIES));
    } else {
      final int maxWidth = size.getColumns() - 8;
      for (int i = 0; i < items.size(); i++) {
        final ConfigEditor.ConfigItem item = items.get(i);
        final String summary = configEditor.summarizeValue(item.path(), item.value(), maxWidth);
        tg.putString(2, row++, (i + 1) + ". " + item.label() + " = " + summary);
      }
    }
    row++;
    row = drawNumberBufferPrompt(tg, row, numberBuffer);
    row = drawChangedKeys(tg, row, size, configEditor);
    if (exitConfirmation) {
      tg.putString(2, row, msg.getMessage(TuiMessageSource.CONFIG_EXIT_CONFIRM));
      return;
    }
    tg.putString(2, row, msg.getMessage(TuiMessageSource.CONFIG_ENTER_ITEM));
  }

  public void drawEdit(
      final TextGraphics tg, int startRow, final TerminalSize size, final ConfigController ctrl) {
    final ConfigEditor configEditor = ctrl.getConfigEditor();
    final String statusMessage = ctrl.getStatusMessage();
    final boolean exitConfirmation = ctrl.isExitConfirmation();
    final List<ConfigEditor.PathSegment> editPath = ctrl.getEditPath();
    final ConfigController.EditMode editMode = ctrl.getEditMode();
    final ConfigEditor.ValueType editValueType = ctrl.getEditValueType();
    final List<String> enumOptions = ctrl.getEnumOptions();
    final MutableTextBuffer numberBuffer = ctrl.getNumberBuffer();
    final MutableTextBuffer valueBuffer = ctrl.getValueBuffer();
    final int listEditIndex = ctrl.getListEditIndex();
    final List<?> currentList = ctrl.getCurrentList();
    int row = startRow;
    tg.putString(2, row++, msg.getMessage(TuiMessageSource.CONFIG_EDIT));
    tg.putString(2, row++, "");
    row = drawStatusMessage(tg, row, statusMessage);
    if (configEditor == null || editPath == null) {
      tg.putString(2, row, msg.getMessage(TuiMessageSource.CONFIG_NO_SELECTION));
      return;
    }
    row = drawEditPath(tg, row, configEditor, editPath, editMode, listEditIndex);
    if (editMode == null) {
      row = drawEditUnsupported(tg, row);
    } else {
      row =
          switch (editMode) {
            case BOOLEAN -> drawEditBoolean(tg, row, configEditor, editPath);
            case ENUM -> drawEditEnum(tg, row, configEditor, editPath, enumOptions, numberBuffer);
            case LIST ->
                drawEditList(tg, row, size, configEditor, editPath, currentList, numberBuffer);
            case LIST_ITEM, LIST_ADD -> drawEditListItem(tg, row, editMode, valueBuffer);
            case SCALAR -> drawEditScalar(tg, row, editValueType, valueBuffer);
          };
    }
    row = drawChangedKeys(tg, row, size, configEditor);
    if (exitConfirmation) {
      tg.putString(2, row, msg.getMessage(TuiMessageSource.CONFIG_EXIT_CONFIRM));
    }
  }

  public void drawValidation(
      final TextGraphics tg, int startRow, final TerminalSize size, final ConfigController ctrl) {
    final String statusMessage = ctrl.getStatusMessage();
    final List<ConfigValidationService.ValidationIssue> validationIssues =
        ctrl.getValidationIssues();
    final MutableTextBuffer numberBuffer = ctrl.getNumberBuffer();
    int row = startRow;
    tg.putString(2, row++, msg.getMessage(TuiMessageSource.CONFIG_VALIDATION_TITLE));
    tg.putString(2, row++, "");
    row = drawStatusMessage(tg, row, statusMessage);
    if (validationIssues == null || validationIssues.isEmpty()) {
      tg.putString(2, row++, msg.getMessage(TuiMessageSource.CONFIG_NO_VALIDATION_ERRORS));
      tg.putString(2, row, msg.getMessage("tui.config_screen.validation.return_hint"));
      return;
    }
    tg.putString(2, row++, msg.getMessage(TuiMessageSource.CONFIG_ERRORS_LABEL));
    final int maxWidth = Math.max(10, size.getColumns() - 6);
    final int remainingRows = Math.max(0, size.getRows() - row - 3);
    final int limit = Math.min(validationIssues.size(), remainingRows);
    for (int i = 0; i < limit; i++) {
      final ConfigValidationService.ValidationIssue issue = validationIssues.get(i);
      final String line = formatValidationLine(i + 1, issue, maxWidth);
      tg.putString(2, row++, line);
    }
    if (validationIssues.size() > limit && remainingRows > 0) {
      tg.putString(
          2, row++, msg.getMessage("tui.common.more_count", validationIssues.size() - limit));
    }
    row++;
    row = drawNumberBufferPrompt(tg, row, numberBuffer);
    tg.putString(2, row, msg.getMessage(TuiMessageSource.CONFIG_JUMP_ERROR));
  }

  private int drawStatusMessage(final TextGraphics tg, final int row, final String statusMessage) {
    if (statusMessage == null || statusMessage.isBlank()) {
      return row;
    }
    int currentRow = row;
    if (isErrorStatusMessage(statusMessage)) {
      tg.setForegroundColor(TextColor.ANSI.RED);
    }
    tg.putString(2, currentRow++, statusMessage);
    tg.setForegroundColor(TextColor.ANSI.DEFAULT);
    tg.putString(2, currentRow++, "");
    return currentRow;
  }

  private int drawNumberBufferPrompt(
      final TextGraphics tg, final int row, final MutableTextBuffer numberBuffer) {
    int currentRow = row;
    final String prompt = msg.getMessage(TuiMessageSource.CONFIG_SELECT_NUMBER);
    if (numberBuffer.isEmpty()) {
      tg.putString(2, currentRow++, prompt);
    } else {
      tg.putString(2, currentRow++, prompt + " " + numberBuffer);
    }
    return currentRow;
  }

  private int drawEditPath(
      final TextGraphics tg,
      final int row,
      final ConfigEditor configEditor,
      final List<ConfigEditor.PathSegment> editPath,
      final ConfigController.EditMode editMode,
      final int listEditIndex) {
    int currentRow = row;
    String pathLabel = configEditor.formatPathForDisplay(editPath);
    if (editMode == ConfigController.EditMode.LIST_ITEM && listEditIndex >= 0) {
      pathLabel = pathLabel + "[" + listEditIndex + "]";
    }
    tg.putString(2, currentRow++, msg.getMessage(TuiMessageSource.CONFIG_PATH) + " " + pathLabel);
    tg.putString(2, currentRow++, "");
    return currentRow;
  }

  private int drawEditBoolean(
      final TextGraphics tg,
      final int row,
      final ConfigEditor configEditor,
      final List<ConfigEditor.PathSegment> editPath) {
    int currentRow = row;
    final Object value = configEditor.getValue(editPath);
    tg.putString(
        2,
        currentRow++,
        msg.getMessage(TuiMessageSource.CONFIG_TYPE)
            + " "
            + msg.getMessage("tui.config.value_type.boolean"));
    tg.putString(2, currentRow++, msg.getMessage(TuiMessageSource.CONFIG_CURRENT) + " " + value);
    tg.putString(2, currentRow++, msg.getMessage(TuiMessageSource.CONFIG_SPACE_TOGGLE));
    return currentRow;
  }

  private int drawEditEnum(
      final TextGraphics tg,
      final int row,
      final ConfigEditor configEditor,
      final List<ConfigEditor.PathSegment> editPath,
      final List<String> enumOptions,
      final MutableTextBuffer numberBuffer) {
    int currentRow = row;
    final Object value = configEditor.getValue(editPath);
    tg.putString(
        2,
        currentRow++,
        msg.getMessage(TuiMessageSource.CONFIG_TYPE)
            + " "
            + msg.getMessage("tui.config.value_type.enum"));
    for (int i = 0; i < enumOptions.size(); i++) {
      final String option = enumOptions.get(i);
      final String marker = value instanceof String s && s.equalsIgnoreCase(option) ? "*" : " ";
      tg.putString(2, currentRow++, (i + 1) + ". " + marker + " " + option);
    }
    currentRow++;
    return drawNumberBufferPrompt(tg, currentRow, numberBuffer);
  }

  private int drawEditList(
      final TextGraphics tg,
      final int row,
      final TerminalSize size,
      final ConfigEditor configEditor,
      final List<ConfigEditor.PathSegment> editPath,
      final List<?> currentList,
      final MutableTextBuffer numberBuffer) {
    int currentRow = row;
    tg.putString(
        2,
        currentRow++,
        msg.getMessage(TuiMessageSource.CONFIG_TYPE)
            + " "
            + msg.getMessage("tui.config.value_type.list"));
    if (currentList == null || currentList.isEmpty()) {
      tg.putString(2, currentRow++, msg.getMessage(TuiMessageSource.CONFIG_NO_ENTRIES));
    } else {
      final int maxWidth = size.getColumns() - 8;
      for (int i = 0; i < currentList.size(); i++) {
        final List<ConfigEditor.PathSegment> itemPath = new ArrayList<>(editPath);
        itemPath.add(ConfigEditor.PathSegment.index(i));
        final String summary = configEditor.summarizeValue(itemPath, currentList.get(i), maxWidth);
        tg.putString(2, currentRow++, (i + 1) + ". " + summary);
      }
    }
    currentRow++;
    currentRow = drawNumberBufferPrompt(tg, currentRow, numberBuffer);
    tg.putString(2, currentRow++, msg.getMessage("tui.config_screen.list_actions"));
    return currentRow;
  }

  private int drawEditListItem(
      final TextGraphics tg,
      final int row,
      final ConfigController.EditMode editMode,
      final MutableTextBuffer valueBuffer) {
    int currentRow = row;
    final String modeLabel =
        editMode == ConfigController.EditMode.LIST_ADD
            ? msg.getMessage("tui.config_screen.edit.add_item")
            : msg.getMessage("tui.config_screen.edit.edit_item");
    tg.putString(2, currentRow++, modeLabel);
    tg.putString(
        2, currentRow++, msg.getMessage(TuiMessageSource.CONFIG_INPUT) + " " + valueBuffer + "_");
    tg.putString(2, currentRow++, msg.getMessage(TuiMessageSource.CONFIG_ENTER_APPLY_BACK));
    return currentRow;
  }

  private int drawEditScalar(
      final TextGraphics tg,
      final int row,
      final ConfigEditor.ValueType editValueType,
      final MutableTextBuffer valueBuffer) {
    int currentRow = row;
    final String typeLabel = resolveTypeLabel(editValueType);
    tg.putString(2, currentRow++, msg.getMessage(TuiMessageSource.CONFIG_TYPE) + " " + typeLabel);
    tg.putString(
        2, currentRow++, msg.getMessage(TuiMessageSource.CONFIG_INPUT) + " " + valueBuffer + "_");
    tg.putString(2, currentRow++, msg.getMessage(TuiMessageSource.CONFIG_ENTER_APPLY_BACK));
    return currentRow;
  }

  private int drawEditUnsupported(final TextGraphics tg, final int row) {
    int currentRow = row;
    tg.putString(2, currentRow++, msg.getMessage(TuiMessageSource.CONFIG_UNSUPPORTED_MODE));
    return currentRow;
  }

  private String formatValidationLine(
      final int index, final ConfigValidationService.ValidationIssue issue, final int maxWidth) {
    final String path =
        issue.path() == null || issue.path().isBlank()
            ? msg.getMessage("tui.config_screen.validation.general_path")
            : issue.path();
    final String message = issue.message() == null ? "" : issue.message();
    final String line = index + ". " + path + " - " + message;
    if (maxWidth > 0 && line.length() > maxWidth) {
      return line.substring(0, Math.max(0, maxWidth - 3)) + "...";
    }
    return line;
  }

  private int drawChangedKeys(
      final TextGraphics tg,
      int startRow,
      final TerminalSize size,
      final ConfigEditor configEditor) {
    int row = startRow;
    if (configEditor == null || !configEditor.isDirty()) {
      return row;
    }
    final List<String> dirtyKeys = configEditor.getDirtyKeys();
    tg.putString(2, row++, msg.getMessage(TuiMessageSource.CONFIG_CHANGED_KEYS));
    final int limit = Math.min(CONFIG_CHANGED_KEYS_LIMIT, dirtyKeys.size());
    final int maxWidth = Math.max(10, size.getColumns() - 6);
    for (int i = 0; i < limit; i++) {
      String key = dirtyKeys.get(i);
      if (key.length() > maxWidth) {
        key = key.substring(0, maxWidth - 3) + "...";
      }
      tg.putString(4, row++, "- " + key);
    }
    if (dirtyKeys.size() > limit) {
      tg.putString(4, row++, msg.getMessage("tui.common.more_count", dirtyKeys.size() - limit));
    }
    tg.putString(2, row++, "");
    return row;
  }

  private String resolveTypeLabel(final ConfigEditor.ValueType valueType) {
    if (valueType == null) {
      return msg.getMessage("tui.config.value_type.value");
    }
    final String key =
        switch (valueType) {
          case BOOLEAN -> "tui.config.value_type.boolean";
          case ENUM -> "tui.config.value_type.enum";
          case INTEGER -> "tui.config.value_type.integer";
          case FLOAT -> "tui.config.value_type.float";
          case STRING -> "tui.config.value_type.string";
          case LIST -> "tui.config.value_type.list";
          case OBJECT -> "tui.config.value_type.object";
          case NULL -> "tui.config.value_type.null";
          case UNKNOWN -> "tui.config.value_type.unknown";
        };
    return msg.getMessage(key);
  }

  private boolean isErrorStatusMessage(final String statusMessage) {
    if (statusMessage == null || statusMessage.isBlank()) {
      return false;
    }
    final String lower = statusMessage.toLowerCase(java.util.Locale.ROOT);
    if (lower.contains("invalid")
        || lower.contains("failed")
        || lower.contains("error")
        || statusMessage.contains("無効")
        || statusMessage.contains("失敗")
        || statusMessage.contains("エラー")) {
      return true;
    }
    for (final String key : ERROR_MESSAGE_KEYS) {
      if (statusMessage.equals(msg.getMessage(key))) {
        return true;
      }
    }
    return false;
  }
}
