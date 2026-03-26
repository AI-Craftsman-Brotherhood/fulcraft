package com.craftsmanbro.fulcraft.plugins.analysis.core.service.analyzer;

import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.FieldInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Analyzes class structure to extract field-to-accessor mappings. This helps LLM understand how to
 * access private fields via public getters/setters.
 */
public class FieldAccessorAnalyzer {

  private static final String PRIVATE = "private";

  private static final String PUBLIC = "public";

  /**
   * Builds a human-readable summary of fields and their accessors.
   *
   * @param classInfo the class to analyze
   * @return formatted string showing field-accessor mappings
   */
  public String buildFieldAccessorSummary(final ClassInfo classInfo) {
    if (classInfo == null) {
      return "";
    }
    final var fields = classInfo.getFields();
    final var methods = classInfo.getMethods();
    if (fields.isEmpty()) {
      return "";
    }
    final var fieldAccessors = mapFieldsToAccessors(fields, methods);
    return formatFieldAccessorSummary(fieldAccessors);
  }

  /** Maps fields to their corresponding getter/setter methods. */
  Map<FieldInfo, FieldAccessors> mapFieldsToAccessors(
      final List<FieldInfo> fields, final List<MethodInfo> methods) {
    final Map<FieldInfo, FieldAccessors> result = new LinkedHashMap<>();
    // Build method lookup by name
    final Map<String, List<MethodInfo>> methodsByName = new HashMap<>();
    for (final var m : methods) {
      if (m != null && m.getName() != null) {
        methodsByName
            .computeIfAbsent(
                m.getName().toLowerCase(java.util.Locale.ROOT), key -> new ArrayList<>())
            .add(m);
      }
    }
    for (final var field : fields) {
      if (field == null || field.getName() == null) {
        continue;
      }
      final String fieldName = field.getName();
      final String capitalizedName = capitalize(fieldName);
      final String booleanSuffix = booleanPropertySuffix(fieldName, field.getType());
      // Look for getter: getX, isX (for boolean)
      final MethodInfo getter =
          findGetter(methodsByName, capitalizedName, field.getType(), booleanSuffix);
      // Look for setter: setX
      final MethodInfo setter = findSetter(methodsByName, capitalizedName, booleanSuffix);
      result.put(field, new FieldAccessors(getter, setter));
    }
    return result;
  }

  private MethodInfo findGetter(
      final Map<String, List<MethodInfo>> methodsByName,
      final String capitalizedName,
      final String fieldType,
      final String booleanSuffix) {
    // Try getX
    MethodInfo getter = findPublicMethod(methodsByName, "get" + capitalizedName, 0);
    if (getter != null) {
      return getter;
    }
    // For boolean fields, try isX
    if (isBooleanType(fieldType)) {
      getter = findPublicMethod(methodsByName, "is" + capitalizedName, 0);
      if (getter != null) {
        return getter;
      }
      if (booleanSuffix != null) {
        getter = findPublicMethod(methodsByName, "is" + booleanSuffix, 0);
        if (getter != null) {
          return getter;
        }
        getter = findPublicMethod(methodsByName, "get" + booleanSuffix, 0);
        return getter;
      }
    }
    return null;
  }

  private MethodInfo findSetter(
      final Map<String, List<MethodInfo>> methodsByName,
      final String capitalizedName,
      final String booleanSuffix) {
    final MethodInfo setter = findPublicMethod(methodsByName, "set" + capitalizedName, 1);
    if (setter != null) {
      return setter;
    }
    if (booleanSuffix != null) {
      return findPublicMethod(methodsByName, "set" + booleanSuffix, 1);
    }
    return null;
  }

  private MethodInfo findPublicMethod(
      final Map<String, List<MethodInfo>> methodsByName,
      final String name,
      final int parameterCount) {
    if (name == null) {
      return null;
    }
    final List<MethodInfo> candidates = methodsByName.get(name.toLowerCase(java.util.Locale.ROOT));
    if (candidates == null) {
      return null;
    }
    for (final var candidate : candidates) {
      if (candidate != null
          && isPublicMethod(candidate)
          && candidate.getParameterCount() == parameterCount) {
        return candidate;
      }
    }
    return null;
  }

