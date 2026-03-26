package com.craftsmanbro.fulcraft.plugins.analysis.core.service.detector;

import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.FieldInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import java.util.List;
import java.util.Locale;

/**
 * Detects class type based on class structure, annotations, and patterns. This helps select
 * appropriate test generation strategies and Few-Shot examples.
 */
public class ClassTypeDetector {

  /** Enum representing different class types. */
  public enum ClassType {

    // DTO, Entity, POJO with fields and getters/setters
    DATA_CLASS,
    // Business logic, dependencies
    SERVICE,
    // Static methods, private constructor
    UTILITY,
    // Builder pattern
    BUILDER,
    // Custom exception classes
    EXCEPTION,
    // Inner class usage examples
    INNER_CLASS,
    // Default/unknown type
    GENERAL
  }

  /**
   * Detects the class type based on class structure and annotations.
   *
   * @param cls the class info to analyze
   * @return the detected class type
   */
  public ClassType detectClassType(final ClassInfo cls) {
    if (cls == null) {
      return ClassType.GENERAL;
    }
    final String simpleClassName = extractSimpleClassName(cls.getFqn());
    // 1. Check annotations first (most reliable)
    final ClassType annotationType = detectByAnnotations(cls.getAnnotations());
    if (annotationType != null) {
      return annotationType;
    }
    // 2. Check extends/implements
    if (isException(cls.getExtendsTypes())) {
      return ClassType.EXCEPTION;
    }
    // 3. Check structure
    final var methods = cls.getMethods();
    final var fields = cls.getFields();
    if (isUtilityClass(methods, simpleClassName)) {
      return ClassType.UTILITY;
    }
    if (isBuilderClass(methods, cls.getFqn())) {
      return ClassType.BUILDER;
    }
    if (isDataClass(methods, fields)) {
      if (hasActionMethods(methods, simpleClassName)) {
        return ClassType.SERVICE;
      }
      return ClassType.DATA_CLASS;
    }
    return ClassType.GENERAL;
  }

  private boolean hasActionMethods(final List<MethodInfo> methods, final String simpleClassName) {
    for (final var method : methods) {
      final String rawName = safeMethodName(method);
      if (rawName == null) {
        continue;
      }
      if (isActionMethod(rawName, simpleClassName)) {
        return true;
      }
    }
    return false;
  }

  /** Detects class type by annotations. */
  private ClassType detectByAnnotations(final List<String> annotations) {
    if (annotations == null || annotations.isEmpty()) {
      return null;
    }
    if (hasAnyAnnotation(annotations, "Service", "Component", "Controller", "RestController")) {
      return ClassType.SERVICE;
    }
    if (hasAnyAnnotation(annotations, "Repository", "Dao")) {
      return ClassType.SERVICE;
    }
    if (hasAnyAnnotation(annotations, "Entity", "Data", "Value", "Record")) {
      return ClassType.DATA_CLASS;
    }
    return null;
  }

