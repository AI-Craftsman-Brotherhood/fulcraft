package com.craftsmanbro.fulcraft.plugins.analysis.core.model;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import java.util.Locale;

/**
 * Reason for unresolved type resolution. Used to categorize why a type or method reference could
 * not be resolved.
 */
public enum UnresolvedReason {

  /** Dependency JAR is missing from classpath */
  MISSING_CLASSPATH("analysis.unresolved.missing_classpath"),
  /** Generic type arguments were erased */
  GENERICS_ERASED("analysis.unresolved.generics_erased"),
  /** Dynamic reflection call (Class.forName, Method.invoke) */
  REFLECTION_CALL("analysis.unresolved.reflection_call"),
  /** Spoon noClasspath mode was active */
  NO_CLASSPATH_MODE("analysis.unresolved.no_classpath_mode"),
  /** Source file parse error */
  PARSE_ERROR("analysis.unresolved.parse_error"),
  /** Unknown reason */
  UNKNOWN("analysis.unresolved.unknown");

  private final String messageKey;

  UnresolvedReason(final String messageKey) {
    this.messageKey = messageKey;
  }

  /**
   * Gets the localized description for this unresolved reason.
   *
   * @return the localized description
   */
  public String getDescription() {
    return MessageSource.getMessage(messageKey);
  }

  /**
   * Gets the message key for this unresolved reason.
   *
   * @return the message key
   */
  public String getMessageKey() {
    return messageKey;
  }

  public static UnresolvedReason fromString(final String value) {
    if (value == null || value.isBlank()) {
      return UNKNOWN;
    }
    try {
      return UnresolvedReason.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      return UNKNOWN;
    }
  }
}
