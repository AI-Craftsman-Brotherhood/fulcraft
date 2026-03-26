package com.craftsmanbro.fulcraft.plugins.document.core.llm.generation;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.RepresentativePath;
import com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmDocumentTextUtils;
import com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmValidationFacts;
import com.craftsmanbro.fulcraft.plugins.document.core.llm.analysis.LlmFallbackPreconditionExtractor;
import com.craftsmanbro.fulcraft.plugins.document.core.llm.analysis.LlmMethodFlowFactsExtractor;
import com.craftsmanbro.fulcraft.plugins.document.core.util.DocumentUtils;
import com.craftsmanbro.fulcraft.plugins.document.core.util.PromptInputCanonicalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Builds fallback section bullets from representative paths and method facts. */
public final class LlmFallbackPathSectionBuilder {

  private static final int PRECONDITION_VIEWPOINT_LIMIT = 4;

  private static final int DEPENDENCY_PREVIEW_LIMIT = 5;

  private static final int EXCEPTION_VIEWPOINT_LIMIT = 3;

  private static final int SOURCE_ASSIGNMENT_LIMIT = 4;

  private static final int SOURCE_STATEMENT_LIMIT = 16;

  private static final Set<String> NON_PARAMETER_TOKENS =
      Set.of(
          "byte", "short", "int", "long", "float", "double", "char", "boolean", "void", "final",
          "var", "this", "super");

  private static final Pattern CONTROL_FLOW_KEYWORD_PATTERN =
      Pattern.compile("\\b(if|for|while|switch|try|catch|throw|do|synchronized)\\b");

  private static final Pattern SIMPLE_ASSIGNMENT_PATTERN =
      Pattern.compile("^(this\\.)?([A-Za-z_$][A-Za-z0-9_$]*)\\s*=\\s*(.+)$");

  private static final Pattern RETURN_STATEMENT_PATTERN = Pattern.compile("^return\\s*(.*)$");

  private final LlmFallbackPreconditionExtractor fallbackPreconditionExtractor;

  private final Predicate<MethodInfo> unresolvedDynamicResolutionChecker;

  private final Predicate<MethodInfo> knownMissingDynamicResolutionChecker;

  private final Function<MethodInfo, String> methodDisplayNameResolver;

  private final Predicate<String> failureFactoryMethodNameChecker;

  private final Predicate<String> errorIndicatorChecker;

  private final Predicate<MethodInfo> earlyReturnIncompatibleChecker;

  private final Function<MethodInfo, List<LlmMethodFlowFactsExtractor.SwitchCaseFact>>
      switchCaseFactsCollector;

  private final String unavailableValue;

  private final LlmConstructorSemantics constructorSemantics;

  public LlmFallbackPathSectionBuilder(
      final LlmFallbackPreconditionExtractor fallbackPreconditionExtractor,
      final Predicate<MethodInfo> unresolvedDynamicResolutionChecker,
      final Function<MethodInfo, String> methodDisplayNameResolver,
      final Predicate<String> failureFactoryMethodNameChecker,
      final Predicate<String> errorIndicatorChecker,
      final Predicate<MethodInfo> earlyReturnIncompatibleChecker,
      final Function<MethodInfo, List<LlmMethodFlowFactsExtractor.SwitchCaseFact>>
          switchCaseFactsCollector,
      final String unavailableValue) {
    this(
        fallbackPreconditionExtractor,
        unresolvedDynamicResolutionChecker,
        method -> false,
        methodDisplayNameResolver,
        failureFactoryMethodNameChecker,
        errorIndicatorChecker,
        earlyReturnIncompatibleChecker,
        switchCaseFactsCollector,
        unavailableValue);
  }

