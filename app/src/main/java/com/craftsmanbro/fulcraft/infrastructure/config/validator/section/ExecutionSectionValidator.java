package com.craftsmanbro.fulcraft.infrastructure.config.validator.section;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.config.validator.ConfigSectionValidator;
import com.craftsmanbro.fulcraft.infrastructure.config.validator.ConfigValidationSupport;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ExecutionSectionValidator implements ConfigSectionValidator {

  private static final String EXECUTION = "execution";

  private static final String RUNTIME_FIX_RETRIES = "runtime_fix_retries";

  private static final int RECOMMENDED_MAX_RETRIES = 10;

  @Override
  public String sectionKey() {
    return EXECUTION;
  }

  @Override
  public void validateRawSection(final Map<?, ?> execution, final List<String> errors) {
    final Set<String> allowed =
        Set.of(
            "per_task_isolation",
            "logs_root",
            RUNTIME_FIX_RETRIES,
            "flaky_reruns",
            "unresolved_policy",
            "test_stability_policy");
    ConfigValidationSupport.addUnknownKeys(execution, EXECUTION, allowed, errors);
    ConfigValidationSupport.warnIfExceedsMax(
        execution, RUNTIME_FIX_RETRIES, EXECUTION, RECOMMENDED_MAX_RETRIES);
  }

  @Override
  public void validateParsedConfig(final Config config, final List<String> errors) {
    final Config.ExecutionConfig execution = config.getExecution();
    if (execution != null) {
      final String logsRoot = execution.getLogsRoot();
      if (logsRoot != null && logsRoot.isBlank()) {
        errors.add("'execution.logs_root' must be a non-empty string when provided.");
      }
      try {
        execution.getTestStabilityPolicyEnum();
      } catch (IllegalArgumentException e) {
        errors.add(e.getMessage());
      }
    }
  }
}
