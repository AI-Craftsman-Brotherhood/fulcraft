package com.craftsmanbro.fulcraft.plugins.document.core.llm.context;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.DynamicResolution;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodSemantics;
import com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmDocumentTextUtils;
import com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmPromptContext;
import com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmValidationFacts;
import com.craftsmanbro.fulcraft.plugins.document.core.llm.MethodDocClassifier;
import com.craftsmanbro.fulcraft.plugins.document.core.util.DocumentUtils;
import com.craftsmanbro.fulcraft.plugins.document.core.util.PromptInputCanonicalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/** Builds LLM prompt context data (prompt text + method/fact metadata) from analysis models. */
public final class LlmPromptContextFactory {

  private static final int HIGH_COMPLEXITY_THRESHOLD = 15;

  private final Predicate<DynamicResolution> resolutionUncertainChecker;

  private final Predicate<DynamicResolution> resolutionOpenQuestionChecker;

  private final Predicate<DynamicResolution> resolutionKnownMissingChecker;

  private final MethodDocClassifier methodDocClassifier;

  public LlmPromptContextFactory(final Predicate<DynamicResolution> resolutionUncertainChecker) {
    this(
        resolutionUncertainChecker,
        resolutionUncertainChecker,
        resolution -> false,
        new MethodDocClassifier());
  }

  public LlmPromptContextFactory(
      final Predicate<DynamicResolution> resolutionUncertainChecker,
      final MethodDocClassifier methodDocClassifier) {
    this(
        resolutionUncertainChecker,
        resolutionUncertainChecker,
        resolution -> false,
        methodDocClassifier);
  }

