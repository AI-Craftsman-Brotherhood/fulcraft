package com.craftsmanbro.fulcraft.plugins.document.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.plugins.analysis.model.BranchSummary;
import com.craftsmanbro.fulcraft.plugins.analysis.model.GuardSummary;
import com.craftsmanbro.fulcraft.plugins.analysis.model.GuardType;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.RepresentativePath;
import com.craftsmanbro.fulcraft.plugins.document.core.llm.analysis.LlmFallbackPreconditionExtractor;
import com.craftsmanbro.fulcraft.plugins.document.core.llm.analysis.LlmPathConditionHeuristics;
import java.util.List;
import org.junit.jupiter.api.Test;

class LlmFallbackPreconditionExtractorTest {

  private final LlmPathConditionHeuristics heuristics = new LlmPathConditionHeuristics();
  private final LlmFallbackPreconditionExtractor extractor =
      new LlmFallbackPreconditionExtractor(
          heuristics::isLikelyFlowCondition, heuristics::isLikelyErrorIndicator);

  @Test
  void collectsFallbackPreconditionsWhenSignatureHasOnlyTypes() {
    MethodInfo method = methodWithTypeOnlySignature();

    List<String> preconditions = extractor.collectFallbackPreconditions(method);

    assertThat(preconditions)
        .contains("orderId != null", "!orderId.isBlank()", "amount != null")
        .doesNotContain("orderId == null || orderId.isBlank()");
  }

  @Test
  void doesNotTreatTypeOnlySignatureMethodAsNoArgWhenSectionUsesParameterCondition() {
    MethodInfo method = methodWithTypeOnlySignature();

    boolean unsupported =
        extractor.containsUnsupportedNoArgPrecondition("- `orderId != null`", method);

    assertThat(unsupported).isFalse();
  }

  @Test
  void canonicalizesParameterNameCaseAndDeduplicatesEquivalentPreconditions() {
    MethodInfo method = new MethodInfo();
    method.setName("validate");
    method.setSignature("validate(String, String)");
    method.setSourceCode(
        """
                        public Result validate(String orderId, String customerId) {
                            if (orderId == null || orderId.isBlank()) {
                                return Result.failure("order");
                            }
                            if (customerId == null || customerId.isBlank()) {
                                return Result.failure("customer");
                            }
                            String normalized = customerId.trim();
                            return Result.success();
                        }
                        """);

    BranchSummary summary = new BranchSummary();
    summary.setGuards(
        List.of(
            failGuard("orderId == null || orderId.isBlank()"),
            failGuard("customerId == null || customerId.isBlank()")));
    method.setBranchSummary(summary);

    List<String> preconditions = extractor.collectFallbackPreconditions(method);

    assertThat(preconditions)
        .contains(
            "orderId != null", "!orderId.isBlank()", "customerId != null", "!customerId.isBlank()");
    assertThat(preconditions)
        .noneMatch(value -> value.contains("orderid") || value.contains("customerid"));
    assertThat(preconditions.stream().filter("customerId != null"::equals).count()).isEqualTo(1);
  }

  @Test
  void doesNotInferPreconditionFromBoundaryOnlyPathAndNullShortCircuitExpression() {
    MethodInfo method = new MethodInfo();
    method.setName("isEmpty");
    method.setSignature("isEmpty(String str)");
    method.setSourceCode(
        """
                        public static boolean isEmpty(String str) {
                            return str == null || str.trim().isEmpty();
                        }
                        """);

    RepresentativePath boundaryPath = new RepresentativePath();
    boundaryPath.setId("path-1");
    boundaryPath.setDescription("Boundary condition str == null");
    boundaryPath.setExpectedOutcomeHint("boundary");
    boundaryPath.setRequiredConditions(List.of("str == null"));
    RepresentativePath earlyReturnPath = new RepresentativePath();
    earlyReturnPath.setId("path-2");
    earlyReturnPath.setDescription("Early return path");
    earlyReturnPath.setExpectedOutcomeHint("early-return");
    earlyReturnPath.setRequiredConditions(List.of("str == null"));
    method.setRepresentativePaths(List.of(boundaryPath, earlyReturnPath));

    List<String> preconditions = extractor.collectFallbackPreconditions(method);

    assertThat(preconditions).isEmpty();
  }

