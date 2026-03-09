package com.craftsmanbro.fulcraft.infrastructure.buildtool.failure.util;

/**
 * Types of assertion mismatches detected in test failures.
 *
 * <p>This enum categorizes the different types of assertion mismatches that can occur in test
 * failures, enabling appropriate fix suggestions and relaxation strategies.
 *
 * @see com.craftsmanbro.fulcraft.infrastructure.buildtool.failure.util.AssertionMismatchExtractor
 * @see com.craftsmanbro.fulcraft.infrastructure.buildtool.failure.util.FixSuggestionTemplates
 */
public enum MismatchType {
  /** Integer/long exact match failure */
  NUMERIC,

  /** Float/double precision issue */
  FLOAT_TOLERANCE,

  /** String content mismatch */
  STRING,

  /** List/Set size or content mismatch */
  COLLECTION,

  /** Map key/value mismatch */
  MAP,

  /** Object.equals() failure */
  OBJECT_EQUALS,

  /** Sort order mismatch */
  ORDERING,

  /** Exception message mismatch */
  EXCEPTION_MESSAGE,

  /** Null-related assertion failure */
  NULL_MISMATCH,

  /** Unknown or unparseable mismatch */
  UNKNOWN
}