  private boolean hasAnyAnnotation(final List<String> annotations, final String... targets) {
    for (final String annotation : annotations) {
      if (annotation == null || annotation.isBlank()) {
        continue;
      }
      final String normalized = normalizeAnnotation(annotation);
      for (final String target : targets) {
        if (target.equals(normalized)) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean isException(final List<String> extendTypes) {
    if (extendTypes == null) {
      return false;
    }
    for (final String type : extendTypes) {
      if (type.contains("Exception") || type.contains("Error") || type.contains("Throwable")) {
        return true;
      }
    }
    return false;
  }

  private boolean isUtilityClass(final List<MethodInfo> methods, final String simpleClassName) {
    if (methods.isEmpty()) {
      return false;
    }
    boolean hasPrivateConstructor = false;
    int staticMethodCount = 0;
    int instanceMethodCount = 0;
    for (final var method : methods) {
      final String name = safeMethodName(method);
      if (name == null) {
        continue;
      }
      final String visibility = method.getVisibility();
      if (isConstructorName(name, simpleClassName)) {
        if ("private".equals(visibility)) {
          hasPrivateConstructor = true;
        }
      } else if (method.isStatic()) {
        staticMethodCount++;
      } else {
        instanceMethodCount++;
      }
    }
    // A utility class has either:
    // 1. Private constructor + static methods + no instance methods (classic
    // pattern)
    // 2. Only static public methods with no instance methods (common pattern)
    return (hasPrivateConstructor && staticMethodCount > 0 && instanceMethodCount == 0)
        || (staticMethodCount >= 2 && instanceMethodCount == 0);
  }

  private boolean isBuilderClass(final List<MethodInfo> methods, final String classFqn) {
    boolean hasBuildMethod = false;
    int fluentSetterCount = 0;
    final String simpleClassName = extractSimpleClassName(classFqn);
    for (final var method : methods) {
      final String name = safeMethodName(method);
      if (name == null) {
        continue;
      }
      final String sig = method.getSignature();
      if ("build".equals(name)) {
        hasBuildMethod = true;
      }
      if (isFluentSetter(name, sig, simpleClassName)) {
        fluentSetterCount++;
      }
    }
    return hasBuildMethod || fluentSetterCount >= 2;
  }

  private boolean isConstructorName(final String methodName, final String simpleClassName) {
    if (methodName == null) {
      return false;
    }
    if ("<init>".equals(methodName)) {
      return true;
    }
    return methodName.equals(simpleClassName);
  }

  private String extractSimpleClassName(final String classFqn) {
    if (classFqn == null) {
      return null;
    }
    return classFqn.contains(".") ? classFqn.substring(classFqn.lastIndexOf('.') + 1) : classFqn;
  }

  private String normalizeAnnotation(final String annotation) {
    final String normalized = annotation.startsWith("@") ? annotation.substring(1) : annotation;
    final int lastDot = normalized.lastIndexOf('.');
    if (lastDot >= 0 && lastDot + 1 < normalized.length()) {
      return normalized.substring(lastDot + 1);
    }
    return normalized;
  }

  private boolean isFluentSetter(
      final String name, final String sig, final String simpleClassName) {
    if (sig == null || simpleClassName == null) {
      return false;
    }
    return sig.contains(simpleClassName) && (name.startsWith("set") || name.startsWith("with"));
  }

  private boolean isDataClass(final List<MethodInfo> methods, final List<FieldInfo> fields) {
    if (fields.isEmpty()) {
      return false;
    }
    final DataMethodIndicators indicators = analyzeDataMethods(methods);
    return fields.size() >= 2 && indicators.matchesDataClass();
  }

  private DataMethodIndicators analyzeDataMethods(final List<MethodInfo> methods) {
    final DataMethodIndicators indicators = new DataMethodIndicators();
    for (final var method : methods) {
      final String name = safeMethodName(method);
      if (name == null) {
        continue;
      }
      if (isGetter(name)) {
        indicators.getterCount++;
      }
      if (isSetter(name)) {
        indicators.setterCount++;
      }
      if (isEqualsOrHashCode(name)) {
        indicators.hasEqualsOrHashCode = true;
      }
      if (isToString(name)) {
        indicators.hasToString = true;
      }
    }
    return indicators;
  }

  private String safeMethodName(final MethodInfo method) {
    if (method == null) {
      return null;
    }
    return method.getName();
  }

  private boolean isActionMethod(final String rawName, final String simpleClassName) {
    // Heuristic: methods that don't look like getters, setters, or standard Object methods.
    if (rawName == null || rawName.isBlank()) {
      return false;
    }
    if (isConstructorName(rawName, simpleClassName)) {
      return false;
    }
    final String name = rawName.toLowerCase(Locale.ROOT);
    if (isObjectMethod(name)) {
      return false;
    }
    return !isAccessorLike(name);
  }

  private boolean isObjectMethod(final String name) {
    return "equals".equals(name) || "hashcode".equals(name) || "tostring".equals(name);
  }

  private boolean isAccessorLike(final String name) {
    return name.startsWith("get")
        || name.startsWith("set")
        || name.startsWith("is")
        || name.startsWith("has");
  }

  private boolean isGetter(final String name) {
    return name.startsWith("get") || name.startsWith("is");
  }

  private boolean isSetter(final String name) {
    return name.startsWith("set");
  }

  private boolean isEqualsOrHashCode(final String name) {
    return "equals".equals(name) || "hashCode".equals(name);
  }

  private boolean isToString(final String name) {
    return "toString".equals(name);
  }

  private static final class DataMethodIndicators {

    private int getterCount;

    private int setterCount;

    private boolean hasEqualsOrHashCode;

    private boolean hasToString;

    private boolean matchesDataClass() {
      return getterCount >= 2 || setterCount >= 2 || hasEqualsOrHashCode || hasToString;
    }
  }
}
