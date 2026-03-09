package com.craftsmanbro.fulcraft.plugins.document.core.llm.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.plugins.analysis.model.BranchSummary;
import com.craftsmanbro.fulcraft.plugins.analysis.model.GuardSummary;
import com.craftsmanbro.fulcraft.plugins.analysis.model.GuardType;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.RepresentativePath;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LlmFallbackPreconditionExtractorTest {

  private final LlmPathConditionHeuristics heuristics = new LlmPathConditionHeuristics();
  private final LlmFallbackPreconditionExtractor extractor =
      new LlmFallbackPreconditionExtractor(
          heuristics::isLikelyFlowCondition, heuristics::isLikelyErrorIndicator);

  @Test
  void containsUnsupportedNoArgPrecondition_shouldIgnoreNoneMarkersOrParameterizedMethods() {
    MethodInfo noArg = method("ping", "ping()", "public String ping() { return \"ok\"; }");
    MethodInfo withParam =
        method("ping", "ping(String value)", "public String ping(String value) { return value; }");

    assertThat(extractor.containsUnsupportedNoArgPrecondition("- none", noArg)).isFalse();
    assertThat(extractor.containsUnsupportedNoArgPrecondition("- なし", noArg)).isFalse();
    assertThat(extractor.containsUnsupportedNoArgPrecondition("- `value != null`", withParam))
        .isFalse();
  }

  @Test
  void containsUnsupportedNoArgPrecondition_shouldReturnTrueForNoArgMethodWithAssertion() {
    MethodInfo method = new MethodInfo();
    method.setName("ping");
    method.setSignature("ping()");
    method.setSourceCode(
        """
                public String ping() {
                    return "ok";
                }
                """);

    boolean unsupported =
        extractor.containsUnsupportedNoArgPrecondition("- `value != null`", method);

    assertThat(unsupported).isTrue();
  }

  @Test
  void
      containsUnsupportedNonNullPreconditionAssumption_shouldReturnFalseWhenSourceAndFallbackAreMissing() {
    MethodInfo method = new MethodInfo();
    method.setName("decorate");
    method.setSignature("decorate(String input)");
    method.setSourceCode("");

    boolean unsupported =
        extractor.containsUnsupportedNonNullPreconditionAssumption("- `input != null`", method);

    assertThat(unsupported).isFalse();
  }

  @Test
  void containsUnsupportedNonNullPreconditionAssumption_shouldUseFallbackSupportedParameters() {
    MethodInfo method = new MethodInfo();
    method.setName("check");
    method.setSignature("check(String input, String name)");
    method.setSourceCode("");
    BranchSummary summary = new BranchSummary();
    summary.setGuards(List.of(guard(GuardType.FAIL_GUARD, "input == null")));
    method.setBranchSummary(summary);

    boolean supportedOnly =
        extractor.containsUnsupportedNonNullPreconditionAssumption("- `input != null`", method);
    boolean mixedUnsupported =
        extractor.containsUnsupportedNonNullPreconditionAssumption(
            "- `input != null`\n- `name != null`", method);

    assertThat(supportedOnly).isFalse();
    assertThat(mixedUnsupported).isTrue();
  }

  @Test
  void containsUnsupportedNonNullPreconditionAssumption_shouldReturnTrueForUnsupportedAssertion() {
    MethodInfo method = new MethodInfo();
    method.setName("decorate");
    method.setSignature("decorate(String input)");
    method.setSourceCode(
        """
                public String decorate(String input) {
                    return "[" + input + "]";
                }
                """);

    boolean unsupported =
        extractor.containsUnsupportedNonNullPreconditionAssumption("- `input != null`", method);

    assertThat(unsupported).isTrue();
  }

  @Test
  void
      containsUnsupportedNonNullPreconditionAssumption_shouldReturnFalseForSourceBackedAssertion() {
    MethodInfo method = new MethodInfo();
    method.setName("normalize");
    method.setSignature("normalize(String input)");
    method.setSourceCode(
        """
                public String normalize(String input) {
                    java.util.Objects.requireNonNull(input, "input");
                    return input.trim();
                }
                """);

    boolean unsupported =
        extractor.containsUnsupportedNonNullPreconditionAssumption("- `input != null`", method);

    assertThat(unsupported).isFalse();
  }

  @Test
  void collectSourceBackedPreconditions_shouldParsePreconditionCallsAndIgnoreLiterals() {
    MethodInfo method =
        method(
            "sanitize",
            "sanitize(String input, String mode, int count)",
            """
            public String sanitize(String input, String mode, int count) {
                Preconditions.checkNotNull(input, "input,required");
                Objects.requireNonNull(mode);
                Preconditions.checkArgument(isValid(input, "a,b"), "count");
                Preconditions.checkNotNull("literal-only");
                return input.trim() + mode.trim() + count;
            }
            """);

    List<String> preconditions = extractor.collectSourceBackedPreconditions(method);

    assertThat(preconditions)
        .containsExactly("input != null", "mode != null", "isValid(input, \"a,b\")");
  }

  @Test
  void collectSourceBackedPreconditions_shouldInferImplicitNonNullForRequiredDereferences() {
    MethodInfo method =
        method(
            "resolve",
            "resolve(String input, String alias, String raw)",
            """
            public String resolve(String input, String alias, String raw) {
                if (alias == null) {
                    return null;
                }
                if (raw == null || raw.isBlank()) {
                    return "";
                }
                return input.trim() + alias.trim() + raw.trim();
            }
            """);

    List<String> preconditions = extractor.collectSourceBackedPreconditions(method);

    assertThat(preconditions).containsExactly("input != null");
  }

  @Test
  void collectSourceBackedPreconditions_shouldExtractParametersFromSourceWhenSignatureIsMissing() {
    MethodInfo method = new MethodInfo();
    method.setName("compose");
    method.setSourceCode(
        """
        public String compose(final @NotNull String left, java.util.List<String> items, int... nums) {
            return left.trim() + items.size() + nums.length;
        }
        """);

    List<String> preconditions = extractor.collectSourceBackedPreconditions(method);

    assertThat(preconditions).contains("left != null", "items != null", "nums != null");
  }

  @Test
  void collectFallbackPreconditions_shouldNormalizeGuardsAndRepresentativePaths() {
    MethodInfo method = new MethodInfo();
    method.setName("validate");
    method.setSignature(
        "validate(String input, java.math.BigDecimal amount, int quantity, String state)");
    method.setSourceCode("");

    BranchSummary summary = new BranchSummary();
    summary.setGuards(
        List.of(
            guard(GuardType.FAIL_GUARD, "input == null || input.isBlank()"),
            guard(
                GuardType.FAIL_GUARD,
                "amount.compareTo(java.math.BigDecimal.ZERO) <= 0 || amount == null"),
            guard(GuardType.FAIL_GUARD, "!isValidFormat(input)"),
            guard(GuardType.FAIL_GUARD, "state.compareTo(limit) <= 0"),
            guard(GuardType.FAIL_GUARD, "!state.isSuccess()"),
            guard(GuardType.LOOP_GUARD_CONTINUE, "quantity < 0")));
    method.setBranchSummary(summary);

    method.setRepresentativePaths(
        List.of(
            path("Boundary only", "boundary", "quantity < 0"),
            path("Fast path", "early-return", "quantity < 0"),
            path(
                "Validation failure when quantity is invalid",
                "error",
                "quantity < 0",
                "!isSupported(input)")));

    List<String> preconditions = extractor.collectFallbackPreconditions(method);

    assertThat(preconditions)
        .contains(
            "input != null",
            "!input.isBlank()",
            "isValidFormat(input)",
            "quantity >= 0",
            "isSupported(input)");
    assertThat(preconditions).doesNotContain("state.compareTo(limit) <= 0");
  }

  @Test
  void collectFallbackPreconditions_shouldCanonicalizeParameterTokensUsingSignatureCase() {
    MethodInfo method = new MethodInfo();
    method.setName("process");
    method.setSignature("process(String userId)");
    BranchSummary summary = new BranchSummary();
    summary.setGuards(List.of(guard(GuardType.FAIL_GUARD, "USERID == null")));
    method.setBranchSummary(summary);

    List<String> preconditions = extractor.collectFallbackPreconditions(method);

    assertThat(preconditions).containsExactly("userId != null");
  }

  @Test
  void collectFallbackPreconditions_shouldIgnoreFlowGuardsAndFlowEffects() {
    MethodInfo method = new MethodInfo();
    method.setName("execute");
    method.setSignature("execute(String input)");
    BranchSummary summary = new BranchSummary();
    summary.setGuards(
        List.of(
            guard(GuardType.FAIL_GUARD, "switch case status=\"NEW\""),
            guard(GuardType.FAIL_GUARD, "input != null", List.of("loop guard"))));
    method.setBranchSummary(summary);
    method.setRepresentativePaths(List.of(path("Main success path", "success", "input != null")));

    List<String> preconditions = extractor.collectFallbackPreconditions(method);

    assertThat(preconditions).isEmpty();
  }

  @Test
  void collectFallbackPreconditions_shouldSkipNullReturningGuardsFromSource() {
    MethodInfo method = new MethodInfo();
    method.setName("resolve");
    method.setSignature("resolve(String input)");
    method.setSourceCode(
        """
        public String resolve(String input) {
            if (input == null) {
                return null;
            }
            return input.trim();
        }
        """);
    BranchSummary summary = new BranchSummary();
    summary.setGuards(
        List.of(
            guard(GuardType.FAIL_GUARD, "input == null"),
            guard(GuardType.FAIL_GUARD, "input.isBlank()")));
    method.setBranchSummary(summary);

    List<String> preconditions = extractor.collectFallbackPreconditions(method);

    assertThat(preconditions).containsExactly("!input.isBlank()");
  }

  @Test
  void privateHelpers_shouldNormalizeConditionsAcrossBranches() throws Exception {
    Set<String> parameters = Set.of("input", "items", "amount");

    assertThat(
            invokeList(
                "normalizeConditionAsPreconditions",
                "(input == null || input.isBlank())",
                parameters,
                false))
        .containsExactly("input != null", "!input.isBlank()");

    assertThat(
            invokeList(
                "normalizeConditionAsPreconditions",
                "input.isBlank() || input == null",
                parameters,
                false))
        .containsExactly("input != null", "!input.isBlank()");

    assertThat(
            invokeList("normalizeConditionAsPreconditions", "!!items.isEmpty()", Set.of(), false))
        .containsExactly("!items.isEmpty()");
    assertThat(
            invokeList("normalizeConditionAsPreconditions", "helper.isEmpty()", parameters, false))
        .isEmpty();

    assertThat(invokeList("normalizeConditionAsPreconditions", "amount <= 0", parameters, false))
        .containsExactly("amount > 0");
    assertThat(invokeList("normalizeConditionAsPreconditions", "amount < 0", parameters, false))
        .containsExactly("amount >= 0");

    assertThat(
            invokeList(
                "normalizeConditionAsPreconditions", "  ((`input == null`))  ", parameters, false))
        .containsExactly("input != null");
    assertThat(invokeList("normalizeConditionAsPreconditions", "unknownCheck()", parameters, false))
        .isEmpty();
    assertThat(invokeList("normalizeConditionAsPreconditions", "unknownCheck()", parameters, true))
        .containsExactly("unknownCheck()");
  }

  @Test
  void privateHelpers_shouldClassifyPathAndInputConditions() throws Exception {
    Set<String> parameterNames = Set.of("input");

    assertThat(invokeBoolean("containsLikelyInputCondition", (Object) null, parameterNames))
        .isFalse();
    assertThat(
            invokeBoolean(
                "containsLikelyInputCondition",
                List.of("service != null", "state == READY"),
                parameterNames))
        .isFalse();
    assertThat(
            invokeBoolean(
                "containsLikelyInputCondition",
                List.of("input != null", "!inputOpt.isEmpty()"),
                parameterNames))
        .isTrue();

    RepresentativePath path = new RepresentativePath();
    path.setDescription("Custom scenario");
    path.setExpectedOutcomeHint("steady");
    path.setRequiredConditions(List.of("input != null"));
    assertThat(invokeBoolean("isLikelyPreconditionPath", path, parameterNames)).isTrue();

    path.setExpectedOutcomeHint("success");
    assertThat(invokeBoolean("isLikelyPreconditionPath", path, parameterNames)).isFalse();

    path.setExpectedOutcomeHint("steady");
    path.setDescription("Switch case status=\"NEW\"");
    assertThat(invokeBoolean("isLikelyPreconditionPath", path, parameterNames)).isFalse();
  }

  @Test
  void privateHelpers_shouldCoverCompareToAndWrapperChecks() throws Exception {
    Set<String> parameterNames = Set.of("amount");

    assertThat(
            invokeList(
                "normalizeNullOrCompareToCondition",
                "amount == null || amount.compareTo(limit) <= 0",
                parameterNames))
        .containsExactly("amount != null", "amount.compareTo(limit) > 0");
    assertThat(
            invokeList(
                "normalizeNullOrCompareToCondition",
                "amount.compareTo(limit) < 0 || amount == null",
                parameterNames))
        .containsExactly("amount != null", "amount.compareTo(limit) >= 0");
    assertThat(
            invokeList(
                "normalizeNullOrCompareToCondition",
                "external == null || external.compareTo(limit) <= 0",
                parameterNames))
        .isEmpty();

    assertThat(invokeBoolean("isCompareToZeroConstantExpression", "amount.compareTo(0) <= 0"))
        .isTrue();
    assertThat(
            invokeBoolean(
                "isCompareToZeroConstantExpression",
                "amount.compareTo(java.math.BigDecimal.ZERO) <= 0"))
        .isTrue();
    assertThat(invokeBoolean("isCompareToZeroConstantExpression", "amount.compareTo(limit) <= 0"))
        .isFalse();

    assertThat(invokeBoolean("isWrappedByParentheses", "(input)")).isTrue();
    assertThat(invokeBoolean("isWrappedByParentheses", "((input))")).isTrue();
    assertThat(invokeBoolean("isWrappedByParentheses", "(input) || ready")).isFalse();
    assertThat(invokeBoolean("isWrappedByParentheses", "(input")).isFalse();
    assertThat(invokeBoolean("isWrappedByParentheses", (String) null)).isFalse();
  }

  @Test
  void privateHelpers_shouldHandleNegatedPredicateVariants() throws Exception {
    Set<String> parameterNames = Set.of("input");

    assertThat(invokeList("normalizeNegatedPredicateCall", "!isSupported(input)", parameterNames))
        .containsExactly("isSupported(input)");
    assertThat(invokeList("normalizeNegatedPredicateCall", "!supports(input)", parameterNames))
        .containsExactly("supports(input)");
    assertThat(invokeList("normalizeNegatedPredicateCall", "!isValidFormat(raw)", parameterNames))
        .isEmpty();
    assertThat(invokeList("normalizeNegatedPredicateCall", "!state.isSuccess()", parameterNames))
        .isEmpty();
    assertThat(invokeList("normalizeNegatedPredicateCall", "!helper.isEmpty()", parameterNames))
        .isEmpty();
  }

  @Test
  void privateHelpers_shouldCoverCanonicalizeParameterTokenEdgeCases() throws Exception {
    assertThat(
            invokeStringBySignature(
                "canonicalizeParameterTokens", (String) null, Map.class, Map.of("user", "userId")))
        .isEmpty();
    assertThat(
            invokeStringBySignature(
                "canonicalizeParameterTokens", "user != null", Map.class, Map.of()))
        .isEqualTo("user != null");

    Map<String, String> malformedMap = new java.util.LinkedHashMap<>();
    malformedMap.put(null, "userId");
    malformedMap.put("user", "");
    malformedMap.put("id", "id");

    assertThat(
            invokeStringBySignature(
                "canonicalizeParameterTokens",
                "id != null && user != null",
                Map.class,
                malformedMap))
        .isEqualTo("id != null && user != null");
  }

  @Test
  void privateHelpers_shouldCoverTokenAndAssertionBranches() throws Exception {
    assertThat(invokeBooleanBySignature("containsParameterToken", "input != null", "input"))
        .isTrue();
    assertThat(invokeBooleanBySignature("containsParameterToken", "value != null", "input"))
        .isFalse();
    assertThat(invokeBooleanBySignature("containsParameterToken", null, "input")).isFalse();
    assertThat(invokeBooleanBySignature("containsParameterToken", "input != null", null)).isFalse();

    assertThat(invokeBooleanBySignature("matchesNonNullAssertion", "input != null", "input"))
        .isTrue();
    assertThat(invokeBooleanBySignature("matchesNonNullAssertion", "input は nullでない", "input"))
        .isTrue();
    assertThat(invokeBooleanBySignature("matchesNonNullAssertion", "input が nullではない", "input"))
        .isTrue();
    assertThat(
            invokeBooleanBySignature("matchesNonNullAssertion", "input must not be null", "input"))
        .isTrue();
    assertThat(
            invokeBooleanBySignature("matchesNonNullAssertion", "input must be non-null", "input"))
        .isTrue();
    assertThat(invokeBooleanBySignature("matchesNonNullAssertion", "input is not null", "input"))
        .isTrue();
    assertThat(invokeBooleanBySignature("matchesNonNullAssertion", "input null禁止", "input"))
        .isTrue();
    assertThat(invokeBooleanBySignature("matchesNonNullAssertion", "input is valid", "input"))
        .isFalse();

    Set<String> parameters = Set.of("input", "value");
    assertThat(
            invokeStringBySignature(
                "extractAssertedNonNullParameter", "- none", Set.class, parameters))
        .isEmpty();
    assertThat(
            invokeStringBySignature(
                "extractAssertedNonNullParameter", "- `input is not null`", Set.class, parameters))
        .isEqualTo("input");
    assertThat(
            invokeStringBySignature(
                "extractAssertedNonNullParameter",
                "- `value must be non-null`",
                Set.class,
                parameters))
        .isEqualTo("value");
    assertThat(
            invokeStringBySignature(
                "extractAssertedNonNullParameter", "- something else", Set.class, parameters))
        .isEmpty();
  }

  @Test
  void privateHelpers_shouldCoverParsingAndResolutionBranches() throws Exception {
    assertThat(invokeString("extractDeclarationHeader", "public void run() { return; }"))
        .isEqualTo("public void run()");
    assertThat(invokeString("extractDeclarationHeader", "public void run()")).isEmpty();
    assertThat(invokeString("extractDeclarationHeader", "  ")).isEmpty();

    assertThat(invokeString("extractParameterName", "@NotNull final String input"))
        .isEqualTo("input");
    assertThat(invokeString("extractParameterName", "String Input")).isEmpty();
    assertThat(invokeString("extractParameterName", "123")).isEmpty();
    assertThat(invokeString("extractParameterName", "   ")).isEmpty();

    assertThat(invokeInt("skipSpaces", "   input", 0)).isEqualTo(3);
    assertThat(invokeInt("skipSpaces", "input", 2)).isEqualTo(2);
    assertThat(invokeInt("skipSpaces", "", 1)).isEqualTo(1);

    Set<String> names = Set.of("input", "items");
    assertThat(invokeStringBySignature("resolveParameterName", "input.value", Set.class, names))
        .isEqualTo("input");
    assertThat(invokeStringBySignature("resolveParameterName", "items[0]", Set.class, names))
        .isEqualTo("items");
    assertThat(invokeStringBySignature("resolveParameterName", "other.value", Set.class, names))
        .isEmpty();

    assertThat(
            invokeStringBySignature(
                "extractNonNullParameterFromCondition", "input != null", Set.class, names))
        .isEqualTo("input");
    assertThat(
            invokeStringBySignature(
                "extractNonNullParameterFromCondition", "input == null", Set.class, names))
        .isEmpty();

    MethodInfo method = new MethodInfo();
    method.setName("work");
    method.setSourceCode("private int other(int x) { return x; }");
    Map<String, String> nameMap =
        (Map<String, String>)
            invokePrivate(
                "extractMethodParameterNameMapFromSourceCode",
                new Class<?>[] {MethodInfo.class},
                method);
    assertThat(nameMap).isEmpty();

    String canonicalized =
        invokeStringBySignature(
            "canonicalizeParameterTokens",
            "user != null && USER != null",
            Map.class,
            Map.of("user", "userId"));
    assertThat(canonicalized).isEqualTo("userId != null && userId != null");
  }

  @Test
  void privateHelpers_shouldCoverGuardHeuristicBranches() throws Exception {
    assertThat(invokeBooleanBySignature("hasDereferenceUsage", "return input.trim();", "input"))
        .isTrue();
    assertThat(invokeBooleanBySignature("hasDereferenceUsage", "return value;", "input")).isFalse();

    assertThat(
            invokeBooleanBySignature(
                "hasNullShortCircuitGuardedDereference",
                "if (input == null || input.isBlank()) return;",
                "input"))
        .isTrue();
    assertThat(
            invokeBooleanBySignature(
                "hasNullShortCircuitGuardedDereference", "if (ok || input == null) {}", "input"))
        .isTrue();
    assertThat(
            invokeBooleanBySignature(
                "hasNullShortCircuitGuardedDereference", "if (input != null && ready) {}", "input"))
        .isTrue();
    assertThat(
            invokeBooleanBySignature(
                "hasNullShortCircuitGuardedDereference", "if (ready && input != null) {}", "input"))
        .isTrue();
    assertThat(
            invokeBooleanBySignature(
                "hasNullShortCircuitGuardedDereference", "input == null ? a : b", "input"))
        .isTrue();
    assertThat(
            invokeBooleanBySignature(
                "hasNullShortCircuitGuardedDereference", "input != null ? a : b", "input"))
        .isTrue();
    assertThat(
            invokeBooleanBySignature(
                "hasNullShortCircuitGuardedDereference", "if (ready) { input.trim(); }", "input"))
        .isFalse();

    assertThat(
            invokeBooleanBySignature(
                "hasExplicitNullReturningGuard", "if (input == null) return null;", "input"))
        .isTrue();
    assertThat(
            invokeBooleanBySignature(
                "hasExplicitNullReturningGuard", "if (input == null) { return null; }", "input"))
        .isTrue();
    assertThat(
            invokeBooleanBySignature(
                "hasExplicitNullReturningGuard", "if (input == null) return value;", "input"))
        .isFalse();

    MethodInfo method = new MethodInfo();
    method.setSourceCode("if (input == null) { return null; }");
    assertThat(invokeBoolean("isNullReturningGuard", "input == null", Set.of("input"), method))
        .isTrue();
    assertThat(invokeBoolean("isNullReturningGuard", "input == null", Set.of("other"), method))
        .isFalse();
    assertThat(invokeBoolean("isNullReturningGuard", "input != null", Set.of("input"), method))
        .isFalse();

    Set<String> params = Set.of("input");
    assertThat(
            invokeBoolean("isLikelyInternalFailureCondition", "early-return when closed", params))
        .isTrue();
    assertThat(invokeBoolean("isLikelyInternalFailureCondition", "!state.isSuccess()", params))
        .isTrue();
    assertThat(invokeBoolean("isLikelyInternalFailureCondition", "result.isPresent()", params))
        .isTrue();
    assertThat(invokeBoolean("isLikelyInternalFailureCondition", "!valueOpt.isEmpty()", params))
        .isTrue();
    assertThat(
            invokeBoolean(
                "isLikelyInternalFailureCondition", "input.compareTo(limit) <= 0", params))
        .isTrue();
    assertThat(invokeBoolean("isLikelyInternalFailureCondition", "left && right", params)).isTrue();
    assertThat(invokeBoolean("isLikelyInternalFailureCondition", "input != null", params))
        .isFalse();

    RepresentativePath boundaryPath = new RepresentativePath();
    boundaryPath.setExpectedOutcomeHint("boundary");
    boundaryPath.setDescription("normal branch");
    assertThat(invokeBoolean("isLikelyBoundaryOnlyPath", boundaryPath)).isTrue();
    boundaryPath.setDescription("validation boundary");
    assertThat(invokeBoolean("isLikelyBoundaryOnlyPath", boundaryPath)).isFalse();

    RepresentativePath earlyPath = new RepresentativePath();
    earlyPath.setExpectedOutcomeHint("early-return");
    earlyPath.setDescription("normal branch");
    assertThat(invokeBoolean("isLikelyEarlyReturnOnlyPath", earlyPath)).isTrue();
    earlyPath.setDescription("validation failure");
    assertThat(invokeBoolean("isLikelyEarlyReturnOnlyPath", earlyPath)).isFalse();

    assertThat(invokeBoolean("isLikelyLiteralExpression", "\"abc\"")).isTrue();
    assertThat(invokeBoolean("isLikelyLiteralExpression", "true")).isTrue();
    assertThat(invokeBoolean("isLikelyLiteralExpression", "42")).isTrue();
    assertThat(invokeBoolean("isLikelyLiteralExpression", "input != null")).isFalse();
  }

  @Test
  void privateHelpers_shouldCoverVoidNormalizationBranch() throws Exception {
    LinkedHashSet<String> values = new LinkedHashSet<>();
    invokeVoid(
        "addNormalizedPreconditions",
        new Class<?>[] {LinkedHashSet.class, String.class, Set.class, boolean.class},
        values,
        "input == null",
        Set.of("input"),
        false);
    invokeVoid(
        "addNormalizedPreconditions",
        new Class<?>[] {LinkedHashSet.class, String.class, Set.class, boolean.class},
        values,
        "",
        Set.of("input"),
        false);
    assertThat(values).containsExactly("input != null");
  }

  private MethodInfo method(String name, String signature, String sourceCode) {
    MethodInfo method = new MethodInfo();
    method.setName(name);
    method.setSignature(signature);
    method.setSourceCode(sourceCode);
    return method;
  }

  private GuardSummary guard(GuardType type, String condition) {
    return guard(type, condition, List.of());
  }

  private GuardSummary guard(GuardType type, String condition, List<String> effects) {
    GuardSummary guard = new GuardSummary();
    guard.setType(type);
    guard.setCondition(condition);
    guard.setEffects(effects);
    return guard;
  }

  private RepresentativePath path(
      String description, String expectedOutcomeHint, String... conditions) {
    RepresentativePath path = new RepresentativePath();
    path.setDescription(description);
    path.setExpectedOutcomeHint(expectedOutcomeHint);
    path.setRequiredConditions(List.of(conditions));
    return path;
  }

  private List<String> invokeList(String methodName, String condition, Set<String> parameterNames)
      throws Exception {
    Method method =
        LlmFallbackPreconditionExtractor.class.getDeclaredMethod(
            methodName, String.class, Set.class);
    method.setAccessible(true);
    return (List<String>) method.invoke(extractor, condition, parameterNames);
  }

  private List<String> invokeList(
      String methodName, String condition, Set<String> parameterNames, boolean allowRaw)
      throws Exception {
    Method method =
        LlmFallbackPreconditionExtractor.class.getDeclaredMethod(
            methodName, String.class, Set.class, boolean.class);
    method.setAccessible(true);
    return (List<String>) method.invoke(extractor, condition, parameterNames, allowRaw);
  }

  private boolean invokeBoolean(String methodName, Object first, Object second) throws Exception {
    Method method;
    if ("containsLikelyInputCondition".equals(methodName)) {
      method =
          LlmFallbackPreconditionExtractor.class.getDeclaredMethod(
              methodName, List.class, Set.class);
    } else if ("isLikelyPreconditionPath".equals(methodName)) {
      method =
          LlmFallbackPreconditionExtractor.class.getDeclaredMethod(
              methodName, RepresentativePath.class, Set.class);
    } else {
      method =
          LlmFallbackPreconditionExtractor.class.getDeclaredMethod(
              methodName, String.class, Set.class);
    }
    method.setAccessible(true);
    return (boolean) method.invoke(extractor, first, second);
  }

  private boolean invokeBoolean(String methodName, String condition) throws Exception {
    Method method =
        LlmFallbackPreconditionExtractor.class.getDeclaredMethod(methodName, String.class);
    method.setAccessible(true);
    return (boolean) method.invoke(extractor, condition);
  }

  private boolean invokeBooleanBySignature(String methodName, String first, String second)
      throws Exception {
    Method method =
        LlmFallbackPreconditionExtractor.class.getDeclaredMethod(
            methodName, String.class, String.class);
    method.setAccessible(true);
    return (boolean) method.invoke(extractor, first, second);
  }

  private String invokeString(String methodName, String value) throws Exception {
    Method method =
        LlmFallbackPreconditionExtractor.class.getDeclaredMethod(methodName, String.class);
    method.setAccessible(true);
    return (String) method.invoke(extractor, value);
  }

  private String invokeStringBySignature(
      String methodName, String first, Class<?> secondType, Object second) throws Exception {
    Method method =
        LlmFallbackPreconditionExtractor.class.getDeclaredMethod(
            methodName, String.class, secondType);
    method.setAccessible(true);
    return (String) method.invoke(extractor, first, second);
  }

  private int invokeInt(String methodName, String value, int index) throws Exception {
    Method method =
        LlmFallbackPreconditionExtractor.class.getDeclaredMethod(
            methodName, String.class, int.class);
    method.setAccessible(true);
    return (int) method.invoke(extractor, value, index);
  }

  private boolean invokeBoolean(String methodName, String first, Set<String> second)
      throws Exception {
    Method method =
        LlmFallbackPreconditionExtractor.class.getDeclaredMethod(
            methodName, String.class, Set.class);
    method.setAccessible(true);
    return (boolean) method.invoke(extractor, first, second);
  }

  private boolean invokeBoolean(
      String methodName, String condition, Set<String> parameterNames, MethodInfo methodInfo)
      throws Exception {
    Method method =
        LlmFallbackPreconditionExtractor.class.getDeclaredMethod(
            methodName, String.class, Set.class, MethodInfo.class);
    method.setAccessible(true);
    return (boolean) method.invoke(extractor, condition, parameterNames, methodInfo);
  }

  private boolean invokeBoolean(String methodName, RepresentativePath path) throws Exception {
    Method method =
        LlmFallbackPreconditionExtractor.class.getDeclaredMethod(
            methodName, RepresentativePath.class);
    method.setAccessible(true);
    return (boolean) method.invoke(extractor, path);
  }

  private void invokeVoid(String methodName, Class<?>[] paramTypes, Object... args)
      throws Exception {
    Method method =
        LlmFallbackPreconditionExtractor.class.getDeclaredMethod(methodName, paramTypes);
    method.setAccessible(true);
    method.invoke(extractor, args);
  }

  private Object invokePrivate(String methodName, Class<?>[] paramTypes, Object... args)
      throws Exception {
    Method method =
        LlmFallbackPreconditionExtractor.class.getDeclaredMethod(methodName, paramTypes);
    method.setAccessible(true);
    return method.invoke(extractor, args);
  }
}
