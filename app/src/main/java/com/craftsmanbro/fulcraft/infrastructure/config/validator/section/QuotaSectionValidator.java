package com.craftsmanbro.fulcraft.infrastructure.config.validator.section;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.config.validator.ConfigSectionValidator;
import com.craftsmanbro.fulcraft.infrastructure.config.validator.ConfigValidationSupport;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

public class QuotaSectionValidator implements ConfigSectionValidator {

  private static final String QUOTA = "quota";

  private static final String KEY_QUOTA_MAX_TASKS = "max_tasks";

  private static final String KEY_QUOTA_MAX_LLM_CALLS = "max_llm_calls";

  private static final String KEY_QUOTA_ON_EXCEED = "on_exceed";

  @Override
  public String sectionKey() {
    return QUOTA;
  }

  @Override
  public void validateRawSection(final Map<?, ?> quota, final List<String> errors) {
    final Set<String> allowed =
        Set.of(KEY_QUOTA_MAX_TASKS, KEY_QUOTA_MAX_LLM_CALLS, KEY_QUOTA_ON_EXCEED);
    ConfigValidationSupport.addUnknownKeys(quota, QUOTA, allowed, errors);
  }

  @Override
  public void validateParsedConfig(final Config config, final List<String> errors) {
    final Config.QuotaConfig quotaConfig = config.getQuota();
    if (quotaConfig == null) {
      return;
    }
    final Integer maxTasks = quotaConfig.getMaxTasks();
    if (maxTasks != null && maxTasks < 0) {
      errors.add("'quota.max_tasks' must be a non-negative integer.");
    }
    final Integer maxLlmCalls = quotaConfig.getMaxLlmCalls();
    if (maxLlmCalls != null && maxLlmCalls < 0) {
      errors.add("'quota.max_llm_calls' must be a non-negative integer.");
    }
    final String onExceed = quotaConfig.getOnExceed();
    if (StringUtils.isNotBlank(onExceed)) {
      final String normalized = onExceed.trim().toLowerCase(java.util.Locale.ROOT);
      if (!"warn".equals(normalized) && !"block".equals(normalized)) {
        errors.add("'quota.on_exceed' must be either 'warn' or 'block'.");
      }
    }
  }
}
