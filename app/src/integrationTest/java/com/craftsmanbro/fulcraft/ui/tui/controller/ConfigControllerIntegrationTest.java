package com.craftsmanbro.fulcraft.ui.tui.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.ui.tui.config.ConfigEditor;
import com.craftsmanbro.fulcraft.ui.tui.config.ConfigValidationService;
import com.craftsmanbro.fulcraft.ui.tui.config.MetadataRegistry;
import com.craftsmanbro.fulcraft.ui.tui.i18n.TuiMessageSource;
import com.craftsmanbro.fulcraft.ui.tui.state.State;
import com.craftsmanbro.fulcraft.ui.tui.state.StateMachine;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigControllerIntegrationTest {

  @TempDir Path tempDir;

  private StateMachine stateMachine;
  private TuiMessageSource messageSource;
  private ConfigController controller;

  @BeforeEach
  void setUp() {
    stateMachine = new StateMachine(State.HOME);
    messageSource = new TuiMessageSource(Locale.ENGLISH);
    controller =
        new ConfigController(
            stateMachine,
            MetadataRegistry.getDefault(),
            new ConfigValidationService(),
            messageSource);
  }

  @Test
  void enterLoadsConfigAndTransitionsToCategory() throws Exception {
    writeConfig(validConfigJson("mock", 1, 3));

    controller.enter(tempDir);

    assertThat(stateMachine.getCurrentState()).isEqualTo(State.CONFIG_CATEGORY);
    assertThat(controller.getConfigEditor()).isNotNull();
    assertThat(controller.getReturnState()).isEqualTo(State.HOME);
    assertThat(controller.getStatusMessage()).isNull();
  }

  @Test
  void enterShowsNotFoundMessageWhenConfigDoesNotExist() {
    controller.enter(tempDir);

    assertThat(controller.getStatusMessage())
        .isEqualTo(messageSource.getMessage(TuiMessageSource.CONFIG_NOT_FOUND));
  }

  @Test
  void selectCategoryAndPluginItemOpensPluginEditor() throws Exception {
    writeConfig(validConfigJson("mock", 1, 3));
    writePluginConfig("demo", "{ \"enabled\": true }");
    controller.enter(tempDir);

    controller.selectCategory(categorySelection("plugins"));

    List<ConfigEditor.ConfigItem> pluginItems = controller.getCurrentItems();
    assertThat(pluginItems).hasSize(1);
    assertThat(pluginItems.get(0).label()).isEqualTo("demo");

    controller.selectItem(1);

    assertThat(controller.getCategory()).isEqualTo("plugins");
    assertThat(controller.getActivePluginId()).isEqualTo("demo");
    assertThat(controller.getPathLabel()).isEqualTo("plugins/demo");
    assertThat(stateMachine.getCurrentState()).isEqualTo(State.CONFIG_ITEMS);
  }

  @Test
  void openAtPathForObjectTransitionsToItems() throws Exception {
    writeConfig(validConfigJson("mock", 1, 3));
    controller.enter(tempDir);

    boolean opened = controller.openAtPath("project");

    assertThat(opened).isTrue();
    assertThat(controller.getCategory()).isEqualTo("project");
    assertThat(controller.getPath()).hasSize(1);
    assertThat(controller.getPath().get(0).isKey()).isTrue();
    assertThat(controller.getPath().get(0).key()).isEqualTo("project");
    assertThat(stateMachine.getCurrentState()).isEqualTo(State.CONFIG_ITEMS);
  }

  @Test
  void openAtPathForSchemaKnownScalarUsesDedicatedCategory() throws Exception {
    writeConfig(validConfigJson("mock", 1, 3));
    controller.enter(tempDir);

    boolean opened = controller.openAtPath("pipeline.workflow_file");

    assertThat(opened).isTrue();
    assertThat(controller.getCategory()).isEqualTo("pipeline");
    assertThat(controller.getEditMode()).isEqualTo(ConfigController.EditMode.SCALAR);
    assertThat(stateMachine.getCurrentState()).isEqualTo(State.CONFIG_EDIT);
  }

  @Test
  void openAtPathForScalarTransitionsToEditAndApplyEnumSelectionUpdatesValue() throws Exception {
    writeConfig(validConfigJson("mock", 1, 3));
    controller.enter(tempDir);

    boolean opened = controller.openAtPath("llm.provider");
    assertThat(opened).isTrue();
    assertThat(controller.getCategory()).isEqualTo("llm");
    assertThat(controller.getEditMode()).isEqualTo(ConfigController.EditMode.ENUM);
    assertThat(stateMachine.getCurrentState()).isEqualTo(State.CONFIG_EDIT);

    int selection = findSelection(controller.getEnumOptions(), "openai");
    controller.applyEnumSelection(selection);

    assertThat(controller.getConfigEditor().getValue(path("llm", "provider"))).isEqualTo("openai");
    assertThat(controller.getStatusMessage())
        .isEqualTo(messageSource.getMessage(TuiMessageSource.CONFIG_SUCCESS_UPDATED));
  }

  @Test
  void applyScalarInputParsesNumberAndReturnsToItems() throws Exception {
    writeConfig(validConfigJson("mock", 1, 3));
    controller.enter(tempDir);
    controller.openAtPath("selection_rules.method_min_loc");
    controller.getValueBuffer().setLength(0);
    controller.getValueBuffer().append("2");

    controller.applyScalarInput();

    assertThat(stateMachine.getCurrentState()).isEqualTo(State.CONFIG_ITEMS);
    assertThat(controller.getConfigEditor().getValue(path("selection_rules", "method_min_loc")))
        .isEqualTo(2L);
    assertThat(controller.getEditMode()).isNull();
  }

  @Test
  void listAddAndDeleteMutateListValue() throws Exception {
    writeConfig(validConfigJson("mock", 1, 3));
    controller.enter(tempDir);
    controller.openAtPath("selection_rules.exclude_annotations");

    controller.beginListAdd();
    controller.getValueBuffer().append("B");
    controller.applyListAdd();

    assertThat(
            controller.getConfigEditor().getValue(path("selection_rules", "exclude_annotations")))
        .isEqualTo(List.of("A", "B"));
    assertThat(controller.getStatusMessage())
        .isEqualTo(messageSource.getMessage(TuiMessageSource.CONFIG_SUCCESS_ITEM_ADDED));

    controller.appendNumber('1');
    controller.deleteListItem();

    assertThat(
            controller.getConfigEditor().getValue(path("selection_rules", "exclude_annotations")))
        .isEqualTo(List.of("B"));
    assertThat(controller.getStatusMessage())
        .isEqualTo(messageSource.getMessage(TuiMessageSource.CONFIG_SUCCESS_ITEM_DELETED));
  }

  @Test
  void runValidationReturnsTrueForValidConfig() throws Exception {
    writeConfig(validConfigJson("mock", 1, 3));
    controller.enter(tempDir);

    boolean valid = controller.runValidation(State.CONFIG_ITEMS, "should not be used");

    assertThat(valid).isTrue();
    assertThat(controller.getStatusMessage())
        .isEqualTo(messageSource.getMessage(TuiMessageSource.CONFIG_VALIDATION_PASSED_MSG));
    assertThat(stateMachine.getCurrentState()).isEqualTo(State.CONFIG_CATEGORY);
  }

  @Test
  void runValidationFailureTransitionsToValidateAndJumpToIssueOpensPath() throws Exception {
    writeConfig(validConfigJson("mock", 5, 3));
    controller.enter(tempDir);

    boolean valid = controller.runValidation(State.CONFIG_ITEMS, "validation failed");

    assertThat(valid).isFalse();
    assertThat(stateMachine.getCurrentState()).isEqualTo(State.CONFIG_VALIDATE);
    assertThat(controller.getValidationReturnState()).isEqualTo(State.CONFIG_ITEMS);
    assertThat(controller.getStatusMessage()).isEqualTo("validation failed");
    assertThat(controller.getValidationIssues()).isNotEmpty();

    List<ConfigValidationService.ValidationIssue> issues = controller.getValidationIssues();
    int issueIndex =
        IntStream.range(0, issues.size())
            .filter(i -> issues.get(i).path() != null && !issues.get(i).path().isBlank())
            .findFirst()
            .orElse(-1);
    assertThat(issueIndex).isGreaterThanOrEqualTo(0);

    ConfigValidationService.ValidationIssue issue = issues.get(issueIndex);
    boolean opened = controller.jumpToIssue(issueIndex + 1);

    assertThat(opened).isTrue();
    assertThat(controller.getStatusMessage()).isEqualTo(issue.message());
    assertThat(stateMachine.getCurrentState()).isIn(State.CONFIG_EDIT, State.CONFIG_ITEMS);
  }

  @Test
  void runValidationSuccessClearsPreviousIssues() throws Exception {
    writeConfig(validConfigJson("mock", 5, 3));
    controller.enter(tempDir);

    boolean initialValid = controller.runValidation(State.CONFIG_ITEMS, "validation failed");

    assertThat(initialValid).isFalse();
    assertThat(controller.getValidationIssues()).isNotEmpty();

    controller.openAtPath("selection_rules.method_min_loc");
    controller.getValueBuffer().setLength(0);
    controller.getValueBuffer().append("2");
    controller.applyScalarInput();

    boolean validAfterFix = controller.runValidation(State.CONFIG_ITEMS, null);

    assertThat(validAfterFix).isTrue();
    assertThat(controller.getValidationIssues()).isEmpty();
    assertThat(controller.getValidationReturnState()).isEqualTo(State.CONFIG_CATEGORY);
    assertThat(controller.getStatusMessage())
        .isEqualTo(messageSource.getMessage(TuiMessageSource.CONFIG_VALIDATION_PASSED_MSG));
  }

  @Test
  void saveAllFailsWhenDirtyConfigDoesNotPassValidation() throws Exception {
    writeConfig(validConfigJson("mock", 1, 3));
    controller.enter(tempDir);
    controller.openAtPath("selection_rules.method_min_loc");
    controller.getValueBuffer().setLength(0);
    controller.getValueBuffer().append("10");
    controller.applyScalarInput();

    boolean saved = controller.saveAll(State.CONFIG_ITEMS, "save failed");

    assertThat(saved).isFalse();
    assertThat(stateMachine.getCurrentState()).isEqualTo(State.CONFIG_VALIDATE);
    assertThat(controller.getStatusMessage()).isEqualTo("save failed");
    assertThat(controller.getValidationIssues()).isNotEmpty();
  }

  @Test
  void saveAllPersistsDirtyRootConfig() throws Exception {
    writeConfig(validConfigJson("mock", 1, 3));
    controller.enter(tempDir);
    controller.openAtPath("selection_rules.method_min_loc");
    controller.getValueBuffer().setLength(0);
    controller.getValueBuffer().append("2");
    controller.applyScalarInput();

    boolean saved = controller.saveAll(State.CONFIG_ITEMS, null);

    assertThat(saved).isTrue();
    assertThat(controller.hasDirtyEditors()).isFalse();
    assertThat(controller.getStatusMessage())
        .isEqualTo(messageSource.getMessage(TuiMessageSource.CONFIG_SUCCESS_SAVED));
    assertThat(Files.readString(tempDir.resolve("config.json"))).contains("\"method_min_loc\" : 2");
  }

  @Test
  void pollExternalChangesReloadsWhenEditorIsClean() throws Exception {
    writeConfig(validConfigJson("mock", 1, 3));
    controller.enter(tempDir);
    overwriteConfig(validConfigJson("openai", 1, 3));

    boolean changed = controller.pollExternalChanges();

    assertThat(changed).isTrue();
    assertThat(controller.getStatusMessage())
        .isEqualTo(messageSource.getMessage(TuiMessageSource.CONFIG_RELOADED));
    assertThat(controller.getConfigEditor().getValue(path("llm", "provider"))).isEqualTo("openai");
  }

  @Test
  void pollExternalChangesMarksPendingWhenEditorIsDirty() throws Exception {
    writeConfig(validConfigJson("mock", 1, 3));
    controller.enter(tempDir);
    controller.openAtPath("llm.provider");
    controller.applyEnumSelection(findSelection(controller.getEnumOptions(), "openai"));
    overwriteConfig(validConfigJson("vertex-ai", 1, 3));

    boolean changed = controller.pollExternalChanges();

    assertThat(changed).isTrue();
    assertThat(controller.getStatusMessage())
        .isEqualTo(messageSource.getMessage(TuiMessageSource.CONFIG_EXTERNAL_CHANGED));
    assertThat(controller.getConfigEditor().getValue(path("llm", "provider"))).isEqualTo("openai");
  }

  @Test
  void openAtPathInPluginContextUsesPluginBranch() throws Exception {
    writeConfig(validConfigJson("mock", 1, 3));
    writePluginConfig("demo", "{ \"enabled\": true }");
    controller.enter(tempDir);
    controller.selectCategory(categorySelection("plugins"));
    controller.selectItem(1);

    boolean opened = controller.openAtPath("enabled");

    assertThat(opened).isTrue();
    assertThat(controller.getCategory()).isEqualTo("plugins");
    assertThat(controller.getEditMode()).isEqualTo(ConfigController.EditMode.SCALAR);
    assertThat(stateMachine.getCurrentState()).isEqualTo(State.CONFIG_EDIT);
  }

  @Test
  void formatCategorySummaryProvidesExpectedLabelsAcrossBranches() throws Exception {
    assertThat(controller.formatCategorySummary("project", 40)).isEmpty();

    writeConfig(
        validConfigJson(
            "mock",
            1,
            3,
            """
            "advanced_list": [{ "name": "x" }],
            "version": "1.2.3"
            """));
    writePluginConfig("demo", "{ \"enabled\": true }");
    controller.enter(tempDir);

    assertThat(controller.formatCategorySummary("plugins", 40)).contains("(1 plugins)");
    assertThat(controller.formatCategorySummary("advanced", 40)).contains("keys");
    assertThat(controller.formatCategorySummary("governance", 40))
        .contains(messageSource.getMessage("tui.config_controller.summary.missing"));
    assertThat(controller.formatCategorySummary("project", 40)).contains("keys");
    assertThat(controller.formatCategorySummary("version", 40)).startsWith(" = ");
  }

  @Test
  void backFromItemsInAdvancedCategoryCoversRootSingleAndListParentBranches() throws Exception {
    writeConfig(
        validConfigJson(
            "mock",
            1,
            3,
            """
            "advanced_object": { "child": { "value": 1 } },
            "advanced_list": [{ "name": "x" }]
            """));
    controller.enter(tempDir);

    controller.selectCategory(categorySelection("advanced"));
    controller.backFromItems();
    assertThat(stateMachine.getCurrentState()).isEqualTo(State.CONFIG_CATEGORY);

    controller.selectCategory(categorySelection("advanced"));
    selectCurrentItemByLabel("advanced_object");
    assertThat(controller.getPath()).hasSize(1);
    controller.backFromItems();
    assertThat(stateMachine.getCurrentState()).isEqualTo(State.CONFIG_ITEMS);
    assertThat(controller.getPath()).isEmpty();

    controller.selectCategory(categorySelection("advanced"));
    selectCurrentItemByLabel("advanced_list");
    assertThat(stateMachine.getCurrentState()).isEqualTo(State.CONFIG_EDIT);
    controller.selectListItem(1);
    assertThat(stateMachine.getCurrentState()).isEqualTo(State.CONFIG_ITEMS);
    controller.backFromItems();
    assertThat(stateMachine.getCurrentState()).isEqualTo(State.CONFIG_EDIT);
    assertThat(controller.getEditMode()).isEqualTo(ConfigController.EditMode.LIST);
  }

  @Test
  void backFromItemsInPluginsCategoryCoversPluginBackBranches() throws Exception {
    writeConfig(validConfigJson("mock", 1, 3));
    writePluginConfig("demo", "{ \"object\": { \"value\": 1 } }");
    controller.enter(tempDir);

    controller.selectCategory(categorySelection("plugins"));
    controller.backFromItems();
    assertThat(stateMachine.getCurrentState()).isEqualTo(State.CONFIG_CATEGORY);

    controller.selectCategory(categorySelection("plugins"));
    controller.selectItem(1);
    controller.backFromItems();
    assertThat(stateMachine.getCurrentState()).isEqualTo(State.CONFIG_ITEMS);
    assertThat(controller.getActivePluginId()).isNull();

    controller.selectItem(1);
    selectCurrentItemByLabel("object");
    assertThat(controller.getPath()).isNotEmpty();
    controller.backFromItems();
    assertThat(stateMachine.getCurrentState()).isEqualTo(State.CONFIG_CATEGORY);
  }

  @Test
  void backFromItemsInStandardCategoryReturnsToListEditWhenParentIsList() throws Exception {
    writeConfig(
        validConfigJson(
            "mock",
            1,
            3,
            """
            "project": {
              "id": "demo-project",
              "list_of_obj": [{ "k": 1 }]
            }
            """));
    controller.enter(tempDir);

    controller.selectCategory(categorySelection("project"));
    selectCurrentItemByLabel("list_of_obj");
    assertThat(stateMachine.getCurrentState()).isEqualTo(State.CONFIG_EDIT);
    controller.selectListItem(1);
    assertThat(stateMachine.getCurrentState()).isEqualTo(State.CONFIG_ITEMS);
    controller.backFromItems();

    assertThat(stateMachine.getCurrentState()).isEqualTo(State.CONFIG_EDIT);
    assertThat(controller.getEditMode()).isEqualTo(ConfigController.EditMode.LIST);
    assertThat(controller.getEditPath()).hasSize(2);
    assertThat(controller.getEditPath().get(0).key()).isEqualTo("project");
    assertThat(controller.getEditPath().get(1).key()).isEqualTo("list_of_obj");
  }

  @Test
  void applyListItemEditCoversEmptyValidationFailureUpdateFailureAndSuccess() throws Exception {
    writeConfig(validConfigJson("mock", 1, 3));
    controller.enter(tempDir);
    controller.openAtPath("selection_rules.exclude_annotations");

    controller.applyListItemEdit();
    assertThat(controller.getStatusMessage())
        .isEqualTo(messageSource.getMessage(TuiMessageSource.CONFIG_ERROR_VALUE_REQUIRED));

    controller.selectListItem(1);
    controller.getValueBuffer().setLength(0);
    controller.getValueBuffer().append("123");
    controller.applyListItemEdit();
    assertThat(controller.getStatusMessage()).contains("Value type");

    controller.beginEdit(path("selection_rules", "exclude_annotations"));
    controller.getValueBuffer().setLength(0);
    controller.getValueBuffer().append("B");
    controller.applyListItemEdit();
    assertThat(controller.getStatusMessage())
        .isEqualTo(messageSource.getMessage(TuiMessageSource.CONFIG_ERROR_ITEM_UPDATE_FAILED));

    controller.selectListItem(1);
    controller.getValueBuffer().setLength(0);
    controller.getValueBuffer().append("B");
    controller.applyListItemEdit();
    assertThat(controller.getStatusMessage())
        .isEqualTo(messageSource.getMessage(TuiMessageSource.CONFIG_SUCCESS_ITEM_UPDATED));
    assertThat(
            controller.getConfigEditor().getValue(path("selection_rules", "exclude_annotations")))
        .isEqualTo(List.of("B"));
    assertThat(controller.getEditMode()).isEqualTo(ConfigController.EditMode.LIST);
  }

  @Test
  void toggleBooleanUsesCreateWhenParentPathIsMissingAndSetsErrorWhenPathIsInvalid()
      throws Exception {
    writeConfig(
        """
        {
          "schema_version": 1,
          "project": {
            "id": "demo-project"
          },
          "selection_rules": {
            "class_min_loc": 1,
            "class_min_method_count": 1,
            "method_min_loc": 1,
            "method_max_loc": 3
          },
          "llm": {
            "provider": "mock"
          }
        }
        """);
    controller.enter(tempDir);
    controller.openAtPath("analysis.spoon.no_classpath");

    controller.toggleBoolean();

    assertThat(controller.getConfigEditor().getValue(path("analysis", "spoon", "no_classpath")))
        .isEqualTo(true);
    assertThat(controller.getStatusMessage())
        .isEqualTo(messageSource.getMessage(TuiMessageSource.CONFIG_SUCCESS_UPDATED));

    controller.beginEdit(List.of(ConfigEditor.PathSegment.index(0)));
    controller.toggleBoolean();
    assertThat(controller.getStatusMessage())
        .isEqualTo(messageSource.getMessage(TuiMessageSource.CONFIG_ERROR_UPDATE_FAILED));
  }

  @Test
  void discardAllClearsDirtyStateForRootAndPluginEditors() throws Exception {
    writeConfig(validConfigJson("mock", 1, 3));
    writePluginConfig("demo", "{ \"enabled\": true }");
    controller.enter(tempDir);

    controller.openAtPath("selection_rules.method_min_loc");
    controller.getValueBuffer().setLength(0);
    controller.getValueBuffer().append("2");
    controller.applyScalarInput();
    assertThat(controller.hasDirtyEditors()).isTrue();

    controller.selectCategory(categorySelection("plugins"));
    controller.selectItem(1);
    controller.openAtPath("enabled");
    controller.toggleBoolean();
    assertThat(controller.hasDirtyEditors()).isTrue();

    controller.discardAll();

    assertThat(controller.hasDirtyEditors()).isFalse();
    assertThat(controller.getStatusMessage())
        .isEqualTo(messageSource.getMessage(TuiMessageSource.CONFIG_SUCCESS_DISCARDED));
    assertThat(controller.getConfigEditor().getValue(path("enabled"))).isEqualTo(true);
  }

  @Test
  void resolvePluginSchemaPathReturnsMappedAndFallbackPaths() throws Exception {
    writeConfig(validConfigJson("mock", 1, 3));
    writePluginConfig("demo", "{ \"enabled\": true }");
    writePluginSchema(
        "demo",
        """
        {
          "$schema": "https://json-schema.org/draft/2020-12/schema",
          "type": "object",
          "properties": { "enabled": { "type": "boolean" } },
          "additionalProperties": true
        }
        """);
    controller.enter(tempDir);

    Path mapped = invokeResolvePluginSchemaPath(controller, "demo");
    Path fallback = invokeResolvePluginSchemaPath(controller, "unknown");
    Path blank = invokeResolvePluginSchemaPath(controller, " ");
    ConfigController noRootController =
        new ConfigController(
            new StateMachine(State.HOME),
            MetadataRegistry.getDefault(),
            new ConfigValidationService(),
            messageSource);
    Path unknownWithoutRoot = invokeResolvePluginSchemaPath(noRootController, "unknown");

    assertThat(mapped)
        .isEqualTo(
            tempDir
                .resolve(".ful")
                .resolve("plugins")
                .resolve("demo")
                .resolve("config")
                .resolve("schema.json"));
    assertThat(fallback)
        .isEqualTo(
            tempDir
                .resolve(".ful")
                .resolve("plugins")
                .resolve("unknown")
                .resolve("config")
                .resolve("schema.json"));
    assertThat(blank).isNull();
    assertThat(unknownWithoutRoot).isNull();
  }

  @Test
  void pollExternalChangesReloadsPluginContextAndHandlesPluginDeletion() throws Exception {
    writeConfig(validConfigJson("mock", 1, 3));
    writePluginConfig("demo", "{ \"enabled\": true }");
    controller.enter(tempDir);
    controller.selectCategory(categorySelection("plugins"));
    controller.selectItem(1);

    overwritePluginConfig("demo", "{ \"enabled\": false }");
    boolean reloadedExisting = controller.pollExternalChanges();
    assertThat(reloadedExisting).isTrue();
    assertThat(controller.getActivePluginId()).isEqualTo("demo");
    assertThat(controller.getConfigEditor().getValue(path("enabled"))).isEqualTo(false);

    resetExternalPollThrottle(controller);
    deleteDirectory(tempDir.resolve(".ful").resolve("plugins").resolve("demo"));
    boolean reloadedAfterDelete = controller.pollExternalChanges();
    assertThat(reloadedAfterDelete).isTrue();
    assertThat(controller.getActivePluginId()).isNull();
    assertThat(controller.getStatusMessage())
        .isEqualTo(messageSource.getMessage(TuiMessageSource.CONFIG_RELOADED));
  }

  @Test
  void enterShowsLoadErrorMessageWhenConfigJsonIsMalformed() throws Exception {
    writeConfig("{");

    controller.enter(tempDir);

    assertThat(stateMachine.getCurrentState()).isEqualTo(State.CONFIG_CATEGORY);
    assertThat(controller.getStatusMessage())
        .startsWith(messageSource.getMessage("tui.config_editor.error.load_failed", ""));
  }

  @Test
  void getPathLabelCoversPluginAdvancedAndStandardBranches() throws Exception {
    assertThat(controller.getPathLabel()).isEqualTo("(none)");

    writeConfig(validConfigJson("mock", 1, 3, "\"advanced_object\": { \"x\": 1 }"));
    writePluginConfig("demo", "{ \"nested\": { \"enabled\": true } }");
    controller.enter(tempDir);

    controller.selectCategory(categorySelection("plugins"));
    assertThat(controller.getPathLabel()).isEqualTo("plugins");

    controller.selectItem(1);
    assertThat(controller.getPathLabel()).isEqualTo("plugins/demo");

    boolean openedPluginObject = controller.openAtPath("nested");
    assertThat(openedPluginObject).isTrue();
    assertThat(controller.getPathLabel()).isEqualTo("plugins/demo > nested");

    controller.selectCategory(categorySelection("advanced"));
    assertThat(controller.getPathLabel()).isEqualTo("advanced");

    controller.selectCategory(categorySelection("project"));
    assertThat(controller.getPathLabel()).contains("project");
  }

  @Test
  void backFromEditReturnsToListModeForListItemAndListAdd() throws Exception {
    writeConfig(validConfigJson("mock", 1, 3));
    controller.enter(tempDir);
    controller.openAtPath("selection_rules.exclude_annotations");
    controller.selectListItem(1);

    controller.backFromEdit();

    assertThat(controller.getEditMode()).isEqualTo(ConfigController.EditMode.LIST);
    assertThat(controller.getListEditIndex()).isEqualTo(-1);
    assertThat(stateMachine.getCurrentState()).isEqualTo(State.CONFIG_EDIT);

    controller.beginListAdd();
    controller.getValueBuffer().append("X");
    controller.backFromEdit();

    assertThat(controller.getEditMode()).isEqualTo(ConfigController.EditMode.LIST);
    assertThat(controller.getListEditIndex()).isEqualTo(-1);
    assertThat(controller.getValueBuffer().toString()).isEmpty();
    assertThat(controller.getNumberBuffer().toString()).isEmpty();
  }

  @Test
  void applyEnumSelectionShowsErrorForInvalidSelection() throws Exception {
    writeConfig(validConfigJson("mock", 1, 3));
    controller.enter(tempDir);
    controller.openAtPath("llm.provider");

    controller.applyEnumSelection(999);

    assertThat(controller.getStatusMessage())
        .isEqualTo(messageSource.getMessage(TuiMessageSource.CONFIG_ERROR_INVALID_ENUM));
  }

  @Test
  void applyScalarInputCoversEmptyValidationCreateAndUpdateFailure() throws Exception {
    writeConfig(validConfigJson("mock", 1, 3));
    controller.enter(tempDir);

    controller.openAtPath("selection_rules.method_min_loc");
    controller.getValueBuffer().setLength(0);
    controller.applyScalarInput();
    assertThat(controller.getStatusMessage())
        .isEqualTo(messageSource.getMessage(TuiMessageSource.CONFIG_ERROR_VALUE_REQUIRED));

    controller.getValueBuffer().setLength(0);
    controller.getValueBuffer().append("invalid");
    controller.applyScalarInput();
    assertThat(controller.getStatusMessage()).contains("Value type");

    boolean openedMissingScalar = controller.openAtPath("analysis.spoon.new_scalar");
    assertThat(openedMissingScalar).isTrue();
    controller.getValueBuffer().setLength(0);
    controller.getValueBuffer().append("42");
    controller.applyScalarInput();
    assertThat(controller.getConfigEditor().getValue(path("analysis", "spoon", "new_scalar")))
        .isEqualTo(42L);
    assertThat(controller.getStatusMessage()).isNull();

    controller.beginEdit(List.of(ConfigEditor.PathSegment.index(0)));
    controller.getValueBuffer().setLength(0);
    controller.getValueBuffer().append("1");
    controller.applyScalarInput();
    assertThat(controller.getStatusMessage())
        .isEqualTo(messageSource.getMessage(TuiMessageSource.CONFIG_ERROR_UPDATE_FAILED));
  }

  @Test
  void applyListAddAndDeleteCoverFailureBranches() throws Exception {
    writeConfig(validConfigJson("mock", 1, 3));
    controller.enter(tempDir);
    controller.openAtPath("selection_rules.exclude_annotations");

    controller.beginListAdd();
    controller.applyListAdd();
    assertThat(controller.getStatusMessage())
        .isEqualTo(messageSource.getMessage(TuiMessageSource.CONFIG_ERROR_VALUE_REQUIRED));

    controller.getValueBuffer().setLength(0);
    controller.getValueBuffer().append("123");
    controller.applyListAdd();
    assertThat(controller.getStatusMessage()).contains("Value type");

    controller.beginEdit(List.of(ConfigEditor.PathSegment.index(0)));
    controller.beginListAdd();
    controller.getValueBuffer().setLength(0);
    controller.getValueBuffer().append("X");
    controller.applyListAdd();
    assertThat(controller.getStatusMessage())
        .isEqualTo(messageSource.getMessage(TuiMessageSource.CONFIG_ERROR_ITEM_ADD_FAILED));

    controller.beginEdit(path("selection_rules", "exclude_annotations"));
    controller.appendNumber('9');
    controller.deleteListItem();
    assertThat(controller.getStatusMessage())
        .isEqualTo(messageSource.getMessage(TuiMessageSource.CONFIG_ERROR_INVALID_ITEM));
  }

  @Test
  void runValidationInPluginContextUsesSchemaAndHandlesJumpToIssueEdgeCases() throws Exception {
    writeConfig(validConfigJson("mock", 1, 3));
    writePluginConfig("demo", "{ \"enabled\": \"yes\" }");
    writePluginSchema(
        "demo",
        """
        {
          "$schema": "https://json-schema.org/draft/2020-12/schema",
          "type": "object",
          "properties": { "enabled": { "type": "boolean" } },
          "additionalProperties": false
        }
        """);
    controller.enter(tempDir);
    controller.selectCategory(categorySelection("plugins"));
    controller.selectItem(1);

    boolean valid = controller.runValidation(State.CONFIG_ITEMS, " ");

    assertThat(valid).isFalse();
    assertThat(stateMachine.getCurrentState()).isEqualTo(State.CONFIG_VALIDATE);
    assertThat(controller.getStatusMessage())
        .isEqualTo(messageSource.getMessage(TuiMessageSource.CONFIG_VALIDATION_FAILED_MSG));

    boolean invalidSelection = controller.jumpToIssue(99);
    assertThat(invalidSelection).isFalse();
    assertThat(controller.getStatusMessage())
        .isEqualTo(messageSource.getMessage(TuiMessageSource.INPUT_ERROR_INVALID_ERROR_NUMBER));

    setValidationIssues(
        controller, List.of(new ConfigValidationService.ValidationIssue("", "missing path")));
    boolean noPath = controller.jumpToIssue(1);
    assertThat(noPath).isFalse();
    assertThat(controller.getStatusMessage())
        .isEqualTo(messageSource.getMessage(TuiMessageSource.CONFIG_ERROR_NO_PATH));
  }

  @Test
  void saveDiscardAndSaveAllCoverNoDirtyAndLoadedEditorBranches() throws Exception {
    writeConfig(validConfigJson("mock", 1, 3));
    controller.enter(tempDir);

    boolean saveAllWithoutChanges = controller.saveAll(State.CONFIG_ITEMS, null);
    assertThat(saveAllWithoutChanges).isTrue();

    controller.openAtPath("selection_rules.method_min_loc");
    controller.getValueBuffer().setLength(0);
    controller.getValueBuffer().append("2");
    controller.applyScalarInput();
    controller.save();
    assertThat(controller.getStatusMessage())
        .isEqualTo(messageSource.getMessage(TuiMessageSource.CONFIG_SUCCESS_SAVED));
    assertThat(Files.readString(tempDir.resolve("config.json"))).contains("\"method_min_loc\" : 2");

    controller.openAtPath("selection_rules.method_min_loc");
    controller.getValueBuffer().setLength(0);
    controller.getValueBuffer().append("3");
    controller.applyScalarInput();
    controller.discard();
    assertThat(controller.getStatusMessage())
        .isEqualTo(messageSource.getMessage(TuiMessageSource.CONFIG_SUCCESS_DISCARDED));
    assertThat(controller.getConfigEditor().getValue(path("selection_rules", "method_min_loc")))
        .isEqualTo(2L);
  }

  @Test
  void pluginSchemaLoadFailureAndPluginRemovalOnDiscardCoverPluginBranches() throws Exception {
    writeConfig(validConfigJson("mock", 1, 3));
    writePluginConfig("demo", "{ \"enabled\": true }");
    writePluginSchema("demo", "{ invalid schema");
    controller.enter(tempDir);
    controller.selectCategory(categorySelection("plugins"));
    controller.selectItem(1);

    assertThat(controller.getStatusMessage()).startsWith("Failed to read schema file:");

    controller.openAtPath("enabled");
    controller.toggleBoolean();
    assertThat(controller.hasDirtyEditors()).isTrue();

    deleteDirectory(tempDir.resolve(".ful").resolve("plugins").resolve("demo"));
    controller.discard();

    assertThat(controller.getActivePluginId()).isNull();
    assertThat(controller.getConfigEditor().getValue(path("project", "id")))
        .isEqualTo("demo-project");
  }

  @Test
  void pollExternalChangesCoversNullRootThrottlePendingReloadAndNullSnapshot() throws Exception {
    ConfigController noRootController =
        new ConfigController(
            new StateMachine(State.HOME),
            MetadataRegistry.getDefault(),
            new ConfigValidationService(),
            messageSource);
    assertThat(noRootController.pollExternalChanges()).isFalse();

    writeConfig(validConfigJson("mock", 1, 3));
    controller.enter(tempDir);
    setLastExternalSnapshot(controller, null);
    resetExternalPollThrottle(controller);
    assertThat(controller.pollExternalChanges()).isFalse();

    overwriteConfig(validConfigJson("openai", 1, 3));
    resetExternalPollThrottle(controller);
    assertThat(controller.pollExternalChanges()).isTrue();
    assertThat(controller.pollExternalChanges()).isFalse();

    controller.openAtPath("llm.provider");
    controller.applyEnumSelection(findSelection(controller.getEnumOptions(), "mock"));
    overwriteConfig(validConfigJson("vertex-ai", 1, 3));
    resetExternalPollThrottle(controller);
    boolean pendingChanged = controller.pollExternalChanges();
    assertThat(pendingChanged).isTrue();
    assertThat(controller.getStatusMessage())
        .isEqualTo(messageSource.getMessage(TuiMessageSource.CONFIG_EXTERNAL_CHANGED));

    controller.openAtPath("llm.provider");
    controller.applyEnumSelection(findSelection(controller.getEnumOptions(), "openai"));
    resetExternalPollThrottle(controller);
    boolean reloadedPendingSnapshot = controller.pollExternalChanges();
    assertThat(reloadedPendingSnapshot).isTrue();
    assertThat(controller.getStatusMessage())
        .isEqualTo(messageSource.getMessage(TuiMessageSource.CONFIG_RELOADED));
  }

  @Test
  void openAtPathUsesMetadataObjectAndPluginObjectBranches() throws Exception {
    writeConfig(validConfigJson("mock", 1, 3));
    writePluginConfig("demo", "{ \"object\": { \"enabled\": true } }");
    controller.enter(tempDir);

    boolean openedMetadataObject = controller.openAtPath("llm.custom_headers");
    assertThat(openedMetadataObject).isTrue();
    assertThat(controller.getCategory()).isEqualTo("llm");
    assertThat(stateMachine.getCurrentState()).isEqualTo(State.CONFIG_ITEMS);
    assertThat(controller.getPath()).hasSize(2);
    assertThat(controller.getPath().get(0).key()).isEqualTo("llm");
    assertThat(controller.getPath().get(1).key()).isEqualTo("custom_headers");

    controller.selectCategory(categorySelection("plugins"));
    controller.selectItem(1);
    boolean openedPluginObject = controller.openAtPath("object");
    assertThat(openedPluginObject).isTrue();
    assertThat(stateMachine.getCurrentState()).isEqualTo(State.CONFIG_ITEMS);
    assertThat(controller.getPath()).hasSize(1);
    assertThat(controller.getPath().get(0).key()).isEqualTo("object");
  }

  private void writeConfig(String json) throws IOException {
    Files.writeString(tempDir.resolve("config.json"), json);
  }

  private void overwriteConfig(String json) throws IOException {
    Path configPath = tempDir.resolve("config.json");
    Files.writeString(configPath, json);
    Files.setLastModifiedTime(configPath, FileTime.fromMillis(System.currentTimeMillis() + 2000));
  }

  private void writePluginConfig(String pluginId, String json) throws IOException {
    Path configDir = tempDir.resolve(".ful").resolve("plugins").resolve(pluginId).resolve("config");
    Files.createDirectories(configDir);
    Files.writeString(configDir.resolve("config.json"), json);
  }

  private void overwritePluginConfig(String pluginId, String json) throws IOException {
    Path configPath =
        tempDir
            .resolve(".ful")
            .resolve("plugins")
            .resolve(pluginId)
            .resolve("config")
            .resolve("config.json");
    Files.writeString(configPath, json);
    Files.setLastModifiedTime(configPath, FileTime.fromMillis(System.currentTimeMillis() + 2000));
  }

  private void writePluginSchema(String pluginId, String json) throws IOException {
    Path configDir = tempDir.resolve(".ful").resolve("plugins").resolve(pluginId).resolve("config");
    Files.createDirectories(configDir);
    Files.writeString(configDir.resolve("schema.json"), json);
  }

  private void deleteDirectory(Path path) throws IOException {
    if (!Files.exists(path)) {
      return;
    }
    try (var stream = Files.walk(path)) {
      stream
          .sorted(Comparator.reverseOrder())
          .forEach(ConfigControllerIntegrationTest::deleteQuietly);
    }
  }

  private static void deleteQuietly(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private int categorySelection(String categoryName) {
    int index = controller.getCategories().indexOf(categoryName);
    assertThat(index).isGreaterThanOrEqualTo(0);
    return index + 1;
  }

  private void selectCurrentItemByLabel(String label) {
    List<ConfigEditor.ConfigItem> items = controller.getCurrentItems();
    int index =
        IntStream.range(0, items.size())
            .filter(i -> label.equals(items.get(i).label()))
            .findFirst()
            .orElse(-1);
    assertThat(index).isGreaterThanOrEqualTo(0);
    controller.selectItem(index + 1);
  }

  private static Path invokeResolvePluginSchemaPath(ConfigController target, String pluginId)
      throws Exception {
    Method method =
        ConfigController.class.getDeclaredMethod("resolvePluginSchemaPath", String.class);
    method.setAccessible(true);
    return (Path) method.invoke(target, pluginId);
  }

  private static void resetExternalPollThrottle(ConfigController target) throws Exception {
    Field field = ConfigController.class.getDeclaredField("lastExternalCheckMs");
    field.setAccessible(true);
    field.setLong(target, 0L);
  }

  private static void setLastExternalSnapshot(ConfigController target, Object value)
      throws Exception {
    Field field = ConfigController.class.getDeclaredField("lastExternalSnapshot");
    field.setAccessible(true);
    field.set(target, value);
  }

  private static void setValidationIssues(
      ConfigController target, List<ConfigValidationService.ValidationIssue> issues)
      throws Exception {
    Field field = ConfigController.class.getDeclaredField("validationIssues");
    field.setAccessible(true);
    field.set(target, issues);
  }

  private static int findSelection(List<String> options, String desiredValue) {
    if (options == null || options.isEmpty()) {
      return 1;
    }
    Optional<Integer> matched =
        IntStream.range(0, options.size())
            .filter(i -> desiredValue.equalsIgnoreCase(options.get(i)))
            .boxed()
            .findFirst();
    if (matched.isPresent()) {
      return matched.get() + 1;
    }
    return 1;
  }

  private static List<ConfigEditor.PathSegment> path(String... keys) {
    List<ConfigEditor.PathSegment> segments = new ArrayList<>();
    for (String key : keys) {
      segments.add(ConfigEditor.PathSegment.key(key));
    }
    return segments;
  }

  private static String validConfigJson(String provider, int methodMinLoc, int methodMaxLoc) {
    return validConfigJson(provider, methodMinLoc, methodMaxLoc, null);
  }

  private static String validConfigJson(
      String provider, int methodMinLoc, int methodMaxLoc, String additionalRootSection) {
    String extra =
        additionalRootSection == null || additionalRootSection.isBlank()
            ? ""
            : ",\n  " + additionalRootSection.stripIndent().trim();
    return """
        {
          "schema_version": 1,
          "project": {
            "id": "demo-project"
          },
          "analysis": {
            "spoon": {
              "no_classpath": false
            }
          },
          "selection_rules": {
            "class_min_loc": 1,
            "class_min_method_count": 1,
            "method_min_loc": %d,
            "method_max_loc": %d,
            "exclude_annotations": [
              "A"
            ]
          },
          "llm": {
            "provider": "%s"
          }%s
        }
        """
        .formatted(methodMinLoc, methodMaxLoc, provider, extra);
  }
}
