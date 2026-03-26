package com.craftsmanbro.fulcraft.infrastructure.config.validator;

import com.craftsmanbro.fulcraft.config.Config;
import java.util.List;
import java.util.Map;

public interface ConfigSectionValidator {

  String sectionKey();

  default boolean isRequired() {
    return false;
  }

  default void validateRawSection(final Map<?, ?> section, final List<String> errors) {}

  default void validateParsedConfig(final Config config, final List<String> errors) {}
}
