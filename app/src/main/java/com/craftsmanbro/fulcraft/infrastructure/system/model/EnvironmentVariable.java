package com.craftsmanbro.fulcraft.infrastructure.system.model;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import java.util.Objects;

/** Value object representing an environment variable lookup result. */
public record EnvironmentVariable(String name, String value) {
  private static final String ARGUMENT_NULL_MESSAGE_KEY = "infra.common.error.argument_null";
  private static final String NAME_NULL_MESSAGE =
      "Environment variable name must not be null";

  public EnvironmentVariable {
    Objects.requireNonNull(name, argumentNullMessage(NAME_NULL_MESSAGE));
  }

  private static String argumentNullMessage(final String argumentDescription) {
    return MessageSource.getMessage(ARGUMENT_NULL_MESSAGE_KEY, argumentDescription);
  }

  public String orDefault(final String defaultValue) {
    return isBlank() ? defaultValue : value;
  }

  public boolean isBlank() {
    return value == null || value.isBlank();
  }
}
