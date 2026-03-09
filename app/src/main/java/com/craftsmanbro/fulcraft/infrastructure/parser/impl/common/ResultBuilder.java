package com.craftsmanbro.fulcraft.infrastructure.parser.impl.common;

import com.craftsmanbro.fulcraft.infrastructure.parser.model.CalledMethodRef;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.FieldInfo;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.MethodInfo;
import java.util.List;

/**
 * Shared builders for analysis domain models to keep engines consistent.
 *
 * <p>Note: This is a copy of {@code feature.analysis.core.model.ResultBuilder} moved to the
 * infrastructure layer to eliminate the reverse dependency on feature internals.
 */
public final class ResultBuilder {

  private ResultBuilder() {}

  public static MethodInfoBuilder methodInfo() {
    return new MethodInfoBuilder();
  }

  /** Builder for creating {@link MethodInfo} instances with a fluent API. */
  public static class MethodInfoBuilder {

    private String name;

    private String signature;

    private int loc;

    private String visibility;

    private int cyclomaticComplexity;

    private boolean usesRemovedApis;

    private int parameterCount;

    private int maxNestingDepth;

    private List<String> thrownExceptions = List.of();

    private List<String> annotations = List.of();

    private List<String> calledMethods = List.of();

    private List<CalledMethodRef> calledMethodRefs = List.of();

    private boolean calledMethodsSet;

    private boolean calledMethodRefsSet;

    public MethodInfoBuilder name(final String name) {
      this.name = name;
      return this;
    }

    public MethodInfoBuilder signature(final String signature) {
      this.signature = signature;
      return this;
    }

    public MethodInfoBuilder loc(final int loc) {
      this.loc = loc;
      return this;
    }

    public MethodInfoBuilder visibility(final String visibility) {
      this.visibility = visibility;
      return this;
    }

    public MethodInfoBuilder cyclomaticComplexity(final int cyclomaticComplexity) {
      this.cyclomaticComplexity = cyclomaticComplexity;
      return this;
    }

    public MethodInfoBuilder usesRemovedApis(final boolean usesRemovedApis) {
      this.usesRemovedApis = usesRemovedApis;
      return this;
    }

    public MethodInfoBuilder parameterCount(final int parameterCount) {
      this.parameterCount = parameterCount;
      return this;
    }

    public MethodInfoBuilder maxNestingDepth(final int maxNestingDepth) {
      this.maxNestingDepth = maxNestingDepth;
      return this;
    }

    public MethodInfoBuilder thrownExceptions(final List<String> thrownExceptions) {
      this.thrownExceptions = thrownExceptions != null ? List.copyOf(thrownExceptions) : List.of();
      return this;
    }

    public MethodInfoBuilder annotations(final List<String> annotations) {
      this.annotations = annotations != null ? List.copyOf(annotations) : List.of();
      return this;
    }

    public MethodInfoBuilder calledMethods(final List<String> calledMethods) {
      this.calledMethods = calledMethods != null ? List.copyOf(calledMethods) : List.of();
      this.calledMethodsSet = true;
      return this;
    }

    public MethodInfoBuilder calledMethodRefs(final List<CalledMethodRef> calledMethodRefs) {
      this.calledMethodRefs = calledMethodRefs != null ? List.copyOf(calledMethodRefs) : List.of();
      this.calledMethodRefsSet = true;
      return this;
    }

    private boolean hasLoops;

    private boolean hasConditionals;

    private boolean isStatic;

    public MethodInfoBuilder hasLoops(final boolean hasLoops) {
      this.hasLoops = hasLoops;
      return this;
    }

    public MethodInfoBuilder hasConditionals(final boolean hasConditionals) {
      this.hasConditionals = hasConditionals;
      return this;
    }

    public MethodInfoBuilder isStatic(final boolean isStatic) {
      this.isStatic = isStatic;
      return this;
    }

    public MethodInfo build() {
      final MethodInfo methodInfo = new MethodInfo();
      methodInfo.setName(name);
      methodInfo.setSignature(signature);
      methodInfo.setLoc(loc);
      methodInfo.setVisibility(visibility);
      methodInfo.setCyclomaticComplexity(cyclomaticComplexity);
      methodInfo.setUsesRemovedApis(usesRemovedApis);
      methodInfo.setParameterCount(parameterCount);
      methodInfo.setMaxNestingDepth(maxNestingDepth);
      methodInfo.setThrownExceptions(thrownExceptions);
      methodInfo.setAnnotations(annotations);
      if (calledMethodRefsSet) {
        methodInfo.setCalledMethodRefs(calledMethodRefs);
      } else if (calledMethodsSet) {
        methodInfo.setCalledMethods(calledMethods);
      }
      methodInfo.setHasLoops(hasLoops);
      methodInfo.setHasConditionals(hasConditionals);
      methodInfo.setStatic(isStatic);
      return methodInfo;
    }
  }

  public static FieldInfo fieldInfo(
      final String name,
      final String type,
      final String visibility,
      final boolean isStatic,
      final boolean isFinal) {
    final FieldInfo fieldInfo = new FieldInfo();
    fieldInfo.setName(name);
    fieldInfo.setType(type);
    fieldInfo.setVisibility(visibility);
    fieldInfo.setStatic(isStatic);
    fieldInfo.setFinal(isFinal);
    return fieldInfo;
  }
}
