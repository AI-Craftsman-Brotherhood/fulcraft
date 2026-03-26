package com.craftsmanbro.fulcraft.infrastructure.parser.impl.spoon;

import spoon.SpoonException;
import spoon.reflect.declaration.CtElement;

/**
 * Safe wrapper for Spoon element toString() which can throw SpoonException when it cannot compute
 * access paths for nested types with unresolved classpath.
 */
final class SafeSpoonPrinter {

  private SafeSpoonPrinter() {}

  /**
   * Returns the string representation of a Spoon element, falling back to an empty string if Spoon
   * throws a SpoonException during pretty-printing.
   */
  static String safeToString(final CtElement element) {
    if (element == null) {
      return "";
    }
    try {
      return element.toString();
    } catch (final SpoonException e) {
      return "";
    }
  }

  /**
   * Returns the string representation of a Spoon element, falling back to the provided default
   * value if Spoon throws a SpoonException during pretty-printing.
   */
  static String safeToString(final CtElement element, final String fallback) {
    if (element == null) {
      return fallback;
    }
    try {
      return element.toString();
    } catch (final SpoonException e) {
      return fallback;
    }
  }
}
