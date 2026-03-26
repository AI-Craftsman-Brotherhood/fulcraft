package com.craftsmanbro.fulcraft.ui.tui;

import com.craftsmanbro.fulcraft.ui.tui.controller.ConfigController;
import com.craftsmanbro.fulcraft.ui.tui.controller.ConfigController.EditMode;
import com.craftsmanbro.fulcraft.ui.tui.i18n.TuiMessageSource;
import com.craftsmanbro.fulcraft.ui.tui.state.StateMachine;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import java.io.IOException;
import java.util.Objects;

/**
 * TUI のキー入力処理を担当するハンドラー。
 *
 * <p>TuiApplication の肥大化を防ぐため、キー入力に関するロジックをこのクラスに委譲します。 各状態に応じた入力処理を実行し、必要に応じて状態遷移や画面更新をトリガーします。
 */
public class TuiInputHandler {

  private static final char KEY_SAVE = 'S';

  private static final char KEY_VALIDATE = 'V';

  private static final char KEY_BACK = 'B';

  private static final char KEY_QUIT = 'Q';

  private static final char KEY_LIST_ADD = 'A';

  private static final char KEY_LIST_DELETE = 'D';

  private static final char KEY_CONFIRM_YES = 'Y';

  private static final char KEY_CONFIRM_NO = 'N';

  private static final char SPACE_CHAR = ' ';

  private static final int DEFAULT_ISSUE_INDEX = 1;

  @FunctionalInterface
  public interface RedrawCallback {
    void redraw() throws IOException;
  }

  private final StateMachine stateMachine;
  private final ConfigController configController;
  private final RedrawCallback redrawCallback;
  private final TuiMessageSource messageSource;

  public TuiInputHandler(
      StateMachine stateMachine, ConfigController configController, RedrawCallback redrawCallback) {
    this(stateMachine, configController, redrawCallback, TuiMessageSource.getDefault());
  }

