package com.craftsmanbro.fulcraft.plugins.analysis.core.service.index;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Lightweight project-wide symbol index for name existence checks. */
public class ProjectSymbolIndex {

  private final Set<String> classFqns = new HashSet<>();

  private final Map<String, Set<String>> classesBySimpleName = new HashMap<>();

  private final Map<String, Set<String>> methodsByClass = new HashMap<>();

  private final Map<String, Set<String>> methodSignaturesByClass = new HashMap<>();

  private final Map<String, Map<String, Set<Integer>>> methodAritiesByClass = new HashMap<>();

  private final Set<String> methodNames = new HashSet<>();

  private final Map<String, Map<String, String>> fieldsByClass = new HashMap<>();

  private final Set<String> fieldNames = new HashSet<>();

  public void addClass(final String classFqn) {
    if (classFqn == null || classFqn.isBlank()) {
      return;
    }
    classFqns.add(classFqn);
    final String simple = simpleName(classFqn);
    if (simple != null) {
      classesBySimpleName.computeIfAbsent(simple, k -> new LinkedHashSet<>()).add(classFqn);
    }
  }

  public void addMethod(
      final String classFqn, final String name, final String signature, final int paramCount) {
    if (classFqn == null || classFqn.isBlank() || name == null || name.isBlank()) {
      return;
    }
    addClass(classFqn);
    methodsByClass.computeIfAbsent(classFqn, k -> new LinkedHashSet<>()).add(name);
    methodNames.add(name);
    if (signature != null && !signature.isBlank()) {
      methodSignaturesByClass.computeIfAbsent(classFqn, k -> new LinkedHashSet<>()).add(signature);
    }
    if (paramCount >= 0) {
      methodAritiesByClass
          .computeIfAbsent(classFqn, k -> new HashMap<>())
          .computeIfAbsent(name, k -> new LinkedHashSet<>())
          .add(paramCount);
    }
  }

  public void addField(final String classFqn, final String name, final String type) {
    if (classFqn == null || classFqn.isBlank() || name == null || name.isBlank()) {
      return;
    }
    fieldsByClass
        .computeIfAbsent(classFqn, k -> new HashMap<>())
        .merge(
            name,
            type == null ? "" : type,
            (oldValue, newValue) -> oldValue.equals(newValue) ? oldValue : "ambiguous");
    fieldNames.add(name);
  }

  public boolean hasClass(final String name) {
    if (name == null || name.isBlank()) {
      return false;
    }
    if (classFqns.contains(name)) {
      return true;
    }
    final String simple = simpleName(name);
    return simple != null && classesBySimpleName.containsKey(simple);
  }

  public boolean hasMethod(final String className, final String methodName) {
    if (methodName == null || methodName.isBlank()) {
      return false;
    }
    final Set<String> candidates = resolveClassCandidates(className);
    if (candidates.isEmpty()) {
      return methodNames.contains(methodName);
    }
    for (final String candidate : candidates) {
      final Set<String> methods = methodsByClass.get(candidate);
      if (methods != null && methods.contains(methodName)) {
        return true;
      }
    }
    return false;
  }

  public boolean hasMethodArity(
      final String className, final String methodName, final int paramCount) {
    if (paramCount < 0) {
      return hasMethod(className, methodName);
    }
    final Set<String> candidates = resolveClassCandidates(className);
    if (candidates.isEmpty()) {
      return false;
    }
    for (final String candidate : candidates) {
      final Map<String, Set<Integer>> byName = methodAritiesByClass.get(candidate);
      if (byName == null) {
        continue;
      }
      final Set<Integer> arities = byName.get(methodName);
      if (arities != null && arities.contains(paramCount)) {
        return true;
      }
    }
    return false;
  }

