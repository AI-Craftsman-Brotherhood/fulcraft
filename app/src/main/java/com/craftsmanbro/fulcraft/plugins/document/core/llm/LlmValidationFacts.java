package com.craftsmanbro.fulcraft.plugins.document.core.llm;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Validation facts extracted from class analysis, shared across LLM processing stages. */
public record LlmValidationFacts(
    List<String> methodNames,
    Set<String> highComplexityMethods,
    Set<String> deadCodeMethods,
    Set<String> duplicateMethods,
    Set<String> uncertainDynamicMethodNames,
    Set<String> uncertainDynamicMethodDisplayNames,
    Map<String, Set<String>> uncertainDynamicMethodNamesByMethod,
    Set<String> knownMissingDynamicMethodNames,
    Set<String> knownMissingDynamicMethodDisplayNames,
    Map<String, Set<String>> knownMissingDynamicMethodNamesByMethod,
    Set<String> knownMethodNames,
    Set<String> knownConstructorSignatures,
    Set<String> privateMethodNames,
    boolean interfaceType,
    boolean nestedClass,
    String enclosingType,
    String classFqn,
    String classSimpleName,
    Map<String, Integer> methodBranchCounts) {

  public boolean hasAnyCautions() {
    return !highComplexityMethods.isEmpty()
        || !deadCodeMethods.isEmpty()
        || !duplicateMethods.isEmpty();
  }

  /**
   * Returns uncertain dynamic method names scoped to a specific owner method.
   *
   * <p>When per-method mapping is unavailable (legacy construction path), falls back to class-wide
   * uncertain names for backward compatibility.
   */
  public Set<String> uncertainDynamicMethodNamesFor(final String ownerMethodName) {
    if (uncertainDynamicMethodNamesByMethod == null
        || uncertainDynamicMethodNamesByMethod.isEmpty()) {
      return uncertainDynamicMethodNames == null ? Set.of() : uncertainDynamicMethodNames;
    }
    final String normalizedOwner = LlmDocumentTextUtils.normalizeMethodName(ownerMethodName);
    if (normalizedOwner.isBlank()) {
      return Set.of();
    }
    final Set<String> methodScoped = uncertainDynamicMethodNamesByMethod.get(normalizedOwner);
    return methodScoped == null ? Set.of() : methodScoped;
  }

  /**
   * Returns known-missing dynamic method names scoped to a specific owner method.
   *
   * <p>Known-missing means target class is known but target method/signature is absent.
   */
  public Set<String> knownMissingDynamicMethodNamesFor(final String ownerMethodName) {
    if (knownMissingDynamicMethodNamesByMethod == null
        || knownMissingDynamicMethodNamesByMethod.isEmpty()) {
      return knownMissingDynamicMethodNames == null ? Set.of() : knownMissingDynamicMethodNames;
    }
    final String normalizedOwner = LlmDocumentTextUtils.normalizeMethodName(ownerMethodName);
    if (normalizedOwner.isBlank()) {
      return Set.of();
    }
    final Set<String> methodScoped = knownMissingDynamicMethodNamesByMethod.get(normalizedOwner);
    return methodScoped == null ? Set.of() : methodScoped;
  }
}
