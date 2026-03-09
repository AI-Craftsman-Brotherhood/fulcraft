package com.craftsmanbro.fulcraft.plugins.document.core.llm.validation;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmAnalysisGapLexicon;
import com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmDocumentTextUtils;
import com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmListStructureNormalizer;
import com.craftsmanbro.fulcraft.plugins.document.core.llm.analysis.LlmMethodFlowFactsExtractor;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Validates method-section quality constraints in section 3.x blocks. */
public final class LlmMethodSectionValidator {

  private static final Pattern METHOD_BLOCK_PATTERN =
      Pattern.compile("(?s)(###\\s*3\\.\\d+\\s+.*?)(?=\\n###\\s*3\\.\\d+\\s+|\\n##\\s*4\\.|\\z)");

  private static final Pattern METHOD_HEADING_PATTERN =
      Pattern.compile("(?m)^###\\s*3\\.\\d+\\s+(.+?)\\s*$");

  private static final Pattern INPUT_OUTPUT_SECTION_PATTERN =
      Pattern.compile(
          "(?s)####\\s+3\\.\\d+\\.1\\s+(?:入出力|Inputs/Outputs)\\R(.*?)(?=####\\s+3\\.\\d+\\.2\\s+(?:事前条件|Preconditions)|\\z)");

  private static final Pattern NORMAL_FLOW_SECTION_PATTERN =
      Pattern.compile(
          "(?s)####\\s+3\\.\\d+\\.4\\s+(?:正常フロー|Normal Flow)\\R(.*?)(?=####\\s+3\\.\\d+\\.5\\s+(?:異常・境界|Error/Boundary Handling)|\\z)");

  private static final Pattern ERROR_BOUNDARY_SECTION_PATTERN =
      Pattern.compile(
          "(?s)####\\s+3\\.\\d+\\.5\\s+(?:異常・境界|Error/Boundary Handling)\\R(.*?)(?=####\\s+3\\.\\d+\\.6\\s+(?:依存呼び出し|Dependencies)|\\z)");

  private static final Pattern PRECONDITION_SECTION_PATTERN =
      Pattern.compile(
          "(?s)####\\s+3\\.\\d+\\.2\\s+(?:事前条件|Preconditions)\\R(.*?)(?=####\\s+3\\.\\d+\\.3\\s+(?:事後条件|Postconditions)|\\z)");

  private static final Pattern POSTCONDITION_SECTION_PATTERN =
      Pattern.compile(
          "(?s)####\\s+3\\.\\d+\\.3\\s+(?:事後条件|Postconditions)\\R(.*?)(?=####\\s+3\\.\\d+\\.4\\s+(?:正常フロー|Normal Flow)|\\z)");

  private static final Pattern DEPENDENCY_SECTION_PATTERN =
      Pattern.compile(
          "(?s)####\\s+3\\.\\d+\\.6\\s+(?:依存呼び出し|Dependencies)\\R(.*?)(?=####\\s+3\\.\\d+\\.7\\s+(?:テスト観点|Test Viewpoints)|\\z)");

  private static final Pattern TEST_VIEWPOINT_SECTION_PATTERN =
      Pattern.compile(
          "(?s)####\\s+3\\.\\d+\\.7\\s+(?:テスト観点|Test Viewpoints)\\R(.*?)(?=###\\s*3\\.\\d+\\s+|##\\s*4\\.|\\z)");

  private static final Pattern NON_NULL_PRECONDITION_PATTERN =
      Pattern.compile("^([a-z_][a-z0-9_$.\\[\\]]*)\\s*!=\\s*null$", Pattern.CASE_INSENSITIVE);

  private static final Pattern SWITCH_CASE_DESCRIPTION_PATTERN =
      Pattern.compile("(?i)^switch case\\s+(.+?)=\"(.+)\"$");

  private static final Pattern SWITCH_DEFAULT_DESCRIPTION_PATTERN =
      Pattern.compile("(?i)^switch default\\s+(.+)$");

  private static final Pattern SWITCH_CASE_OUTCOME_PATTERN = Pattern.compile("(?i)^case-\"(.+)\"$");

  private static final Pattern MALFORMED_INLINE_INPUT_NONE_PATTERN =
      Pattern.compile(
          "(?i)^-\\s*(?:inputs/outputs|inputs?|input|入出力|入力/出力|入力)\\s*[:：]\\s*-\\s*(?:none|なし)\\s*$");

  private static final List<String> VAGUE_PRECONDITION_PHRASES_JA =
      List.of("適切な値", "妥当な値", "有効なインスタンス", "正しい値", "必要な値", "有効であること", "有効なこと");