  public LlmFallbackPathSectionBuilder(
      final LlmFallbackPreconditionExtractor fallbackPreconditionExtractor,
      final Predicate<MethodInfo> unresolvedDynamicResolutionChecker,
      final Predicate<MethodInfo> knownMissingDynamicResolutionChecker,
      final Function<MethodInfo, String> methodDisplayNameResolver,
      final Predicate<String> failureFactoryMethodNameChecker,
      final Predicate<String> errorIndicatorChecker,
      final Predicate<MethodInfo> earlyReturnIncompatibleChecker,
      final Function<MethodInfo, List<LlmMethodFlowFactsExtractor.SwitchCaseFact>>
          switchCaseFactsCollector,
      final String unavailableValue) {
    this.fallbackPreconditionExtractor =
        Objects.requireNonNull(
            fallbackPreconditionExtractor, "fallbackPreconditionExtractor must not be null");
    this.unresolvedDynamicResolutionChecker =
        Objects.requireNonNull(
            unresolvedDynamicResolutionChecker,
            "unresolvedDynamicResolutionChecker must not be null");
    this.knownMissingDynamicResolutionChecker =
        Objects.requireNonNull(
            knownMissingDynamicResolutionChecker,
            "knownMissingDynamicResolutionChecker must not be null");
    this.methodDisplayNameResolver =
        Objects.requireNonNull(
            methodDisplayNameResolver, "methodDisplayNameResolver must not be null");
    this.failureFactoryMethodNameChecker =
        Objects.requireNonNull(
            failureFactoryMethodNameChecker, "failureFactoryMethodNameChecker must not be null");
    this.errorIndicatorChecker =
        Objects.requireNonNull(
            errorIndicatorChecker,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "document.common.error.argument_null", "errorIndicatorChecker must not be null"));
    this.earlyReturnIncompatibleChecker =
        Objects.requireNonNull(
            earlyReturnIncompatibleChecker, "earlyReturnIncompatibleChecker must not be null");
    this.switchCaseFactsCollector =
        Objects.requireNonNull(
            switchCaseFactsCollector, "switchCaseFactsCollector must not be null");
    this.unavailableValue = unavailableValue == null ? "" : unavailableValue;
    this.constructorSemantics = new LlmConstructorSemantics();
  }

  public List<String> collectFallbackPostconditions(
      final MethodInfo method, final boolean japanese, final LlmValidationFacts facts) {
    final LinkedHashSet<String> values = new LinkedHashSet<>();
    if (method == null) {
      return List.of();
    }
    final boolean hasUnresolvedDynamicResolution = unresolvedDynamicResolutionChecker.test(method);
    final boolean hasKnownMissingDynamicResolution =
        knownMissingDynamicResolutionChecker.test(method);
    final Set<String> coveredSwitchCaseKeys = collectCoveredSwitchCaseKeys(method);
    for (final RepresentativePath path : method.getRepresentativePaths()) {
      if (path == null) {
        continue;
      }
      if (containsUncertainDynamicReference(path, facts, method)) {
        continue;
      }
      if (isPositiveNonNullBoundaryPath(path)) {
        continue;
      }
      String description = path.getDescription() == null ? "" : path.getDescription().strip();
      String expectedSource = path.getExpectedOutcomeHint();
      expectedSource = normalizeExpectedOutcomeForMethodBehavior(method, expectedSource);
      String expected = normalizeExpectedOutcomeForPostcondition(method, expectedSource, japanese);
      expected =
          normalizeExpectedOutcomeForUncertainDynamicMethod(
              expected, japanese, hasUnresolvedDynamicResolution, hasKnownMissingDynamicResolution);
      if (expected == null || expected.isBlank()) {
        continue;
      }
      description =
          normalizePathDescriptionForMethodBehavior(method, description, expected, japanese);
      final String pathLabel = buildFallbackPathLabel(path.getId(), description);
      if (pathLabel.isBlank()) {
        values.add(
            localized(
                japanese,
                "document.llm.fallback.path.postcondition.expected.ja",
                "document.llm.fallback.path.postcondition.expected.en",
                expected.strip()));
      } else {
        values.add(
            localized(
                japanese,
                "document.llm.fallback.path.postcondition.branch.ja",
                "document.llm.fallback.path.postcondition.branch.en",
                pathLabel,
                expected.strip()));
      }
    }
    for (final LlmMethodFlowFactsExtractor.SwitchCaseFact switchCaseFact :
        collectSwitchCaseFacts(method)) {
      if (isCoveredSwitchCaseFact(switchCaseFact, coveredSwitchCaseKeys)) {
        continue;
      }
      final String pathLabel =
          buildFallbackPathLabel(switchCaseFact.id(), switchCaseFact.description());
      String expected =
          normalizeExpectedOutcomeForPostcondition(
              method, switchCaseFact.expectedOutcome(), japanese);
      expected =
          normalizeExpectedOutcomeForUncertainDynamicMethod(
              expected, japanese, hasUnresolvedDynamicResolution, hasKnownMissingDynamicResolution);
      if (expected == null || expected.isBlank()) {
        continue;
      }
      values.add(
          localized(
              japanese,
              "document.llm.fallback.path.postcondition.branch.ja",
              "document.llm.fallback.path.postcondition.branch.en",
              pathLabel,
              expected.strip()));
    }
    if (values.isEmpty() && hasKnownMissingDynamicResolution) {
      values.add(
          localized(
              japanese,
              "document.llm.fallback.path.postcondition.method_missing.ja",
              "document.llm.fallback.path.postcondition.method_missing.en",
              formatMethodList(resolveKnownMissingMethodNames(facts, method))));
    }
    if (values.isEmpty() && hasUnresolvedDynamicResolution) {
      values.add(
          localized(
              japanese,
              "document.llm.fallback.path.postcondition.dynamic_pending.ja",
              "document.llm.fallback.path.postcondition.dynamic_pending.en"));
    }
    if (values.isEmpty()) {
      values.addAll(collectSourceBackedPostconditions(method, japanese));
    }
    if (values.isEmpty() && constructorSemantics.isTrivialEmptyConstructor(method)) {
      values.add(
          localized(
              japanese,
              "document.llm.fallback.path.postcondition.empty_constructor.ja",
              "document.llm.fallback.path.postcondition.empty_constructor.en"));
    }
    if (values.isEmpty() && !isVoidSignature(method.getSignature())) {
      final String signature = nullSafe(method.getSignature());
      if (!isUnavailable(signature)) {
        values.add(
            localized(
                japanese,
                "document.llm.fallback.path.postcondition.signature.ja",
                "document.llm.fallback.path.postcondition.signature.en",
                signature));
      }
    }
    return new ArrayList<>(values);
  }

  public List<String> collectFallbackNormalFlows(
      final MethodInfo method,
      final List<String> calledMethods,
      final boolean japanese,
      final LlmValidationFacts facts) {
    final LinkedHashSet<String> values = new LinkedHashSet<>();
    final boolean hasUnresolvedDynamicResolution =
        method != null && unresolvedDynamicResolutionChecker.test(method);
    final boolean hasKnownMissingDynamicResolution =
        method != null && knownMissingDynamicResolutionChecker.test(method);
    if (method != null) {
      for (final RepresentativePath path : method.getRepresentativePaths()) {
        if (containsUncertainDynamicReference(path, facts, method)) {
          continue;
        }
        if (containsKnownMissingDynamicReference(path, facts, method)) {
          continue;
        }
        if (isPositiveNonNullBoundaryPath(path)) {
          continue;
        }
        if (isLikelyErrorOrBoundaryPath(path)) {
          continue;
        }
        final String summary =
            summarizeRepresentativePath(
                path,
                method,
                japanese,
                hasUnresolvedDynamicResolution,
                hasKnownMissingDynamicResolution);
        if (!summary.isBlank()) {
          values.add(summary);
        }
      }
    }
    if (values.isEmpty() && hasKnownMissingDynamicResolution) {
      values.add(
          localized(
              japanese,
              "document.llm.fallback.path.normal.method_missing.ja",
              "document.llm.fallback.path.normal.method_missing.en",
              formatMethodList(resolveKnownMissingMethodNames(facts, method))));
    }
    if (values.isEmpty() && hasUnresolvedDynamicResolution) {
      values.add(
          localized(
              japanese,
              "document.llm.fallback.path.normal.dynamic_pending.ja",
              "document.llm.fallback.path.normal.dynamic_pending.en"));
    }
    if (values.isEmpty()) {
      values.addAll(collectSourceBackedNormalFlows(method, japanese));
    }
    if (values.isEmpty() && constructorSemantics.isTrivialEmptyConstructor(method)) {
      values.add(
          localized(
              japanese,
              "document.llm.fallback.path.normal.empty_constructor.ja",
              "document.llm.fallback.path.normal.empty_constructor.en"));
    }
    final List<String> safeCalledMethods =
        filterUncertainMethodReferences(calledMethods, facts, method);
    if (values.isEmpty() && !safeCalledMethods.isEmpty()) {
      final String preview =
          String.join(
              "`, `",
              safeCalledMethods.subList(
                  0, Math.min(DEPENDENCY_PREVIEW_LIMIT, safeCalledMethods.size())));
      values.add(
          localized(
              japanese,
              "document.llm.fallback.path.normal.dependency_preview.ja",
              "document.llm.fallback.path.normal.dependency_preview.en",
              preview));
    }
    if (values.isEmpty()) {
      values.add(defaultNormalFlowBySource(method, japanese));
    }
    return new ArrayList<>(values);
  }

  public List<String> collectFallbackErrorBoundaries(
      final MethodInfo method, final boolean japanese, final LlmValidationFacts facts) {
    final LinkedHashSet<String> values = new LinkedHashSet<>();
    if (method == null) {
      return List.of();
    }
    if (knownMissingDynamicResolutionChecker.test(method)) {
      values.add(
          localized(
              japanese,
              "document.llm.fallback.path.error.method_missing.ja",
              "document.llm.fallback.path.error.method_missing.en",
              formatMethodList(resolveKnownMissingMethodNames(facts, method))));
    }
    for (final String exception :
        PromptInputCanonicalizer.sortStrings(method.getThrownExceptions())) {
      values.add(
          localized(
              japanese,
              "document.llm.fallback.path.error.exception.ja",
              "document.llm.fallback.path.error.exception.en",
              exception));
    }
    for (final RepresentativePath path : method.getRepresentativePaths()) {
      if (path == null) {
        continue;
      }
      if (containsUncertainDynamicReference(path, facts, method)) {
        continue;
      }
      if (isPositiveNonNullBoundaryPath(path)) {
        continue;
      }
      if (!isLikelyErrorOrBoundaryPath(path)) {
        continue;
      }
      final String summary = summarizeRepresentativePath(path, method, japanese);
      if (!summary.isBlank()) {
        values.add(summary);
      }
    }
    if (values.isEmpty()) {
      values.add(defaultErrorBoundaryBySource(method, japanese));
    }
    return new ArrayList<>(values);
  }

  public List<String> collectFallbackTestViewpoints(
      final MethodInfo method, final boolean japanese, final LlmValidationFacts facts) {
    final LinkedHashSet<String> values = new LinkedHashSet<>();
    if (method == null) {
      return List.of();
    }
    final boolean hasUnresolvedDynamicResolution = unresolvedDynamicResolutionChecker.test(method);
    final boolean hasKnownMissingDynamicResolution =
        knownMissingDynamicResolutionChecker.test(method);
    final Set<String> coveredSwitchCaseKeys = collectCoveredSwitchCaseKeys(method);
    int preconditionCount = 0;
    for (final String precondition :
        fallbackPreconditionExtractor.collectFallbackPreconditions(method)) {
      values.add(
          localized(
              japanese,
              "document.llm.fallback.path.test.precondition.ja",
              "document.llm.fallback.path.test.precondition.en",
              precondition));
      preconditionCount++;
      if (preconditionCount >= PRECONDITION_VIEWPOINT_LIMIT) {
        break;
      }
    }
    for (final RepresentativePath path : method.getRepresentativePaths()) {
      if (path == null || path.getId() == null || path.getId().isBlank()) {
        continue;
      }
      if (containsUncertainDynamicReference(path, facts, method)) {
        continue;
      }
      if (isPositiveNonNullBoundaryPath(path)) {
        continue;
      }
      String description = path.getDescription() == null ? "" : path.getDescription().strip();
      String expected = path.getExpectedOutcomeHint();
      expected = normalizeExpectedOutcomeForMethodBehavior(method, expected);
      String normalizedExpected =
          expected == null || expected.isBlank()
              ? localized(
                  japanese,
                  "document.llm.fallback.path.test.expected_check.ja",
                  "document.llm.fallback.path.test.expected_check.en")
              : normalizeExpectedOutcomeForPostcondition(method, expected, japanese).strip();
      normalizedExpected =
          normalizeExpectedOutcomeForUncertainDynamicMethod(
                  normalizedExpected,
                  japanese,
                  hasUnresolvedDynamicResolution,
                  hasKnownMissingDynamicResolution)
              .strip();
      if (normalizedExpected.isBlank()) {
        normalizedExpected =
            localized(
                japanese,
                "document.llm.fallback.path.test.expected_check.ja",
                "document.llm.fallback.path.test.expected_check.en");
      }
      description =
          normalizePathDescriptionForMethodBehavior(
              method, description, normalizedExpected, japanese);
      final String pathLabel = buildFallbackPathLabel(path.getId(), description);
      if (pathLabel.isBlank()) {
        continue;
      }
      values.add(
          localized(
              japanese,
              "document.llm.fallback.path.test.branch.ja",
              "document.llm.fallback.path.test.branch.en",
              pathLabel,
              normalizedExpected));
    }
    for (final LlmMethodFlowFactsExtractor.SwitchCaseFact switchCaseFact :
        collectSwitchCaseFacts(method)) {
      if (isCoveredSwitchCaseFact(switchCaseFact, coveredSwitchCaseKeys)) {
        continue;
      }
      final String pathLabel =
          buildFallbackPathLabel(switchCaseFact.id(), switchCaseFact.description());
      if (pathLabel.isBlank()) {
        continue;
      }
      String normalizedExpected =
          normalizeExpectedOutcomeForPostcondition(
              method, switchCaseFact.expectedOutcome(), japanese);
      normalizedExpected =
          normalizeExpectedOutcomeForUncertainDynamicMethod(
                  normalizedExpected,
                  japanese,
                  hasUnresolvedDynamicResolution,
                  hasKnownMissingDynamicResolution)
              .strip();
      if (normalizedExpected.isBlank()) {
        normalizedExpected =
            localized(
                japanese,
                "document.llm.fallback.path.test.expected_check.ja",
                "document.llm.fallback.path.test.expected_check.en");
      }
      values.add(
          localized(
              japanese,
              "document.llm.fallback.path.test.branch.ja",
              "document.llm.fallback.path.test.branch.en",
              pathLabel,
              normalizedExpected));
    }
    if (hasKnownMissingDynamicResolution) {
      values.add(
          localized(
              japanese,
              "document.llm.fallback.path.test.method_missing.ja",
              "document.llm.fallback.path.test.method_missing.en",
              formatMethodList(resolveKnownMissingMethodNames(facts, method))));
    } else if (hasUnresolvedDynamicResolution) {
      values.add(
          localized(
              japanese,
              "document.llm.fallback.path.test.dynamic_pending.ja",
              "document.llm.fallback.path.test.dynamic_pending.en"));
    }
    int exceptionCount = 0;
    for (final String exception :
        PromptInputCanonicalizer.sortStrings(method.getThrownExceptions())) {
      values.add(
          localized(
              japanese,
              "document.llm.fallback.path.test.exception.ja",
              "document.llm.fallback.path.test.exception.en",
              exception));
      exceptionCount++;
      if (exceptionCount >= EXCEPTION_VIEWPOINT_LIMIT) {
        break;
      }
    }
    if (values.isEmpty()) {
      values.addAll(collectSourceBackedTestViewpoints(method, japanese));
    }
    if (values.isEmpty() && constructorSemantics.isTrivialEmptyConstructor(method)) {
      values.add(
          localized(
              japanese,
              "document.llm.fallback.path.test.empty_constructor.ja",
              "document.llm.fallback.path.test.empty_constructor.en"));
    }
    return new ArrayList<>(values);
  }

  private List<String> collectSourceBackedPostconditions(
      final MethodInfo method, final boolean japanese) {
    final SourceBehaviorFacts facts = extractSourceBehaviorFacts(method);
    if (facts.isEmpty()) {
      return List.of();
    }
    final LinkedHashSet<String> values = new LinkedHashSet<>();
    for (final String assignment : facts.assignments()) {
      values.add(
          localized(
              japanese,
              "document.llm.fallback.path.postcondition.assignment.ja",
              "document.llm.fallback.path.postcondition.assignment.en",
              assignment));
    }
    if (values.isEmpty() && facts.hasReturnExpression()) {
      values.add(
          localized(
              japanese,
              "document.llm.fallback.path.postcondition.return_expression.ja",
              "document.llm.fallback.path.postcondition.return_expression.en",
              facts.returnExpression()));
    }
    return new ArrayList<>(values);
  }

  private List<String> collectSourceBackedNormalFlows(
      final MethodInfo method, final boolean japanese) {
    final SourceBehaviorFacts facts = extractSourceBehaviorFacts(method);
    if (facts.isEmpty()) {
      return List.of();
    }
    if (!facts.assignments().isEmpty()) {
      final String joinedAssignments = String.join("`, `", facts.assignments());
      return List.of(
          localized(
              japanese,
              "document.llm.fallback.path.normal.assignments.ja",
              "document.llm.fallback.path.normal.assignments.en",
              joinedAssignments));
    }
    if (facts.hasReturnExpression()) {
      return List.of(
          localized(
              japanese,
              "document.llm.fallback.path.normal.return_expression.ja",
              "document.llm.fallback.path.normal.return_expression.en",
              facts.returnExpression()));
    }
    return List.of();
  }

  private List<String> collectSourceBackedTestViewpoints(
      final MethodInfo method, final boolean japanese) {
    final SourceBehaviorFacts facts = extractSourceBehaviorFacts(method);
    if (facts.isEmpty()) {
      return List.of();
    }
    final LinkedHashSet<String> values = new LinkedHashSet<>();
    for (final String assignment : facts.assignments()) {
      values.add(
          localized(
              japanese,
              "document.llm.fallback.path.test.assignment.ja",
              "document.llm.fallback.path.test.assignment.en",
              assignment));
      if (values.size() >= PRECONDITION_VIEWPOINT_LIMIT) {
        break;
      }
    }
    if (values.isEmpty() && facts.hasReturnExpression()) {
      values.add(
          localized(
              japanese,
              "document.llm.fallback.path.test.return_expression.ja",
              "document.llm.fallback.path.test.return_expression.en",
              facts.returnExpression()));
    }
    return new ArrayList<>(values);
  }

  private SourceBehaviorFacts extractSourceBehaviorFacts(final MethodInfo method) {
    if (method == null) {
      return SourceBehaviorFacts.empty();
    }
    final String body = extractMethodBody(method.getSourceCode());
    if (body.isBlank()) {
      return SourceBehaviorFacts.empty();
    }
    if (CONTROL_FLOW_KEYWORD_PATTERN.matcher(body).find()) {
      return SourceBehaviorFacts.empty();
    }
    final Set<String> parameterNames = extractParameterNames(method);
    final boolean stateMutator = isLikelyStateMutator(method);
    final List<String> assignments = new ArrayList<>();
    String returnExpression = "";
    int statementCount = 0;
    for (final String rawStatement : body.split(";")) {
      final String statement = rawStatement == null ? "" : rawStatement.strip();
      if (statement.isBlank()) {
        continue;
      }
      statementCount++;
      if (statementCount > SOURCE_STATEMENT_LIMIT) {
        return SourceBehaviorFacts.empty();
      }
      if (isIgnorableSourceStatement(statement)) {
        continue;
      }
      final Matcher returnMatcher = RETURN_STATEMENT_PATTERN.matcher(statement);
      if (returnMatcher.matches()) {
        final String candidate = normalizeInlineExpression(returnMatcher.group(1));
        if (candidate.isBlank()) {
          continue;
        }
        if (!returnExpression.isBlank()) {
          return SourceBehaviorFacts.empty();
        }
        returnExpression = candidate;
        continue;
      }
      final Matcher assignmentMatcher = SIMPLE_ASSIGNMENT_PATTERN.matcher(statement);
      if (!assignmentMatcher.matches()) {
        return SourceBehaviorFacts.empty();
      }
      final boolean explicitThis = assignmentMatcher.group(1) != null;
      final String left = assignmentMatcher.group(2);
      final String right = normalizeInlineExpression(assignmentMatcher.group(3));
      if (left == null || left.isBlank() || right.isBlank()) {
        return SourceBehaviorFacts.empty();
      }
      if (!explicitThis && !(stateMutator && referencesAnyParameter(right, parameterNames))) {
        return SourceBehaviorFacts.empty();
      }
      final String assignment = (explicitThis ? "this." : "") + left + " = " + right;
      if (assignments.size() < SOURCE_ASSIGNMENT_LIMIT) {
        assignments.add(assignment);
      }
    }
    if (assignments.isEmpty() && returnExpression.isBlank()) {
      return SourceBehaviorFacts.empty();
    }
    return new SourceBehaviorFacts(List.copyOf(assignments), returnExpression);
  }

  private String extractMethodBody(final String sourceCode) {
    final String normalized = DocumentUtils.stripCommentedRegions(sourceCode);
    if (normalized == null || normalized.isBlank()) {
      return "";
    }
    final int openBrace = normalized.indexOf('{');
    final int closeBrace = normalized.lastIndexOf('}');
    if (openBrace >= 0 && closeBrace > openBrace) {
      return normalized.substring(openBrace + 1, closeBrace).strip();
    }
    return normalized.strip();
  }

  private boolean isIgnorableSourceStatement(final String statement) {
    if (statement == null || statement.isBlank()) {
      return true;
    }
    final String normalized = LlmDocumentTextUtils.normalizeLine(statement);
    return normalized.startsWith("objects.requirenonnull(")
        || normalized.startsWith("preconditions.checknotnull(")
        || normalized.startsWith("preconditions.checkargument(")
        || normalized.startsWith("assert ")
        || normalized.startsWith("super(")
        || normalized.startsWith("this(");
  }

  private boolean isLikelyStateMutator(final MethodInfo method) {
    if (method == null) {
      return false;
    }
    final String name = method.getName();
    if (name == null || name.isBlank()) {
      return false;
    }
    final String normalized = name.strip().toLowerCase(Locale.ROOT);
    if (normalized.startsWith("set")
        || normalized.startsWith("add")
        || normalized.startsWith("update")
        || normalized.startsWith("init")
        || normalized.startsWith("with")
        || normalized.startsWith("put")) {
      return true;
    }
    return isConstructorLikeName(name);
  }

  private boolean isConstructorLikeName(final String methodName) {
    if (methodName == null || methodName.isBlank()) {
      return false;
    }
    return Character.isUpperCase(methodName.strip().charAt(0));
  }

  private Set<String> extractParameterNames(final MethodInfo method) {
    final LinkedHashSet<String> names = new LinkedHashSet<>();
    if (method == null) {
      return names;
    }
    collectParameterNames(
        extractParameterSectionFromDeclaration(
            DocumentUtils.stripCommentedRegions(method.getSourceCode())),
        names);
    if (!names.isEmpty()) {
      return names;
    }
    collectParameterNames(extractParameterSectionFromDeclaration(method.getSignature()), names);
    return names;
  }

  private String extractParameterSectionFromDeclaration(final String declaration) {
    if (declaration == null || declaration.isBlank()) {
      return "";
    }
    final int headerEnd = declaration.indexOf('{');
    final String header = headerEnd >= 0 ? declaration.substring(0, headerEnd) : declaration;
    final int openParen = header.indexOf('(');
    final int closeParen = header.lastIndexOf(')');
    if (openParen < 0 || closeParen <= openParen) {
      return "";
    }
    return header.substring(openParen + 1, closeParen);
  }

  private void collectParameterNames(
      final String parameterSection, final Set<String> parameterNames) {
    if (parameterSection == null || parameterSection.isBlank() || parameterNames == null) {
      return;
    }
    for (final String token : LlmDocumentTextUtils.splitTopLevelCsv(parameterSection)) {
      final String parameterName = extractParameterName(token);
      if (!parameterName.isBlank()) {
        parameterNames.add(parameterName);
      }
    }
  }

  private String extractParameterName(final String parameterToken) {
    if (parameterToken == null || parameterToken.isBlank()) {
      return "";
    }
    final String normalized =
        parameterToken
            .replace("...", " ")
            .replace("[]", " ")
            .replaceAll("@[A-Za-z_$][A-Za-z0-9_$.]*(?:\\s*\\([^)]*\\))?", " ")
            .replaceAll("\\bfinal\\b", " ")
            .strip();
    if (normalized.isBlank()) {
      return "";
    }
    final String[] parts = normalized.split("\\s+");
    if (parts.length == 0) {
      return "";
    }
    final String candidate = parts[parts.length - 1].replaceAll("[^A-Za-z0-9_$]", "");
    if (!isLikelyParameterName(candidate)) {
      return "";
    }
    return candidate;
  }

  private boolean isLikelyParameterName(final String token) {
    if (token == null || token.isBlank()) {
      return false;
    }
    if (!token.matches("[A-Za-z_$][A-Za-z0-9_$]*")) {
      return false;
    }
    final String normalized = token.toLowerCase(Locale.ROOT);
    if (NON_PARAMETER_TOKENS.contains(normalized)) {
      return false;
    }
    return !token.contains(".");
  }

  private boolean referencesAnyParameter(
      final String expression, final Set<String> parameterNames) {
    if (expression == null
        || expression.isBlank()
        || parameterNames == null
        || parameterNames.isEmpty()) {
      return false;
    }
    for (final String parameterName : parameterNames) {
      if (parameterName == null || parameterName.isBlank()) {
        continue;
      }
      final Pattern tokenPattern =
          Pattern.compile("\\b" + Pattern.quote(parameterName) + "\\b", Pattern.CASE_INSENSITIVE);
      if (tokenPattern.matcher(expression).find()) {
        return true;
      }
    }
    return false;
  }

  private String normalizeInlineExpression(final String expression) {
    if (expression == null || expression.isBlank()) {
      return "";
    }
    return expression.strip().replaceAll("\\s+", " ");
  }

  private record SourceBehaviorFacts(List<String> assignments, String returnExpression) {

    private static SourceBehaviorFacts empty() {
      return new SourceBehaviorFacts(List.of(), "");
    }

    private boolean hasReturnExpression() {
      return returnExpression != null && !returnExpression.isBlank();
    }

    private boolean isEmpty() {
      return (assignments == null || assignments.isEmpty()) && !hasReturnExpression();
    }
  }

  private List<String> filterUncertainMethodReferences(
      final List<String> references, final LlmValidationFacts facts, final MethodInfo ownerMethod) {
    if (references == null || references.isEmpty()) {
      return List.of();
    }
    final List<String> filtered = new ArrayList<>();
    for (final String reference : references) {
      if (reference == null || reference.isBlank()) {
        continue;
      }
      if (containsUncertainDynamicReference(reference, facts, ownerMethod)) {
        continue;
      }
      filtered.add(reference);
    }
    return filtered;
  }

  private List<LlmMethodFlowFactsExtractor.SwitchCaseFact> collectSwitchCaseFacts(
      final MethodInfo method) {
    if (method == null) {
      return List.of();
    }
    final List<LlmMethodFlowFactsExtractor.SwitchCaseFact> facts =
        switchCaseFactsCollector.apply(method);
    return facts == null ? List.of() : facts;
  }

  private boolean containsUncertainDynamicReference(
      final RepresentativePath path, final LlmValidationFacts facts, final MethodInfo ownerMethod) {
    if (path == null) {
      return false;
    }
    if (containsUncertainDynamicReference(path.getDescription(), facts, ownerMethod)
        || containsUncertainDynamicReference(path.getExpectedOutcomeHint(), facts, ownerMethod)) {
      return true;
    }
    final List<String> requiredConditions = path.getRequiredConditions();
    if (requiredConditions == null || requiredConditions.isEmpty()) {
      return false;
    }
    for (final String condition : requiredConditions) {
      if (containsUncertainDynamicReference(condition, facts, ownerMethod)) {
        return true;
      }
    }
    return false;
  }

  private boolean containsKnownMissingDynamicReference(
      final RepresentativePath path, final LlmValidationFacts facts, final MethodInfo ownerMethod) {
    if (path == null) {
      return false;
    }
    if (containsKnownMissingDynamicReference(path.getDescription(), facts, ownerMethod)
        || containsKnownMissingDynamicReference(
            path.getExpectedOutcomeHint(), facts, ownerMethod)) {
      return true;
    }
    final List<String> requiredConditions = path.getRequiredConditions();
    if (requiredConditions == null || requiredConditions.isEmpty()) {
      return false;
    }
    for (final String condition : requiredConditions) {
      if (containsKnownMissingDynamicReference(condition, facts, ownerMethod)) {
        return true;
      }
    }
    return false;
  }

  private boolean containsUncertainDynamicReference(
      final String text, final LlmValidationFacts facts, final MethodInfo ownerMethod) {
    if (text == null || text.isBlank() || facts == null) {
      return false;
    }
    final Set<String> uncertainMethodNames = resolveUncertainMethodNames(facts, ownerMethod);
    if (uncertainMethodNames.isEmpty()) {
      return false;
    }
    for (final String uncertainMethod : uncertainMethodNames) {
      if (uncertainMethod == null || uncertainMethod.isBlank()) {
        continue;
      }
      if (LlmDocumentTextUtils.containsMethodToken(text, uncertainMethod)) {
        return true;
      }
    }
    return false;
  }

  private boolean containsKnownMissingDynamicReference(
      final String text, final LlmValidationFacts facts, final MethodInfo ownerMethod) {
    if (text == null || text.isBlank() || facts == null) {
      return false;
    }
    final Set<String> knownMissingMethodNames = resolveKnownMissingMethodNames(facts, ownerMethod);
    if (knownMissingMethodNames.isEmpty()) {
      return false;
    }
    for (final String knownMissingMethod : knownMissingMethodNames) {
      if (knownMissingMethod == null || knownMissingMethod.isBlank()) {
        continue;
      }
      if (LlmDocumentTextUtils.containsMethodToken(text, knownMissingMethod)) {
        return true;
      }
    }
    return false;
  }

  private Set<String> resolveUncertainMethodNames(
      final LlmValidationFacts facts, final MethodInfo ownerMethod) {
    if (facts == null) {
      return Set.of();
    }
    final String ownerMethodName =
        ownerMethod == null
            ? ""
            : LlmDocumentTextUtils.normalizeMethodName(
                methodDisplayNameResolver.apply(ownerMethod));
    return facts.uncertainDynamicMethodNamesFor(ownerMethodName);
  }

  private Set<String> resolveKnownMissingMethodNames(
      final LlmValidationFacts facts, final MethodInfo ownerMethod) {
    if (facts == null) {
      return Set.of();
    }
    final String ownerMethodName =
        ownerMethod == null
            ? ""
            : LlmDocumentTextUtils.normalizeMethodName(
                methodDisplayNameResolver.apply(ownerMethod));
    return facts.knownMissingDynamicMethodNamesFor(ownerMethodName);
  }

  private String formatMethodList(final Set<String> methodNames) {
    if (methodNames == null || methodNames.isEmpty()) {
      return msg("document.value.na");
    }
    final List<String> sorted = PromptInputCanonicalizer.sortStrings(methodNames);
    return String.join("`, `", sorted);
  }

  private String summarizeRepresentativePath(
      final RepresentativePath path, final MethodInfo method, final boolean japanese) {
    return summarizeRepresentativePath(
        path,
        method,
        japanese,
        unresolvedDynamicResolutionChecker.test(method),
        knownMissingDynamicResolutionChecker.test(method));
  }

  private String summarizeRepresentativePath(
      final RepresentativePath path,
      final MethodInfo method,
      final boolean japanese,
      final boolean hasUnresolvedDynamicResolution,
      final boolean hasKnownMissingDynamicResolution) {
    if (path == null) {
      return "";
    }
    final String id = path.getId() == null ? "" : path.getId().strip();
    String description = path.getDescription() == null ? "" : path.getDescription().strip();
    String expectedSource = path.getExpectedOutcomeHint();
    expectedSource = normalizeExpectedOutcomeForMethodBehavior(method, expectedSource);
    String expected =
        expectedSource == null
            ? ""
            : normalizeExpectedOutcomeForPostcondition(method, expectedSource, japanese).strip();
    expected =
        normalizeExpectedOutcomeForUncertainDynamicMethod(
                expected,
                japanese,
                hasUnresolvedDynamicResolution,
                hasKnownMissingDynamicResolution)
            .strip();
    description =
        normalizePathDescriptionForMethodBehavior(method, description, expected, japanese);
    if (id.isBlank() && description.isBlank() && expected.isBlank()) {
      return "";
    }
    final StringBuilder sb = new StringBuilder();
    if (!id.isBlank()) {
      sb.append("[").append(id).append("] ");
    }
    if (!description.isBlank()) {
      sb.append(description);
    }
    if (!expected.isBlank()) {
      if (sb.length() > 0) {
        sb.append(" -> ");
      }
      sb.append(expected);
    }
    return sb.toString().strip();
  }

  private String buildFallbackPathLabel(final String pathId, final String pathDescription) {
    final String id = pathId == null ? "" : pathId.strip();
    final String description = pathDescription == null ? "" : pathDescription.strip();
    if (!id.isBlank() && !description.isBlank()) {
      return id + ": " + description;
    }
    if (!id.isBlank()) {
      return id;
    }
    return description;
  }

  private String normalizeExpectedOutcomeForPostcondition(
      final MethodInfo method, final String expectedOutcome, final boolean japanese) {
    if (expectedOutcome == null || expectedOutcome.isBlank()) {
      return expectedOutcome;
    }
    if (!"success".equalsIgnoreCase(expectedOutcome.strip())) {
      return expectedOutcome;
    }
    if (!failureFactoryMethodNameChecker.test(methodDisplayNameResolver.apply(method))) {
      return expectedOutcome;
    }
    return localized(
        japanese,
        "document.llm.fallback.path.expected.failure_result.ja",
        "document.llm.fallback.path.expected.failure_result.en");
  }

  private String normalizeExpectedOutcomeForUncertainDynamicMethod(
      final String expectedOutcome,
      final boolean japanese,
      final boolean hasUnresolvedDynamicResolution,
      final boolean hasKnownMissingDynamicResolution) {
    if ((!hasUnresolvedDynamicResolution && !hasKnownMissingDynamicResolution)
        || expectedOutcome == null
        || expectedOutcome.isBlank()) {
      return expectedOutcome;
    }
    final String normalized = LlmDocumentTextUtils.normalizeLine(expectedOutcome);
    if (!normalized.contains("success") && !normalized.contains("成功")) {
      return expectedOutcome;
    }
    if (hasKnownMissingDynamicResolution) {
      return localized(
          japanese,
          "document.llm.fallback.path.expected.method_missing.ja",
          "document.llm.fallback.path.expected.method_missing.en");
    }
    return localized(
        japanese,
        "document.llm.fallback.path.expected.dynamic_pending.ja",
        "document.llm.fallback.path.expected.dynamic_pending.en");
  }

  private boolean isLikelyErrorOrBoundaryPath(final RepresentativePath path) {
    if (path == null) {
      return false;
    }
    return errorIndicatorChecker.test(path.getExpectedOutcomeHint())
        || errorIndicatorChecker.test(path.getDescription());
  }

  private boolean isPositiveNonNullBoundaryPath(final RepresentativePath path) {
    if (path == null) {
      return false;
    }
    final String description = path.getDescription() == null ? "" : path.getDescription().strip();
    final String expected =
        path.getExpectedOutcomeHint() == null ? "" : path.getExpectedOutcomeHint().strip();
    final String required =
        String.join(
            " ", path.getRequiredConditions() == null ? List.of() : path.getRequiredConditions());
    final String combined =
        (description + " " + expected + " " + required).toLowerCase(Locale.ROOT);
    final boolean boundaryLike = combined.contains("boundary") || combined.contains("境界");
    if (!boundaryLike) {
      return false;
    }
    final boolean nonNullPositive =
        combined.contains("!= null")
            || combined.contains("!=null")
            || combined.contains("nullでない")
            || combined.contains("null でない")
            || combined.contains("nullではない")
            || combined.contains("null ではない");
    if (!nonNullPositive) {
      return false;
    }
    return !combined.contains("== null")
        && !combined.contains("==null")
        && !combined.contains("is null")
        && !combined.contains("cannot be null")
        && !combined.contains("must not be null")
        && !combined.contains("nullの場合")
        && !combined.contains("null の場合");
  }

  private String normalizePathDescriptionForMethodBehavior(
      final MethodInfo method,
      final String description,
      final String expectedOutcome,
      final boolean japanese) {
    if (description == null || description.isBlank()) {
      return description;
    }
    final String normalizedDescription = LlmDocumentTextUtils.normalizeLine(description);
    if (normalizedDescription.startsWith("main success path")
        || normalizedDescription.startsWith("main return path")) {
      return localized(
          japanese,
          "document.llm.fallback.path.main_return.ja",
          "document.llm.fallback.path.main_return.en");
    }
    if (method == null) {
      return description;
    }
    if (!failureFactoryMethodNameChecker.test(methodDisplayNameResolver.apply(method))) {
      return description;
    }
    final String normalizedExpected = LlmDocumentTextUtils.normalizeLine(expectedOutcome);
    if (!normalizedDescription.startsWith("main path")) {
      return description;
    }
    if (normalizedExpected.contains("success")) {
      return description;
    }
    return localized(
        japanese,
        "document.llm.fallback.path.main_return.ja",
        "document.llm.fallback.path.main_return.en");
  }

  private String normalizeExpectedOutcomeForMethodBehavior(
      final MethodInfo method, final String expectedOutcome) {
    if (expectedOutcome == null || expectedOutcome.isBlank() || method == null) {
      return expectedOutcome;
    }
    final String normalized = LlmDocumentTextUtils.normalizeLine(expectedOutcome);
    if (!normalized.contains("early-return") && !normalized.contains("early return")) {
      return expectedOutcome;
    }
    if (!earlyReturnIncompatibleChecker.test(method)) {
      return expectedOutcome;
    }
    return "failure";
  }

  private Set<String> collectCoveredSwitchCaseKeys(final MethodInfo method) {
    final LinkedHashSet<String> keys = new LinkedHashSet<>();
    if (method == null) {
      return keys;
    }
    for (final RepresentativePath path : method.getRepresentativePaths()) {
      if (path == null) {
        continue;
      }
      final String key = extractSwitchCaseKey(path.getDescription(), path.getExpectedOutcomeHint());
      if (!key.isBlank()) {
        keys.add(key);
      }
    }
    return keys;
  }

  private boolean isCoveredSwitchCaseFact(
      final LlmMethodFlowFactsExtractor.SwitchCaseFact switchCaseFact,
      final Set<String> coveredSwitchCaseKeys) {
    if (switchCaseFact == null
        || coveredSwitchCaseKeys == null
        || coveredSwitchCaseKeys.isEmpty()) {
      return false;
    }
    final String key =
        extractSwitchCaseKey(switchCaseFact.description(), switchCaseFact.expectedOutcome());
    if (key.isBlank()) {
      return false;
    }
    if (coveredSwitchCaseKeys.contains(key)) {
      return true;
    }
    final String labelWildcard = extractSwitchCaseLabelWildcard(key);
    if (labelWildcard.isBlank()) {
      return false;
    }
    if (coveredSwitchCaseKeys.contains(labelWildcard)) {
      return true;
    }
    if (key.startsWith("::")) {
      for (final String coveredKey : coveredSwitchCaseKeys) {
        if (coveredKey.endsWith(labelWildcard)) {
          return true;
        }
      }
    }
    return false;
  }

  private String extractSwitchCaseKey(final String description, final String expectedOutcome) {
    final String byDescription = extractSwitchCaseKeyFromDescription(description);
    if (!byDescription.isBlank()) {
      return byDescription;
    }
    return extractSwitchCaseKeyFromExpectedOutcome(expectedOutcome);
  }

  private String extractSwitchCaseKeyFromDescription(final String description) {
    if (description == null || description.isBlank()) {
      return "";
    }
    final String raw = description.strip().replace("`", "");
    final String normalized = raw.toLowerCase(Locale.ROOT);
    if (normalized.startsWith("switch default ")) {
      final String expression =
          normalizeSwitchExpression(raw.substring("switch default ".length()));
      if (expression.isBlank()) {
        return "";
      }
      return expression + "::default";
    }
    if (!normalized.startsWith("switch case ")) {
      return "";
    }
    final String body = raw.substring("switch case ".length()).strip();
    final int eqIndex = body.lastIndexOf('=');
    if (eqIndex < 0 || eqIndex + 1 >= body.length()) {
      return "";
    }
    final String expression = normalizeSwitchExpression(body.substring(0, eqIndex));
    final String label = normalizeSwitchCaseLabel(body.substring(eqIndex + 1));
    if (expression.isBlank() || label.isBlank()) {
      return "";
    }
    return expression + "::" + label;
  }

  private String extractSwitchCaseKeyFromExpectedOutcome(final String expectedOutcome) {
    if (expectedOutcome == null || expectedOutcome.isBlank()) {
      return "";
    }
    final String normalized = LlmDocumentTextUtils.normalizeLine(expectedOutcome);
    if ("default".equals(normalized) || "case-default".equals(normalized)) {
      return "::default";
    }
    if (!normalized.startsWith("case-")) {
      return "";
    }
    final String label = normalizeSwitchCaseLabel(normalized.substring("case-".length()));
    if (label.isBlank()) {
      return "";
    }
    return "::" + label;
  }

  private String extractSwitchCaseLabelWildcard(final String key) {
    if (key == null || key.isBlank()) {
      return "";
    }
    final int delimiter = key.indexOf("::");
    if (delimiter < 0 || delimiter + 2 >= key.length()) {
      return "";
    }
    final String label = key.substring(delimiter + 2);
    if (label.isBlank()) {
      return "";
    }
    return "::" + label;
  }

  private String normalizeSwitchExpression(final String expression) {
    if (expression == null || expression.isBlank()) {
      return "";
    }
    return expression.strip().replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
  }

  private String normalizeSwitchCaseLabel(final String rawLabel) {
    if (rawLabel == null || rawLabel.isBlank()) {
      return "";
    }
    String label = rawLabel.strip().replace("`", "");
    if (label.startsWith("\"") && label.endsWith("\"") && label.length() >= 2) {
      label = label.substring(1, label.length() - 1);
    } else if (label.startsWith("'") && label.endsWith("'") && label.length() >= 2) {
      label = label.substring(1, label.length() - 1);
    }
    label = label.strip().toLowerCase(Locale.ROOT);
    if ("case-default".equals(label)) {
      return "default";
    }
    return label;
  }

  private boolean isVoidSignature(final String signature) {
    if (signature == null || signature.isBlank()) {
      return false;
    }
    final String normalized = signature.replace("`", "").strip();
    return normalized.matches(".*\\bvoid\\b.*");
  }

  private boolean isUnavailable(final String value) {
    if (value == null || value.isBlank()) {
      return true;
    }
    return unavailableValue.equals(value.strip());
  }

  private String nullSafe(final String value) {
    return value == null ? "" : value;
  }

  private String defaultNormalFlowBySource(final MethodInfo method, final boolean japanese) {
    if (constructorSemantics.isTrivialEmptyConstructor(method)) {
      return localized(
          japanese,
          "document.llm.fallback.path.normal.empty_constructor.ja",
          "document.llm.fallback.path.normal.empty_constructor.en");
    }
    if (hasReturnStatement(method)) {
      return japanese
          ? "ソースコードに記載された処理を実行し、`return`文で結果を返す。"
          : "Execute source-defined logic and return results according to `return` statements.";
    }
    return japanese ? "ソースコードに記載された処理を順に実行する。" : "Execute source-defined logic in order.";
  }

  private String defaultErrorBoundaryBySource(final MethodInfo method, final boolean japanese) {
    if (hasExplicitThrowStatement(method)) {
      return japanese
          ? "ソースコード上で例外が送出される経路を検証する。"
          : "Verify paths where exceptions are explicitly thrown in source.";
    }
    return japanese
        ? "ソースコード上に明示的な例外送出・境界分岐は確認されない。"
        : "No explicit exception throw or boundary-only branch is observed in source.";
  }

  private boolean hasReturnStatement(final MethodInfo method) {
    if (method == null || method.getSourceCode() == null || method.getSourceCode().isBlank()) {
      return false;
    }
    return method.getSourceCode().contains("return");
  }

  private boolean hasExplicitThrowStatement(final MethodInfo method) {
    if (method == null || method.getSourceCode() == null || method.getSourceCode().isBlank()) {
      return false;
    }
    return method.getSourceCode().contains("throw");
  }

  private String localized(
      final boolean japanese, final String jaKey, final String enKey, final Object... args) {
    return msg(japanese ? jaKey : enKey, args);
  }

  private String msg(final String key, final Object... args) {
    return MessageSource.getMessage(key, args);
  }
}
