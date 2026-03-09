package com.craftsmanbro.fulcraft.plugins.analysis.model;

import java.util.ArrayList;
import java.util.List;

/** Shared semantic helpers for method-level classification and counting. */
public final class MethodSemantics {

  private MethodSemantics() {
    // Utility class
  }

  public static String simpleClassName(final String classFqn) {
    if (classFqn == null || classFqn.isBlank()) {
      return "";
    }
    final String normalized = classFqn.strip().replace('$', '.');
    final int lastDot = normalized.lastIndexOf('.');
    if (lastDot >= 0 && lastDot + 1 < normalized.length()) {
      return normalized.substring(lastDot + 1);
    }
    return normalized;
  }

  public static boolean isConstructor(final MethodInfo method, final String classSimpleName) {
    if (method == null || classSimpleName == null || classSimpleName.isBlank()) {
      return false;
    }
    final String normalizedClassSimpleName = classSimpleName.strip().replace("`", "");
    final String rawMethodName = method.getName();
    final String normalizedMethodName =
        rawMethodName == null ? null : rawMethodName.replace("`", "");
    if ("<init>".equals(normalizedMethodName)
        || normalizedClassSimpleName.equals(normalizedMethodName)) {
      return true;
    }
    final String signature = method.getSignature();
    if (signature == null || signature.isBlank()) {
      return false;
    }
    final String normalizedSignature = signature.strip().replace('$', '.').replace("`", "");
    final int parenIndex = normalizedSignature.indexOf('(');
    if (parenIndex <= 0) {
      return false;
    }
    String candidate = normalizedSignature.substring(0, parenIndex).trim();
    final int spaceIndex = candidate.lastIndexOf(' ');
    if (spaceIndex >= 0 && spaceIndex + 1 < candidate.length()) {
      candidate = candidate.substring(spaceIndex + 1);
    }
    final int dotIndex = candidate.lastIndexOf('.');
    if (dotIndex >= 0 && dotIndex + 1 < candidate.length()) {
      candidate = candidate.substring(dotIndex + 1);
    }
    return normalizedClassSimpleName.equals(candidate);
  }

  public static boolean isImplicitDefaultConstructor(
      final MethodInfo method, final String classSimpleName) {
    return isConstructor(method, classSimpleName)
        && method.getParameterCount() == 0
        && method.getLoc() <= 0;
  }

  public static List<MethodInfo> methodsExcludingImplicitDefaultConstructors(
      final ClassInfo classInfo) {
    if (classInfo == null) {
      return List.of();
    }
    final List<MethodInfo> methods = classInfo.getMethods();
    if (methods.isEmpty()) {
      return List.of();
    }
    final String classSimpleName = simpleClassName(classInfo.getFqn());
    final List<MethodInfo> filtered = new ArrayList<>();
    for (final MethodInfo method : methods) {
      if (method == null || isImplicitDefaultConstructor(method, classSimpleName)) {
        continue;
      }
      filtered.add(method);
    }
    return filtered;
  }

  public static int countMethodsExcludingImplicitDefaultConstructors(final ClassInfo classInfo) {
    return methodsExcludingImplicitDefaultConstructors(classInfo).size();
  }
}