  private static final List<String> VAGUE_PRECONDITION_PHRASES_EN =
      List.of(
          "appropriate value",
          "valid value",
          "valid instance",
          "proper value",
          "required value",
          "is valid");

  private static final Set<String> CONSTRUCTOR_ALLOWED_MODIFIERS =
      Set.of("public", "protected", "private");

  private static final LlmListStructureNormalizer LIST_STRUCTURE_NORMALIZER =
      new LlmListStructureNormalizer();

  private final Function<MethodInfo, String> methodDisplayNameResolver;

  private final Function<String, String> methodNameNormalizer;

  private final Predicate<MethodInfo> uncertainDynamicResolutionChecker;

  private final Predicate<String> failureFactoryMethodNameChecker;

  private final BiPredicate<String, MethodInfo> unsupportedNoArgPreconditionChecker;

  private final BiPredicate<String, MethodInfo> unsupportedNonNullPreconditionChecker;

  private final Function<MethodInfo, List<String>> sourceBackedPreconditionCollector;

  private final Predicate<MethodInfo> earlyReturnIncompatibleChecker;

  private final Function<MethodInfo, List<LlmMethodFlowFactsExtractor.SwitchCaseFact>>
      switchCaseFactsCollector;

  public LlmMethodSectionValidator(
      final Function<MethodInfo, String> methodDisplayNameResolver,
      final Function<String, String> methodNameNormalizer,
      final Predicate<MethodInfo> uncertainDynamicResolutionChecker,
      final Predicate<String> failureFactoryMethodNameChecker,
      final BiPredicate<String, MethodInfo> unsupportedNoArgPreconditionChecker,
      final BiPredicate<String, MethodInfo> unsupportedNonNullPreconditionChecker,
      final Function<MethodInfo, List<String>> sourceBackedPreconditionCollector,
      final Predicate<MethodInfo> earlyReturnIncompatibleChecker,
      final Function<MethodInfo, List<LlmMethodFlowFactsExtractor.SwitchCaseFact>>
          switchCaseFactsCollector) {
    this.methodDisplayNameResolver = methodDisplayNameResolver;
    this.methodNameNormalizer = methodNameNormalizer;
    this.uncertainDynamicResolutionChecker = uncertainDynamicResolutionChecker;
    this.failureFactoryMethodNameChecker = failureFactoryMethodNameChecker;
    this.unsupportedNoArgPreconditionChecker = unsupportedNoArgPreconditionChecker;
    this.unsupportedNonNullPreconditionChecker = unsupportedNonNullPreconditionChecker;
    this.sourceBackedPreconditionCollector = sourceBackedPreconditionCollector;
    this.earlyReturnIncompatibleChecker = earlyReturnIncompatibleChecker;
    this.switchCaseFactsCollector = switchCaseFactsCollector;
  }

  public void validate(
      final String document,
      final List<MethodInfo> methods,
      final List<String> reasons,
      final boolean japanese,
      final String unavailableValue) {
    final Map<String, Deque<MethodInfo>> methodsByName = indexMethodsByName(methods);
    final Matcher methodMatcher = METHOD_BLOCK_PATTERN.matcher(document);
    while (methodMatcher.find()) {
      final MethodSectionSnapshot snapshot =
          buildMethodSectionSnapshot(methodMatcher.group(1), methodsByName, unavailableValue);
      final String reason = validateMethodSectionSnapshot(snapshot, japanese);
      if (reason != null) {
        reasons.add(reason);
        return;
      }
    }
  }

  private MethodSectionSnapshot buildMethodSectionSnapshot(
      final String methodBlock,
      final Map<String, Deque<MethodInfo>> methodsByName,
      final String unavailableValue) {
    final String methodName = extractMethodHeadingName(methodBlock, unavailableValue);
    final MethodInfo method = pollMethodByHeading(methodName, methodsByName);
    final String inputOutputSection =
        extractMethodSubsection(methodBlock, INPUT_OUTPUT_SECTION_PATTERN);
    final String preconditionSection =
        extractMethodSubsection(methodBlock, PRECONDITION_SECTION_PATTERN);
    final String postconditionSection =
        extractMethodSubsection(methodBlock, POSTCONDITION_SECTION_PATTERN);
    final String normalFlowSection =
        extractMethodSubsection(methodBlock, NORMAL_FLOW_SECTION_PATTERN);
    final String errorBoundarySection =
        extractMethodSubsection(methodBlock, ERROR_BOUNDARY_SECTION_PATTERN);
    final String dependencySection =
        extractMethodSubsection(methodBlock, DEPENDENCY_SECTION_PATTERN);
    final String testViewpointSection =
        extractMethodSubsection(methodBlock, TEST_VIEWPOINT_SECTION_PATTERN);
    final boolean hasUncertainDynamicResolution =
        method != null && uncertainDynamicResolutionChecker.test(method);
    return new MethodSectionSnapshot(
        methodBlock,
        methodName,
        method,
        inputOutputSection,
        preconditionSection,
        postconditionSection,
        normalFlowSection,
        errorBoundarySection,
        dependencySection,
        testViewpointSection,
        hasUncertainDynamicResolution);
  }

