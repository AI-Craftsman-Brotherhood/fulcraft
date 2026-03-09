package com.craftsmanbro.fulcraft.plugins.document.core.llm.analysis;

import com.craftsmanbro.fulcraft.plugins.analysis.model.BranchSummary;
import com.craftsmanbro.fulcraft.plugins.analysis.model.GuardSummary;
import com.craftsmanbro.fulcraft.plugins.analysis.model.GuardType;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.RepresentativePath;
import com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmDocumentTextUtils;
import com.craftsmanbro.fulcraft.plugins.document.core.util.DocumentUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts and normalizes source-backed fallback preconditions for method specifications. */
public final class LlmFallbackPreconditionExtractor {

  private static final String PRECONDITION_CHECK_NOT_NULL_CALL = "Preconditions.checkNotNull(";

  private static final String PRECONDITION_CHECK_ARGUMENT_CALL = "Preconditions.checkArgument(";

  private static final String PRECONDITION_REQUIRE_NON_NULL_CALL = "Objects.requireNonNull(";

  private static final Pattern NULL_OR_EMPTY_CONDITION_PATTERN =
      Pattern.compile(
          "^(.+?)\\s*==\\s*null\\s*\\|\\|\\s*\\1\\s*\\.\\s*(isEmpty|isBlank)\\s*\\(\\s*\\)\\s*$");

  private static final Pattern EMPTY_OR_NULL_CONDITION_PATTERN =
      Pattern.compile(
          "^(.+?)\\s*\\.\\s*(isEmpty|isBlank)\\s*\\(\\s*\\)\\s*\\|\\|\\s*\\1\\s*==\\s*null\\s*$");

  private static final Pattern EQUALS_NULL_CONDITION_PATTERN =
      Pattern.compile("^(.+?)\\s*==\\s*null\\s*$");

  private static final Pattern NOT_NULL_CONDITION_PATTERN =
      Pattern.compile("^(.+?)\\s*!=\\s*null\\s*$");

  private static final Pattern LESS_OR_EQUAL_ZERO_CONDITION_PATTERN =
      Pattern.compile("^(.+?)\\s*<=\\s*0(?:\\.0+)?\\s*$");

  private static final Pattern LESS_THAN_ZERO_CONDITION_PATTERN =
      Pattern.compile("^(.+?)\\s*<\\s*0(?:\\.0+)?\\s*$");

  private static final Pattern NULL_OR_COMPARE_TO_NON_POSITIVE_CONDITION_PATTERN =
      Pattern.compile(
          "^(.+?)\\s*==\\s*null\\s*\\|\\|\\s*\\1\\s*\\.\\s*compareTo\\s*\\((.+?)\\)\\s*<=\\s*0(?:\\.0+)?\\s*$");

  private static final Pattern COMPARE_TO_NON_POSITIVE_OR_NULL_CONDITION_PATTERN =
      Pattern.compile(
          "^(.+?)\\s*\\.\\s*compareTo\\s*\\((.+?)\\)\\s*<=\\s*0(?:\\.0+)?\\s*\\|\\|\\s*\\1\\s*==\\s*null\\s*$");

  private static final Pattern NULL_OR_COMPARE_TO_NEGATIVE_CONDITION_PATTERN =
      Pattern.compile(
          "^(.+?)\\s*==\\s*null\\s*\\|\\|\\s*\\1\\s*\\.\\s*compareTo\\s*\\((.+?)\\)\\s*<\\s*0(?:\\.0+)?\\s*$");

  private static final Pattern COMPARE_TO_NEGATIVE_OR_NULL_CONDITION_PATTERN =
      Pattern.compile(
          "^(.+?)\\s*\\.\\s*compareTo\\s*\\((.+?)\\)\\s*<\\s*0(?:\\.0+)?\\s*\\|\\|\\s*\\1\\s*==\\s*null\\s*$");

  private static final Pattern NEGATED_EMPTY_CHECK_CONDITION_PATTERN =
      Pattern.compile("^!\\s*(.+?)\\s*\\.\\s*(isEmpty|isBlank)\\s*\\(\\s*\\)\\s*$");

  private static final Pattern EMPTY_CHECK_CONDITION_PATTERN =
      Pattern.compile("^(.+?)\\s*\\.\\s*(isEmpty|isBlank)\\s*\\(\\s*\\)\\s*$");

  private static final Pattern NEGATED_PREDICATE_CALL_PATTERN =
      Pattern.compile("^!\\s*([A-Za-z_][A-Za-z0-9_.$]*)\\s*\\((.*)\\)\\s*$");

  private static final Pattern COMPARE_TO_CONDITION_PATTERN =
      Pattern.compile("^(.+?)\\s*\\.\\s*compareTo\\s*\\((.+?)\\)\\s*([<>]=?)\\s*0(?:\\.0+)?\\s*$");

  private final Predicate<String> flowConditionChecker;

  private final Predicate<String> errorIndicatorChecker;

