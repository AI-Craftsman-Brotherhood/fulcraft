package com.craftsmanbro.fulcraft.infrastructure.parser.impl.common;

import com.craftsmanbro.fulcraft.infrastructure.parser.model.FieldInfo;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Determines whether a field should be mocked using AST-independent heuristics.
 *
 * <p>Uses a combination of: 1. Same-file type analysis (interfaces/abstract classes defined in the
 * same file) 2. Constructor injection detection (DI pattern) 3. Naming convention heuristics
 * (Repository, Gateway, Service, etc.)
 */
public final class MockHintStrategy {

  /** Naming patterns that typically indicate mockable dependencies. */
  private static final Pattern MOCKABLE_SUFFIX_PATTERN =
      Pattern.compile(
          ".*(Repository|Gateway|Service|Client|Port|Adapter|Handler|Provider|Factory"
              + "|Dao|Api|Connector|Manager|Helper|Delegate|Proxy|Strategy|Validator)$",
          Pattern.CASE_INSENSITIVE);

  /** Mock hint values. */
  public static final String HINT_REQUIRED = "required";

  public static final String HINT_RECOMMENDED = "recommended";

  private MockHintStrategy() {
    // Utility class
  }

  /**
   * Analyzes a field and determines its mock hint.
   *
   * @param field the field info to analyze
   * @param interfaceNames set of interface names defined in the same file
   * @param abstractClassNames set of abstract class names defined in the same file
   * @param constructorParamTypes set of type names passed to constructors
   * @return the mock hint: "required", "recommended", or null
   */
  public static String determineMockHint(
      final FieldInfo field,
      final Set<String> interfaceNames,
      final Set<String> abstractClassNames,
      final Set<String> constructorParamTypes) {
    final String typeName = field.getType();
    if (typeName == null) {
      return null;
    }
    final String normalizedTypeName = normalizeTypeName(typeName);
    // 1. Check if field is injectable (private final + constructor param)
    final boolean isInjectable =
        "private".equals(field.getVisibility())
            && field.isFinal()
            && !field.isStatic()
            && containsNormalized(constructorParamTypes, normalizedTypeName);
    if (isInjectable) {
      field.setInjectable(true);
    }
    // 2. Check if type is an interface or abstract class defined in the same file
    if (containsNormalized(interfaceNames, normalizedTypeName)
        || containsNormalized(abstractClassNames, normalizedTypeName)) {
      return HINT_REQUIRED;
    }
    // 3. Check heuristics for injectable fields
    if (isInjectable && matchesMockablePattern(normalizedTypeName)) {
      return HINT_RECOMMENDED;
    }
    // 4. Check naming heuristics even for non-injectable fields
    if (matchesMockablePattern(normalizedTypeName)
        && "private".equals(field.getVisibility())
        && !field.isStatic()) {
      return HINT_RECOMMENDED;
    }
    return null;
  }

  /**
   * Checks if a type name matches common mockable dependency patterns.
   *
   * @param typeName the type name to check
   * @return true if the type name matches a mockable pattern
   */
  public static boolean matchesMockablePattern(final String typeName) {
    final String normalizedTypeName = normalizeTypeName(typeName);
    if (normalizedTypeName == null) {
      return false;
    }
    return MOCKABLE_SUFFIX_PATTERN.matcher(normalizedTypeName).matches();
  }

  private static boolean containsNormalized(
      final Set<String> candidates, final String normalizedTypeName) {
    if (normalizedTypeName == null || candidates == null || candidates.isEmpty()) {
      return false;
    }
    for (final String candidate : candidates) {
      if (normalizedTypeName.equals(normalizeTypeName(candidate))) {
        return true;
      }
    }
    return false;
  }

  private static String normalizeTypeName(final String typeName) {
    if (typeName == null) {
      return null;
    }
    String baseType = typeName;
    final int genericStart = baseType.indexOf('<');
    if (genericStart >= 0) {
      baseType = baseType.substring(0, genericStart);
    }
    final int arrayStart = baseType.indexOf('[');
    if (arrayStart >= 0) {
      baseType = baseType.substring(0, arrayStart);
    }
    baseType = baseType.trim();
    final int lastDot = baseType.lastIndexOf('.');
    if (lastDot >= 0) {
      baseType = baseType.substring(lastDot + 1);
    }
    return baseType;
  }
}
