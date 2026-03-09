package com.craftsmanbro.fulcraft.infrastructure.parser.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Locale;

/**
 * Identifies which resolution rule was applied during dynamic analysis. Each value corresponds to a
 * specific resolution strategy in DynamicResolver.
 */
public enum ResolutionRuleId {

  /** Direct string literal expression */
  LITERAL("Resolved from direct string literal"),
  /** Resolved from a static final String constant field */
  STATIC_FIELD("Resolved from static final String constant"),
  /** Resolved from a local constant variable */
  LOCAL_CONST("Resolved from local constant variable"),
  /** Resolved by concatenating binary + expressions */
  BINARY_CONCAT("Resolved via binary + concatenation"),
  /** Resolved via String.format() pattern */
  STRING_FORMAT("Resolved via String.format()"),
  /** Resolved via String.join() */
  STRING_JOIN("Resolved via String.join()"),
  /** Resolved via StringBuilder/StringBuffer append chain */
  STRING_BUILDER("Resolved via StringBuilder append chain"),
  /** Resolved via String.concat() method */
  STRING_CONCAT_METHOD("Resolved via String.concat() method"),
  /** Resolved via String.valueOf() */
  STRING_VALUEOF("Resolved via String.valueOf()"),
  /** Resolved via inter-procedural analysis (single call site) */
  INTERPROCEDURAL("Resolved via inter-procedural analysis"),
  /** Resolved via external configuration values */
  EXTERNAL_CONFIG("Resolved from external configuration values"),
  /** Resolved via enum constant with string value */
  ENUM_CONST("Resolved from enum constant");

  private final String description;

  ResolutionRuleId(final String description) {
    this.description = description;
  }

  public String getDescription() {
    return description;
  }

  @JsonCreator
  public static ResolutionRuleId fromString(final String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return ResolutionRuleId.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
