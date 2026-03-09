package com.craftsmanbro.fulcraft.plugins.analysis.config.validation;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.config.validator.ConfigSectionValidator;
import com.craftsmanbro.fulcraft.infrastructure.config.validator.ConfigValidationSupport;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SelectionRulesSectionValidator implements ConfigSectionValidator {

  private static final String SELECTION_RULES = "selection_rules";

  private static final String KEY_CLASS_MIN_LOC = "class_min_loc";

  private static final String KEY_CLASS_MIN_METHOD_COUNT = "class_min_method_count";

  private static final String KEY_METHOD_MIN_LOC = "method_min_loc";

  private static final String KEY_METHOD_MAX_LOC = "method_max_loc";

  private static final String KEY_MAX_TARGETS = "max_targets";

  private static final String KEY_MAX_METHODS_PER_CLASS = "max_methods_per_class";

  private static final String KEY_EXCLUDE_GETTERS_SETTERS = "exclude_getters_setters";

  private static final String KEY_MAX_METHODS_PER_PACKAGE = "max_methods_per_package";

  private static final String KEY_EXCLUDE_ANNOTATIONS = "exclude_annotations";

  private static final String KEY_DEPRIORITIZE_ANNOTATIONS = "deprioritize_annotations";

  private static final String KEY_SELECTION_ENGINE = "selection_engine";

  private static final String KEY_COMPLEXITY = "complexity";

  @Override
  public String sectionKey() {
    return SELECTION_RULES;
  }

  @Override
  public boolean isRequired() {
    return true;
  }

  @Override
  public void validateRawSection(final Map<?, ?> selectionRules, final List<String> errors) {
    validateKnownKeys(selectionRules, errors);
    validateLocRules(selectionRules, errors);
  }

  private void validateKnownKeys(final Map<?, ?> selectionRules, final List<String> errors) {
    final Set<String> allowed =
        Set.of(
            KEY_CLASS_MIN_LOC,
            KEY_CLASS_MIN_METHOD_COUNT,
            KEY_METHOD_MIN_LOC,
            KEY_METHOD_MAX_LOC,
            KEY_MAX_TARGETS,
            KEY_MAX_METHODS_PER_CLASS,
            KEY_EXCLUDE_GETTERS_SETTERS,
            KEY_MAX_METHODS_PER_PACKAGE,
            KEY_EXCLUDE_ANNOTATIONS,
            KEY_DEPRIORITIZE_ANNOTATIONS,
            KEY_SELECTION_ENGINE,
            KEY_COMPLEXITY);
    ConfigValidationSupport.addUnknownKeys(selectionRules, SELECTION_RULES, allowed, errors);
  }

  private void validateLocRules(final Map<?, ?> selectionRules, final List<String> errors) {
    final Integer minLoc =
        ConfigValidationSupport.coerceInteger(selectionRules.get(KEY_METHOD_MIN_LOC));
    final Integer maxLoc =
        ConfigValidationSupport.coerceInteger(selectionRules.get(KEY_METHOD_MAX_LOC));
    if (minLoc != null && maxLoc != null && minLoc > maxLoc) {
      errors.add("'selection_rules.method_min_loc' cannot be greater than 'method_max_loc'.");
    }
  }

  @Override
  public void validateParsedConfig(final Config config, final List<String> errors) {
    if (config.getSelectionRules() == null) {
      errors.add("'selection_rules' section is required");
      return;
    }
    final Integer minLoc = config.getSelectionRules().getMethodMinLoc();
    final Integer maxLoc = config.getSelectionRules().getMethodMaxLoc();
    if (minLoc != null && maxLoc != null && minLoc > maxLoc) {
      errors.add("'selection_rules.method_min_loc' cannot be greater than 'method_max_loc'.");
    }
  }
}