  public LlmFallbackPreconditionExtractor(
      final Predicate<String> flowConditionChecker, final Predicate<String> errorIndicatorChecker) {
    this.flowConditionChecker =
        Objects.requireNonNull(
            flowConditionChecker,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "document.common.error.argument_null", "flowConditionChecker must not be null"));
    this.errorIndicatorChecker =
        Objects.requireNonNull(
            errorIndicatorChecker,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "document.common.error.argument_null", "errorIndicatorChecker must not be null"));
  }

  public boolean containsUnsupportedNoArgPrecondition(
      final String section, final MethodInfo method) {
    if (section == null || section.isBlank() || method == null) {
      return false;
    }
    if (!extractMethodParameterNames(method).isEmpty()) {
      return false;
    }
    if (!collectFallbackPreconditions(method).isEmpty()) {
      return false;
    }
    for (final String rawLine : section.split("\\R")) {
      final String normalized =
          LlmDocumentTextUtils.normalizeLine(rawLine).replaceFirst("^-\\s*", "");
      if (normalized.isBlank() || LlmDocumentTextUtils.isNoneMarker(normalized)) {
        continue;
      }
      return true;
    }
    return false;
  }

  public boolean containsUnsupportedNonNullPreconditionAssumption(
      final String section, final MethodInfo method) {
    if (section == null || section.isBlank() || method == null) {
      return false;
    }
    final Set<String> parameterNames = extractMethodParameterNames(method);
    if (parameterNames.isEmpty()) {
      return false;
    }
    final Set<String> supportedNonNullParameters =
        collectSupportedNonNullParameters(method, parameterNames);
    final String sourceCode = DocumentUtils.stripCommentedRegions(method.getSourceCode());
    final boolean sourceAvailable = sourceCode != null && !sourceCode.isBlank();
    if (!sourceAvailable && supportedNonNullParameters.isEmpty()) {
      return false;
    }
    for (final String rawLine : section.split("\\R")) {
      final String assertedParameter = extractAssertedNonNullParameter(rawLine, parameterNames);
      if (assertedParameter.isBlank()) {
        continue;
      }
      if (!supportedNonNullParameters.contains(assertedParameter)) {
        return true;
      }
    }
    return false;
  }

  public List<String> collectSourceBackedPreconditions(final MethodInfo method) {
    final LinkedHashSet<String> values = new LinkedHashSet<>();
    if (method == null) {
      return List.of();
    }
    final Map<String, String> parameterNameMap = extractMethodParameterNameMap(method);
    final Set<String> parameterNames = parameterNameMap.keySet();
    for (final String sourceCondition : collectPreconditionsFromSourceCode(method)) {
      addNormalizedPreconditions(values, sourceCondition, parameterNames, true);
    }
    addImplicitNonNullPreconditions(values, method, parameterNames);
    return finalizePreconditions(values, parameterNameMap);
  }

  public List<String> collectFallbackPreconditions(final MethodInfo method) {
    final LinkedHashSet<String> values = new LinkedHashSet<>();
    if (method == null) {
      return List.of();
    }
    final Map<String, String> parameterNameMap = extractMethodParameterNameMap(method);
    final Set<String> parameterNames = parameterNameMap.keySet();
    values.addAll(collectSourceBackedPreconditions(method));
    if (parameterNames.isEmpty()) {
      return finalizePreconditions(values, parameterNameMap);
    }
    final BranchSummary summary = method.getBranchSummary();
    if (summary != null) {
      for (final GuardSummary guard : summary.getGuards()) {
        if (!isLikelyPreconditionGuard(method, guard, parameterNames)) {
          continue;
        }
        addNormalizedPreconditions(values, guard.getCondition(), parameterNames, false);
      }
    }
    for (final RepresentativePath path : method.getRepresentativePaths()) {
      if (path == null || !isLikelyPreconditionPath(path, parameterNames)) {
        continue;
      }
      if (isLikelyBoundaryOnlyPath(path) || isLikelyEarlyReturnOnlyPath(path)) {
        continue;
      }
      for (final String condition : path.getRequiredConditions()) {
        addNormalizedPreconditions(values, condition, parameterNames, false);
      }
    }
    return finalizePreconditions(values, parameterNameMap);
  }

  private void addImplicitNonNullPreconditions(
      final LinkedHashSet<String> values,
      final MethodInfo method,
      final Set<String> parameterNames) {
    if (values == null || method == null || parameterNames == null || parameterNames.isEmpty()) {
      return;
    }
    final String sourceCode = DocumentUtils.stripCommentedRegions(method.getSourceCode());
    if (sourceCode == null || sourceCode.isBlank()) {
      return;
    }
    for (final String parameterName : parameterNames) {
      if (parameterName == null || parameterName.isBlank()) {
        continue;
      }
      if (hasNullShortCircuitGuardedDereference(sourceCode, parameterName)) {
        continue;
      }
      if (hasExplicitNullReturningGuard(sourceCode, parameterName)) {
        continue;
      }
      if (!hasDereferenceUsage(sourceCode, parameterName)) {
        continue;
      }
      if (alreadyHasNonNullPrecondition(values, parameterName, parameterNames)) {
        continue;
      }
      values.add(parameterName + " != null");
    }
  }

  private boolean hasDereferenceUsage(final String sourceCode, final String parameterName) {
    if (sourceCode == null
        || sourceCode.isBlank()
        || parameterName == null
        || parameterName.isBlank()) {
      return false;
    }
    final Pattern dereferencePattern =
        Pattern.compile(
            "\\b" + Pattern.quote(parameterName) + "\\b\\s*(?:\\.|\\[)", Pattern.CASE_INSENSITIVE);
    return dereferencePattern.matcher(sourceCode).find();
  }

  private boolean hasNullShortCircuitGuardedDereference(
      final String sourceCode, final String parameterName) {
    if (sourceCode == null
        || sourceCode.isBlank()
        || parameterName == null
        || parameterName.isBlank()) {
      return false;
    }
    final String token = "\\b" + Pattern.quote(parameterName) + "\\b";
    return Pattern.compile(token + "\\s*==\\s*null\\s*\\|\\|", Pattern.CASE_INSENSITIVE)
            .matcher(sourceCode)
            .find()
        || Pattern.compile("\\|\\|\\s*" + token + "\\s*==\\s*null", Pattern.CASE_INSENSITIVE)
            .matcher(sourceCode)
            .find()
        || Pattern.compile(token + "\\s*!=\\s*null\\s*&&", Pattern.CASE_INSENSITIVE)
            .matcher(sourceCode)
            .find()
        || Pattern.compile("&&\\s*" + token + "\\s*!=\\s*null", Pattern.CASE_INSENSITIVE)
            .matcher(sourceCode)
            .find()
        || Pattern.compile(token + "\\s*==\\s*null\\s*\\?", Pattern.CASE_INSENSITIVE)
            .matcher(sourceCode)
            .find()
        || Pattern.compile(token + "\\s*!=\\s*null\\s*\\?", Pattern.CASE_INSENSITIVE)
            .matcher(sourceCode)
            .find();
  }

  private boolean hasExplicitNullReturningGuard(
      final String sourceCode, final String parameterName) {
    if (sourceCode == null
        || sourceCode.isBlank()
        || parameterName == null
        || parameterName.isBlank()) {
      return false;
    }
    final String token = "\\b" + Pattern.quote(parameterName) + "\\b";
    final Pattern inlineReturnNull =
        Pattern.compile(
            "if\\s*\\(\\s*" + token + "\\s*==\\s*null\\s*\\)\\s*return\\s+null\\s*;",
            Pattern.CASE_INSENSITIVE);
    final Pattern bracedReturnNull =
        Pattern.compile(
            "if\\s*\\(\\s*" + token + "\\s*==\\s*null\\s*\\)\\s*\\{\\s*return\\s+null\\s*;\\s*\\}",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    return inlineReturnNull.matcher(sourceCode).find()
        || bracedReturnNull.matcher(sourceCode).find();
  }

  private boolean alreadyHasNonNullPrecondition(
      final LinkedHashSet<String> values,
      final String parameterName,
      final Set<String> parameterNames) {
    if (values == null || values.isEmpty() || parameterName == null || parameterName.isBlank()) {
      return false;
    }
    for (final String candidate : values) {
      final String resolved = extractNonNullParameterFromCondition(candidate, parameterNames);
      if (parameterName.equalsIgnoreCase(resolved)) {
        return true;
      }
    }
    return false;
  }

  private Set<String> collectSupportedNonNullParameters(
      final MethodInfo method, final Set<String> parameterNames) {
    final LinkedHashSet<String> supported = new LinkedHashSet<>();
    if (method == null || parameterNames == null || parameterNames.isEmpty()) {
      return supported;
    }
    for (final String precondition : collectFallbackPreconditions(method)) {
      final String target = extractNonNullParameterFromCondition(precondition, parameterNames);
      if (!target.isBlank()) {
        supported.add(target);
      }
    }
    return supported;
  }

  private String extractNonNullParameterFromCondition(
      final String condition, final Set<String> parameterNames) {
    if (condition == null
        || condition.isBlank()
        || parameterNames == null
        || parameterNames.isEmpty()) {
      return "";
    }
    final Matcher matcher = NOT_NULL_CONDITION_PATTERN.matcher(normalizeConditionToken(condition));
    if (!matcher.matches()) {
      return "";
    }
    return resolveParameterName(matcher.group(1), parameterNames);
  }

  private String extractAssertedNonNullParameter(
      final String rawLine, final Set<String> parameterNames) {
    if (rawLine == null
        || rawLine.isBlank()
        || parameterNames == null
        || parameterNames.isEmpty()) {
      return "";
    }
    final String stripped = rawLine.strip().replaceFirst("^-\\s*", "");
    final String normalized = LlmDocumentTextUtils.normalizeLine(stripped);
    if (normalized.isBlank() || LlmDocumentTextUtils.isNoneMarker(normalized)) {
      return "";
    }
    final String line = stripped.replace("`", "");
    final String lower = line.toLowerCase(Locale.ROOT);
    for (final String parameterName : parameterNames) {
      if (parameterName == null || parameterName.isBlank()) {
        continue;
      }
      if (!containsParameterToken(lower, parameterName)) {
        continue;
      }
      if (matchesNonNullAssertion(lower, parameterName)) {
        return parameterName.toLowerCase(Locale.ROOT);
      }
    }
    return "";
  }

  private boolean containsParameterToken(final String line, final String parameterName) {
    if (line == null || line.isBlank() || parameterName == null || parameterName.isBlank()) {
      return false;
    }
    final Pattern token =
        Pattern.compile("\\b" + Pattern.quote(parameterName.toLowerCase(Locale.ROOT)) + "\\b");
    return token.matcher(line).find();
  }

  private boolean matchesNonNullAssertion(final String line, final String parameterName) {
    if (line == null || line.isBlank() || parameterName == null || parameterName.isBlank()) {
      return false;
    }
    final String lower = line.toLowerCase(Locale.ROOT);
    final String compact = lower.replaceAll("\\s+", "");
    final String parameter = parameterName.toLowerCase(Locale.ROOT);
    if (compact.contains(parameter + "!=null")) {
      return true;
    }
    if (compact.contains(parameter + "はnullでない")
        || compact.contains(parameter + "がnullでない")
        || compact.contains(parameter + "はnullではない")
        || compact.contains(parameter + "がnullではない")
        || compact.contains(parameter + "null不可")
        || compact.contains(parameter + "null禁止")) {
      return true;
    }
    return lower.contains("must not be null")
        || lower.contains("must be non-null")
        || lower.contains("is not null")
        || lower.contains("non-null");
  }

  private String resolveParameterName(final String expression, final Set<String> parameterNames) {
    if (expression == null
        || expression.isBlank()
        || parameterNames == null
        || parameterNames.isEmpty()) {
      return "";
    }
    final String normalized = normalizeConditionToken(expression);
    if (normalized.isBlank()) {
      return "";
    }
    String token = normalized;
    final int dotIndex = token.indexOf('.');
    if (dotIndex >= 0) {
      token = token.substring(0, dotIndex);
    }
    final int bracketIndex = token.indexOf('[');
    if (bracketIndex >= 0) {
      token = token.substring(0, bracketIndex);
    }
    final String candidate = token.strip().toLowerCase(Locale.ROOT);
    if (candidate.isBlank() || !parameterNames.contains(candidate)) {
      return "";
    }
    return candidate;
  }

  private List<String> collectPreconditionsFromSourceCode(final MethodInfo method) {
    if (method == null) {
      return List.of();
    }
    final String sourceCode = DocumentUtils.stripCommentedRegions(method.getSourceCode());
    if (sourceCode == null || sourceCode.isBlank()) {
      return List.of();
    }
    final LinkedHashSet<String> extracted = new LinkedHashSet<>();
    for (final String expression :
        extractFirstArguments(sourceCode, PRECONDITION_CHECK_NOT_NULL_CALL)) {
      final String normalized = normalizeConditionToken(expression);
      if (normalized.isBlank() || isLikelyLiteralExpression(normalized)) {
        continue;
      }
      extracted.add(normalized + " != null");
    }
    for (final String expression :
        extractFirstArguments(sourceCode, PRECONDITION_REQUIRE_NON_NULL_CALL)) {
      final String normalized = normalizeConditionToken(expression);
      if (normalized.isBlank() || isLikelyLiteralExpression(normalized)) {
        continue;
      }
      extracted.add(normalized + " != null");
    }
    for (final String expression :
        extractFirstArguments(sourceCode, PRECONDITION_CHECK_ARGUMENT_CALL)) {
      final String normalized = normalizeConditionToken(expression);
      if (normalized.isBlank() || isLikelyLiteralExpression(normalized)) {
        continue;
      }
      extracted.add(normalized);
    }
    return new ArrayList<>(extracted);
  }

  private List<String> extractFirstArguments(final String sourceCode, final String callPrefix) {
    if (sourceCode == null || sourceCode.isBlank() || callPrefix == null || callPrefix.isBlank()) {
      return List.of();
    }
    final LinkedHashSet<String> arguments = new LinkedHashSet<>();
    int searchIndex = 0;
    while (searchIndex < sourceCode.length()) {
      final int callIndex = sourceCode.indexOf(callPrefix, searchIndex);
      if (callIndex < 0) {
        break;
      }
      final int argumentsStart = callIndex + callPrefix.length();
      final FirstArgumentParseResult parseResult = parseFirstArgument(sourceCode, argumentsStart);
      if (parseResult == null) {
        searchIndex = argumentsStart;
        continue;
      }
      final String firstArgument = normalizeConditionToken(parseResult.firstArgument());
      if (!firstArgument.isBlank()) {
        arguments.add(firstArgument);
      }
      searchIndex = parseResult.nextIndex();
    }
    return new ArrayList<>(arguments);
  }

  private FirstArgumentParseResult parseFirstArgument(
      final String sourceCode, final int argumentsStart) {
    int depth = 0;
    int argumentEnd = -1;
    Character quote = null;
    boolean escaped = false;
    for (int i = argumentsStart; i < sourceCode.length(); i++) {
      final char current = sourceCode.charAt(i);
      if (quote != null) {
        if (escaped) {
          escaped = false;
          continue;
        }
        if (current == '\\') {
          escaped = true;
          continue;
        }
        if (current == quote) {
          quote = null;
        }
        continue;
      }
      if (current == '\'' || current == '"') {
        quote = current;
        continue;
      }
      if (current == '(') {
        depth++;
        continue;
      }
      if (current == ')') {
        if (depth == 0) {
          final int end = argumentEnd >= 0 ? argumentEnd : i;
          return new FirstArgumentParseResult(sourceCode.substring(argumentsStart, end), i + 1);
        }
        depth--;
        continue;
      }
      if (current == ',' && depth == 0 && argumentEnd < 0) {
        argumentEnd = i;
      }
    }
    return null;
  }

  private void addNormalizedPreconditions(
      final LinkedHashSet<String> values,
      final String condition,
      final Set<String> parameterNames,
      final boolean allowRawConditionFallback) {
    if (values == null || condition == null || condition.isBlank()) {
      return;
    }
    for (final String normalized :
        normalizeConditionAsPreconditions(condition, parameterNames, allowRawConditionFallback)) {
      if (normalized == null || normalized.isBlank()) {
        continue;
      }
      values.add(normalized);
    }
  }

  private List<String> normalizeConditionAsPreconditions(
      final String condition,
      final Set<String> parameterNames,
      final boolean allowRawConditionFallback) {
    final String normalized = normalizeConditionToken(condition);
    if (normalized.isBlank()
        || flowConditionChecker.test(normalized)
        || isLikelyInternalFailureCondition(normalized, parameterNames)) {
      return List.of();
    }
    final List<String> normalizedNullOrEmpty = normalizeNullOrEmptyCondition(normalized);
    if (!normalizedNullOrEmpty.isEmpty()) {
      return normalizedNullOrEmpty;
    }
    final List<String> normalizedNullOrCompareTo =
        normalizeNullOrCompareToCondition(normalized, parameterNames);
    if (!normalizedNullOrCompareTo.isEmpty()) {
      return normalizedNullOrCompareTo;
    }
    if (normalized.contains("||")) {
      return List.of();
    }
    if (normalized.contains("&&")) {
      return List.of();
    }
    final Matcher equalsNull = EQUALS_NULL_CONDITION_PATTERN.matcher(normalized);
    if (equalsNull.matches()) {
      final String target = normalizeConditionToken(equalsNull.group(1));
      return target.isBlank() ? List.of() : List.of(target + " != null");
    }
    final Matcher lessOrEqualZero = LESS_OR_EQUAL_ZERO_CONDITION_PATTERN.matcher(normalized);
    if (lessOrEqualZero.matches()) {
      final String target = normalizeConditionToken(lessOrEqualZero.group(1));
      return target.isBlank() ? List.of() : List.of(target + " > 0");
    }
    final Matcher lessThanZero = LESS_THAN_ZERO_CONDITION_PATTERN.matcher(normalized);
    if (lessThanZero.matches()) {
      final String target = normalizeConditionToken(lessThanZero.group(1));
      return target.isBlank() ? List.of() : List.of(target + " >= 0");
    }
    final Matcher negatedEmptyCheck = NEGATED_EMPTY_CHECK_CONDITION_PATTERN.matcher(normalized);
    if (negatedEmptyCheck.matches()) {
      final String target = normalizeConditionToken(negatedEmptyCheck.group(1));
      final String methodName = negatedEmptyCheck.group(2);
      if (target.isBlank() || methodName == null || methodName.isBlank()) {
        return List.of();
      }
      if (!isLikelyParameterBasedTarget(target, parameterNames)) {
        return List.of();
      }
      final String normalizedTarget = normalizeNegatedTarget(target);
      if (normalizedTarget.isBlank()) {
        return List.of();
      }
      return List.of("!" + normalizedTarget + "." + methodName + "()");
    }
    final Matcher emptyCheck = EMPTY_CHECK_CONDITION_PATTERN.matcher(normalized);
    if (emptyCheck.matches()) {
      final String target = normalizeConditionToken(emptyCheck.group(1));
      final String methodName = emptyCheck.group(2);
      if (target.isBlank() || methodName == null || methodName.isBlank()) {
        return List.of();
      }
      if (!isLikelyParameterBasedTarget(target, parameterNames)) {
        return List.of();
      }
      final String normalizedTarget = normalizeNegatedTarget(target);
      if (normalizedTarget.isBlank()) {
        return List.of();
      }
      return List.of("!" + normalizedTarget + "." + methodName + "()");
    }
    final List<String> negatedPredicate = normalizeNegatedPredicateCall(normalized, parameterNames);
    if (!negatedPredicate.isEmpty()) {
      return negatedPredicate;
    }
    if (!allowRawConditionFallback) {
      return List.of();
    }
    return List.of(normalized);
  }

  private String normalizeNegatedTarget(final String target) {
    if (target == null || target.isBlank()) {
      return "";
    }
    String normalized = target.strip();
    while (normalized.startsWith("!")) {
      normalized = normalized.substring(1).strip();
    }
    return normalized;
  }

  private List<String> normalizeNullOrEmptyCondition(final String condition) {
    if (condition == null || condition.isBlank()) {
      return List.of();
    }
    final Matcher nullOrEmpty = NULL_OR_EMPTY_CONDITION_PATTERN.matcher(condition);
    if (nullOrEmpty.matches()) {
      final String target = normalizeConditionToken(nullOrEmpty.group(1));
      final String checkName = nullOrEmpty.group(2);
      if (target.isBlank() || checkName == null || checkName.isBlank()) {
        return List.of();
      }
      return List.of(target + " != null", "!" + target + "." + checkName + "()");
    }
    final Matcher emptyOrNull = EMPTY_OR_NULL_CONDITION_PATTERN.matcher(condition);
    if (!emptyOrNull.matches()) {
      return List.of();
    }
    final String target = normalizeConditionToken(emptyOrNull.group(1));
    final String checkName = emptyOrNull.group(2);
    if (target.isBlank() || checkName == null || checkName.isBlank()) {
      return List.of();
    }
    return List.of(target + " != null", "!" + target + "." + checkName + "()");
  }

  private List<String> normalizeNullOrCompareToCondition(
      final String condition, final Set<String> parameterNames) {
    if (condition == null || condition.isBlank()) {
      return List.of();
    }
    final CompareToNullNormalization nonPositive =
        parseCompareToNullNormalization(
            condition,
            parameterNames,
            NULL_OR_COMPARE_TO_NON_POSITIVE_CONDITION_PATTERN,
            COMPARE_TO_NON_POSITIVE_OR_NULL_CONDITION_PATTERN);
    if (nonPositive != null) {
      return List.of(
          nonPositive.target() + " != null",
          nonPositive.target() + ".compareTo(" + nonPositive.compareTarget() + ") > 0");
    }
    final CompareToNullNormalization negative =
        parseCompareToNullNormalization(
            condition,
            parameterNames,
            NULL_OR_COMPARE_TO_NEGATIVE_CONDITION_PATTERN,
            COMPARE_TO_NEGATIVE_OR_NULL_CONDITION_PATTERN);
    if (negative != null) {
      return List.of(
          negative.target() + " != null",
          negative.target() + ".compareTo(" + negative.compareTarget() + ") >= 0");
    }
    return List.of();
  }

  private CompareToNullNormalization parseCompareToNullNormalization(
      final String condition,
      final Set<String> parameterNames,
      final Pattern nullFirstPattern,
      final Pattern compareFirstPattern) {
    if (condition == null || condition.isBlank()) {
      return null;
    }
    Matcher matcher = nullFirstPattern.matcher(condition);
    if (!matcher.matches()) {
      matcher = compareFirstPattern.matcher(condition);
      if (!matcher.matches()) {
        return null;
      }
    }
    final String target = normalizeConditionToken(matcher.group(1));
    final String compareTarget = normalizeConditionToken(matcher.group(2));
    if (target.isBlank()
        || compareTarget.isBlank()
        || !isLikelyParameterBasedTarget(target, parameterNames)) {
      return null;
    }
    return new CompareToNullNormalization(target, compareTarget);
  }

  private List<String> normalizeNegatedPredicateCall(
      final String condition, final Set<String> parameterNames) {
    if (condition == null || condition.isBlank()) {
      return List.of();
    }
    final Matcher matcher = NEGATED_PREDICATE_CALL_PATTERN.matcher(condition);
    if (!matcher.matches()) {
      return List.of();
    }
    final String callTarget = normalizeConditionToken(matcher.group(1));
    final String arguments = normalizeConditionToken(matcher.group(2));
    if (callTarget.isBlank()) {
      return List.of();
    }
    final String methodName = LlmDocumentTextUtils.extractMethodName(callTarget);
    final String normalizedMethodName = methodName.toLowerCase(Locale.ROOT);
    if ("issuccess".equals(normalizedMethodName)
        || "ispresent".equals(normalizedMethodName)
        || "isempty".equals(normalizedMethodName)
            && !isLikelyParameterBasedTarget(callTarget, parameterNames)) {
      return List.of();
    }
    if (!callTarget.contains(".")) {
      if ((normalizedMethodName.startsWith("isvalid")
              || normalizedMethodName.startsWith("hasvalid")
              || normalizedMethodName.startsWith("issupported")
              || normalizedMethodName.startsWith("supports"))
          && containsAnyParameterReference(arguments, parameterNames)) {
        return List.of(
            arguments.isBlank() ? callTarget + "()" : callTarget + "(" + arguments + ")");
      }
      return List.of();
    }
    return List.of();
  }

  private boolean isLikelyParameterBasedTarget(
      final String expression, final Set<String> parameterNames) {
    if (expression == null || expression.isBlank()) {
      return false;
    }
    if (parameterNames == null || parameterNames.isEmpty()) {
      return true;
    }
    final String normalized = normalizeConditionToken(expression);
    String firstToken = normalized;
    final int dotIndex = firstToken.indexOf('.');
    if (dotIndex >= 0) {
      firstToken = firstToken.substring(0, dotIndex);
    }
    final int bracketIndex = firstToken.indexOf('[');
    if (bracketIndex >= 0) {
      firstToken = firstToken.substring(0, bracketIndex);
    }
    final String candidate = firstToken.strip().toLowerCase(Locale.ROOT);
    if (candidate.isBlank()) {
      return false;
    }
    return parameterNames.contains(candidate);
  }

  private boolean containsAnyParameterReference(
      final String expression, final Set<String> parameterNames) {
    if (expression == null
        || expression.isBlank()
        || parameterNames == null
        || parameterNames.isEmpty()) {
      return false;
    }
    final String normalized = normalizeConditionToken(expression).toLowerCase(Locale.ROOT);
    for (final String parameterName : parameterNames) {
      if (parameterName == null || parameterName.isBlank()) {
        continue;
      }
      final Pattern parameterPattern =
          Pattern.compile("\\b" + Pattern.quote(parameterName.toLowerCase(Locale.ROOT)) + "\\b");
      if (parameterPattern.matcher(normalized).find()) {
        return true;
      }
    }
    return false;
  }

  private boolean isLikelyInternalFailureCondition(
      final String condition, final Set<String> parameterNames) {
    if (condition == null || condition.isBlank()) {
      return false;
    }
    final String normalized = normalizeConditionToken(condition);
    if (normalized.isBlank()) {
      return false;
    }
    final String compact = normalized.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    if (compact.contains("early-return") || compact.contains("earlyreturn")) {
      return true;
    }
    if (compact.matches(".*!+[a-z_][a-z0-9_$.]*\\.issuccess\\(\\).*")) {
      return true;
    }
    if (compact.contains(".ispresent()")) {
      return true;
    }
    if (compact.matches(".*!+[a-z_][a-z0-9_]*opt[a-z0-9_]*\\.isempty\\(\\).*")) {
      return true;
    }
    if (compact.contains(".compareto(") && !isCompareToZeroConstantExpression(normalized)) {
      return true;
    }
    if (compact.contains("&&") && !containsAnyParameterReference(normalized, parameterNames)) {
      return true;
    }
    return false;
  }

  private boolean isCompareToZeroConstantExpression(final String condition) {
    if (condition == null || condition.isBlank()) {
      return false;
    }
    final Matcher matcher =
        COMPARE_TO_CONDITION_PATTERN.matcher(normalizeConditionToken(condition));
    if (!matcher.matches()) {
      return false;
    }
    final String target = normalizeConditionToken(matcher.group(2)).toLowerCase(Locale.ROOT);
    return "0".equals(target)
        || "0.0".equals(target)
        || "bigdecimal.zero".equals(target)
        || "java.math.bigdecimal.zero".equals(target);
  }

  private boolean containsLikelyInputCondition(
      final List<String> requiredConditions, final Set<String> parameterNames) {
    if (requiredConditions == null || requiredConditions.isEmpty()) {
      return false;
    }
    for (final String requiredCondition : requiredConditions) {
      if (requiredCondition == null || requiredCondition.isBlank()) {
        continue;
      }
      if (containsAnyParameterReference(requiredCondition, parameterNames)
          && !isLikelyInternalFailureCondition(requiredCondition, parameterNames)) {
        return true;
      }
    }
    return false;
  }

  private boolean isLikelyPreconditionGuard(
      final MethodInfo method, final GuardSummary guard, final Set<String> parameterNames) {
    if (guard == null || guard.getCondition() == null || guard.getCondition().isBlank()) {
      return false;
    }
    if (isNullReturningGuard(guard.getCondition(), parameterNames, method)) {
      return false;
    }
    final GuardType type = guard.getType();
    if (type == GuardType.LOOP_GUARD_CONTINUE || type == GuardType.LOOP_GUARD_BREAK) {
      return false;
    }
    final String condition = guard.getCondition();
    if (flowConditionChecker.test(condition)
        || isLikelyInternalFailureCondition(condition, parameterNames)) {
      return false;
    }
    for (final String effect : guard.getEffects()) {
      if (effect != null && flowConditionChecker.test(effect)) {
        return false;
      }
    }
    return true;
  }

  private boolean isNullReturningGuard(
      final String condition, final Set<String> parameterNames, final MethodInfo method) {
    if (condition == null
        || condition.isBlank()
        || parameterNames == null
        || parameterNames.isEmpty()
        || method == null) {
      return false;
    }
    final Matcher equalsNull =
        EQUALS_NULL_CONDITION_PATTERN.matcher(normalizeConditionToken(condition));
    if (!equalsNull.matches()) {
      return false;
    }
    final String parameterName = resolveParameterName(equalsNull.group(1), parameterNames);
    if (parameterName.isBlank()) {
      return false;
    }
    final String sourceCode = DocumentUtils.stripCommentedRegions(method.getSourceCode());
    if (sourceCode == null || sourceCode.isBlank()) {
      return false;
    }
    final String token = "\\b" + Pattern.quote(parameterName) + "\\b";
    final Pattern inlineReturnNull =
        Pattern.compile(
            "if\\s*\\(\\s*" + token + "\\s*==\\s*null\\s*\\)\\s*return\\s+null\\s*;",
            Pattern.CASE_INSENSITIVE);
    final Pattern bracedReturnNull =
        Pattern.compile(
            "if\\s*\\(\\s*" + token + "\\s*==\\s*null\\s*\\)\\s*\\{\\s*return\\s+null\\s*;\\s*\\}",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    return inlineReturnNull.matcher(sourceCode).find()
        || bracedReturnNull.matcher(sourceCode).find();
  }

  private boolean isLikelyPreconditionPath(
      final RepresentativePath path, final Set<String> parameterNames) {
    if (path == null) {
      return false;
    }
    if (flowConditionChecker.test(path.getExpectedOutcomeHint())) {
      return false;
    }
    if (errorIndicatorChecker.test(path.getExpectedOutcomeHint())) {
      return true;
    }
    final String description = path.getDescription();
    if (description == null || description.isBlank()) {
      return false;
    }
    if (flowConditionChecker.test(description)) {
      return false;
    }
    final String normalized = description.toLowerCase(Locale.ROOT);
    return normalized.contains("validation")
        || normalized.contains("invalid")
        || normalized.contains("precondition")
        || normalized.contains("fail")
        || normalized.contains("error")
        || normalized.contains("null")
        || normalized.contains("検証")
        || normalized.contains("事前条件")
        || normalized.contains("不正")
        || normalized.contains("失敗")
        || normalized.contains("境界")
        || containsLikelyInputCondition(path.getRequiredConditions(), parameterNames);
  }

  private boolean isLikelyBoundaryOnlyPath(final RepresentativePath path) {
    if (path == null) {
      return false;
    }
    final String expected = path.getExpectedOutcomeHint();
    if (expected == null || expected.isBlank()) {
      return false;
    }
    final String normalizedExpected = expected.toLowerCase(Locale.ROOT);
    final boolean boundaryExpected =
        normalizedExpected.contains("boundary") || normalizedExpected.contains("境界");
    if (!boundaryExpected) {
      return false;
    }
    final String description =
        path.getDescription() == null ? "" : path.getDescription().toLowerCase(Locale.ROOT);
    return !description.contains("validation")
        && !description.contains("invalid")
        && !description.contains("fail")
        && !description.contains("error")
        && !description.contains("exception")
        && !description.contains("throw")
        && !description.contains("検証")
        && !description.contains("不正")
        && !description.contains("失敗")
        && !description.contains("例外")
        && !description.contains("事前条件");
  }

  private boolean isLikelyEarlyReturnOnlyPath(final RepresentativePath path) {
    if (path == null) {
      return false;
    }
    final String expected = path.getExpectedOutcomeHint();
    if (expected == null || expected.isBlank()) {
      return false;
    }
    final String normalizedExpected = expected.toLowerCase(Locale.ROOT);
    final boolean earlyReturnExpected =
        normalizedExpected.contains("early-return")
            || normalizedExpected.contains("early return")
            || normalizedExpected.contains("short-circuit")
            || normalizedExpected.contains("short circuit");
    if (!earlyReturnExpected) {
      return false;
    }
    final String description =
        path.getDescription() == null ? "" : path.getDescription().toLowerCase(Locale.ROOT);
    return !description.contains("validation")
        && !description.contains("invalid")
        && !description.contains("fail")
        && !description.contains("error")
        && !description.contains("exception")
        && !description.contains("throw")
        && !description.contains("検証")
        && !description.contains("不正")
        && !description.contains("失敗")
        && !description.contains("例外")
        && !description.contains("事前条件");
  }

  private Set<String> extractMethodParameterNames(final MethodInfo method) {
    final Map<String, String> parameterNames = extractMethodParameterNameMap(method);
    if (parameterNames.isEmpty()) {
      return Set.of();
    }
    return new LinkedHashSet<>(parameterNames.keySet());
  }

  private Map<String, String> extractMethodParameterNameMap(final MethodInfo method) {
    if (method == null) {
      return Map.of();
    }
    final Map<String, String> fromSignature =
        extractMethodParameterNameMapFromSignature(method.getSignature());
    if (!fromSignature.isEmpty()) {
      return fromSignature;
    }
    return extractMethodParameterNameMapFromSourceCode(method);
  }

  private Map<String, String> extractMethodParameterNameMapFromSignature(final String signature) {
    if (signature == null || signature.isBlank()) {
      return Map.of();
    }
    final int openParen = signature.indexOf('(');
    final int closeParen = signature.lastIndexOf(')');
    if (openParen < 0 || closeParen <= openParen) {
      return Map.of();
    }
    final String parameterSection = signature.substring(openParen + 1, closeParen);
    return extractParameterNameMapFromSection(parameterSection);
  }

  private Map<String, String> extractMethodParameterNameMapFromSourceCode(final MethodInfo method) {
    if (method == null) {
      return Map.of();
    }
    final String sourceCode = DocumentUtils.stripCommentedRegions(method.getSourceCode());
    if (sourceCode == null || sourceCode.isBlank()) {
      return Map.of();
    }
    final String declaration = extractDeclarationHeader(sourceCode);
    if (declaration.isBlank()) {
      return Map.of();
    }
    final String methodName = method.getName();
    if (methodName == null || methodName.isBlank()) {
      return Map.of();
    }
    final Matcher matcher =
        Pattern.compile("\\b" + Pattern.quote(methodName) + "\\b").matcher(declaration);
    while (matcher.find()) {
      final int openParen = skipSpaces(declaration, matcher.end());
      if (openParen >= declaration.length() || declaration.charAt(openParen) != '(') {
        continue;
      }
      final int closeParen = findMatchingParenthesis(declaration, openParen);
      if (closeParen <= openParen) {
        continue;
      }
      final String parameterSection = declaration.substring(openParen + 1, closeParen);
      final Map<String, String> names = extractParameterNameMapFromSection(parameterSection);
      if (!names.isEmpty()) {
        return names;
      }
      return Map.of();
    }
    return Map.of();
  }

  private Map<String, String> extractParameterNameMapFromSection(final String parameterSection) {
    if (parameterSection == null || parameterSection.isBlank()) {
      return Map.of();
    }
    final LinkedHashMap<String, String> names = new LinkedHashMap<>();
    for (final String token : LlmDocumentTextUtils.splitTopLevelCsv(parameterSection)) {
      final String name = extractParameterName(token);
      if (!name.isBlank()) {
        names.putIfAbsent(name.toLowerCase(Locale.ROOT), name);
      }
    }
    if (names.isEmpty()) {
      return Map.of();
    }
    return names;
  }

  private List<String> finalizePreconditions(
      final LinkedHashSet<String> preconditions, final Map<String, String> parameterNameMap) {
    if (preconditions == null || preconditions.isEmpty()) {
      return List.of();
    }
    final LinkedHashSet<String> finalized = new LinkedHashSet<>();
    for (final String raw : preconditions) {
      final String normalized = normalizeConditionToken(raw);
      if (normalized.isBlank()) {
        continue;
      }
      final String canonicalized = canonicalizeParameterTokens(normalized, parameterNameMap);
      if (!canonicalized.isBlank()) {
        finalized.add(canonicalized);
      }
    }
    if (finalized.isEmpty()) {
      return List.of();
    }
    return new ArrayList<>(finalized);
  }

  private String canonicalizeParameterTokens(
      final String condition, final Map<String, String> parameterNameMap) {
    if (condition == null
        || condition.isBlank()
        || parameterNameMap == null
        || parameterNameMap.isEmpty()) {
      return condition == null ? "" : condition;
    }
    String canonicalized = condition;
    for (final Map.Entry<String, String> entry : parameterNameMap.entrySet()) {
      final String normalizedName = entry.getKey();
      final String canonicalName = entry.getValue();
      if (normalizedName == null
          || normalizedName.isBlank()
          || canonicalName == null
          || canonicalName.isBlank()) {
        continue;
      }
      final Pattern tokenPattern =
          Pattern.compile("\\b" + Pattern.quote(normalizedName) + "\\b", Pattern.CASE_INSENSITIVE);
      canonicalized =
          tokenPattern.matcher(canonicalized).replaceAll(Matcher.quoteReplacement(canonicalName));
    }
    return canonicalized;
  }

  private String extractDeclarationHeader(final String sourceCode) {
    if (sourceCode == null || sourceCode.isBlank()) {
      return "";
    }
    final String trimmed = sourceCode.strip();
    final int braceIndex = trimmed.indexOf('{');
    if (braceIndex < 0) {
      return "";
    }
    return trimmed.substring(0, braceIndex).strip();
  }

  private int skipSpaces(final String value, final int startIndex) {
    if (value == null || value.isBlank()) {
      return startIndex;
    }
    int cursor = Math.max(0, startIndex);
    while (cursor < value.length() && Character.isWhitespace(value.charAt(cursor))) {
      cursor++;
    }
    return cursor;
  }

  private int findMatchingParenthesis(final String value, final int openParenIndex) {
    if (value == null
        || value.isBlank()
        || openParenIndex < 0
        || openParenIndex >= value.length()
        || value.charAt(openParenIndex) != '(') {
      return -1;
    }
    int depth = 0;
    for (int i = openParenIndex; i < value.length(); i++) {
      final char current = value.charAt(i);
      if (current == '(') {
        depth++;
      } else if (current == ')') {
        depth--;
        if (depth == 0) {
          return i;
        }
      }
      if (depth < 0) {
        return -1;
      }
    }
    return -1;
  }

  private String extractParameterName(final String parameterToken) {
    if (parameterToken == null || parameterToken.isBlank()) {
      return "";
    }
    final String sanitized =
        parameterToken
            .replaceAll("@[A-Za-z_][A-Za-z0-9_.]*(\\([^)]*\\))?", " ")
            .replace("final", " ")
            .replace("...", " ")
            .replaceAll("\\[\\s*\\]", " ")
            .trim();
    if (sanitized.isBlank()) {
      return "";
    }
    if (!sanitized.matches(".*\\s+[A-Za-z_][A-Za-z0-9_]*$")) {
      return "";
    }
    final Matcher matcher = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\s*$").matcher(sanitized);
    if (!matcher.find()) {
      return "";
    }
    final String candidate = matcher.group(1);
    if (candidate.isBlank() || Character.isUpperCase(candidate.charAt(0))) {
      return "";
    }
    return candidate;
  }

  private String normalizeConditionToken(final String condition) {
    if (condition == null || condition.isBlank()) {
      return "";
    }
    String normalized = condition.replace("`", "").replaceAll("\\s+", " ").strip();
    while (isWrappedByParentheses(normalized)) {
      normalized = normalized.substring(1, normalized.length() - 1).strip();
    }
    if (normalized.endsWith(";")) {
      normalized = normalized.substring(0, normalized.length() - 1).strip();
    }
    return normalized;
  }

  private boolean isWrappedByParentheses(final String value) {
    if (value == null
        || value.length() < 2
        || value.charAt(0) != '('
        || value.charAt(value.length() - 1) != ')') {
      return false;
    }
    int depth = 0;
    for (int i = 0; i < value.length(); i++) {
      final char current = value.charAt(i);
      if (current == '(') {
        depth++;
      } else if (current == ')') {
        depth--;
        if (depth == 0 && i < value.length() - 1) {
          return false;
        }
      }
      if (depth < 0) {
        return false;
      }
    }
    return depth == 0;
  }

  private boolean isLikelyLiteralExpression(final String expression) {
    if (expression == null || expression.isBlank()) {
      return true;
    }
    final String normalized = expression.strip();
    if (normalized.startsWith("\"") || normalized.startsWith("'")) {
      return true;
    }
    if ("true".equals(normalized) || "false".equals(normalized) || "null".equals(normalized)) {
      return true;
    }
    return normalized.matches("[-+]?\\d+(\\.\\d+)?");
  }

  private record FirstArgumentParseResult(String firstArgument, int nextIndex) {}

  private record CompareToNullNormalization(String target, String compareTarget) {}
}