  private String validateMethodSectionSnapshot(
      final MethodSectionSnapshot snapshot, final boolean japanese) {
    final String structureReason = validateStructureAndPreconditions(snapshot, japanese);
    if (structureReason != null) {
      return structureReason;
    }
    final String outcomeReason = validateOutcomeConsistency(snapshot, japanese);
    if (outcomeReason != null) {
      return outcomeReason;
    }
    return validateCoverageAndDependencySections(snapshot, japanese);
  }

  private String validateStructureAndPreconditions(
      final MethodSectionSnapshot snapshot, final boolean japanese) {
    if (containsMalformedInlineInputNone(snapshot.inputOutputSection())) {
      return localizedReason(
          japanese,
          "document.llm.method_section.malformed_input_none_line.ja",
          "document.llm.method_section.malformed_input_none_line.en",
          snapshot.methodName());
    }
    if (hasNonCanonicalOrderedList(
        snapshot.preconditionSection(),
        snapshot.postconditionSection(),
        snapshot.normalFlowSection(),
        snapshot.errorBoundarySection(),
        snapshot.dependencySection(),
        snapshot.testViewpointSection())) {
      return localizedReason(
          japanese,
          "document.llm.method_section.non_canonical_ordered_list.ja",
          "document.llm.method_section.non_canonical_ordered_list.en",
          snapshot.methodName());
    }
    if (containsVaguePrecondition(snapshot.preconditionSection())) {
      return localizedReason(
          japanese,
          "document.llm.method_section.vague_preconditions.ja",
          "document.llm.method_section.vague_preconditions.en",
          snapshot.methodName());
    }
    if (containsFailureSidePrecondition(snapshot.preconditionSection())) {
      return localizedReason(
          japanese,
          "document.llm.method_section.failure_side_preconditions.ja",
          "document.llm.method_section.failure_side_preconditions.en",
          snapshot.methodName());
    }
    if (unsupportedNoArgPreconditionChecker.test(
        snapshot.preconditionSection(), snapshot.method())) {
      return localizedReason(
          japanese,
          "document.llm.method_section.no_arg_unsupported_preconditions.ja",
          "document.llm.method_section.no_arg_unsupported_preconditions.en",
          snapshot.methodName());
    }
    if (unsupportedNonNullPreconditionChecker.test(
        snapshot.preconditionSection(), snapshot.method())) {
      return localizedReason(
          japanese,
          "document.llm.method_section.unsupported_non_null_preconditions.ja",
          "document.llm.method_section.unsupported_non_null_preconditions.en",
          snapshot.methodName());
    }
    final List<String> missingSourcePreconditions =
        collectMissingSourceBackedPreconditions(snapshot.preconditionSection(), snapshot.method());
    if (missingSourcePreconditions.isEmpty()) {
      return null;
    }
    final String joined = String.join(", ", missingSourcePreconditions);
    return localizedReason(
        japanese,
        "document.llm.method_section.missing_source_preconditions.ja",
        "document.llm.method_section.missing_source_preconditions.en",
        snapshot.methodName(),
        joined);
  }

