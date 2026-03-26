package com.craftsmanbro.fulcraft.config;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import java.util.List;
import java.util.Objects;

/**
 * Exception thrown when configuration validation fails.
 *
 * <p>This exception is thrown during startup when required configuration values are missing or
 * invalid. The application should stop execution when this exception is thrown.
 */
public class InvalidConfigurationException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private static final String VALIDATION_FAILED_MESSAGE_KEY = "config.invalid.validation_failed";

  private final List<String> errors;

  /**
   * Creates a new InvalidConfigurationException with a single error message.
   *
   * @param message The error message describing the configuration problem.
   */
  public InvalidConfigurationException(final String message) {
    super(normalizeMessage(message));
    this.errors = List.of(normalizeMessage(message));
  }

  /**
   * Creates a new InvalidConfigurationException with a single error message and cause.
   *
   * @param message The error message describing the configuration problem.
   * @param cause The underlying cause of the exception.
   */
  public InvalidConfigurationException(final String message, final Throwable cause) {
    super(normalizeMessage(message), cause);
    this.errors = List.of(normalizeMessage(message));
  }

  /**
   * Creates a new InvalidConfigurationException with multiple error messages.
   *
   * @param errors The list of validation error messages.
   */
  public InvalidConfigurationException(final List<String> errors) {
    super(formatErrors(errors));
    this.errors = sanitizeErrors(errors);
  }

  /**
   * Returns the list of validation errors.
   *
   * @return An unmodifiable list of error messages.
   */
  public List<String> getErrors() {
    return errors;
  }

  private static String formatErrors(final List<String> errors) {
    final String validationFailedMessage = getValidationFailedMessage();
    if (errors == null || errors.isEmpty()) {
      return validationFailedMessage;
    }
    final StringBuilder formattedErrorsBuilder =
        new StringBuilder(validationFailedMessage).append(":\n");
    for (final String errorMessage : errors) {
      formattedErrorsBuilder.append("  - ").append(errorMessage).append("\n");
    }
    return formattedErrorsBuilder.toString();
  }

  private static List<String> sanitizeErrors(final List<String> errors) {
    if (errors == null || errors.isEmpty()) {
      return List.of(getValidationFailedMessage());
    }
    return errors.stream()
        .filter(Objects::nonNull)
        .map(InvalidConfigurationException::normalizeMessage)
        .filter(normalizedErrorMessage -> !normalizedErrorMessage.isBlank())
        .toList();
  }

  private static String normalizeMessage(final String message) {
    if (message == null || message.isBlank()) {
      return getValidationFailedMessage();
    }
    return message;
  }

  private static String getValidationFailedMessage() {
    return MessageSource.getMessage(VALIDATION_FAILED_MESSAGE_KEY);
  }
}
