package com.craftsmanbro.fulcraft.ui.tui.controller;

import com.craftsmanbro.fulcraft.config.plugin.PluginConfigPaths;
import com.craftsmanbro.fulcraft.infrastructure.collection.contract.CollectionServicePort;
import com.craftsmanbro.fulcraft.infrastructure.collection.impl.DefaultCollectionService;
import com.craftsmanbro.fulcraft.ui.tui.MutableTextBuffer;
import com.craftsmanbro.fulcraft.ui.tui.PathParser;
import com.craftsmanbro.fulcraft.ui.tui.config.ConfigEditor;
import com.craftsmanbro.fulcraft.ui.tui.config.ConfigSchemaIndex;
import com.craftsmanbro.fulcraft.ui.tui.config.ConfigValidationService;
import com.craftsmanbro.fulcraft.ui.tui.config.MetadataRegistry;
import com.craftsmanbro.fulcraft.ui.tui.config.PluginConfigEntry;
import com.craftsmanbro.fulcraft.ui.tui.config.PluginConfigIndex;
import com.craftsmanbro.fulcraft.ui.tui.i18n.TuiMessageSource;
import com.craftsmanbro.fulcraft.ui.tui.state.State;
import com.craftsmanbro.fulcraft.ui.tui.state.StateMachine;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Config Editor の状態とロジックを管理するコントローラー。
 *
 * <p>TuiApplication の肥大化を防ぐため、設定編集に関連するすべての状態と操作をこのクラスに委譲します。
 */
public class ConfigController {

  /** 編集モード */
  public enum EditMode {
    BOOLEAN,
    ENUM,
    SCALAR,
    LIST,
    LIST_ITEM,
    LIST_ADD
  }

  private final StateMachine stateMachine;

  private final MetadataRegistry metadataRegistry;

  private final ConfigValidationService validationService;

  private final TuiMessageSource messageSource;

  private static final String CATEGORY_PLUGINS = "plugins";

  private static final String CATEGORY_ADVANCED = "advanced";

  private static final String CONFIG_JSON_FILE = "config.json";

  private static final List<String> CATEGORIES = buildCategories();

  private static final Set<String> ROOT_CATEGORIES = Set.copyOf(ConfigEditor.CATEGORIES);

  private static final long EXTERNAL_CHECK_INTERVAL_MS = 1000;

  private final CollectionServicePort collectionService = new DefaultCollectionService();

  private final PluginConfigIndex pluginConfigIndex = new PluginConfigIndex();

  private final Map<String, ConfigEditor> pluginEditors = new HashMap<>();

  private List<PluginConfigEntry> pluginEntries = List.of();

  private Map<String, PluginConfigEntry> pluginEntryMap = Map.of();

  private ConfigEditor rootEditor;

  private String activePluginId;

  private Path projectRoot;

  private ExternalSnapshot lastExternalSnapshot;

  private boolean externalChangePending;

  private long lastExternalCheckMs;

  // Config editor state
  private ConfigEditor configEditor;

  private String category;

  private final List<ConfigEditor.PathSegment> path = new ArrayList<>();

  private List<ConfigEditor.PathSegment> editPath;

  private EditMode editMode;

  private ConfigEditor.ValueType editValueType;

  private final MutableTextBuffer numberBuffer = new MutableTextBuffer();

  private final MutableTextBuffer valueBuffer = new MutableTextBuffer();

  private List<String> enumOptions = List.of();

  private int listEditIndex = -1;

  private String statusMessage;

  private boolean exitConfirmation;

  private State returnState = State.CHAT_INPUT;

  private State validationReturnState = State.CONFIG_CATEGORY;

  private List<ConfigValidationService.ValidationIssue> validationIssues = List.of();

  private record ExternalSnapshot(
      List<PluginConfigEntry> pluginEntries, Map<Path, Long> fileTimes) {}

  private record DirtyConfig(String pluginId, ConfigEditor editor) {}

  private static List<String> buildCategories() {
    final List<String> categories = new ArrayList<>(ConfigEditor.CATEGORIES);
    categories.add(CATEGORY_PLUGINS);
    categories.add(CATEGORY_ADVANCED);
    return List.copyOf(categories);
  }

  public ConfigController(
      final StateMachine stateMachine,
      final MetadataRegistry metadataRegistry,
      final ConfigValidationService validationService) {
    this(stateMachine, metadataRegistry, validationService, TuiMessageSource.getDefault());
  }