  private String validateOutcomeConsistency(
      final MethodSectionSnapshot snapshot, final boolean japanese) {
    if (snapshot.method() != null
        && earlyReturnIncompatibleChecker.test(snapshot.method())
        && containsEarlyReturnOutcomeLabel(snapshot.methodBlock())) {
      return localizedReason(
          japanese,
          "document.llm.method_section.early_return_incompatible.ja",
          "document.llm.method_section.early_return_incompatible.en",
          snapshot.methodName());
    }
    if (snapshot.hasUncertainDynamicResolution()
        && containsExplicitSuccessOutcome(snapshot.postconditionSection())) {
      return localizedReason(
          japanese,
          "document.llm.method_section.uncertain_dynamic_success_postconditions.ja",
          "document.llm.method_section.uncertain_dynamic_success_postconditions.en",
          snapshot.methodName());
    }
    if (containsFailureFactorySuccessWording(
        snapshot.postconditionSection(), snapshot.methodName())) {
      return localizedReason(
          japanese,
          "document.llm.method_section.failure_factory_success_postconditions.ja",
          "document.llm.method_section.failure_factory_success_postconditions.en",
          snapshot.methodName());
    }
    if (snapshot.hasUncertainDynamicResolution()
        && containsExplicitSuccessOutcome(snapshot.normalFlowSection())) {
      return localizedReason(
          japanese,
          "document.llm.method_section.uncertain_dynamic_success_normal_flow.ja",
          "document.llm.method_section.uncertain_dynamic_success_normal_flow.en",
          snapshot.methodName());
    }
    if (containsFailureFactorySuccessWording(snapshot.normalFlowSection(), snapshot.methodName())) {
      return localizedReason(
          japanese,
          "document.llm.method_section.failure_factory_success_normal_flow.ja",
          "document.llm.method_section.failure_factory_success_normal_flow.en",
          snapshot.methodName());
    }
    if (shouldValidateFailureLikeNormalFlow(snapshot.method(), snapshot.methodName())
        && !failureFactoryMethodNameChecker.test(snapshot.methodName())
        && containsFailureLikeNormalFlow(snapshot.normalFlowSection())) {
      return localizedReason(
          japanese,
          "document.llm.method_section.failure_like_normal_flow.ja",
          "document.llm.method_section.failure_like_normal_flow.en",
          snapshot.methodName());
    }
    if (snapshot.hasUncertainDynamicResolution()
        && containsExplicitSuccessOutcome(snapshot.testViewpointSection())) {
      return localizedReason(
          japanese,
          "document.llm.method_section.uncertain_dynamic_success_test_viewpoints.ja",
          "document.llm.method_section.uncertain_dynamic_success_test_viewpoints.en",
          snapshot.methodName());
    }
    if (containsFailureFactorySuccessWording(
        snapshot.testViewpointSection(), snapshot.methodName())) {
      return localizedReason(
          japanese,
          "document.llm.method_section.failure_factory_success_test_viewpoints.ja",
          "document.llm.method_section.failure_factory_success_test_viewpoints.en",
          snapshot.methodName());
    }
    return null;
  }

  private String validateCoverageAndDependencySections(
      final MethodSectionSnapshot snapshot, final boolean japanese) {
    final List<String> missingSwitchCases =
        collectMissingSwitchCaseCoverage(
            snapshot.method(),
            snapshot.postconditionSection(),
            snapshot.normalFlowSection(),
            snapshot.errorBoundarySection(),
            snapshot.testViewpointSection());
    if (!missingSwitchCases.isEmpty()) {
      final String joined = String.join(", ", missingSwitchCases);
      return localizedReason(
          japanese,
          "document.llm.method_section.switch_case_coverage_missing.ja",
          "document.llm.method_section.switch_case_coverage_missing.en",
          snapshot.methodName(),
          joined);
    }
    if (containsAmbiguousDependencyPlaceholder(snapshot.dependencySection())) {
      return localizedReason(
          japanese,
          "document.llm.method_section.ambiguous_dependency_placeholder.ja",
          "document.llm.method_section.ambiguous_dependency_placeholder.en",
          snapshot.methodName());
    }
    if (snapshot.method() != null
        && isThinContentDespiteAnalysis(
            snapshot.method(), snapshot.normalFlowSection(), snapshot.errorBoundarySection())) {
      return localizedReason(
          japanese,
          "document.llm.method_section.thin_content_despite_analysis.ja",
          "document.llm.method_section.thin_content_despite_analysis.en",
          snapshot.methodName());
    }
    return null;
  }

  private String localizedReason(
      final boolean japanese, final String jaKey, final String enKey, final Object... args) {
    return japanese ? msg(jaKey, args) : msg(enKey, args);
  }

  private record MethodSectionSnapshot(
      String methodBlock,
      String methodName,
      MethodInfo method,
      String inputOutputSection,
      String preconditionSection,
      String postconditionSection,
      String normalFlowSection,
      String errorBoundarySection,
      String dependencySection,
      String testViewpointSection,
      boolean hasUncertainDynamicResolution) {}

  private Map<String, Deque<MethodInfo>> indexMethodsByName(final List<MethodInfo> methods) {
    final Map<String, Deque<MethodInfo>> indexed = new HashMap<>();
    if (methods == null || methods.isEmpty()) {
      return indexed;
    }
    for (final MethodInfo method : methods) {
      final String key = methodNameNormalizer.apply(methodDisplayNameResolver.apply(method));
      if (key == null || key.isBlank()) {
        continue;
      }
      indexed.computeIfAbsent(key, ignored -> new ArrayDeque<>()).add(method);
    }
    return indexed;
  }