  public LlmPromptContextFactory(
      final Predicate<DynamicResolution> resolutionUncertainChecker,
      final Predicate<DynamicResolution> resolutionOpenQuestionChecker,
      final Predicate<DynamicResolution> resolutionKnownMissingChecker,
      final MethodDocClassifier methodDocClassifier) {
    this.resolutionUncertainChecker =
        Objects.requireNonNull(
            resolutionUncertainChecker,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "document.common.error.argument_null", "resolutionUncertainChecker"));
    this.resolutionOpenQuestionChecker =
        Objects.requireNonNull(
            resolutionOpenQuestionChecker,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "document.common.error.argument_null", "resolutionOpenQuestionChecker"));
    this.resolutionKnownMissingChecker =
        Objects.requireNonNull(
            resolutionKnownMissingChecker,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "document.common.error.argument_null", "resolutionKnownMissingChecker"));
    this.methodDocClassifier =
        Objects.requireNonNull(
            methodDocClassifier,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "document.common.error.argument_null", "methodDocClassifier"));
  }

  public LlmPromptContext buildPromptContext(
      final ClassInfo classInfo,
      final Set<String> crossClassKnownMethodNames,
      final String promptTemplate,
      final BiFunction<List<MethodInfo>, LlmValidationFacts, String> methodsInfoBuilder) {
    // 1. Identify all spec methods (full list for the document)
    final List<MethodInfo> specMethods = DocumentUtils.filterMethodsForSpecification(classInfo);
    final String simpleName = DocumentUtils.getSimpleName(classInfo.getFqn());
    // 2. Separate LLM methods (exclude template targets)
    final List<MethodInfo> llmMethods = new ArrayList<>();
    for (final MethodInfo method : specMethods) {
      if (!methodDocClassifier.isTemplateTarget(method, simpleName)) {
        llmMethods.add(method);
      }
    }
    final String packageName =
        DocumentUtils.formatPackageNameForDisplay(DocumentUtils.getPackageName(classInfo));
    // Validation facts are based on specMethods (all methods in document)
    final LlmValidationFacts validationFacts =
        buildValidationFacts(classInfo, specMethods, crossClassKnownMethodNames);
    final String classType = DocumentUtils.buildClassType(classInfo);
    final String classAttributes = buildClassAttributes(classInfo);
    final String extendsInfo = formatExtendsInfo(classInfo);
    final String implementsInfo = formatImplementsInfo(classInfo);
    final String fieldsInfo = DocumentUtils.buildFieldsInfo(classInfo.getFields());
    // Only LLM methods go into the prompt
    final String methodsInfo = methodsInfoBuilder.apply(llmMethods, validationFacts);
    final String cautionsInfo = buildCautionsInfo(specMethods);
    final String declaredMethodsInfo = buildDeclaredMethodsInfo(llmMethods);
    final String prompt =
        promptTemplate
            .replace("{{CLASS_NAME}}", simpleName)
            .replace("{{PACKAGE_NAME}}", packageName)
            .replace("{{FILE_PATH}}", nullSafe(classInfo.getFilePath()))
            .replace("{{LOC}}", String.valueOf(classInfo.getLoc()))
            .replace("{{METHOD_COUNT}}", String.valueOf(llmMethods.size()))
            .replace("{{CLASS_TYPE}}", classType)
            .replace("{{CLASS_ATTRIBUTES}}", classAttributes)
            .replace("{{EXTENDS_INFO}}", extendsInfo)
            .replace("{{IMPLEMENTS_INFO}}", implementsInfo)
            .replace("{{FIELDS_INFO}}", fieldsInfo)
            .replace("{{CAUTIONS_INFO}}", cautionsInfo)
            .replace("{{DECLARED_METHODS}}", declaredMethodsInfo)
            .replace("{{METHODS_INFO}}", methodsInfo);
    return new LlmPromptContext(prompt, specMethods, llmMethods, validationFacts);
  }

  public String buildClassAttributes(final ClassInfo classInfo) {
    final boolean japanese = isJapaneseLocale();
    final String enclosingType = resolveEnclosingType(classInfo);
    final StringBuilder sb = new StringBuilder();
    if (japanese) {
      sb.append("- nested_class: ")
          .append(classInfo != null && classInfo.isNestedClass())
          .append("\n");
      sb.append("- anonymous_class: ")
          .append(classInfo != null && classInfo.isAnonymous())
          .append("\n");
      sb.append("- has_nested_classes: ")
          .append(classInfo != null && classInfo.hasNestedClasses())
          .append("\n");
      sb.append("- enclosing_type: ")
          .append(
              enclosingType.isBlank()
                  ? msg("document.llm.prompt_context.enclosing.none.ja")
                  : "`" + enclosingType + "`");
    } else {
      sb.append("- nested_class: ")
          .append(classInfo != null && classInfo.isNestedClass())
          .append("\n");
      sb.append("- anonymous_class: ")
          .append(classInfo != null && classInfo.isAnonymous())
          .append("\n");
      sb.append("- has_nested_classes: ")
          .append(classInfo != null && classInfo.hasNestedClasses())
          .append("\n");
      sb.append("- enclosing_type: ")
          .append(
              enclosingType.isBlank()
                  ? msg("document.llm.prompt_context.enclosing.none.en")
                  : "`" + enclosingType + "`");
    }
    return sb.toString();
  }

  public Set<String> collectKnownMethodNames(final List<ClassInfo> classes) {
    final LinkedHashSet<String> knownMethods = new LinkedHashSet<>();
    if (classes == null || classes.isEmpty()) {
      return knownMethods;
    }
    for (final ClassInfo classInfo : classes) {
      if (classInfo == null) {
        continue;
      }
      for (final MethodInfo method : PromptInputCanonicalizer.sortMethods(classInfo.getMethods())) {
        final String methodName =
            LlmDocumentTextUtils.normalizeMethodName(resolveMethodDisplayName(method));
        if (!methodName.isBlank()) {
          knownMethods.add(methodName);
        }
      }
    }
    return knownMethods;
  }

  public String buildCautionsInfo(final List<MethodInfo> methods) {
    final List<String> cautionItems = buildCautionItems(methods);
    if (cautionItems.isEmpty()) {
      return isJapaneseLocale()
          ? msg("document.llm.prompt_context.bullet.none.ja")
          : msg("document.llm.prompt_context.bullet.none.en");
    }
    final StringBuilder sb = new StringBuilder();
    for (final String item : cautionItems) {
      sb.append("- ").append(item).append("\n");
    }
    return sb.toString().stripTrailing();
  }

  public String resolveMethodDisplayName(final MethodInfo method) {
    if (method == null) {
      return msg("document.value.na");
    }
    if (method.getName() != null && !method.getName().isBlank()) {
      return method.getName().strip();
    }
    final String fromSignature = LlmDocumentTextUtils.extractMethodName(method.getSignature());
    if (!fromSignature.isEmpty()) {
      return fromSignature;
    }
    return msg("document.value.na");
  }

  private String buildDeclaredMethodsInfo(final List<MethodInfo> methods) {
    final List<MethodInfo> sorted = PromptInputCanonicalizer.sortMethods(methods);
    if (sorted.isEmpty()) {
      return isJapaneseLocale()
          ? msg("document.llm.prompt_context.bullet.none.ja")
          : msg("document.llm.prompt_context.bullet.none.en");
    }
    final StringBuilder sb = new StringBuilder();
    for (int i = 0; i < sorted.size(); i++) {
      final MethodInfo method = sorted.get(i);
      sb.append("- ")
          .append(i + 1)
          .append(". ")
          .append(resolveMethodDisplayName(method))
          .append("\n");
    }
    return sb.toString().stripTrailing();
  }

  private LlmValidationFacts buildValidationFacts(
      final ClassInfo classInfo,
      final List<MethodInfo> methods,
      final Set<String> crossClassKnownMethodNames) {
    final List<String> methodNames = new ArrayList<>();
    final Set<String> highComplexityMethods = new LinkedHashSet<>();
    final Set<String> deadCodeMethods = new LinkedHashSet<>();
    final Set<String> duplicateMethods = new LinkedHashSet<>();
    final Map<String, String> uncertainDynamicMethods = new HashMap<>();
    final Map<String, Set<String>> uncertainDynamicMethodNamesByMethod = new HashMap<>();
    final Map<String, String> knownMissingDynamicMethods = new HashMap<>();
    final Map<String, Set<String>> knownMissingDynamicMethodNamesByMethod = new HashMap<>();
    final Set<String> privateMethodNames = new LinkedHashSet<>();
    final Map<String, Integer> methodBranchCounts = new HashMap<>();
    final Set<String> knownMethodNames =
        collectKnownMethodNames(classInfo, crossClassKnownMethodNames);
    final Set<String> knownConstructorSignatures = collectKnownConstructorSignatures(classInfo);
    for (final MethodInfo method : PromptInputCanonicalizer.sortMethods(classInfo.getMethods())) {
      if (!isPrivateMethod(method)) {
        continue;
      }
      final String privateMethodName =
          LlmDocumentTextUtils.normalizeMethodName(resolveMethodDisplayName(method));
      if (!privateMethodName.isEmpty()) {
        privateMethodNames.add(privateMethodName);
      }
    }
    for (final MethodInfo method : PromptInputCanonicalizer.sortMethods(methods)) {
      final String methodName =
          LlmDocumentTextUtils.normalizeMethodName(resolveMethodDisplayName(method));
      if (methodName.isEmpty()) {
        continue;
      }
      methodNames.add(methodName);
      if (method.getCyclomaticComplexity() >= HIGH_COMPLEXITY_THRESHOLD) {
        highComplexityMethods.add(methodName);
      }
      if (method.isDeadCode()) {
        deadCodeMethods.add(methodName);
      }
      if (method.isDuplicate()) {
        duplicateMethods.add(methodName);
      }
      final int branchCount = computeBranchCount(method);
      if (branchCount > 0) {
        methodBranchCounts.put(methodName, branchCount);
      }
      for (final DynamicResolution resolution : method.getDynamicResolutions()) {
        if (resolutionKnownMissingChecker.test(resolution)) {
          final String registeredResolvedMethodName =
              registerUncertainDynamicMethod(
                  knownMissingDynamicMethods,
                  LlmDocumentTextUtils.extractMethodName(resolution.resolvedMethodSig()),
                  null);
          registerMethodScopedUncertainDynamicMethod(
              knownMissingDynamicMethodNamesByMethod, methodName, registeredResolvedMethodName);
          for (final String candidate : resolution.candidates()) {
            final String registeredCandidateMethodName =
                registerUncertainDynamicMethod(
                    knownMissingDynamicMethods,
                    LlmDocumentTextUtils.extractMethodName(candidate),
                    null);
            registerMethodScopedUncertainDynamicMethod(
                knownMissingDynamicMethodNamesByMethod, methodName, registeredCandidateMethodName);
          }
          continue;
        }
        if (!resolutionOpenQuestionChecker.test(resolution)
            && !resolutionUncertainChecker.test(resolution)) {
          continue;
        }
        final String registeredResolvedMethodName =
            registerUncertainDynamicMethod(
                uncertainDynamicMethods,
                LlmDocumentTextUtils.extractMethodName(resolution.resolvedMethodSig()),
                knownMethodNames);
        registerMethodScopedUncertainDynamicMethod(
            uncertainDynamicMethodNamesByMethod, methodName, registeredResolvedMethodName);
        for (final String candidate : resolution.candidates()) {
          final String registeredCandidateMethodName =
              registerUncertainDynamicMethod(
                  uncertainDynamicMethods,
                  LlmDocumentTextUtils.extractMethodName(candidate),
                  knownMethodNames);
          registerMethodScopedUncertainDynamicMethod(
              uncertainDynamicMethodNamesByMethod, methodName, registeredCandidateMethodName);
        }
      }
    }
    final Set<String> uncertainDynamicMethodNames =
        new LinkedHashSet<>(PromptInputCanonicalizer.sortStrings(uncertainDynamicMethods.keySet()));
    final Set<String> uncertainDynamicMethodDisplayNames =
        new LinkedHashSet<>(PromptInputCanonicalizer.sortStrings(uncertainDynamicMethods.values()));
    final Map<String, Set<String>> normalizedUncertainDynamicMethodNamesByMethod =
        toSortedUnmodifiableMethodMap(uncertainDynamicMethodNamesByMethod);
    final Set<String> knownMissingDynamicMethodNames =
        new LinkedHashSet<>(
            PromptInputCanonicalizer.sortStrings(knownMissingDynamicMethods.keySet()));
    final Set<String> knownMissingDynamicMethodDisplayNames =
        new LinkedHashSet<>(
            PromptInputCanonicalizer.sortStrings(knownMissingDynamicMethods.values()));
    final Map<String, Set<String>> normalizedKnownMissingDynamicMethodNamesByMethod =
        toSortedUnmodifiableMethodMap(knownMissingDynamicMethodNamesByMethod);
    return new LlmValidationFacts(
        methodNames,
        highComplexityMethods,
        deadCodeMethods,
        duplicateMethods,
        uncertainDynamicMethodNames,
        uncertainDynamicMethodDisplayNames,
        normalizedUncertainDynamicMethodNamesByMethod,
        knownMissingDynamicMethodNames,
        knownMissingDynamicMethodDisplayNames,
        normalizedKnownMissingDynamicMethodNamesByMethod,
        knownMethodNames,
        knownConstructorSignatures,
        privateMethodNames,
        classInfo.isInterface(),
        classInfo.isNestedClass(),
        resolveEnclosingType(classInfo),
        normalizeTypeName(classInfo.getFqn()),
        normalizeTypeName(DocumentUtils.getSimpleName(classInfo.getFqn())),
        Map.copyOf(methodBranchCounts));
  }

  private Map<String, Set<String>> toSortedUnmodifiableMethodMap(
      final Map<String, Set<String>> uncertainDynamicMethodNamesByMethod) {
    if (uncertainDynamicMethodNamesByMethod == null
        || uncertainDynamicMethodNamesByMethod.isEmpty()) {
      return Map.of();
    }
    final Map<String, Set<String>> normalized = new HashMap<>();
    for (final Map.Entry<String, Set<String>> entry :
        uncertainDynamicMethodNamesByMethod.entrySet()) {
      if (entry == null || entry.getKey() == null || entry.getKey().isBlank()) {
        continue;
      }
      final Set<String> values = entry.getValue();
      if (values == null || values.isEmpty()) {
        continue;
      }
      final Set<String> sortedValues =
          new LinkedHashSet<>(PromptInputCanonicalizer.sortStrings(values));
      normalized.put(entry.getKey(), Set.copyOf(sortedValues));
    }
    return normalized.isEmpty() ? Map.of() : Map.copyOf(normalized);
  }

  private int computeBranchCount(final MethodInfo method) {
    if (method == null || method.getBranchSummary() == null) {
      return 0;
    }
    final com.craftsmanbro.fulcraft.plugins.analysis.model.BranchSummary bs =
        method.getBranchSummary();
    return bs.getGuards().size() + bs.getSwitches().size() + bs.getPredicates().size();
  }

  private void registerMethodScopedUncertainDynamicMethod(
      final Map<String, Set<String>> uncertainDynamicMethodNamesByMethod,
      final String ownerMethodName,
      final String uncertainMethodName) {
    if (uncertainDynamicMethodNamesByMethod == null
        || ownerMethodName == null
        || ownerMethodName.isBlank()
        || uncertainMethodName == null
        || uncertainMethodName.isBlank()) {
      return;
    }
    uncertainDynamicMethodNamesByMethod
        .computeIfAbsent(ownerMethodName, ignored -> new LinkedHashSet<>())
        .add(uncertainMethodName);
  }

  private String registerUncertainDynamicMethod(
      final Map<String, String> uncertainDynamicMethods,
      final String methodName,
      final Set<String> knownMethodNames) {
    if (methodName == null || methodName.isBlank()) {
      return "";
    }
    final String displayName = methodName.strip().replace("`", "");
    final String normalized = LlmDocumentTextUtils.normalizeMethodName(displayName);
    if (normalized.isBlank()) {
      return "";
    }
    if (knownMethodNames != null && knownMethodNames.contains(normalized)) {
      return "";
    }
    uncertainDynamicMethods.putIfAbsent(normalized, displayName);
    return normalized;
  }

  private Set<String> collectKnownMethodNames(
      final ClassInfo classInfo, final Set<String> crossClassKnownMethodNames) {
    final LinkedHashSet<String> knownMethods = new LinkedHashSet<>();
    if (crossClassKnownMethodNames != null) {
      for (final String methodName : crossClassKnownMethodNames) {
        final String normalized = LlmDocumentTextUtils.normalizeMethodName(methodName);
        if (!normalized.isBlank()) {
          knownMethods.add(normalized);
        }
      }
    }
    if (classInfo == null) {
      return knownMethods;
    }
    for (final MethodInfo method : PromptInputCanonicalizer.sortMethods(classInfo.getMethods())) {
      final String methodName =
          LlmDocumentTextUtils.normalizeMethodName(resolveMethodDisplayName(method));
      if (!methodName.isBlank()) {
        knownMethods.add(methodName);
      }
    }
    return knownMethods;
  }

  private Set<String> collectKnownConstructorSignatures(final ClassInfo classInfo) {
    final LinkedHashSet<String> knownConstructors = new LinkedHashSet<>();
    if (classInfo == null) {
      return knownConstructors;
    }
    final String classSimpleName = MethodSemantics.simpleClassName(classInfo.getFqn());
    for (final MethodInfo method : PromptInputCanonicalizer.sortMethods(classInfo.getMethods())) {
      if (isImplicitDefaultConstructor(method, classSimpleName)) {
        continue;
      }
      final String normalized = normalizeConstructorSignature(method, classSimpleName);
      if (!normalized.isBlank()) {
        knownConstructors.add(normalized);
      }
    }
    return knownConstructors;
  }

  private String normalizeConstructorSignature(
      final MethodInfo method, final String classSimpleName) {
    if (method == null || classSimpleName == null || classSimpleName.isBlank()) {
      return "";
    }
    final String signature = method.getSignature();
    if (signature == null || signature.isBlank()) {
      return "";
    }
    final String normalizedSignature = signature.strip().replace('$', '.').replace("`", "");
    final int openParen = normalizedSignature.indexOf('(');
    final int closeParen = normalizedSignature.lastIndexOf(')');
    if (openParen <= 0 || closeParen <= openParen) {
      return "";
    }
    String constructorToken = normalizedSignature.substring(0, openParen).trim();
    final int spaceIndex = constructorToken.lastIndexOf(' ');
    if (spaceIndex >= 0 && spaceIndex + 1 < constructorToken.length()) {
      constructorToken = constructorToken.substring(spaceIndex + 1);
    }
    final int dotIndex = constructorToken.lastIndexOf('.');
    if (dotIndex >= 0 && dotIndex + 1 < constructorToken.length()) {
      constructorToken = constructorToken.substring(dotIndex + 1);
    }
    if (!classSimpleName.equals(constructorToken)) {
      return "";
    }
    final List<String> parameterTypes =
        normalizeConstructorParameterTypes(
            normalizedSignature.substring(openParen + 1, closeParen).trim());
    return LlmDocumentTextUtils.normalizeMethodName(constructorToken)
        + "("
        + String.join(",", parameterTypes)
        + ")";
  }

  private List<String> normalizeConstructorParameterTypes(final String parameterSection) {
    if (parameterSection == null || parameterSection.isBlank()) {
      return List.of();
    }
    final List<String> normalized = new ArrayList<>();
    for (final String token : LlmDocumentTextUtils.splitTopLevelCsv(parameterSection)) {
      final String parameter = normalizeConstructorParameterType(token);
      if (!parameter.isBlank()) {
        normalized.add(parameter);
      }
    }
    return normalized;
  }

  private String normalizeConstructorParameterType(final String rawParameter) {
    if (rawParameter == null || rawParameter.isBlank()) {
      return "";
    }
    String normalized = rawParameter.strip().replace('$', '.');
    normalized = normalized.replaceAll("@\\w+(\\([^)]*\\))?\\s*", "");
    normalized = normalized.replaceAll("\\b(final|volatile|transient)\\b\\s*", "");
    final int lastSpace = normalized.lastIndexOf(' ');
    if (lastSpace > 0 && lastSpace + 1 < normalized.length()) {
      final String tail = normalized.substring(lastSpace + 1);
      if (isLikelyParameterName(tail)) {
        normalized = normalized.substring(0, lastSpace).trim();
      }
    }
    normalized = normalized.replace("...", "[]");
    normalized = eraseGenericArguments(normalized);
    normalized = simplifyQualifiedTypes(normalized);
    return normalized.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
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
    final StringBuilder sb = new StringBuilder();
    final int length = value.length();
    int lastStart = 0;
    for (int i = 0; i < length; i++) {
      final char ch = value.charAt(i);
      if (!Character.isJavaIdentifierPart(ch) && ch != '.') {
        if (i > lastStart) {
          sb.append(simplifyToken(value.substring(lastStart, i)));
        }
        sb.append(ch);
        lastStart = i + 1;
      }
    }
    if (lastStart < length) {
      sb.append(simplifyToken(value.substring(lastStart)));
    }
    return sb.toString();
  }

  private String simplifyToken(final String token) {
    if (token == null || token.isBlank() || !token.contains(".")) {
      return token == null ? "" : token;
    }
    final int lastDot = token.lastIndexOf('.');
    if (lastDot > 0 && lastDot < token.length() - 1) {
      return token.substring(lastDot + 1);
    }
    return token;
  }

  private boolean isLikelyParameterName(final String token) {
    if (token == null || token.isBlank() || !Character.isLowerCase(token.charAt(0))) {
      return false;
    }
    for (int i = 0; i < token.length(); i++) {
      final char ch = token.charAt(i);
      if (!Character.isLetterOrDigit(ch) && ch != '_') {
        return false;
      }
    }
    return true;
  }

  private boolean isImplicitDefaultConstructor(
      final MethodInfo method, final String classSimpleName) {
    return isConstructor(method, classSimpleName)
        && method.getParameterCount() == 0
        && method.getLoc() <= 0;
  }

  private boolean isConstructor(final MethodInfo method, final String classSimpleName) {
    return MethodSemantics.isConstructor(method, classSimpleName);
  }

  private List<String> buildCautionItems(final List<MethodInfo> methods) {
    final List<String> cautionItems = new ArrayList<>();
    final boolean japanese = isJapaneseLocale();
    for (final MethodInfo method : PromptInputCanonicalizer.sortMethods(methods)) {
      final String methodName = resolveMethodDisplayName(method);
      if (method.getCyclomaticComplexity() >= HIGH_COMPLEXITY_THRESHOLD) {
        cautionItems.add(
            japanese
                ? msg(
                    "document.llm.prompt_context.caution.high_complexity.ja",
                    methodName,
                    method.getCyclomaticComplexity())
                : msg(
                    "document.llm.prompt_context.caution.high_complexity.en",
                    methodName,
                    method.getCyclomaticComplexity()));
      }
      if (method.isDeadCode()) {
        cautionItems.add(
            japanese
                ? msg("document.llm.prompt_context.caution.dead_code.ja", methodName)
                : msg("document.llm.prompt_context.caution.dead_code.en", methodName));
      }
      if (method.isDuplicate()) {
        cautionItems.add(
            japanese
                ? msg("document.llm.prompt_context.caution.duplicate.ja", methodName)
                : msg("document.llm.prompt_context.caution.duplicate.en", methodName));
      }
      if (method.isUsesRemovedApis()) {
        cautionItems.add(
            japanese
                ? msg("document.llm.prompt_context.caution.removed_api.ja", methodName)
                : msg("document.llm.prompt_context.caution.removed_api.en", methodName));
      }
      if (method.isPartOfCycle()) {
        cautionItems.add(
            japanese
                ? msg("document.llm.prompt_context.caution.cycle.ja", methodName)
                : msg("document.llm.prompt_context.caution.cycle.en", methodName));
      }
    }
    return cautionItems;
  }

  private String resolveEnclosingType(final ClassInfo classInfo) {
    if (classInfo == null || classInfo.getFqn() == null || classInfo.getFqn().isBlank()) {
      return "";
    }
    final String normalizedFqn = classInfo.getFqn().replace('$', '.').strip();
    final String packageName = DocumentUtils.getPackageName(classInfo);
    String typePath = normalizedFqn;
    if (packageName != null
        && !packageName.isBlank()
        && !"(default)".equals(packageName)
        && normalizedFqn.startsWith(packageName + ".")) {
      typePath = normalizedFqn.substring(packageName.length() + 1);
    }
    final String[] typeTokens = typePath.split("\\.");
    if (typeTokens.length <= 1) {
      return "";
    }
    return String.join(".", Arrays.copyOf(typeTokens, typeTokens.length - 1));
  }

  private boolean isPrivateMethod(final MethodInfo method) {
    if (method == null || method.getVisibility() == null) {
      return false;
    }
    return "private".equalsIgnoreCase(method.getVisibility().strip());
  }

  private String normalizeTypeName(final String typeName) {
    if (typeName == null || typeName.isBlank()) {
      return "";
    }
    return typeName.strip().replace('$', '.').toLowerCase(Locale.ROOT);
  }

  private String formatExtendsInfo(final ClassInfo classInfo) {
    if (classInfo == null || classInfo.getExtendsTypes().isEmpty()) {
      return msg("document.value.none");
    }
    return PromptInputCanonicalizer.sortAndJoin(classInfo.getExtendsTypes(), ", ");
  }

  private String formatImplementsInfo(final ClassInfo classInfo) {
    if (classInfo == null || classInfo.getImplementsTypes().isEmpty()) {
      return msg("document.value.none");
    }
    return PromptInputCanonicalizer.sortAndJoin(classInfo.getImplementsTypes(), ", ");
  }

  private boolean isJapaneseLocale() {
    final Locale locale = MessageSource.getLocale();
    return locale == null || "ja".equalsIgnoreCase(locale.getLanguage());
  }

  private String msg(final String key, final Object... args) {
    return MessageSource.getMessage(key, args);
  }

  private String nullSafe(final String value) {
    return value != null ? value : msg("document.value.na");
  }
}