  private boolean isPublicMethod(final MethodInfo method) {
    return PUBLIC.equals(method.getVisibility());
  }

  private boolean isBooleanType(final String type) {
    return "boolean".equals(type) || "Boolean".equals(type) || "java.lang.Boolean".equals(type);
  }

  private String capitalize(final String s) {
    if (s == null || s.isEmpty()) {
      return s;
    }
    return Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }

  private String booleanPropertySuffix(final String fieldName, final String fieldType) {
    if (!isBooleanType(fieldType) || fieldName == null) {
      return null;
    }
    if (fieldName.startsWith("is") && fieldName.length() > 2) {
      final char thirdChar = fieldName.charAt(2);
      if (Character.isUpperCase(thirdChar)) {
        return fieldName.substring(2);
      }
    }
    return null;
  }

  /** Formats the field-accessor mapping into a readable string for the LLM prompt. */
  String formatFieldAccessorSummary(final Map<FieldInfo, FieldAccessors> fieldAccessors) {
    if (fieldAccessors.isEmpty()) {
      return "";
    }
    final var sb = new StringBuilder();
    sb.append("\n=== FIELD ACCESS RULES (visibility-based) ===\n");
    sb.append("IMPORTANT: Access method depends on field visibility!\n");
    sb.append("- PUBLIC fields → Access DIRECTLY (e.g., obj.fieldName)\n");
    sb.append(
        "- PRIVATE fields → Use public accessors when available (e.g., obj.getFieldName())\n");
    sb.append("---\n");
    final List<FieldInfo> privateFields = new ArrayList<>();
    final List<FieldInfo> otherFields = new ArrayList<>();
    final List<FieldInfo> sortedFields = new ArrayList<>(fieldAccessors.keySet());
    sortedFields.sort(
        java.util.Comparator.comparing(
            FieldAccessorAnalyzer::fieldName, java.util.Comparator.nullsLast(String::compareTo)));
    for (final var field : sortedFields) {
      if (PRIVATE.equals(field.getVisibility())) {
        privateFields.add(field);
      } else {
        otherFields.add(field);
      }
    }
    // Show private fields first (most important)
    if (!privateFields.isEmpty()) {
      for (final var field : privateFields) {
        final var accessors = fieldAccessors.get(field);
        sb.append(formatFieldLine(field, accessors));
      }
    }
    // Also show public/protected fields
    for (final var field : otherFields) {
      final var accessors = fieldAccessors.get(field);
      sb.append(formatFieldLine(field, accessors));
    }
    sb.append("=== END MAPPING ===\n");
    return sb.toString();
  }

  private static String fieldName(final FieldInfo field) {
    return field != null ? field.getName() : null;
  }

  private String formatFieldLine(final FieldInfo field, final FieldAccessors accessors) {
    final var sb = new StringBuilder();
    final String visibility = field.getVisibility();
    if (PUBLIC.equals(visibility)) {
      // Public field: emphasize direct access without getters
      sb.append("- ").append(field.getName());
      sb.append(" (PUBLIC ").append(field.getType()).append(")");
      sb.append(" → ACCESS DIRECTLY: obj.").append(field.getName());
      sb.append("\n");
      return sb.toString();
    }
    if (PRIVATE.equals(visibility)) {
      sb.append("- ").append(field.getName());
      sb.append(" (private ").append(field.getType()).append(")");
      sb.append(" → ");
      final List<String> accessorNames = new ArrayList<>();
      if (accessors.getter() != null) {
        accessorNames.add(accessors.getter().getName() + "()");
      }
      if (accessors.setter() != null) {
        accessorNames.add(accessors.setter().getName() + "(...)");
      }
      if (accessorNames.isEmpty()) {
        sb.append("NO PUBLIC ACCESSOR (use Reflection if needed)");
      } else {
        sb.append(String.join(", ", accessorNames));
      }
      sb.append("\n");
      return sb.toString();
    }
    // Protected or package-private: allow direct field access when viable
    sb.append("- ").append(field.getName());
    sb.append(" (").append(visibility).append(" ").append(field.getType()).append(")");
    sb.append(" → ACCESS DIRECTLY if in same package\n");
    return sb.toString();
  }

  /** Holds getter and setter for a field. */
  record FieldAccessors(MethodInfo getter, MethodInfo setter) {}
}
