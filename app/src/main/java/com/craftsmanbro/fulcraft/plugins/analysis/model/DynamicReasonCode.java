package com.craftsmanbro.fulcraft.plugins.analysis.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Locale;

/**
 * Reason codes for unresolved or partially resolved DynamicResolution results. These codes provide
 * insight into why a resolution could not be determined.
 */
public enum DynamicReasonCode {

  /** Expression type is not supported by resolveStringExpression */
  UNSUPPORTED_EXPRESSION("Expression type is not supported for resolution"),
  /** Multiple branch candidates found but cannot determine single value */
  AMBIGUOUS_CANDIDATES("Multiple candidates found, cannot determine single value"),
  /** Recursion depth exceeded during resolution */
  DEPTH_LIMIT_EXCEEDED("Resolution depth limit exceeded"),
  /** Number of candidates exceeded the configured limit */
  CANDIDATE_LIMIT_EXCEEDED("Candidate limit exceeded, results truncated"),
  /** Required dependency could not be resolved */
  UNRESOLVED_DEPENDENCY("Required dependency could not be resolved"),
  /** Target class could not be resolved from available symbols */
  TARGET_CLASS_UNRESOLVED("Target class could not be resolved"),
  /** Target class is known but requested method/signature is not found */
  TARGET_METHOD_MISSING("Target class is known but requested method was not found");

  private final String description;

  DynamicReasonCode(final String description) {
    this.description = description;
  }

  public String getDescription() {
    return description;
  }

  @JsonCreator
  public static DynamicReasonCode fromString(final String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return DynamicReasonCode.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
