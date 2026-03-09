package com.craftsmanbro.fulcraft.ui.tui.i18n;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * TUI コンポーネント用のメッセージソース。
 *
 * <p>ResourceBundle を使用して、TUI の文字列を国際化します。 messages.properties および messages_ja.properties
 * からメッセージを取得します。
 */
public class TuiMessageSource {

  private static final String MESSAGE_BUNDLE_NAME = "messages";

  private ResourceBundle bundle;

  private Locale locale;

  /** デフォルトロケールで TuiMessageSource を作成します。 */
  public TuiMessageSource() {
    this(Locale.getDefault());
  }

  /**
   * 指定されたロケールで TuiMessageSource を作成します。
   *
   * @param locale 使用するロケール
   */
  public TuiMessageSource(final Locale locale) {
    applyLocale(locale);
  }

  /**
   * デフォルトのインスタンスを取得します。
   *
   * @return デフォルトの TuiMessageSource
   */
  public static TuiMessageSource getDefault() {
    return new TuiMessageSource(Locale.getDefault());
  }

  /**
   * メッセージを取得します。
   *
   * @param key メッセージキー
   * @return ローカライズされたメッセージ、見つからない場合はキー自体
   */
  public String getMessage(final String key) {
    return getMessage(key, (Object[]) null);
  }

  /**
   * 引数付きでメッセージを取得します。
   *
   * @param key メッセージキー
   * @param args フォーマット引数
   * @return ローカライズされたメッセージ、見つからない場合はキー自体
   */
  public String getMessage(final String key, final Object... args) {
    if (key == null) {
      return "";
    }
    final String messagePattern = resolveMessagePattern(key);
    if (messagePattern == null) {
      return key;
    }
    return formatMessage(messagePattern, args);
  }

  /**
   * 現在のロケールを取得します。
   *
   * @return 現在のロケール
   */
  public Locale getLocale() {
    return locale;
  }

  /**
   * ロケールを変更します。
   *
   * @param locale 新しいロケール
   */
  public void setLocale(final Locale locale) {
    applyLocale(locale);
  }

  /** バンドルを再読み込みします。 */
  public void reload() {
    bundle = loadBundle(locale);
  }

  private void applyLocale(final Locale locale) {
    this.locale = resolveLocale(locale);
    bundle = loadBundle(this.locale);
  }

  private String resolveMessagePattern(final String key) {
    if (bundle == null) {
      return null;
    }
    try {
      return bundle.getString(key);
    } catch (MissingResourceException e) {
      return null;
    }
  }

  private ResourceBundle loadBundle(final Locale locale) {
    try {
      return ResourceBundle.getBundle(MESSAGE_BUNDLE_NAME, locale);
    } catch (MissingResourceException e) {
      return null;
    }
  }

  private String formatMessage(final String messagePattern, final Object... args) {
    if (args == null || args.length == 0) {
      return messagePattern;
    }
    try {
      final MessageFormat messageFormatter = new MessageFormat(messagePattern, locale);
      return messageFormatter.format(args);
    } catch (IllegalArgumentException e) {
      // フォーマット指定が不正でも、未加工の文言は表示できるようにします。
      return messagePattern;
    }
  }