  @Test
  void doesNotTreatNullReturningGuardAsMandatoryPrecondition() {
    MethodInfo method = new MethodInfo();
    method.setName("toUpperCase");
    method.setSignature("toUpperCase(String str)");
    method.setSourceCode(
        """
                        public static String toUpperCase(String str) {
                            if (str == null) {
                                return null;
                            }
                            return str.toUpperCase();
                        }
                        """);

    BranchSummary summary = new BranchSummary();
    summary.setGuards(List.of(failGuard("str == null")));
    method.setBranchSummary(summary);

    RepresentativePath boundaryPath = new RepresentativePath();
    boundaryPath.setId("path-1");
    boundaryPath.setDescription("Boundary condition str == null");
    boundaryPath.setExpectedOutcomeHint("boundary");
    boundaryPath.setRequiredConditions(List.of("str == null"));
    method.setRepresentativePaths(List.of(boundaryPath));

    List<String> preconditions = extractor.collectFallbackPreconditions(method);

    assertThat(preconditions).isEmpty();
  }

  @Test
  void normalizesNullOrCompareToGuardIntoPositiveContract() {
    MethodInfo method = new MethodInfo();
    method.setName("processPayment");
    method.setSignature("processPayment(String, BigDecimal)");
    method.setSourceCode(
        """
                        public Result processPayment(String orderId, BigDecimal amount) {
                            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                                return Result.failure("amount");
                            }
                            return Result.success();
                        }
                        """);

    BranchSummary summary = new BranchSummary();
    summary.setGuards(
        List.of(failGuard("amount == null || amount.compareTo(BigDecimal.ZERO) <= 0")));
    method.setBranchSummary(summary);

    List<String> preconditions = extractor.collectFallbackPreconditions(method);

    assertThat(preconditions).contains("amount != null", "amount.compareTo(BigDecimal.ZERO) > 0");
    assertThat(preconditions)
        .doesNotContain("amount == null || amount.compareTo(BigDecimal.ZERO) <= 0");
  }

  @Test
  void fallsBackToSourceParameterNamesWhenSignatureContainsOnlyTypesIncludingPrimitive() {
    MethodInfo method = new MethodInfo();
    method.setName("truncate");
    method.setSignature("truncate(String, int)");
    method.setSourceCode(
        """
                        public static String truncate(String str, int length) {
                            if (str == null) {
                                return null;
                            }
                            if (str.length() <= length) {
                                return str;
                            }
                            return str.substring(0, length);
                        }
                        """);

    BranchSummary summary = new BranchSummary();
    summary.setGuards(List.of(failGuard("str == null")));
    method.setBranchSummary(summary);

    RepresentativePath earlyReturnPath = new RepresentativePath();
    earlyReturnPath.setId("path-1");
    earlyReturnPath.setDescription("Early return path");
    earlyReturnPath.setExpectedOutcomeHint("early-return");
    earlyReturnPath.setRequiredConditions(List.of("str == null"));

    RepresentativePath boundaryPath = new RepresentativePath();
    boundaryPath.setId("path-2");
    boundaryPath.setDescription("Boundary condition str == null");
    boundaryPath.setExpectedOutcomeHint("boundary");
    boundaryPath.setRequiredConditions(List.of("str == null"));

    method.setRepresentativePaths(List.of(earlyReturnPath, boundaryPath));

    List<String> preconditions = extractor.collectFallbackPreconditions(method);

    assertThat(preconditions).isEmpty();
  }

  private GuardSummary failGuard(String condition) {
    GuardSummary guard = new GuardSummary();
    guard.setType(GuardType.FAIL_GUARD);
    guard.setCondition(condition);
    return guard;
  }

  private MethodInfo methodWithTypeOnlySignature() {
    MethodInfo method = new MethodInfo();
    method.setName("processPayment");
    method.setSignature("processPayment(String, BigDecimal, String, String)");
    method.setSourceCode(
        """
                        public PaymentResult processPayment(String orderId, BigDecimal amount, String paymentMethod, String currency) {
                            if (orderId == null || orderId.isBlank()) {
                                return PaymentResult.failure("orderId");
                            }
                            if (amount == null) {
                                return PaymentResult.failure("amount");
                            }
                            return PaymentResult.success();
                        }
                        """);

    GuardSummary orderIdGuard = new GuardSummary();
    orderIdGuard.setType(GuardType.FAIL_GUARD);
    orderIdGuard.setCondition("orderId == null || orderId.isBlank()");

    GuardSummary amountGuard = new GuardSummary();
    amountGuard.setType(GuardType.FAIL_GUARD);
    amountGuard.setCondition("amount == null");

    BranchSummary summary = new BranchSummary();
    summary.setGuards(List.of(orderIdGuard, amountGuard));
    method.setBranchSummary(summary);
    return method;
  }
}
