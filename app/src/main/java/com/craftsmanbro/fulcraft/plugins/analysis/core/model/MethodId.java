package com.craftsmanbro.fulcraft.plugins.analysis.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic identifier for a method based on declaring class, name, parameters, and optionally
 * return type.
 */
public final class MethodId {

  private final String declaringClassFqn;

  private final String methodName;

  private final List<String> parameterTypes;

  private final String returnType;

  public MethodId(
      final String declaringClassFqn,
      final String methodName,
      final List<String> parameterTypes,
      final String returnType) {
    this.declaringClassFqn = Objects.requireNonNullElse(declaringClassFqn, "").trim();
    this.methodName = Objects.requireNonNullElse(methodName, "").trim();
    final List<String> params = Objects.requireNonNullElse(parameterTypes, List.of());
    this.parameterTypes = Collections.unmodifiableList(new ArrayList<>(params));
    this.returnType = (returnType == null || returnType.isBlank()) ? null : returnType.trim();
  }

  public String declaringClassFqn() {
    return declaringClassFqn;
  }

  public String methodName() {
    return methodName;
  }

  public List<String> parameterTypes() {
    return parameterTypes;
  }

  public String returnType() {
    return returnType;
  }

  @Override
  public String toString() {
    final String params = String.join(",", parameterTypes);
    if (returnType == null) {
      return declaringClassFqn + "#" + methodName + "(" + params + ")";
    }
    return declaringClassFqn + "#" + methodName + "(" + params + "):" + returnType;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof MethodId methodId)) {
      return false;
    }
    return declaringClassFqn.equals(methodId.declaringClassFqn)
        && methodName.equals(methodId.methodName)
        && parameterTypes.equals(methodId.parameterTypes)
        && Objects.equals(returnType, methodId.returnType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(declaringClassFqn, methodName, parameterTypes, returnType);
  }
}