  public int getMethodOverloadCount(final String className, final String methodName) {
    if (methodName == null || methodName.isBlank()) {
      return 0;
    }
    final Set<String> candidates = resolveClassCandidates(className);
    if (candidates.isEmpty()) {
      return 0;
    }
    int max = 0;
    for (final String candidate : candidates) {
      final int overloads = getOverloadCount(candidate, methodName);
      if (overloads > max) {
        max = overloads;
      }
    }
    return max;
  }

  public boolean hasMethodSignature(final String className, final String signature) {
    if (signature == null || signature.isBlank()) {
      return false;
    }
    final Set<String> candidates = resolveClassCandidates(className);
    if (candidates.isEmpty()) {
      return false;
    }
    for (final String candidate : candidates) {
      final Set<String> signatures = methodSignaturesByClass.get(candidate);
      if (signatures != null && signatures.contains(signature)) {
        return true;
      }
    }
    return false;
  }

  public boolean hasField(final String className, final String fieldName) {
    if (fieldName == null || fieldName.isBlank()) {
      return false;
    }
    final Set<String> candidates = resolveClassCandidates(className);
    if (candidates.isEmpty()) {
      return fieldNames.contains(fieldName);
    }
    for (final String candidate : candidates) {
      final Map<String, String> fields = fieldsByClass.get(candidate);
      if (fields != null && fields.containsKey(fieldName)) {
        return true;
      }
    }
    return false;
  }

  public Map<String, String> getFields(final String classFqn) {
    return fieldsByClass.getOrDefault(classFqn, Map.of());
  }

  public List<String> findClassCandidates(final String simpleName, final int limit) {
    if (simpleName == null || simpleName.isBlank()) {
      return List.of();
    }
    final Set<String> candidates = classesBySimpleName.get(simpleName);
    if (candidates == null || candidates.isEmpty()) {
      return List.of();
    }
    final List<String> list = new ArrayList<>(candidates);
    list.sort(String::compareTo);
    if (limit > 0 && list.size() > limit) {
      return list.subList(0, limit);
    }
    return list;
  }

  private Set<String> resolveClassCandidates(final String className) {
    if (className == null || className.isBlank()) {
      return Set.of();
    }
    if (classFqns.contains(className)) {
      return Set.of(className);
    }
    final String simple = simpleName(className);
    if (simple == null) {
      return Set.of();
    }
    return classesBySimpleName.getOrDefault(simple, Set.of());
  }

  private String simpleName(final String className) {
    if (className == null || className.isBlank()) {
      return null;
    }
    final int dot = className.lastIndexOf('.');
    return dot >= 0 ? className.substring(dot + 1) : className;
  }

  private int getOverloadCount(final String classFqn, final String methodName) {
    final int signatureCount = getSignatureOverloadCount(classFqn, methodName);
    if (signatureCount > 0) {
      return signatureCount;
    }
    final int arityCount = getArityOverloadCount(classFqn, methodName);
    if (arityCount > 0) {
      return arityCount;
    }
    return hasNamedMethod(classFqn, methodName) ? 1 : 0;
  }

  private int getSignatureOverloadCount(final String classFqn, final String methodName) {
    final Set<String> signatures = methodSignaturesByClass.get(classFqn);
    if (signatures == null || signatures.isEmpty()) {
      return 0;
    }
    final String prefix = methodName + "(";
    int count = 0;
    for (final String signature : signatures) {
      if (signature != null && signature.startsWith(prefix)) {
        count++;
      }
    }
    return count;
  }

  private int getArityOverloadCount(final String classFqn, final String methodName) {
    final Map<String, Set<Integer>> byName = methodAritiesByClass.get(classFqn);
    if (byName == null) {
      return 0;
    }
    final Set<Integer> arities = byName.get(methodName);
    if (arities == null || arities.isEmpty()) {
      return 0;
    }
    return arities.size();
  }

  private boolean hasNamedMethod(final String classFqn, final String methodName) {
    final Set<String> names = methodsByClass.get(classFqn);
    return names != null && names.contains(methodName);
  }
}