  private static Locale resolveLocale(final Locale locale) {
    return locale != null ? locale : Locale.getDefault();
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // TUI-specific message keys
  // ─────────────────────────────────────────────────────────────────────────────
  // Home screen
  public static final String HOME_TITLE = "tui.home.title";

  public static final String HOME_WELCOME = "tui.home.welcome";

  public static final String HOME_MENU_HINT = "tui.home.menu_hint";

  // Chat input
  public static final String CHAT_PROMPT = "tui.chat.prompt";

  public static final String CHAT_HINT = "tui.chat.hint";

  public static final String CHAT_TITLE = "tui.chat.title";

  public static final String CHAT_COMMANDS_HINT = "tui.chat.commands_hint";

  // Plan review
  public static final String PLAN_TITLE = "tui.plan.title";

  public static final String PLAN_APPROVE = "tui.plan.approve";

  public static final String PLAN_EDIT = "tui.plan.edit";

  public static final String PLAN_QUIT = "tui.plan.quit";

  // Config editor
  public static final String CONFIG_TITLE = "tui.config.title";

  public static final String CONFIG_CATEGORIES = "tui.config.categories";

  public static final String CONFIG_ITEMS = "tui.config.items";

  public static final String CONFIG_EDIT = "tui.config.edit";

  public static final String CONFIG_SAVE = "tui.config.save";

  public static final String CONFIG_SAVED = "tui.config.saved";

  public static final String CONFIG_DISCARD = "tui.config.discard";

  public static final String CONFIG_DISCARDED = "tui.config.discarded";

  public static final String CONFIG_UPDATED = "tui.config.updated";

  public static final String CONFIG_VALIDATION_PASSED = "tui.config.validation_passed";

  public static final String CONFIG_VALIDATION_FAILED = "tui.config.validation_failed";

  public static final String CONFIG_EXIT_CONFIRM = "tui.config.exit_confirm";

  public static final String CONFIG_INVALID_NUMBER = "tui.config.invalid_number";

  public static final String CONFIG_NOT_FOUND = "tui.config.not_found";

  public static final String CONFIG_EDITOR_NOT_INIT = "tui.config.editor_not_init";

  public static final String CONFIG_ENTER_CATEGORY = "tui.config.enter_category";

  public static final String CONFIG_PATH = "tui.config.path";

  public static final String CONFIG_NO_ENTRIES = "tui.config.no_entries";

  public static final String CONFIG_SELECT_NUMBER = "tui.config.select_number";

  public static final String CONFIG_ENTER_ITEM = "tui.config.enter_item";

  public static final String CONFIG_NO_SELECTION = "tui.config.no_selection";

  public static final String CONFIG_TYPE = "tui.config.type";

  public static final String CONFIG_CURRENT = "tui.config.current";

  public static final String CONFIG_SPACE_TOGGLE = "tui.config.space_toggle";

  public static final String CONFIG_INPUT = "tui.config.input";

  public static final String CONFIG_ENTER_APPLY_BACK = "tui.config.enter_apply_back";

  public static final String CONFIG_VALIDATION_TITLE = "tui.config.validation_title";

  public static final String CONFIG_NO_VALIDATION_ERRORS = "tui.config.no_validation_errors";

  public static final String CONFIG_ERRORS_LABEL = "tui.config.errors_label";

  public static final String CONFIG_JUMP_ERROR = "tui.config.jump_error";

  public static final String CONFIG_CHANGED_KEYS = "tui.config.changed_keys";

  public static final String CONFIG_UNSUPPORTED_MODE = "tui.config.unsupported_mode";

  public static final String CONFIG_EXTERNAL_CHANGED = "tui.config.external_changed";

  public static final String CONFIG_RELOADED = "tui.config.reloaded";

  // Conflict Policy
  public static final String CONFLICT_SELECTION_TITLE = "tui.conflict.selection_title";

  public static final String CONFLICT_NO_CONFLICTS = "tui.conflict.no_conflicts";

  public static final String CONFLICT_CONFLICTS_FOUND = "tui.conflict.conflicts_found";

  public static final String CONFLICT_OVERWRITE_LIST = "tui.conflict.overwrite_list";

  public static final String CONFLICT_SELECT_POLICY = "tui.conflict.select_policy";

  public static final String CONFLICT_SAFE_LABEL = "tui.conflict.safe_label";

  public static final String CONFLICT_SAFE_DESC = "tui.conflict.safe_desc";

  public static final String CONFLICT_SKIP_LABEL = "tui.conflict.skip_label";

  public static final String CONFLICT_SKIP_DESC = "tui.conflict.skip_desc";

  public static final String CONFLICT_OVERWRITE_LABEL = "tui.conflict.overwrite_label";

  public static final String CONFLICT_OVERWRITE_DESC1 = "tui.conflict.overwrite_desc1";

  public static final String CONFLICT_OVERWRITE_DESC2 = "tui.conflict.overwrite_desc2";

  public static final String CONFLICT_OTHER_OPTIONS = "tui.conflict.other_options";

  public static final String CONFLICT_BACK = "tui.conflict.back";

  public static final String CONFLICT_QUIT = "tui.conflict.quit";

  public static final String CONFLICT_WARNING_BOX_TITLE = "tui.conflict.warning_box_title";

  public static final String CONFLICT_WARNING_BOX_MESSAGE = "tui.conflict.warning_box_message";

  public static final String CONFLICT_WARNING_BOX_DESC1 = "tui.conflict.warning_box_desc1";

  public static final String CONFLICT_WARNING_BOX_DESC2 = "tui.conflict.warning_box_desc2";

  public static final String CONFLICT_CONFIRM_QUESTION = "tui.conflict.confirm_question";

  public static final String CONFLICT_CONFIRM_YES = "tui.conflict.confirm_yes";

  public static final String CONFLICT_CONFIRM_NO = "tui.conflict.confirm_no";

  // Execution
  public static final String EXEC_TITLE = "tui.exec.title";

  public static final String EXEC_RUNNING = "tui.exec.running";

  public static final String EXEC_COMPLETED = "tui.exec.completed";

  public static final String EXEC_FAILED = "tui.exec.failed";

  public static final String EXEC_CANCELLED = "tui.exec.cancelled";

  public static final String EXEC_CANCEL_HINT = "tui.exec.cancel_hint";

  public static final String EXEC_TITLE_RUNNING = "tui.exec.title_running";

  public static final String EXEC_STAGE = "tui.exec.stage";

  public static final String EXEC_PROGRESS = "tui.exec.progress";

  public static final String EXEC_FINISH_HINT = "tui.exec.finish_hint";

  public static final String EXEC_CONTROLS_HINT = "tui.exec.controls_hint";

  // Summary
  public static final String SUMMARY_TITLE = "tui.summary.title";

  public static final String SUMMARY_SUCCESS = "tui.summary.success";

  public static final String SUMMARY_PARTIAL = "tui.summary.partial";

  public static final String SUMMARY_FAILED = "tui.summary.failed";

  public static final String SUMMARY_GENERATED_FILES = "tui.summary.generated_files";

  public static final String SUMMARY_SUCCESS_MSG = "tui.summary.success_msg";

  public static final String SUMMARY_CANCELLED_MSG = "tui.summary.cancelled_msg";

  public static final String SUMMARY_FAILED_MSG = "tui.summary.failed_msg";

  public static final String SUMMARY_ERROR_PREFIX = "tui.summary.error_prefix";

  public static final String SUMMARY_STATUS_PREFIX = "tui.summary.status_prefix";

  public static final String SUMMARY_STAGES_COMPLETED = "tui.summary.stages_completed";

  public static final String SUMMARY_NO_FILES = "tui.summary.no_files";

  public static final String SUMMARY_GENERATED_FILES_LABEL = "tui.summary.generated_files_label";

  public static final String SUMMARY_TIP1 = "tui.summary.tip1";

  public static final String SUMMARY_TIP2 = "tui.summary.tip2";

  public static final String SUMMARY_FOOTER = "tui.summary.footer";

  // Issue Handling
  public static final String ISSUE_TITLE = "tui.issue.title";

  public static final String ISSUE_RETRY = "tui.issue.retry";

  public static final String ISSUE_SKIP = "tui.issue.skip";

  public static final String ISSUE_ABORT = "tui.issue.abort";

  public static final String ISSUE_TITLE_DETECTED = "tui.issue.title_detected";

  public static final String ISSUE_NO_ISSUE = "tui.issue.no_issue";

  public static final String ISSUE_QUIT = "tui.issue.quit";

  public static final String ISSUE_CATEGORY = "tui.issue.category";

  public static final String ISSUE_TARGET = "tui.issue.target";

  public static final String ISSUE_STAGE = "tui.issue.stage";

  public static final String ISSUE_CAUSE = "tui.issue.cause";

  public static final String ISSUE_SELECT_OPTION = "tui.issue.select_option";

  public static final String ISSUE_FOOTER = "tui.issue.footer";

  // Common
  public static final String COMMON_BACK = "tui.common.back";

  public static final String COMMON_QUIT = "tui.common.quit";

  public static final String COMMON_ENTER = "tui.common.enter";

  public static final String COMMON_ERROR = "tui.common.error";

  public static final String COMMON_SUCCESS = "tui.common.success";

  // Config Errors & Status (Added for Refactoring)
  public static final String CONFIG_ERROR_INVALID_CATEGORY = "tui.config.error.invalid_category";

  public static final String CONFIG_ERROR_INVALID_ITEM = "tui.config.error.invalid_item";

  public static final String CONFIG_ERROR_INVALID_ENUM = "tui.config.error.invalid_enum";

  public static final String CONFIG_ERROR_VALUE_REQUIRED = "tui.config.error.value_required";

  public static final String CONFIG_ERROR_ENTER_ITEM_DELETE =
      "tui.config.error.enter_item_for_delete";

  public static final String CONFIG_ERROR_INVALID_LIST_ITEM = "tui.config.error.invalid_list_item";

  public static final String CONFIG_SUCCESS_UPDATED = "tui.config.success.updated";

  public static final String CONFIG_ERROR_UPDATE_FAILED = "tui.config.error.update_failed";

  public static final String CONFIG_SUCCESS_ITEM_UPDATED = "tui.config.success.item_updated";

  public static final String CONFIG_ERROR_ITEM_UPDATE_FAILED =
      "tui.config.error.item_update_failed";

  public static final String CONFIG_SUCCESS_ITEM_ADDED = "tui.config.success.item_added";

  public static final String CONFIG_ERROR_ITEM_ADD_FAILED = "tui.config.error.item_add_failed";

  public static final String CONFIG_SUCCESS_ITEM_DELETED = "tui.config.success.item_deleted";

  public static final String CONFIG_ERROR_ITEM_DELETE_FAILED =
      "tui.config.error.item_delete_failed";

  public static final String CONFIG_ERROR_EDITOR_NOT_AVAILABLE =
      "tui.config.error.editor_not_available";

  public static final String CONFIG_VALIDATION_PASSED_MSG = "tui.config.validation.passed";

  public static final String CONFIG_VALIDATION_FAILED_MSG = "tui.config.validation.failed";

  public static final String CONFIG_ERROR_NO_PATH = "tui.config.error.no_path";

  public static final String CONFIG_SUCCESS_SAVED = "tui.config.success.saved";

  public static final String CONFIG_SUCCESS_DISCARDED = "tui.config.success.discarded";

  // Input Hints (Added for Refactoring)
  public static final String INPUT_HINT_ENTER_CATEGORY = "tui.input.hint.enter_category";

  public static final String INPUT_HINT_ENTER_ITEM = "tui.input.hint.enter_item";

  public static final String INPUT_HINT_ENTER_SELECTION = "tui.input.hint.enter_selection";

  public static final String INPUT_HINT_ENTER_LIST_ITEM = "tui.input.hint.enter_list_item";

  public static final String INPUT_ERROR_INVALID_ERROR_NUMBER =
      "tui.input.error.invalid_error_number";

  public static final String INPUT_ERROR_VALIDATION_FAILED_SAVE =
      "tui.input.error.validation_failed_save";

  // Header Hints (Added for Refactoring)
  public static final String HEADER_QUIT_HINT = "tui.header.hint.quit";

  public static final String HEADER_CONFIG_HINT = "tui.header.hint.config";

  public static final String HEADER_CONFIG_VALIDATE_HINT = "tui.header.hint.validate";

  public static final String HEADER_TITLE_PREFIX = "tui.header.title_prefix";

  public static final String HEADER_DIRTY = "tui.header.dirty";
}
