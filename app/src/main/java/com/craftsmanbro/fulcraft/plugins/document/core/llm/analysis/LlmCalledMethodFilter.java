package com.craftsmanbro.fulcraft.plugins.document.core.llm.analysis;

import com.craftsmanbro.fulcraft.plugins.analysis.model.CalledMethodRef;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmDocumentTextUtils;
import com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmValidationFacts;
import com.craftsmanbro.fulcraft.plugins.document.core.util.DocumentUtils;
import com.craftsmanbro.fulcraft.plugins.document.core.util.PromptInputCanonicalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Filters and canonicalizes called-method references for specification output. */
public final class LlmCalledMethodFilter {

  private final String unavailableValue;

  public LlmCalledMethodFilter(final String unavailableValue) {
    this.unavailableValue = Objects.requireNonNullElse(unavailableValue, "");
  }

  public List<String> filterCalledMethodsForSpecification(
      final MethodInfo method, final LlmValidationFacts validationFacts) {
    return filterCalledMethods(method, validationFacts, false);
  }

  public List<String> filterCalledMethodsForSpecificationWithArgumentLiterals(
      final MethodInfo method, final LlmValidationFacts validationFacts) {
    return filterCalledMethods(method, validationFacts, true);
  }

  private List<String> filterCalledMethods(
      final MethodInfo method,
      final LlmValidationFacts validationFacts,
      final boolean includeArgumentLiterals) {
    if (method == null) {
      return List.of();
    }
    final Map<String, CalledMethodOutput> deduplicated = new LinkedHashMap<>();
    addRefsFromCalledMethodRefs(method.getCalledMethodRefs(), validationFacts, deduplicated);
    addRefsFromCalledMethodStrings(method.getCalledMethods(), validationFacts, deduplicated);
    if (deduplicated.isEmpty()) {
      return List.of();
    }
    final List<String> values = new ArrayList<>();
    for (final CalledMethodOutput output : deduplicated.values()) {
      if (output == null) {
        continue;
      }
      final String reference = output.reference();
      if (reference == null || reference.isBlank()) {
        continue;
      }
      if (!includeArgumentLiterals || output.argumentLiterals().isEmpty()) {
        values.add(reference);
      } else {
        values.add(
            reference + " [arg_literals: " + String.join(", ", output.argumentLiterals()) + "]");
      }
    }
    return values;
  }

  private void addRefsFromCalledMethodRefs(
      final List<CalledMethodRef> refs,
      final LlmValidationFacts validationFacts,
      final Map<String, CalledMethodOutput> deduplicated) {
    if (refs == null || refs.isEmpty()) {
      return;
    }
    for (final CalledMethodRef ref : refs) {
      if (ref == null) {
        continue;
      }
      final String candidate =
          ref.getResolved() != null && !ref.getResolved().isBlank()
              ? ref.getResolved()
              : ref.getRaw();
      mergeCalledMethodCandidate(
          candidate, ref.getArgumentLiterals(), validationFacts, deduplicated);
    }
  }

  private void addRefsFromCalledMethodStrings(
      final List<String> calledMethods,
      final LlmValidationFacts validationFacts,
      final Map<String, CalledMethodOutput> deduplicated) {
    if (calledMethods == null || calledMethods.isEmpty()) {
      return;
    }
    for (final String calledMethod : PromptInputCanonicalizer.sortStrings(calledMethods)) {
      mergeCalledMethodCandidate(calledMethod, List.of(), validationFacts, deduplicated);
    }
  }

  private void mergeCalledMethodCandidate(
      final String calledMethod,
      final List<String> argumentLiterals,
      final LlmValidationFacts validationFacts,
      final Map<String, CalledMethodOutput> deduplicated) {
    final CalledMethodReference reference = canonicalizeCalledMethodReference(calledMethod);
    if (reference == null) {
      return;
    }
    if (isPrivateSameClassCall(reference.normalizedReference(), validationFacts)) {
      return;
    }
    if (isImplicitNoArgConstructorCall(reference, validationFacts)) {
      return;
    }
    final List<String> normalizedLiterals = normalizeArgumentLiterals(argumentLiterals);
    final CalledMethodOutput existing = deduplicated.get(reference.dedupKey());
    if (existing == null) {
      deduplicated.put(
          reference.dedupKey(),
          new CalledMethodOutput(reference.normalizedReference(), normalizedLiterals));
      return;
    }
    if (normalizedLiterals.isEmpty()) {
      return;
    }
    deduplicated.put(
        reference.dedupKey(),
        new CalledMethodOutput(
            existing.reference(),
            mergeArgumentLiterals(existing.argumentLiterals(), normalizedLiterals)));
  }