  private MethodInfo pollMethodByHeading(
      final String methodHeadingName, final Map<String, Deque<MethodInfo>> methodsByName) {
    if (methodHeadingName == null || methodHeadingName.isBlank() || methodsByName == null) {
      return null;
    }
    final String key = methodNameNormalizer.apply(methodHeadingName);
    if (key == null || key.isBlank()) {
      return null;
    }
    final Deque<MethodInfo> candidates = methodsByName.get(key);
    if (candidates == null || candidates.isEmpty()) {
      return null;
    }
    return candidates.pollFirst();
  }

  private String extractMethodSubsection(final String methodBlock, final Pattern sectionPattern) {
    if (methodBlock == null || methodBlock.isBlank() || sectionPattern == null) {
      return "";
    }
    final Matcher matcher = sectionPattern.matcher(methodBlock);
    if (!matcher.find()) {
      return "";
    }
    return matcher.group(1).strip();
  }

  private boolean hasNonCanonicalOrderedList(final String... sections) {
    if (sections == null || sections.length == 0) {
      return false;
    }
    for (final String section : sections) {
      if (section == null || section.isBlank()) {
        continue;
      }
      if (LIST_STRUCTURE_NORMALIZER.hasNonCanonicalOrderedList(section)) {
        return true;
      }
    }
    return false;
  }

  private String extractMethodHeadingName(final String methodBlock, final String unavailableValue) {
    if (methodBlock == null || methodBlock.isBlank()) {
      return unavailableValue;
    }
    final Matcher matcher = METHOD_HEADING_PATTERN.matcher(methodBlock);
    if (!matcher.find()) {
      return unavailableValue;
    }
    return matcher.group(1).strip();
  }

  private boolean isThinContentDespiteAnalysis(
      final MethodInfo method, final String normalFlowSection, final String errorBoundarySection) {
    if (method.getBranchSummary() == null) {
      return false;
    }
    final com.craftsmanbro.fulcraft.plugins.analysis.model.BranchSummary bs =
        method.getBranchSummary();
    final int branchCount =
        bs.getGuards().size() + bs.getSwitches().size() + bs.getPredicates().size();
    if (branchCount < 2) {
      return false;
    }
    return isFallbackOnlySection(normalFlowSection) && isFallbackOnlySection(errorBoundarySection);
  }

  private boolean isFallbackOnlySection(final String section) {
    if (section == null || section.isBlank()) {
      return true;
    }
    for (final String rawLine : section.split("\\R")) {
      final String normalized =
          LlmDocumentTextUtils.normalizeLine(rawLine).replaceFirst("^-\\s*", "");
      if (normalized.isBlank() || LlmDocumentTextUtils.isNoneMarker(normalized)) {
        continue;
      }
      if (LlmAnalysisGapLexicon.isAnalysisGapLine(rawLine)) {
        continue;
      }
      return false;
    }
    return true;
  }

  private boolean containsMalformedInlineInputNone(final String section) {
    if (section == null || section.isBlank()) {
      return false;
    }
    for (final String rawLine : section.split("\\R")) {
      final String stripped = rawLine == null ? "" : rawLine.strip();
      if (stripped.isBlank()) {
        continue;
      }
      if (MALFORMED_INLINE_INPUT_NONE_PATTERN.matcher(stripped).matches()) {
        return true;
      }
    }
    return false;
  }

