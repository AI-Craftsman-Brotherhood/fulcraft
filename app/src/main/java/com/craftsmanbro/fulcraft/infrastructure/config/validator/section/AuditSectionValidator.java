package com.craftsmanbro.fulcraft.infrastructure.config.validator.section;

import com.craftsmanbro.fulcraft.infrastructure.config.validator.ConfigSectionValidator;
import com.craftsmanbro.fulcraft.infrastructure.config.validator.ConfigValidationSupport;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AuditSectionValidator implements ConfigSectionValidator {

  private static final String AUDIT = "audit";

  private static final String KEY_AUDIT_ENABLED = "enabled";

  private static final String KEY_AUDIT_LOG_PATH = "log_path";

  private static final String KEY_AUDIT_INCLUDE_RAW = "include_raw";

  @Override
  public String sectionKey() {
    return AUDIT;
  }

  @Override
  public void validateRawSection(final Map<?, ?> audit, final List<String> errors) {
    final Set<String> allowed =
        Set.of(KEY_AUDIT_ENABLED, KEY_AUDIT_LOG_PATH, KEY_AUDIT_INCLUDE_RAW);
    ConfigValidationSupport.addUnknownKeys(audit, AUDIT, allowed, errors);
  }
}