  public ConfigController(
      final StateMachine stateMachine,
      final MetadataRegistry metadataRegistry,
      final ConfigValidationService validationService,
      final TuiMessageSource messageSource) {
    this.stateMachine = Objects.requireNonNull(stateMachine, "stateMachine");
    this.metadataRegistry = Objects.requireNonNull(metadataRegistry, "metadataRegistry");
    this.validationService = Objects.requireNonNull(validationService, "validationService");
    this.messageSource = Objects.requireNonNull(messageSource, "messageSource");
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // State accessors (for Screens)
  // ─────────────────────────────────────────────────────────────────────────────
  public ConfigEditor getConfigEditor() {
    return configEditor;
  }

  public List<String> getCategories() {
    return CATEGORIES;
  }

  public String getCategory() {
    return category;
  }

  public String getActivePluginId() {
    return activePluginId;
  }

  public List<ConfigEditor.PathSegment> getPath() {
    return List.copyOf(path);
  }

  public List<ConfigEditor.PathSegment> getEditPath() {
    return editPath != null ? List.copyOf(editPath) : List.of();
  }

  public String getPathLabel() {
    if (configEditor == null) {
      return messageSource.getMessage("tui.config_controller.path.none");
    }
    if (CATEGORY_PLUGINS.equals(category)) {
      if (activePluginId == null || activePluginId.isBlank()) {
        return CATEGORY_PLUGINS;
      }
      final String pluginPathLabel = CATEGORY_PLUGINS + "/" + activePluginId;
      if (path.isEmpty()) {
        return pluginPathLabel;
      }
      return pluginPathLabel + " > " + configEditor.formatPathForDisplay(path);
    }
    if (CATEGORY_ADVANCED.equals(category) && path.isEmpty()) {
      return CATEGORY_ADVANCED;
    }
    if (path.isEmpty()) {
      return messageSource.getMessage("tui.config_controller.path.none");
    }
    return configEditor.formatPathForDisplay(path);
  }

  public EditMode getEditMode() {
    return editMode;
  }

  public ConfigEditor.ValueType getEditValueType() {
    return editValueType;
  }

  public MutableTextBuffer getNumberBuffer() {
    return numberBuffer;
  }

  public MutableTextBuffer getValueBuffer() {
    return valueBuffer;
  }

  public List<String> getEnumOptions() {
    return enumOptions;
  }

  public int getListEditIndex() {
    return listEditIndex;
  }

  public String getStatusMessage() {
    return statusMessage;
  }

  public void setStatusMessage(final String message) {
    this.statusMessage = message;
  }

  public boolean isExitConfirmation() {
    return exitConfirmation;
  }

  public State getReturnState() {
    return returnState;
  }

  public State getValidationReturnState() {
    return validationReturnState;
  }

  public List<ConfigValidationService.ValidationIssue> getValidationIssues() {
    return validationIssues;
  }

  public List<Object> getCurrentList() {
    if (editPath == null || configEditor == null) {
      return List.of();
    }
    final Object value = configEditor.getValue(editPath);
    return value instanceof List<?> ? collectionService.toList(value) : List.of();
  }

  public List<ConfigEditor.ConfigItem> getCurrentItems() {
    if (configEditor == null) {
      return List.of();
    }
    if (CATEGORY_PLUGINS.equals(category) && activePluginId == null) {
      final List<ConfigEditor.ConfigItem> items = new ArrayList<>();
      for (final PluginConfigEntry entry : pluginEntries) {
        items.add(
            new ConfigEditor.ConfigItem(
                entry.pluginId(),
                entry.summary(),
                List.of(ConfigEditor.PathSegment.key(entry.pluginId()))));
      }
      return items;
    }
    if (CATEGORY_ADVANCED.equals(category) && path.isEmpty()) {
      return configEditor.getAdvancedTopLevelItems();
    }
    return configEditor.getItemsForPath(path);
  }

  public String formatCategorySummary(final String category, final int maxWidth) {
    if (rootEditor == null || category == null) {
      return "";
    }
    if (CATEGORY_PLUGINS.equals(category)) {
      final int pluginCount = pluginEntries.size();
      return pluginCount == 0
          ? withLeadingSpace(messageSource.getMessage("tui.config_controller.summary.empty"))
          : withLeadingSpace(
              messageSource.getMessage("tui.config_controller.summary.plugins", pluginCount));
    }
    if (CATEGORY_ADVANCED.equals(category)) {
      final int advancedKeyCount = rootEditor.getAdvancedTopLevelKeys().size();
      return advancedKeyCount == 0
          ? withLeadingSpace(messageSource.getMessage("tui.config_controller.summary.empty"))
          : withLeadingSpace(
              messageSource.getMessage("tui.config_controller.summary.keys", advancedKeyCount));
    }
    final List<ConfigEditor.PathSegment> categoryPath =
        List.of(ConfigEditor.PathSegment.key(category));
    if (!rootEditor.pathExists(categoryPath)) {
      return withLeadingSpace(messageSource.getMessage("tui.config_controller.summary.missing"));
    }
    final Object categoryValue = rootEditor.getValue(categoryPath);
    if (categoryValue instanceof Map<?, ?> map) {
      return map.isEmpty()
          ? withLeadingSpace(messageSource.getMessage("tui.config_controller.summary.empty"))
          : withLeadingSpace(
              messageSource.getMessage("tui.config_controller.summary.keys", map.size()));
    }
    final String valueSummary =
        rootEditor.summarizeValue(categoryPath, categoryValue, Math.max(10, maxWidth));
    return withLeadingSpace(
        messageSource.getMessage("tui.config_controller.summary.value", valueSummary));
  }

  private static String withLeadingSpace(final String text) {
    if (text == null || text.isBlank()) {
      return "";
    }
    return " " + text;
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Lifecycle
  // ─────────────────────────────────────────────────────────────────────────────
  public void enter(final Path projectRoot) {
    returnState = stateMachine.getCurrentState();
    this.projectRoot = projectRoot != null ? projectRoot.toAbsolutePath() : null;
    final Path configFilePath =
        this.projectRoot != null
            ? this.projectRoot.resolve(CONFIG_JSON_FILE)
            : Path.of(CONFIG_JSON_FILE);
    rootEditor = ConfigEditor.load(configFilePath);
    configEditor = rootEditor;
    activePluginId = null;
    pluginEditors.clear();
    refreshPluginEntries();
    lastExternalSnapshot = captureExternalSnapshot();
    externalChangePending = false;
    category = null;
    path.clear();
    clearEditState();
    clearValidationState();
    if (configEditor.getLoadError() != null) {
      statusMessage = configEditor.getLoadError();
    } else if (configEditor.isNewFile()) {
      statusMessage = messageSource.getMessage(TuiMessageSource.CONFIG_NOT_FOUND);
    } else {
      statusMessage = null;
    }
    stateMachine.transitionTo(State.CONFIG_CATEGORY);
  }

  public void attemptExit() {
    if (hasDirtyEditors()) {
      exitConfirmation = true;
    } else {
      exit();
    }
  }

  public void exit() {
    exitConfirmation = false;
    stateMachine.transitionTo(returnState);
  }

  public void cancelExit() {
    exitConfirmation = false;
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Category navigation
  // ─────────────────────────────────────────────────────────────────────────────
  public void selectCategory(final int selection) {
    final List<String> availableCategories = CATEGORIES;
    if (selection < 1 || selection > availableCategories.size()) {
      statusMessage = messageSource.getMessage(TuiMessageSource.CONFIG_ERROR_INVALID_CATEGORY);
      return;
    }
    category = availableCategories.get(selection - 1);
    path.clear();
    numberBuffer.setLength(0);
    clearEditState();
    statusMessage = null;
    if (CATEGORY_PLUGINS.equals(category)) {
      activePluginId = null;
      configEditor = rootEditor;
      stateMachine.transitionTo(State.CONFIG_ITEMS);
      return;
    }
    activePluginId = null;
    configEditor = rootEditor;
    if (!CATEGORY_ADVANCED.equals(category)) {
      path.add(ConfigEditor.PathSegment.key(category));
    }
    stateMachine.transitionTo(State.CONFIG_ITEMS);
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Item navigation
  // ─────────────────────────────────────────────────────────────────────────────
  public void selectItem(final int selection) {
    final List<ConfigEditor.ConfigItem> items = getCurrentItems();
    if (selection < 1 || selection > items.size()) {
      statusMessage = messageSource.getMessage(TuiMessageSource.CONFIG_ERROR_INVALID_ITEM);
      return;
    }
    if (CATEGORY_PLUGINS.equals(category) && activePluginId == null) {
      final PluginConfigEntry entry = pluginEntries.get(selection - 1);
      openPlugin(entry);
      return;
    }
    final ConfigEditor.ConfigItem item = items.get(selection - 1);
    final Object value = item.value();
    final ConfigEditor.ValueType valueType = configEditor.classifyValue(item.path(), value);
    statusMessage = null;
    if (valueType == ConfigEditor.ValueType.OBJECT) {
      path.clear();
      path.addAll(item.path());
      clearEditState();
      stateMachine.transitionTo(State.CONFIG_ITEMS);
    } else {
      beginEdit(item.path());
      stateMachine.transitionTo(State.CONFIG_EDIT);
    }
  }

  public void backFromItems() {
    numberBuffer.setLength(0);
    clearEditState();
    statusMessage = null;
    if (CATEGORY_PLUGINS.equals(category) && handlePluginsBack()) {
      return;
    }
    if (CATEGORY_ADVANCED.equals(category)) {
      handleAdvancedBack();
      return;
    }
    handleStandardBack();
  }

  private boolean handlePluginsBack() {
    if (activePluginId == null) {
      stateMachine.transitionTo(State.CONFIG_CATEGORY);
      return true;
    }
    if (path.isEmpty()) {
      activePluginId = null;
      configEditor = rootEditor;
      stateMachine.transitionTo(State.CONFIG_ITEMS);
      return true;
    }
    return false;
  }

  private void handleAdvancedBack() {
    if (path.isEmpty()) {
      stateMachine.transitionTo(State.CONFIG_CATEGORY);
      return;
    }
    if (path.size() == 1) {
      path.clear();
      stateMachine.transitionTo(State.CONFIG_ITEMS);
      return;
    }
    path.remove(path.size() - 1);
    if (pointsToList(path)) {
      beginEdit(path);
      stateMachine.transitionTo(State.CONFIG_EDIT);
    } else {
      stateMachine.transitionTo(State.CONFIG_ITEMS);
    }
  }

  private void handleStandardBack() {
    if (path.size() <= 1) {
      stateMachine.transitionTo(State.CONFIG_CATEGORY);
      return;
    }
    path.remove(path.size() - 1);
    if (pointsToList(path)) {
      beginEdit(path);
      stateMachine.transitionTo(State.CONFIG_EDIT);
    } else {
      stateMachine.transitionTo(State.CONFIG_ITEMS);
    }
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Edit operations
  // ─────────────────────────────────────────────────────────────────────────────
  public void beginEdit(final List<ConfigEditor.PathSegment> editPath) {
    clearEditState();
    this.editPath = new ArrayList<>(editPath);
    numberBuffer.setLength(0);
    valueBuffer.setLength(0);
    final Object value = configEditor.getValue(this.editPath);
    ConfigEditor.ValueType type = configEditor.classifyValue(this.editPath, value);
    final ConfigEditor.ValueType schemaType = configEditor.resolveSchemaValueType(this.editPath);
    if (schemaType != null) {
      type = schemaType;
    }
    editValueType = type;
    switch (type) {
      case BOOLEAN -> editMode = EditMode.BOOLEAN;
      case ENUM -> {
        editMode = EditMode.ENUM;
        enumOptions = configEditor.getEnumOptions(this.editPath, value);
      }
      case LIST -> editMode = EditMode.LIST;
      default -> {
        editMode = EditMode.SCALAR;
        if (value != null) {
          valueBuffer.append(configEditor.formatScalarForEdit(this.editPath, value));
        }
      }
    }
  }

  public void backFromEdit() {
    if ((editMode == EditMode.LIST_ITEM || editMode == EditMode.LIST_ADD) && editPath != null) {
      editMode = EditMode.LIST;
      listEditIndex = -1;
      valueBuffer.setLength(0);
      numberBuffer.setLength(0);
      statusMessage = null;
      stateMachine.transitionTo(State.CONFIG_EDIT);
      return;
    }
    clearEditState();
    statusMessage = null;
    numberBuffer.setLength(0);
    stateMachine.transitionTo(State.CONFIG_ITEMS);
  }

  public void toggleBoolean() {
    if (editPath == null || configEditor == null) {
      return;
    }
    final Object currentValue = configEditor.getValue(editPath);
    final boolean next = !Boolean.TRUE.equals(currentValue);
    if (updateConfigValue(editPath, next)) {
      configEditor.updateDirtyForPath(editPath);
      statusMessage = messageSource.getMessage(TuiMessageSource.CONFIG_SUCCESS_UPDATED);
    } else {
      statusMessage = messageSource.getMessage(TuiMessageSource.CONFIG_ERROR_UPDATE_FAILED);
    }
  }

  public void applyEnumSelection(final int selection) {
    if (selection < 1 || selection > enumOptions.size()) {
      statusMessage = messageSource.getMessage(TuiMessageSource.CONFIG_ERROR_INVALID_ENUM);
      return;
    }
    final String selected = enumOptions.get(selection - 1);
    if (updateConfigValue(editPath, selected)) {
      configEditor.updateDirtyForPath(editPath);
      statusMessage = messageSource.getMessage(TuiMessageSource.CONFIG_SUCCESS_UPDATED);
    } else {
      statusMessage = messageSource.getMessage(TuiMessageSource.CONFIG_ERROR_UPDATE_FAILED);
    }
    numberBuffer.setLength(0);
  }

  public void applyScalarInput() {
    final String input = valueBuffer.toString().trim();
    if (input.isEmpty()) {
      statusMessage = messageSource.getMessage(TuiMessageSource.CONFIG_ERROR_VALUE_REQUIRED);
      return;
    }
    final Object parsed = parseScalarInput(input);
    final ConfigEditor.ValidationResult validation = configEditor.validateValue(editPath, parsed);
    if (!validation.ok()) {
      statusMessage = validation.message();
      return;
    }
    if (updateConfigValue(editPath, parsed)) {
      configEditor.updateDirtyForPath(editPath);
      statusMessage = messageSource.getMessage(TuiMessageSource.CONFIG_SUCCESS_UPDATED);
      backFromEdit();
    } else {
      statusMessage = messageSource.getMessage(TuiMessageSource.CONFIG_ERROR_UPDATE_FAILED);
    }
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // List operations
  // ─────────────────────────────────────────────────────────────────────────────
  public void selectListItem(final int selection) {
    final List<?> list = getCurrentList();
    if (selection < 1 || selection > list.size()) {
      statusMessage = messageSource.getMessage(TuiMessageSource.CONFIG_ERROR_INVALID_LIST_ITEM);
      return;
    }
    listEditIndex = selection - 1;
    final Object itemValue = list.get(listEditIndex);
    final ConfigEditor.ValueType itemType = configEditor.resolveSchemaListItemType(editPath);
    if (itemType == ConfigEditor.ValueType.OBJECT || itemValue instanceof java.util.Map) {
      final List<ConfigEditor.PathSegment> itemPath = new ArrayList<>(editPath);
      itemPath.add(ConfigEditor.PathSegment.index(listEditIndex));
      path.clear();
      path.addAll(itemPath);
      clearEditState();
      stateMachine.transitionTo(State.CONFIG_ITEMS);
    } else {
      editMode = EditMode.LIST_ITEM;
      valueBuffer.setLength(0);
      if (itemValue != null) {
        final List<ConfigEditor.PathSegment> itemPath = new ArrayList<>(editPath);
        itemPath.add(ConfigEditor.PathSegment.index(listEditIndex));
        valueBuffer.append(configEditor.formatScalarForEdit(itemPath, itemValue));
      }
      statusMessage = null;
    }
    numberBuffer.setLength(0);
  }

  public void beginListAdd() {
    editMode = EditMode.LIST_ADD;
    valueBuffer.setLength(0);
    listEditIndex = -1;
    statusMessage = null;
    numberBuffer.setLength(0);
  }

  public void applyListItemEdit() {
    final String input = valueBuffer.toString().trim();
    if (input.isEmpty()) {
      statusMessage = messageSource.getMessage(TuiMessageSource.CONFIG_ERROR_VALUE_REQUIRED);
      return;
    }
    final Object parsed = parseScalarInput(input);
    final List<ConfigEditor.PathSegment> itemPath = new ArrayList<>(editPath);
    itemPath.add(ConfigEditor.PathSegment.index(listEditIndex));
    final ConfigEditor.ValidationResult validation =
        configEditor.validateListItemValue(editPath, parsed);
    if (!validation.ok()) {
      statusMessage = validation.message();
      return;
    }
    final boolean updated = configEditor.setValue(itemPath, parsed);
    if (updated) {
      configEditor.updateDirtyForPath(editPath);
      statusMessage = messageSource.getMessage(TuiMessageSource.CONFIG_SUCCESS_ITEM_UPDATED);
      editMode = EditMode.LIST;
      listEditIndex = -1;
      valueBuffer.setLength(0);
    } else {
      statusMessage = messageSource.getMessage(TuiMessageSource.CONFIG_ERROR_ITEM_UPDATE_FAILED);
    }
  }

  public void applyListAdd() {
    final String input = valueBuffer.toString().trim();
    if (input.isEmpty()) {
      statusMessage = messageSource.getMessage(TuiMessageSource.CONFIG_ERROR_VALUE_REQUIRED);
      return;
    }
    final Object parsed = parseScalarInput(input);
    final ConfigEditor.ValidationResult validation =
        configEditor.validateListItemValue(editPath, parsed);
    if (!validation.ok()) {
      statusMessage = validation.message();
      return;
    }
    final boolean added = configEditor.appendListValueWithCreate(editPath, parsed);
    if (added) {
      configEditor.updateDirtyForPath(editPath);
      statusMessage = messageSource.getMessage(TuiMessageSource.CONFIG_SUCCESS_ITEM_ADDED);
      editMode = EditMode.LIST;
      listEditIndex = -1;
      valueBuffer.setLength(0);
    } else {
      statusMessage = messageSource.getMessage(TuiMessageSource.CONFIG_ERROR_ITEM_ADD_FAILED);
    }
  }

  public void deleteListItem() {
    final Integer selection = consumeNumber();
    if (selection == null) {
      statusMessage = messageSource.getMessage(TuiMessageSource.CONFIG_ERROR_ENTER_ITEM_DELETE);
      return;
    }
    final List<?> list = getCurrentList();
    if (selection < 1 || selection > list.size()) {
      statusMessage = messageSource.getMessage(TuiMessageSource.CONFIG_ERROR_INVALID_ITEM);
      return;
    }
    final boolean removed = configEditor.removeListIndex(editPath, selection - 1);
    if (removed) {
      configEditor.updateDirtyForPath(editPath);
      statusMessage = messageSource.getMessage(TuiMessageSource.CONFIG_SUCCESS_ITEM_DELETED);
    } else {
      statusMessage = messageSource.getMessage(TuiMessageSource.CONFIG_ERROR_ITEM_DELETE_FAILED);
    }
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Validation
  // ─────────────────────────────────────────────────────────────────────────────
  public boolean runValidation(final State returnState, final String failureMessage) {
    if (configEditor == null) {
      statusMessage = messageSource.getMessage(TuiMessageSource.CONFIG_ERROR_EDITOR_NOT_AVAILABLE);
      return false;
    }
    final List<ConfigValidationService.ValidationIssue> issues =
        activePluginId != null
            ? validationService.validateWithSchema(
                configEditor.toJson(), resolvePluginSchemaPath(activePluginId))
            : validationService.validate(configEditor.toJson());
    if (issues.isEmpty()) {
      clearValidationState();
      statusMessage = messageSource.getMessage(TuiMessageSource.CONFIG_VALIDATION_PASSED_MSG);
      return true;
    }
    showValidationFailure(issues, returnState, failureMessage);
    return false;
  }

  public void exitValidation() {
    numberBuffer.setLength(0);
    final State target =
        validationReturnState != null ? validationReturnState : State.CONFIG_CATEGORY;
    stateMachine.transitionTo(target);
  }

  public boolean jumpToIssue(final int selection) {
    if (validationIssues == null || validationIssues.isEmpty()) {
      return false;
    }
    if (selection < 1 || selection > validationIssues.size()) {
      statusMessage = messageSource.getMessage(TuiMessageSource.INPUT_ERROR_INVALID_ERROR_NUMBER);
      return false;
    }
    final ConfigValidationService.ValidationIssue issue = validationIssues.get(selection - 1);
    final String issuePath = issue.path();
    if (issuePath == null || issuePath.isBlank()) {
      statusMessage = messageSource.getMessage(TuiMessageSource.CONFIG_ERROR_NO_PATH);
      return false;
    }
    final boolean opened = openAtPath(issuePath);
    if (opened && issue.message() != null && !issue.message().isBlank()) {
      statusMessage = issue.message();
    }
    return opened;
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Save/Discard
  // ─────────────────────────────────────────────────────────────────────────────
  public void save() throws IOException {
    if (configEditor == null) {
      statusMessage = messageSource.getMessage(TuiMessageSource.CONFIG_ERROR_EDITOR_NOT_AVAILABLE);
      return;
    }
    configEditor.save();
    finishPersistence(TuiMessageSource.CONFIG_SUCCESS_SAVED);
  }

  public void discard() {
    if (configEditor != null) {
      configEditor.discard();
    }
    finishPersistence(TuiMessageSource.CONFIG_SUCCESS_DISCARDED);
  }

  public boolean saveAll(final State returnState, final String failureMessage) throws IOException {
    final List<DirtyConfig> dirtyConfigs = collectDirtyConfigs();
    if (dirtyConfigs.isEmpty()) {
      exitConfirmation = false;
      return true;
    }
    for (final DirtyConfig dirtyConfig : dirtyConfigs) {
      final List<ConfigValidationService.ValidationIssue> issues = validateEditor(dirtyConfig);
      if (!issues.isEmpty()) {
        switchToContext(dirtyConfig);
        showValidationFailure(issues, returnState, failureMessage);
        return false;
      }
    }
    for (final DirtyConfig dirtyConfig : dirtyConfigs) {
      dirtyConfig.editor().save();
    }
    finishPersistence(TuiMessageSource.CONFIG_SUCCESS_SAVED);
    return true;
  }

  public void discardAll() {
    if (rootEditor != null) {
      rootEditor.discard();
    }
    for (final ConfigEditor editor : pluginEditors.values()) {
      editor.discard();
    }
    finishPersistence(TuiMessageSource.CONFIG_SUCCESS_DISCARDED);
  }

  public boolean hasDirtyEditors() {
    if (rootEditor != null && rootEditor.isDirty()) {
      return true;
    }
    for (final ConfigEditor editor : pluginEditors.values()) {
      if (editor.isDirty()) {
        return true;
      }
    }
    return false;
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Number buffer helpers
  // ─────────────────────────────────────────────────────────────────────────────
  public void appendNumber(final char c) {
    if (Character.isDigit(c)) {
      numberBuffer.append(c);
    }
  }

  public void trimNumber() {
    if (!numberBuffer.isEmpty()) {
      numberBuffer.deleteCharAt(numberBuffer.length() - 1);
    }
  }

  public Integer consumeNumber() {
    if (numberBuffer.isEmpty()) {
      return null;
    }
    try {
      return Integer.parseInt(numberBuffer.toString());
    } catch (NumberFormatException e) {
      return null;
    } finally {
      numberBuffer.setLength(0);
    }
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Helpers
  // ─────────────────────────────────────────────────────────────────────────────
  public boolean pollExternalChanges() {
    if (projectRoot == null) {
      return false;
    }
    final long now = System.currentTimeMillis();
    if (now - lastExternalCheckMs < EXTERNAL_CHECK_INTERVAL_MS) {
      return false;
    }
    lastExternalCheckMs = now;
    final ExternalSnapshot currentSnapshot = captureExternalSnapshot();
    if (lastExternalSnapshot == null) {
      lastExternalSnapshot = currentSnapshot;
      return false;
    }
    final boolean snapshotChanged = !currentSnapshot.equals(lastExternalSnapshot);
    // Apply a previously deferred reload only after local edits are no longer
    // dirty.
    if (externalChangePending && !hasDirtyEditors()) {
      applyExternalSnapshot(currentSnapshot);
      externalChangePending = false;
      lastExternalSnapshot = currentSnapshot;
      statusMessage = messageSource.getMessage(TuiMessageSource.CONFIG_RELOADED);
      return true;
    }
    if (!snapshotChanged) {
      return false;
    }
    lastExternalSnapshot = currentSnapshot;
    if (hasDirtyEditors()) {
      externalChangePending = true;
      statusMessage = messageSource.getMessage(TuiMessageSource.CONFIG_EXTERNAL_CHANGED);
      return true;
    }
    applyExternalSnapshot(currentSnapshot);
    externalChangePending = false;
    statusMessage = messageSource.getMessage(TuiMessageSource.CONFIG_RELOADED);
    return true;
  }

  private void refreshPluginEntries() {
    pluginEntries = pluginConfigIndex.list(projectRoot);
    pluginEntryMap = indexPluginEntries(pluginEntries);
    pluginEditors.keySet().removeIf(pluginId -> !pluginEntryMap.containsKey(pluginId));
    if (activePluginId != null && !pluginEntryMap.containsKey(activePluginId)) {
      activePluginId = null;
      configEditor = rootEditor;
    }
  }

  private ExternalSnapshot captureExternalSnapshot() {
    final List<PluginConfigEntry> currentPluginEntries = pluginConfigIndex.list(projectRoot);
    final Map<Path, Long> fileTimes = new HashMap<>();
    if (projectRoot != null) {
      addFileTime(projectRoot.resolve(CONFIG_JSON_FILE), fileTimes);
    }
    for (final PluginConfigEntry entry : currentPluginEntries) {
      addFileTime(entry.configPath(), fileTimes);
      addFileTime(entry.schemaPath(), fileTimes);
    }
    return new ExternalSnapshot(currentPluginEntries, fileTimes);
  }

  private void addFileTime(final Path filePath, final Map<Path, Long> fileTimes) {
    if (filePath == null) {
      return;
    }
    try {
      if (java.nio.file.Files.exists(filePath)) {
        fileTimes.put(filePath, java.nio.file.Files.getLastModifiedTime(filePath).toMillis());
      }
    } catch (IOException ignored) {
      // Ignore unreadable files for reload detection.
    }
  }

  private void applyExternalSnapshot(final ExternalSnapshot snapshot) {
    pluginEntries = snapshot.pluginEntries();
    pluginEntryMap = indexPluginEntries(pluginEntries);
    pluginEditors.clear();
    rootEditor =
        ConfigEditor.load(
            projectRoot != null
                ? projectRoot.resolve(CONFIG_JSON_FILE)
                : Path.of(CONFIG_JSON_FILE));
    if (activePluginId != null) {
      final PluginConfigEntry entry = pluginEntryMap.get(activePluginId);
      if (entry == null) {
        activePluginId = null;
        configEditor = rootEditor;
        return;
      }
      final ConfigEditor editor = loadPluginEditor(entry);
      pluginEditors.put(activePluginId, editor);
      configEditor = editor;
      return;
    }
    configEditor = rootEditor;
  }

  private void openPlugin(final PluginConfigEntry entry) {
    if (entry == null) {
      return;
    }
    activePluginId = entry.pluginId();
    ConfigEditor pluginEditor = pluginEditors.get(activePluginId);
    if (pluginEditor == null) {
      pluginEditor = loadPluginEditor(entry);
      pluginEditors.put(activePluginId, pluginEditor);
    }
    configEditor = pluginEditor;
    path.clear();
    clearEditState();
    if (configEditor.getLoadError() != null) {
      statusMessage = configEditor.getLoadError();
    }
    stateMachine.transitionTo(State.CONFIG_ITEMS);
  }

  private ConfigEditor loadPluginEditor(final PluginConfigEntry entry) {
    ConfigSchemaIndex pluginSchemaIndex = null;
    if (entry.schemaExists()) {
      try {
        pluginSchemaIndex = ConfigSchemaIndex.forSchemaPath(entry.schemaPath());
      } catch (IllegalStateException e) {
        statusMessage = e.getMessage();
      }
    }
    return ConfigEditor.load(entry.configPath(), pluginSchemaIndex, false);
  }

  private Path resolvePluginSchemaPath(final String pluginId) {
    if (pluginId == null || pluginId.isBlank()) {
      return null;
    }
    final PluginConfigEntry entry = pluginEntryMap.get(pluginId);
    if (entry != null) {
      return entry.schemaPath();
    }
    if (projectRoot == null) {
      return null;
    }
    final PluginConfigPaths.ResolvedPaths resolvedPaths =
        PluginConfigPaths.resolve(projectRoot, pluginId);
    return resolvedPaths != null ? resolvedPaths.schemaPath() : null;
  }

  private List<DirtyConfig> collectDirtyConfigs() {
    final List<DirtyConfig> dirtyConfigs = new ArrayList<>();
    if (rootEditor != null && rootEditor.isDirty()) {
      dirtyConfigs.add(new DirtyConfig(null, rootEditor));
    }
    for (final Map.Entry<String, ConfigEditor> pluginEditorEntry : pluginEditors.entrySet()) {
      if (pluginEditorEntry.getValue().isDirty()) {
        dirtyConfigs.add(new DirtyConfig(pluginEditorEntry.getKey(), pluginEditorEntry.getValue()));
      }
    }
    return dirtyConfigs;
  }

  private List<ConfigValidationService.ValidationIssue> validateEditor(
      final DirtyConfig dirtyConfig) {
    if (dirtyConfig.pluginId() == null) {
      return validationService.validate(dirtyConfig.editor().toJson());
    }
    return validationService.validateWithSchema(
        dirtyConfig.editor().toJson(), resolvePluginSchemaPath(dirtyConfig.pluginId()));
  }

  private void switchToContext(final DirtyConfig dirtyConfig) {
    if (dirtyConfig.pluginId() == null) {
      activePluginId = null;
      configEditor = rootEditor;
      category = null;
      path.clear();
      clearEditState();
      return;
    }
    activePluginId = dirtyConfig.pluginId();
    configEditor = dirtyConfig.editor();
    category = CATEGORY_PLUGINS;
    path.clear();
    clearEditState();
  }

  private void finishPersistence(final String messageKey) {
    refreshPluginEntries();
    lastExternalSnapshot = captureExternalSnapshot();
    externalChangePending = false;
    clearValidationState();
    statusMessage = messageSource.getMessage(messageKey);
    exitConfirmation = false;
  }

  private Map<String, PluginConfigEntry> indexPluginEntries(final List<PluginConfigEntry> entries) {
    final Map<String, PluginConfigEntry> entriesByPluginId = new HashMap<>();
    for (final PluginConfigEntry entry : entries) {
      entriesByPluginId.put(entry.pluginId(), entry);
    }
    return entriesByPluginId;
  }

  private void showValidationFailure(
      final List<ConfigValidationService.ValidationIssue> issues,
      final State returnState,
      final String failureMessage) {
    validationIssues = List.copyOf(issues);
    validationReturnState = returnState != null ? returnState : State.CONFIG_CATEGORY;
    numberBuffer.setLength(0);
    exitConfirmation = false;
    statusMessage =
        failureMessage != null && !failureMessage.isBlank()
            ? failureMessage
            : messageSource.getMessage(TuiMessageSource.CONFIG_VALIDATION_FAILED_MSG);
    stateMachine.transitionTo(State.CONFIG_VALIDATE);
  }

  private void clearValidationState() {
    validationIssues = List.of();
    validationReturnState = State.CONFIG_CATEGORY;
  }

  private void clearEditState() {
    editPath = null;
    editMode = null;
    editValueType = null;
    enumOptions = List.of();
    listEditIndex = -1;
    valueBuffer.setLength(0);
  }

  private boolean pointsToList(final List<ConfigEditor.PathSegment> candidatePath) {
    if (candidatePath == null || candidatePath.isEmpty() || configEditor == null) {
      return false;
    }
    final Object value = configEditor.getValue(candidatePath);
    return value instanceof List;
  }

  private boolean updateConfigValue(
      final List<ConfigEditor.PathSegment> targetPath, final Object value) {
    if (configEditor == null || targetPath == null) {
      return false;
    }
    return configEditor.setValue(targetPath, value)
        || configEditor.setValueWithCreate(targetPath, value);
  }

  private Object parseScalarInput(final String input) {
    if ("null".equalsIgnoreCase(input)) {
      return null;
    }
    if ("true".equalsIgnoreCase(input) || "false".equalsIgnoreCase(input)) {
      return Boolean.parseBoolean(input);
    }
    try {
      return Long.parseLong(input);
    } catch (NumberFormatException ignored) {
      // Not an integer
    }
    try {
      return Double.parseDouble(input);
    } catch (NumberFormatException ignored) {
      // Not a number
    }
    return input;
  }

  private String resolveCategoryForSegments(final List<ConfigEditor.PathSegment> pathSegments) {
    if (pathSegments == null || pathSegments.isEmpty()) {
      return null;
    }
    final ConfigEditor.PathSegment firstSegment = pathSegments.get(0);
    if (!firstSegment.isKey()) {
      return CATEGORY_ADVANCED;
    }
    final String rootKey = firstSegment.key();
    if (ROOT_CATEGORIES.contains(rootKey)) {
      return rootKey;
    }
    return CATEGORY_ADVANCED;
  }

  public boolean openAtPath(final String pathString) {
    final PathParser.ParsedPath parsedPath;
    try {
      parsedPath = PathParser.parse(pathString);
    } catch (IllegalArgumentException e) {
      statusMessage = e.getMessage();
      return false;
    }
    if (configEditor == null) {
      statusMessage = messageSource.getMessage(TuiMessageSource.CONFIG_ERROR_EDITOR_NOT_AVAILABLE);
      return false;
    }
    final List<ConfigEditor.PathSegment> targetSegments = parsedPath.segments();
    if (activePluginId != null) {
      category = CATEGORY_PLUGINS;
      path.clear();
      clearEditState();
      openObjectOrEdit(targetSegments, configEditor.getValue(targetSegments));
      return true;
    }
    category = resolveCategoryForSegments(targetSegments);
    path.clear();
    clearEditState();
    final String metadataPath = PathParser.toMetadataPath(targetSegments);
    final MetadataRegistry.ConfigKeyMetadata configKeyMetadata =
        metadataRegistry.find(metadataPath).orElse(null);
    // Metadata still tells us an object node is navigable even if the config value
    // is absent.
    if (configKeyMetadata != null
        && configKeyMetadata.type() == MetadataRegistry.ValueType.OBJECT) {
      path.addAll(targetSegments);
      stateMachine.transitionTo(State.CONFIG_ITEMS);
      return true;
    }
    openObjectOrEdit(targetSegments, configEditor.getValue(targetSegments));
    return true;
  }

  private void setParentPathForEdit(final List<ConfigEditor.PathSegment> targetSegments) {
    path.clear();
    if (targetSegments != null && targetSegments.size() > 1) {
      path.addAll(targetSegments.subList(0, targetSegments.size() - 1));
    } else if (category != null
        && !CATEGORY_ADVANCED.equals(category)
        && !CATEGORY_PLUGINS.equals(category)) {
      path.add(ConfigEditor.PathSegment.key(category));
    }
  }

  private void openObjectOrEdit(
      final List<ConfigEditor.PathSegment> targetSegments, final Object targetValue) {
    if (targetValue instanceof java.util.Map) {
      path.addAll(targetSegments);
      stateMachine.transitionTo(State.CONFIG_ITEMS);
      return;
    }
    setParentPathForEdit(targetSegments);
    beginEdit(targetSegments);
    stateMachine.transitionTo(State.CONFIG_EDIT);
  }
}
