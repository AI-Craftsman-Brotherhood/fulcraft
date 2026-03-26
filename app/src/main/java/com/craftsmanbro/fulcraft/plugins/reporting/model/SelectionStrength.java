package com.craftsmanbro.fulcraft.plugins.reporting.model;

/**
 * Represents the evaluation result of Selection strength.
 *
 * <p>Used to classify whether Defensive Selection rules are too strict (excluding too many
 * potentially successful tasks), too weak (not excluding enough failing tasks), or appropriately
 * calibrated.
 */
public enum SelectionStrength {
  /** Selection rules are appropriately calibrated. */
  OK,

  /** Selection rules are too strict - excluding too many tasks that could have succeeded. */
  TOO_STRICT,

  /** Selection rules are too weak - not excluding enough tasks that are likely to fail. */
  TOO_WEAK
}