  private boolean containsVaguePrecondition(final String section) {
    if (section == null || section.isBlank()) {
      return false;
    }
    for (final String rawLine : section.split("\\R")) {
      final String normalized =
          LlmDocumentTextUtils.normalizeLine(rawLine).replaceFirst("^-\\s*", "");
      if (normalized.isBlank() || LlmDocumentTextUtils.isNoneMarker(normalized)) {
        continue;
      }
      for (final String phrase : VAGUE_PRECONDITION_PHRASES_JA) {
        if (normalized.contains(phrase)) {
          return true;
        }
      }
      for (final String phrase : VAGUE_PRECONDITION_PHRASES_EN) {
        if (normalized.contains(phrase)) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean shouldValidateFailureLikeNormalFlow(
      final MethodInfo method, final String methodName) {
    if (method == null) {
      return true;
    }
    return !isConstructorMethod(method, methodName);
  }

  private boolean isConstructorMethod(final MethodInfo method, final String methodName) {
    if (method == null || methodName == null || methodName.isBlank()) {
      return false;
    }
    final String normalizedHeading = methodNameNormalizer.apply(methodName);
    if (normalizedHeading == null || normalizedHeading.isBlank()) {
      return false;
    }
    if (normalizedHeading.equals(methodNameNormalizer.apply(method.getName()))) {
      final String signature = method.getSignature();
      if (signature == null || signature.isBlank()) {
        return false;
      }
      String head = signature.strip().replace('$', '.').replace("`", "");
      final int paren = head.indexOf('(');
      if (paren <= 0) {
        return false;
      }
      head = head.substring(0, paren).trim();
      if (head.isBlank()) {
        return false;
      }
      final String[] tokens = head.split("\\s+");
      if (tokens.length == 0) {
        return false;
      }
      final String normalizedNameToken =
          methodNameNormalizer.apply(extractSimpleName(tokens[tokens.length - 1]));
      if (!normalizedHeading.equals(normalizedNameToken)) {
        return false;
      }
      boolean inTypeParameterClause = false;
      for (int i = 0; i < tokens.length - 1; i++) {
        final String token = tokens[i] == null ? "" : tokens[i].trim();
        if (token.isBlank() || token.startsWith("@")) {
          continue;
        }
        if (token.startsWith("<")) {
          inTypeParameterClause = true;
          if (token.endsWith(">")) {
            inTypeParameterClause = false;
          }
          continue;
        }
        if (inTypeParameterClause) {
          if (token.endsWith(">")) {
            inTypeParameterClause = false;
          }
          continue;
        }
        if (CONSTRUCTOR_ALLOWED_MODIFIERS.contains(token.toLowerCase(Locale.ROOT))) {
          continue;
        }
        return false;
      }
      return true;
    }
    return false;
  }

  private String extractSimpleName(final String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    String token = value.strip();
    final int dotIndex = token.lastIndexOf('.');
    if (dotIndex >= 0 && dotIndex + 1 < token.length()) {
      token = token.substring(dotIndex + 1);
    }
    return token;
  }

  private boolean containsFailureSidePrecondition(final String section) {
    if (section == null || section.isBlank()) {
      return false;
    }
    for (final String rawLine : section.split("\\R")) {
      final String normalized =
          LlmDocumentTextUtils.normalizeLine(rawLine).replaceFirst("^-\\s*", "");
      if (normalized.isBlank() || LlmDocumentTextUtils.isNoneMarker(normalized)) {
        continue;
      }
      final String compact = normalized.replaceAll("\\s+", "");
      if (compact.matches(".*!+[a-z_][a-z0-9_$.]*\\.issuccess\\(\\).*")
          || compact.matches(".*[a-z_][a-z0-9_$.]*\\.ispresent\\(\\).*")
          || compact.matches(".*!+[a-z_][a-z0-9_]*opt[a-z0-9_]*\\.isempty\\(\\).*")) {
        return true;
      }
      if (compact.contains("compareto(")
          && compact.contains(">0")
          && !compact.contains("bigdecimal.zero")
          && !compact.contains("(0)")) {
        return true;
      }
    }
    return false;
  }

  private List<String> collectMissingSourceBackedPreconditions(
      final String section, final MethodInfo method) {
    if (method == null) {
      return List.of();
    }
    final List<String> sourceBackedPreconditions = collectSourceBackedPreconditions(method);
    if (sourceBackedPreconditions.isEmpty()) {
      return List.of();
    }
    final Set<String> missing = new LinkedHashSet<>();
    for (final String precondition : sourceBackedPreconditions) {
      if (precondition == null || precondition.isBlank()) {
        continue;
      }
      if (containsRequiredPreconditionInSection(section, precondition)) {
        continue;
      }
      missing.add(precondition.strip());
    }
    return new ArrayList<>(missing);
  }

  private List<String> collectSourceBackedPreconditions(final MethodInfo method) {
    if (method == null || sourceBackedPreconditionCollector == null) {
      return List.of();
    }
    final List<String> collected = sourceBackedPreconditionCollector.apply(method);
    return collected == null ? List.of() : collected;
  }

  private boolean containsRequiredPreconditionInSection(
      final String section, final String precondition) {
    if (section == null || section.isBlank() || precondition == null || precondition.isBlank()) {
      return false;
    }
    final String sectionNormalized = normalizeMatchToken(section);
    final String requiredNormalized = normalizeMatchToken(precondition);
    if (!requiredNormalized.isBlank() && sectionNormalized.contains(requiredNormalized)) {
      return true;
    }
    return containsEquivalentNonNullPrecondition(sectionNormalized, precondition);
  }

  private boolean containsEquivalentNonNullPrecondition(
      final String sectionNormalized, final String precondition) {
    if (sectionNormalized == null
        || sectionNormalized.isBlank()
        || precondition == null
        || precondition.isBlank()) {
      return false;
    }
    final Matcher matcher =
        NON_NULL_PRECONDITION_PATTERN.matcher(LlmDocumentTextUtils.normalizeLine(precondition));
    if (!matcher.matches()) {
      return false;
    }
    final String target = normalizeMatchToken(matcher.group(1));
    if (target.isBlank() || !sectionNormalized.contains(target)) {
      return false;
    }
    return sectionNormalized.contains(target + "!=null")
        || sectionNormalized.contains(target + "はnullでない")
        || sectionNormalized.contains(target + "がnullでない")
        || sectionNormalized.contains(target + "はnullではない")
        || sectionNormalized.contains(target + "がnullではない")
        || sectionNormalized.contains(target + "null不可")
        || sectionNormalized.contains(target + "null禁止")
        || sectionNormalized.contains("mustnotbenull")
        || sectionNormalized.contains("mustbenon-null")
        || sectionNormalized.contains("isnotnull")
        || sectionNormalized.contains("non-null");
  }

  private boolean containsEarlyReturnOutcomeLabel(final String text) {
    if (text == null || text.isBlank()) {
      return false;
    }
    final String normalized = LlmDocumentTextUtils.normalizeLine(text);
    return normalized.contains("early-return")
        || normalized.contains("early return")
        || normalized.contains("早期リターン")
        || normalized.contains("早期 return");
  }

  private List<String> collectMissingSwitchCaseCoverage(
      final MethodInfo method,
      final String postconditionSection,
      final String normalFlowSection,
      final String errorBoundarySection,
      final String testViewpointSection) {
    if (method == null) {
      return List.of();
    }
    final List<LlmMethodFlowFactsExtractor.SwitchCaseFact> switchCaseFacts =
        collectSwitchCaseFacts(method);
    if (switchCaseFacts.isEmpty()) {
      return List.of();
    }
    final String coverageText =
        String.join(
            "\n",
            postconditionSection == null ? "" : postconditionSection,
            normalFlowSection == null ? "" : normalFlowSection,
            errorBoundarySection == null ? "" : errorBoundarySection,
            testViewpointSection == null ? "" : testViewpointSection);
    final Set<String> missing = new LinkedHashSet<>();
    for (final LlmMethodFlowFactsExtractor.SwitchCaseFact switchCaseFact : switchCaseFacts) {
      if (switchCaseFact == null || containsSwitchCaseCoverage(coverageText, switchCaseFact)) {
        continue;
      }
      final String label =
          switchCaseFact.description() == null || switchCaseFact.description().isBlank()
              ? switchCaseFact.expectedOutcome()
              : switchCaseFact.description();
      if (label != null && !label.isBlank()) {
        missing.add(label.strip());
      }
    }
    return new ArrayList<>(missing);
  }

  private List<LlmMethodFlowFactsExtractor.SwitchCaseFact> collectSwitchCaseFacts(
      final MethodInfo method) {
    if (method == null || switchCaseFactsCollector == null) {
      return List.of();
    }
    final List<LlmMethodFlowFactsExtractor.SwitchCaseFact> facts =
        switchCaseFactsCollector.apply(method);
    return facts == null ? List.of() : facts;
  }

  private boolean containsSwitchCaseCoverage(
      final String coverageText, final LlmMethodFlowFactsExtractor.SwitchCaseFact switchCaseFact) {
    if (coverageText == null || coverageText.isBlank() || switchCaseFact == null) {
      return false;
    }
    final String normalizedCoverage = normalizeMatchToken(coverageText);
    final String normalizedFactId = normalizeMatchToken(switchCaseFact.id());
    if (!normalizedFactId.isBlank() && normalizedCoverage.contains(normalizedFactId)) {
      return true;
    }
    final String normalizedExpected = normalizeMatchToken(switchCaseFact.expectedOutcome());
    if (!normalizedExpected.isBlank() && normalizedCoverage.contains(normalizedExpected)) {
      return true;
    }
    final String description =
        switchCaseFact.description() == null ? "" : switchCaseFact.description().strip();
    final Matcher caseDescriptionMatcher = SWITCH_CASE_DESCRIPTION_PATTERN.matcher(description);
    if (caseDescriptionMatcher.matches()) {
      final String switchExpression = normalizeMatchToken(caseDescriptionMatcher.group(1));
      final String caseLabel = normalizeMatchToken(caseDescriptionMatcher.group(2));
      if (!switchExpression.isBlank()
          && !caseLabel.isBlank()
          && normalizedCoverage.contains(switchExpression)
          && normalizedCoverage.contains(caseLabel)) {
        return true;
      }
      if (!caseLabel.isBlank()
          && normalizedCoverage.contains("case")
          && normalizedCoverage.contains(caseLabel)) {
        return true;
      }
    }
    final Matcher defaultDescriptionMatcher =
        SWITCH_DEFAULT_DESCRIPTION_PATTERN.matcher(description);
    if (defaultDescriptionMatcher.matches()) {
      final String switchExpression = normalizeMatchToken(defaultDescriptionMatcher.group(1));
      if (normalizedCoverage.contains("default")
          && (switchExpression.isBlank() || normalizedCoverage.contains(switchExpression))) {
        return true;
      }
    }
    final String expectedOutcome =
        switchCaseFact.expectedOutcome() == null ? "" : switchCaseFact.expectedOutcome().strip();
    final Matcher caseOutcomeMatcher = SWITCH_CASE_OUTCOME_PATTERN.matcher(expectedOutcome);
    if (caseOutcomeMatcher.matches()) {
      final String caseLabel = normalizeMatchToken(caseOutcomeMatcher.group(1));
      if (!caseLabel.isBlank()
          && normalizedCoverage.contains("case")
          && normalizedCoverage.contains(caseLabel)) {
        return true;
      }
    }
    if ("default".equalsIgnoreCase(expectedOutcome)) {
      return normalizedCoverage.contains("default");
    }
    return false;
  }

  private String normalizeMatchToken(final String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    return value
        .replace("`", "")
        .replace("\"", "")
        .replace("'", "")
        .replace("“", "")
        .replace("”", "")
        .replace("。", "")
        .replace("、", "")
        .toLowerCase(Locale.ROOT)
        .replaceAll("\\s+", "");
  }

  private boolean containsExplicitSuccessOutcome(final String section) {
    if (section == null || section.isBlank()) {
      return false;
    }
    for (final String rawLine : section.split("\\R")) {
      final String normalized =
          LlmDocumentTextUtils.normalizeLine(rawLine).replaceFirst("^-\\s*", "");
      if (normalized.isBlank() || LlmDocumentTextUtils.isNoneMarker(normalized)) {
        continue;
      }
      if (containsSuccessOutcomeToken(normalized)) {
        return true;
      }
    }
    return false;
  }

  private boolean containsFailureFactorySuccessWording(
      final String section, final String methodName) {
    if (section == null
        || section.isBlank()
        || methodName == null
        || methodName.isBlank()
        || !failureFactoryMethodNameChecker.test(methodName)) {
      return false;
    }
    for (final String rawLine : section.split("\\R")) {
      final String normalized =
          LlmDocumentTextUtils.normalizeLine(rawLine).replaceFirst("^-\\s*", "");
      if (normalized.isBlank() || LlmDocumentTextUtils.isNoneMarker(normalized)) {
        continue;
      }
      if (containsSuccessOutcomeToken(normalized)) {
        return true;
      }
    }
    return false;
  }

  private boolean containsSuccessOutcomeToken(final String normalizedLine) {
    if (normalizedLine == null || normalizedLine.isBlank()) {
      return false;
    }
    return normalizedLine.contains("期待結果: success")
        || normalizedLine.contains("expected outcome: success")
        || normalizedLine.contains("結果: success")
        || normalizedLine.contains("result: success")
        || normalizedLine.contains("結果（success）")
        || normalizedLine.contains("result (success)")
        || normalizedLine.contains("outcome (success)")
        || normalizedLine.matches(".*->\\s*success\\b.*");
  }

  private boolean containsFailureLikeNormalFlow(final String section) {
    if (section == null || section.isBlank()) {
      return false;
    }
    for (final String rawLine : section.split("\\R")) {
      final String normalized =
          LlmDocumentTextUtils.normalizeLine(rawLine).replaceFirst("^-\\s*", "");
      if (normalized.isBlank() || LlmDocumentTextUtils.isNoneMarker(normalized)) {
        continue;
      }
      if (normalized.contains("early-return")
          || normalized.contains("early return")
          || normalized.contains("failure")
          || normalized.contains("error")
          || normalized.contains("boundary")
          || normalized.contains("失敗")
          || normalized.contains("異常")
          || normalized.contains("境界")) {
        return true;
      }
    }
    return false;
  }

  private boolean containsAmbiguousDependencyPlaceholder(final String section) {
    if (section == null || section.isBlank()) {
      return false;
    }
    for (final String rawLine : section.split("\\R")) {
      final String value = rawLine == null ? "" : rawLine.strip();
      if (!value.startsWith("-")) {
        continue;
      }
      final String normalized =
          value
              .replaceFirst("^-\\s*", "")
              .replace("`", "")
              .replace("。", "")
              .replace(".", "")
              .trim()
              .toLowerCase(Locale.ROOT);
      if (normalized.isBlank() || LlmDocumentTextUtils.isNoneMarker(normalized)) {
        continue;
      }
      if ("他".equals(normalized)
          || "その他".equals(normalized)
          || "等".equals(normalized)
          || "others".equals(normalized)
          || "other".equals(normalized)
          || "etc".equals(normalized)
          || "and more".equals(normalized)) {
        return true;
      }
    }
    return false;
  }

  private String msg(final String key, final Object... args) {
    return MessageSource.getMessage(key, args);
  }
}
