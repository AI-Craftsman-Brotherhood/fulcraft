package com.craftsmanbro.fulcraft.plugins.analysis.config.validation;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.config.validator.ConfigSectionValidator;
import com.craftsmanbro.fulcraft.infrastructure.config.validator.ConfigValidationSupport;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AnalysisSectionValidator implements ConfigSectionValidator {

  private static final String ANALYSIS = "analysis";

  private static final String KEY_SOURCE_ROOT_PATHS = "source_root_paths";

  private static final Set<String> ALLOWED_KEYS =
      Set.of(
          "spoon",
          "dump_file_list",
          "source_root_mode",
          KEY_SOURCE_ROOT_PATHS,
          "source_charset",
          "classpath",
          "external_config_resolution",
          "enable_interprocedural_resolution",
          "interprocedural_callsite_limit",
          "debug_dynamic_resolution",
          "experimental_candidate_enum");

  @Override
  public String sectionKey() {
    return ANALYSIS;
  }

  @Override
  public void validateRawSection(final Map<?, ?> analysis, final List<String> errors) {
    ConfigValidationSupport.addUnknownKeys(analysis, ANALYSIS, ALLOWED_KEYS, errors);
    final Object sourceRootPaths = analysis.get(KEY_SOURCE_ROOT_PATHS);
    if (sourceRootPaths instanceof List<?> paths) {
      for (final Object path : paths) {
        if (!(path instanceof String value) || value.isBlank()) {
          errors.add(MessageSource.getMessage("analysis.config.validation.source_root_paths"));
          return;
        }
      }
    }
  }
}