  private List<String> normalizeArgumentLiterals(final List<String> argumentLiterals) {
    if (argumentLiterals == null || argumentLiterals.isEmpty()) {
      return List.of();
    }
    final LinkedHashSet<String> normalized = new LinkedHashSet<>();
    for (final String literal : argumentLiterals) {
      if (literal == null || literal.isBlank()) {
        continue;
      }
      String compact = literal.replaceAll("\\s+", " ").strip();
      if (compact.length() > 120) {
        compact = compact.substring(0, 117) + "...";
      }
      normalized.add(compact.replace("`", ""));
    }
    if (normalized.isEmpty()) {
      return List.of();
    }
    final List<String> values = new ArrayList<>(normalized);
    Collections.sort(values);
    return values;
  }

  private List<String> mergeArgumentLiterals(final List<String> first, final List<String> second) {
    final LinkedHashSet<String> merged = new LinkedHashSet<>();
    if (first != null) {
      merged.addAll(first);
    }
    if (second != null) {
      merged.addAll(second);
    }
    if (merged.isEmpty()) {
      return List.of();
    }
    final List<String> values = new ArrayList<>(merged);
    Collections.sort(values);
    return values;
  }

  private boolean isPrivateSameClassCall(
      final String calledMethod, final LlmValidationFacts validationFacts) {
    if (calledMethod == null || calledMethod.isBlank()) {
      return false;
    }
    final String methodName =
        LlmDocumentTextUtils.normalizeMethodName(
            LlmDocumentTextUtils.extractMethodName(calledMethod));
    if (methodName.isBlank() || !validationFacts.privateMethodNames().contains(methodName)) {
      return false;
    }
    final String ownerType = normalizeTypeName(extractOwnerType(calledMethod));
    if (ownerType.isBlank()) {
      return true;
    }
    return ownerType.equals(validationFacts.classFqn())
        || ownerType.equals(validationFacts.classSimpleName());
  }

  private CalledMethodReference canonicalizeCalledMethodReference(final String calledMethod) {
    if (calledMethod == null || calledMethod.isBlank()) {
      return null;
    }
    final String normalized = calledMethod.strip().replace('$', '.');
    final int hashIndex = normalized.indexOf('#');
    if (hashIndex < 0) {
      final String compact = normalized.replaceAll("\\s+", " ").strip();
      if (compact.isBlank()) {
        return null;
      }
      return new CalledMethodReference(
          "", "", List.of(), compact, compact.toLowerCase(Locale.ROOT), false);
    }
    final String ownerType = normalized.substring(0, hashIndex).strip();
    final String rawSignature = normalized.substring(hashIndex + 1).strip();
    if (ownerType.isBlank() || rawSignature.isBlank()) {
      final String compact = normalized.replaceAll("\\s+", " ").strip();
      if (compact.isBlank()) {
        return null;
      }
      return new CalledMethodReference(
          ownerType, "", List.of(), compact, compact.toLowerCase(Locale.ROOT), false);
    }
    String methodToken = rawSignature;
    List<String> parameterTypes = List.of();
    final int openParen = rawSignature.indexOf('(');
    final int closeParen = rawSignature.lastIndexOf(')');
    final boolean hasParameterList = openParen >= 0 && closeParen > openParen;
    if (hasParameterList) {
      methodToken = rawSignature.substring(0, openParen).trim();
      final String parameterSection = rawSignature.substring(openParen + 1, closeParen);
      parameterTypes = normalizeCalledMethodParameterTypes(parameterSection);
    }
    String methodName = LlmDocumentTextUtils.extractMethodName(methodToken);
    if (methodName.isBlank()) {
      methodName = methodToken.replace("`", "").strip();
    }
    if (methodName.isBlank()) {
      methodName = unavailableValue;
    }
    final boolean constructor = isConstructorReference(ownerType, methodToken, methodName);
    final String normalizedSignature =
        hasParameterList ? methodName + "(" + String.join(", ", parameterTypes) + ")" : methodName;
    final String normalizedReference = ownerType + "#" + normalizedSignature;
    final String semanticParameterDedupKey =
        hasParameterList ? buildSemanticParameterDedupKey(parameterTypes) : "";
    final String dedupKey =
        normalizeTypeName(ownerType)
            + "#"
            + LlmDocumentTextUtils.normalizeMethodName(methodName)
            + (hasParameterList ? "(" + semanticParameterDedupKey + ")" : "");
    return new CalledMethodReference(
        ownerType, methodName, parameterTypes, normalizedReference, dedupKey, constructor);
  }

