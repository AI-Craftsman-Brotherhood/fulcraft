package com.craftsmanbro.fulcraft.kernel.pipeline.context;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Captures warnings and errors during a pipeline run. */
public final class RunDiagnostics {

  private static final String ARGUMENT_NULL_MESSAGE_KEY = "kernel.common.error.argument_null";

  private static final String ERROR_ARGUMENT_NAME = "error";

  private static final String WARNING_ARGUMENT_NAME = "warning";

  private final List<String> errors = new ArrayList<>();

  private final List<String> warnings = new ArrayList<>();

  public List<String> getErrors() {
    return List.copyOf(errors);
  }

  public List<String> getWarnings() {
    return List.copyOf(warnings);
  }

  public void addError(final String error) {
    errors.add(Objects.requireNonNull(error, argumentNullMessage(ERROR_ARGUMENT_NAME)));
  }

  public void addWarning(final String warning) {
    warnings.add(Objects.requireNonNull(warning, argumentNullMessage(WARNING_ARGUMENT_NAME)));
  }

  public boolean hasErrors() {
    return !errors.isEmpty();
  }

  public boolean hasWarnings() {
    return !warnings.isEmpty();
  }

  private static String argumentNullMessage(final String argumentName) {
    return MessageSource.getMessage(ARGUMENT_NULL_MESSAGE_KEY, argumentName);
  }
}
