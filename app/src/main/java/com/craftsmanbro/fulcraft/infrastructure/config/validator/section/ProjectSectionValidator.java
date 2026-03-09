package com.craftsmanbro.fulcraft.infrastructure.config.validator.section;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.config.validator.ConfigSectionValidator;
import com.craftsmanbro.fulcraft.infrastructure.config.validator.ConfigValidationSupport;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

public class ProjectSectionValidator implements ConfigSectionValidator {

  private static final String PROJECT = "project";

  private static final String KEY_ID = "id";

  private static final String KEY_ROOT = "root";

  private static final String KEY_DOCS_OUTPUT = "docs_output";

  private static final String KEY_EXCLUDE_PATHS = "exclude_paths";

  private static final String KEY_INCLUDE_PATHS = "include_paths";

  @Override
  public String sectionKey() {
    return PROJECT;
  }

  @Override
  public boolean isRequired() {
    return true;
  }

  @Override
  public void validateRawSection(final Map<?, ?> project, final List<String> errors) {
    final Set<String> allowed =
        Set.of(
            KEY_ID,
            KEY_ROOT,
            KEY_DOCS_OUTPUT,
            "repo_url",
            "commit",
            "build_tool",
            "build_command",
            KEY_EXCLUDE_PATHS,
            KEY_INCLUDE_PATHS);
    ConfigValidationSupport.addUnknownKeys(project, PROJECT, allowed, errors);
  }

  @Override
  public void validateParsedConfig(final Config config, final List<String> errors) {
    if (config.getProject() == null || StringUtils.isBlank(config.getProject().getId())) {
      errors.add("'project.id' is required and cannot be empty");
    }
  }
}