  private boolean isConstructorReference(
      final String ownerType, final String rawMethodToken, final String normalizedMethodName) {
    if (ownerType == null
        || ownerType.isBlank()
        || normalizedMethodName == null
        || normalizedMethodName.isBlank()) {
      return false;
    }
    final String ownerSimple = DocumentUtils.getSimpleName(ownerType).replace('$', '.');
    final String methodSimple = DocumentUtils.getSimpleName(normalizedMethodName).replace('$', '.');
    if (ownerSimple.isBlank() || methodSimple.isBlank()) {
      return false;
    }
    final String rawSimple =
        DocumentUtils.getSimpleName(LlmDocumentTextUtils.extractMethodName(rawMethodToken))
            .replace('$', '.');
    if (!rawSimple.isBlank() && ownerSimple.equals(rawSimple)) {
      return true;
    }
    return ownerSimple.equals(methodSimple);
  }

  private List<String> normalizeCalledMethodParameterTypes(final String parameterSection) {
    if (parameterSection == null || parameterSection.isBlank()) {
      return List.of();
    }
    final List<String> normalized = new ArrayList<>();
    for (final String token : LlmDocumentTextUtils.splitTopLevelCsv(parameterSection)) {
      if (token == null || token.isBlank()) {
        continue;
      }
      final String compact = token.strip().replace('$', '.').replaceAll("\\s+", " ");
      if (!compact.isBlank()) {
        normalized.add(compact);
      }
    }
    return normalized;
  }

  private String buildSemanticParameterDedupKey(final List<String> parameterTypes) {
    if (parameterTypes == null || parameterTypes.isEmpty()) {
      return "";
    }
    final List<String> semanticTypes = new ArrayList<>();
    for (final String parameterType : parameterTypes) {
      final String normalized = normalizeSemanticParameterType(parameterType);
      if (!normalized.isBlank()) {
        semanticTypes.add(normalized);
      }
    }
    return String.join(",", semanticTypes);
  }

  private String normalizeSemanticParameterType(final String parameterType) {
    if (parameterType == null || parameterType.isBlank()) {
      return "";
    }
    String normalized = parameterType.strip().replace('$', '.');
    normalized = normalized.replace("...", "[]");
    normalized = eraseGenericArguments(normalized);
    normalized = simplifyQualifiedTypes(normalized);
    normalized = normalized.replaceAll("\\s+", "");
    normalized = normalizeTypeVariableErasure(normalized);
    return normalized.toLowerCase(Locale.ROOT);
  }

  private String normalizeTypeVariableErasure(final String typeName) {
    if (typeName == null || typeName.isBlank()) {
      return "";
    }
    if (typeName.matches("[A-Z][A-Z0-9_]*")) {
      return "Object";
    }
    if (typeName.matches("[A-Z][A-Z0-9_]*\\[\\]")) {
      return "Object[]";
    }
    return typeName;
  }

  private String eraseGenericArguments(final String signature) {
    if (signature == null || signature.isBlank() || !signature.contains("<")) {
      return signature == null ? "" : signature;
    }
    final StringBuilder sb = new StringBuilder(signature.length());
    int depth = 0;
    for (int i = 0; i < signature.length(); i++) {
      final char ch = signature.charAt(i);
      if (ch == '<') {
        depth++;
        continue;
      }
      if (ch == '>' && depth > 0) {
        depth--;
        continue;
      }
      if (depth == 0) {
        sb.append(ch);
      }
    }
    return sb.toString();
  }

  private String simplifyQualifiedTypes(final String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    String current = value;
    while (true) {
      final String updated =
          current.replaceAll(
              "(?<![A-Za-z0-9_])(?:[A-Za-z_][A-Za-z0-9_]*\\.)+([A-Za-z_][A-Za-z0-9_]*)", "$1");
      if (updated.equals(current)) {
        return updated;
      }
      current = updated;
    }
  }

  private boolean isImplicitNoArgConstructorCall(
      final CalledMethodReference reference, final LlmValidationFacts validationFacts) {
    if (reference == null || !reference.constructor() || !reference.parameterTypes().isEmpty()) {
      return false;
    }
    final String ownerType = normalizeTypeName(reference.ownerType());
    if (ownerType.isBlank()) {
      return false;
    }
    if (ownerType.equals(validationFacts.classFqn())) {
      return true;
    }
    return ownerType.startsWith(validationFacts.classFqn() + ".");
  }

  private String extractOwnerType(final String methodReference) {
    if (methodReference == null || methodReference.isBlank()) {
      return "";
    }
    final String value = methodReference.strip().replace('$', '.');
    final int hashIndex = value.indexOf('#');
    if (hashIndex <= 0) {
      return "";
    }
    return value.substring(0, hashIndex).strip();
  }

  private String normalizeTypeName(final String typeName) {
    if (typeName == null || typeName.isBlank()) {
      return "";
    }
    return typeName.strip().replace('$', '.').toLowerCase(Locale.ROOT);
  }

  private record CalledMethodReference(
      String ownerType,
      String methodName,
      List<String> parameterTypes,
      String normalizedReference,
      String dedupKey,
      boolean constructor) {}

  private record CalledMethodOutput(String reference, List<String> argumentLiterals) {}
}