  public TuiInputHandler(
      StateMachine stateMachine,
      ConfigController configController,
      RedrawCallback redrawCallback,
      TuiMessageSource messageSource) {
    this.stateMachine = Objects.requireNonNull(stateMachine, "stateMachine");
    this.configController = configController;
    this.redrawCallback = Objects.requireNonNull(redrawCallback, "redrawCallback");
    this.messageSource = Objects.requireNonNull(messageSource, "messageSource");
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Config Category Input
  // ─────────────────────────────────────────────────────────────────────────────

  /**
   * CONFIG_CATEGORY 状態でのキー入力を処理します。
   *
   * @param keyStroke 入力されたキー
   * @throws IOException 描画エラーが発生した場合
   */
  public void handleConfigCategoryInput(KeyStroke keyStroke) throws IOException {
    if (configController == null) {
      return;
    }
    if (configController.isExitConfirmation()) {
      handleConfigExitConfirmation(keyStroke);
      return;
    }
    switch (keyStroke.getKeyType()) {
      case Enter -> {
        final Integer selection = configController.consumeNumber();
        if (selection == null) {
          configController.setStatusMessage(
              messageSource.getMessage(TuiMessageSource.INPUT_HINT_ENTER_CATEGORY));
        } else {
          configController.selectCategory(selection);
        }
        redrawCallback.redraw();
      }
      case Backspace -> {
        configController.trimNumber();
        redrawCallback.redraw();
      }
      case Character -> {
        final char c = keyStroke.getCharacter();
        final char upper = Character.toUpperCase(c);
        if (upper == KEY_SAVE) {
          saveConfig();
          redrawCallback.redraw();
          return;
        }
        if (upper == KEY_VALIDATE) {
          configController.runValidation(
              stateMachine.getCurrentState(),
              messageSource.getMessage(TuiMessageSource.CONFIG_VALIDATION_FAILED_MSG));
          redrawCallback.redraw();
          return;
        }
        if (upper == KEY_QUIT || upper == KEY_BACK) {
          configController.attemptExit();
          redrawCallback.redraw();
          return;
        }
        if (Character.isDigit(c)) {
          configController.appendNumber(c);
          redrawCallback.redraw();
        }
      }
      default -> {
        // Ignore other key types in category selection
      }
    }
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Config Items Input
  // ─────────────────────────────────────────────────────────────────────────────

  /**
   * CONFIG_ITEMS 状態でのキー入力を処理します。
   *
   * @param keyStroke 入力されたキー
   * @throws IOException 描画エラーが発生した場合
   */
  public void handleConfigItemsInput(KeyStroke keyStroke) throws IOException {
    if (configController == null) {
      return;
    }
    if (configController.isExitConfirmation()) {
      handleConfigExitConfirmation(keyStroke);
      return;
    }
    switch (keyStroke.getKeyType()) {
      case Enter -> {
        final Integer selection = configController.consumeNumber();
        if (selection == null) {
          configController.setStatusMessage(
              messageSource.getMessage(TuiMessageSource.INPUT_HINT_ENTER_ITEM));
        } else {
          configController.selectItem(selection);
        }
        redrawCallback.redraw();
      }
      case Backspace -> {
        configController.trimNumber();
        redrawCallback.redraw();
      }
      case Character -> {
        final char c = keyStroke.getCharacter();
        final char upper = Character.toUpperCase(c);
        if (upper == KEY_SAVE) {
          saveConfig();
          redrawCallback.redraw();
          return;
        }
        if (upper == KEY_VALIDATE) {
          configController.runValidation(
              stateMachine.getCurrentState(),
              messageSource.getMessage(TuiMessageSource.CONFIG_VALIDATION_FAILED_MSG));
          redrawCallback.redraw();
          return;
        }
        if (upper == KEY_QUIT || upper == KEY_BACK) {
          configController.backFromItems();
          redrawCallback.redraw();
          return;
        }
        if (Character.isDigit(c)) {
          configController.appendNumber(c);
          redrawCallback.redraw();
        }
      }
      default -> {
        // Ignore other key types in items selection
      }
    }
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Config Edit Input
  // ─────────────────────────────────────────────────────────────────────────────

  /**
   * CONFIG_EDIT 状態でのキー入力を処理します。
   *
   * @param keyStroke 入力されたキー
   * @throws IOException 描画エラーが発生した場合
   */
  public void handleConfigEditInput(KeyStroke keyStroke) throws IOException {
    if (configController == null) {
      return;
    }
    if (configController.isExitConfirmation()) {
      handleConfigExitConfirmation(keyStroke);
      return;
    }
    final EditMode mode = configController.getEditMode();
    if (mode == null) {
      return;
    }
    // V キーによるバリデーション（BOOLEAN/ENUM/LIST のみ）
    if (keyStroke.getKeyType() == KeyType.Character) {
      final char upper = Character.toUpperCase(keyStroke.getCharacter());
      if (upper == KEY_VALIDATE
          && (mode == EditMode.BOOLEAN || mode == EditMode.ENUM || mode == EditMode.LIST)) {
        configController.runValidation(
            stateMachine.getCurrentState(),
            messageSource.getMessage(TuiMessageSource.CONFIG_VALIDATION_FAILED_MSG));
        redrawCallback.redraw();
        return;
      }
    }
    switch (mode) {
      case BOOLEAN -> handleConfigBooleanInput(keyStroke);
      case ENUM -> handleConfigEnumInput(keyStroke);
      case SCALAR -> handleConfigScalarInput(keyStroke);
      case LIST -> handleConfigListInput(keyStroke);
      case LIST_ITEM, LIST_ADD -> handleConfigListItemInput(keyStroke);
    }
  }

  private void handleConfigBooleanInput(KeyStroke keyStroke) throws IOException {
    if (keyStroke.getKeyType() != KeyType.Character) {
      return;
    }
    final char c = keyStroke.getCharacter();
    final char upper = Character.toUpperCase(c);
    if (upper == KEY_SAVE) {
      saveConfig();
      redrawCallback.redraw();
      return;
    }
    if (upper == KEY_BACK || upper == KEY_QUIT) {
      configController.backFromEdit();
      redrawCallback.redraw();
      return;
    }
    if (c == SPACE_CHAR) {
      configController.toggleBoolean();
      redrawCallback.redraw();
    }
  }

  private void handleConfigEnumInput(KeyStroke keyStroke) throws IOException {
    switch (keyStroke.getKeyType()) {
      case Enter -> {
        final Integer selection = configController.consumeNumber();
        if (selection == null) {
          configController.setStatusMessage(
              messageSource.getMessage(TuiMessageSource.INPUT_HINT_ENTER_SELECTION));
        } else {
          configController.applyEnumSelection(selection);
        }
        redrawCallback.redraw();
      }
      case Backspace -> {
        configController.trimNumber();
        redrawCallback.redraw();
      }
      case Character -> {
        final char c = keyStroke.getCharacter();
        final char upper = Character.toUpperCase(c);
        if (upper == KEY_SAVE) {
          saveConfig();
          redrawCallback.redraw();
          return;
        }
        if (upper == KEY_BACK || upper == KEY_QUIT) {
          configController.backFromEdit();
          redrawCallback.redraw();
          return;
        }
        if (Character.isDigit(c)) {
          configController.appendNumber(c);
          redrawCallback.redraw();
        }
      }
      default -> {
        // Ignore other key types in enum selection
      }
    }
  }

  private void handleConfigScalarInput(KeyStroke keyStroke) throws IOException {
    final MutableTextBuffer buffer = configController.getValueBuffer();
    switch (keyStroke) {
      case KeyStroke ks when ks.getKeyType() == KeyType.Enter -> {
        configController.applyScalarInput();
        redrawCallback.redraw();
      }
      case KeyStroke ks when ks.getKeyType() == KeyType.Backspace && !buffer.isEmpty() -> {
        buffer.deleteCharAt(buffer.length() - 1);
        redrawCallback.redraw();
      }
      case KeyStroke ks when ks.getKeyType() == KeyType.Character -> {
        final char c = ks.getCharacter();
        if ((c == KEY_BACK || c == KEY_QUIT) && buffer.isEmpty()) {
          configController.backFromEdit();
          redrawCallback.redraw();
          return;
        }
        buffer.append(c);
        redrawCallback.redraw();
      }
      default -> {
        // Ignore other key types in scalar input
      }
    }
  }

  private void handleConfigListInput(KeyStroke keyStroke) throws IOException {
    switch (keyStroke.getKeyType()) {
      case Enter -> {
        final Integer selection = configController.consumeNumber();
        if (selection == null) {
          configController.setStatusMessage(
              messageSource.getMessage(TuiMessageSource.INPUT_HINT_ENTER_LIST_ITEM));
        } else {
          configController.selectListItem(selection);
        }
        redrawCallback.redraw();
      }
      case Backspace -> {
        configController.trimNumber();
        redrawCallback.redraw();
      }
      case Character -> {
        final char c = keyStroke.getCharacter();
        final char upper = Character.toUpperCase(c);
        if (upper == KEY_SAVE) {
          saveConfig();
          redrawCallback.redraw();
          return;
        }
        if (upper == KEY_BACK || upper == KEY_QUIT) {
          configController.backFromEdit();
          redrawCallback.redraw();
          return;
        }
        if (upper == KEY_LIST_ADD) {
          configController.beginListAdd();
          redrawCallback.redraw();
          return;
        }
        if (upper == KEY_LIST_DELETE) {
          configController.deleteListItem();
          redrawCallback.redraw();
          return;
        }
        if (Character.isDigit(c)) {
          configController.appendNumber(c);
          redrawCallback.redraw();
        }
      }
      default -> {
        // Ignore other key types in list operations
      }
    }
  }

  private void handleConfigListItemInput(KeyStroke keyStroke) throws IOException {
    final MutableTextBuffer buffer = configController.getValueBuffer();
    final EditMode mode = configController.getEditMode();
    switch (keyStroke) {
      case KeyStroke ks when ks.getKeyType() == KeyType.Enter -> {
        if (mode == EditMode.LIST_ITEM) {
          configController.applyListItemEdit();
        } else {
          configController.applyListAdd();
        }
        redrawCallback.redraw();
      }
      case KeyStroke ks when ks.getKeyType() == KeyType.Backspace && !buffer.isEmpty() -> {
        buffer.deleteCharAt(buffer.length() - 1);
        redrawCallback.redraw();
      }
      case KeyStroke ks when ks.getKeyType() == KeyType.Character -> {
        final char c = ks.getCharacter();
        if ((c == KEY_BACK || c == KEY_QUIT) && buffer.isEmpty()) {
          // Return to LIST mode
          configController.backFromEdit();
          configController.beginEdit(configController.getEditPath());
          redrawCallback.redraw();
          return;
        }
        buffer.append(c);
        redrawCallback.redraw();
      }
      default -> {
        // Ignore other key types in list item input
      }
    }
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Config Validation Input
  // ─────────────────────────────────────────────────────────────────────────────

  /**
   * CONFIG_VALIDATE 状態でのキー入力を処理します。
   *
   * @param keyStroke 入力されたキー
   * @throws IOException 描画エラーが発生した場合
   */
  public void handleConfigValidationInput(KeyStroke keyStroke) throws IOException {
    if (configController == null) {
      return;
    }
    switch (keyStroke.getKeyType()) {
      case Enter -> {
        final var issues = configController.getValidationIssues();
        if (issues == null || issues.isEmpty()) {
          configController.exitValidation();
        } else {
          final Integer selection = configController.consumeNumber();
          final int index = selection != null ? selection : DEFAULT_ISSUE_INDEX;
          configController.jumpToIssue(index);
        }
        redrawCallback.redraw();
      }
      case Backspace -> {
        configController.trimNumber();
        redrawCallback.redraw();
      }
      case Character -> {
        final char c = keyStroke.getCharacter();
        final char upper = Character.toUpperCase(c);
        if (upper == KEY_BACK || upper == KEY_QUIT) {
          configController.exitValidation();
          redrawCallback.redraw();
          return;
        }
        if (Character.isDigit(c)) {
          configController.appendNumber(c);
          redrawCallback.redraw();
        }
      }
      default -> {
        // Ignore other key types in validation screen
      }
    }
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Exit Confirmation
  // ─────────────────────────────────────────────────────────────────────────────

  private void handleConfigExitConfirmation(KeyStroke keyStroke) throws IOException {
    if (keyStroke.getKeyType() != KeyType.Character) {
      return;
    }
    final char c = Character.toUpperCase(keyStroke.getCharacter());
    switch (c) {
      case KEY_CONFIRM_YES -> {
        final boolean saved =
            configController.saveAll(
                stateMachine.getCurrentState(),
                messageSource.getMessage(TuiMessageSource.INPUT_ERROR_VALIDATION_FAILED_SAVE));
        if (saved && !configController.hasDirtyEditors()) {
          configController.exit();
        }
      }
      case KEY_CONFIRM_NO -> {
        configController.discardAll();
        configController.exit();
      }
      case KEY_BACK, KEY_QUIT -> configController.cancelExit();
      default -> {
        // Ignore other characters during exit confirmation
      }
    }
    redrawCallback.redraw();
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Helpers
  // ─────────────────────────────────────────────────────────────────────────────

  private void saveConfig() throws IOException {
    if (configController == null) {
      return;
    }
    // バリデーションを先に実行
    final boolean valid =
        configController.runValidation(
            stateMachine.getCurrentState(),
            messageSource.getMessage(TuiMessageSource.INPUT_ERROR_VALIDATION_FAILED_SAVE));
    if (!valid) {
      return;
    }
    configController.save();
  }
}
