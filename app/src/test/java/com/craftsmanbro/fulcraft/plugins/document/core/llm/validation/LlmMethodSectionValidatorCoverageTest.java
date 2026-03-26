package com.craftsmanbro.fulcraft.plugins.document.core.llm.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmDocumentTextUtils;
import com.craftsmanbro.fulcraft.plugins.document.core.llm.analysis.LlmMethodFlowFactsExtractor.SwitchCaseFact;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class LlmMethodSectionValidatorCoverageTest {

  @Test
  void validate_shouldRejectVaguePreconditions() {
    LlmMethodSectionValidator validator = newValidator();
    MethodInfo method = method("processOrder", "public void processOrder()");
    String document =
        document(
            "- appropriate value is required",
            "- Completed.",
            "- Completed.",
            "- None",
            "- None",
            "- None",
            "processOrder");
    List<String> reasons = new ArrayList<>();

    validator.validate(document, List.of(method), reasons, false, "n/a");

    assertReasonContains(reasons, "Preconditions are vague");
  }

  @Test
  void validate_shouldRejectFailureSidePreconditions() {
    LlmMethodSectionValidator validator = newValidator();
    MethodInfo method = method("processOrder", "public void processOrder()");
    String document =
        document(
            "- !result.isSuccess()",
            "- Completed.",
            "- Completed.",
            "- None",
            "- None",
            "- None",
            "processOrder");
    List<String> reasons = new ArrayList<>();

    validator.validate(document, List.of(method), reasons, false, "n/a");

    assertReasonContains(reasons, "failure-side checks");
  }

  @Test
  void validate_shouldRejectUnsupportedNoArgPreconditions() {
    LlmMethodSectionValidator validator =
        newValidator(
            method -> false,
            methodName -> false,
            (section, method) -> true,
            (section, method) -> false,
            method -> List.of(),
            method -> false,
            method -> List.of());
    MethodInfo method = method("processOrder", "public void processOrder()");
    String document =
        document(
            "- None", "- Completed.", "- Completed.", "- None", "- None", "- None", "processOrder");
    List<String> reasons = new ArrayList<>();

    validator.validate(document, List.of(method), reasons, false, "n/a");

    assertReasonContains(reasons, "No-arg method Preconditions contain unsupported constraints");
  }

  @Test
  void validate_shouldRejectUnsupportedNonNullPreconditions() {
    LlmMethodSectionValidator validator =
        newValidator(
            method -> false,
            methodName -> false,
            (section, method) -> false,
            (section, method) -> true,
            method -> List.of(),
            method -> false,
            method -> List.of());
    MethodInfo method = method("processOrder", "public void processOrder()");
    String document =
        document(
            "- None", "- Completed.", "- Completed.", "- None", "- None", "- None", "processOrder");
    List<String> reasons = new ArrayList<>();

    validator.validate(document, List.of(method), reasons, false, "n/a");

    assertReasonContains(reasons, "unsupported non-null assumptions");
  }

  @Test
  void validate_shouldRejectMissingSourceBackedPreconditions() {
    LlmMethodSectionValidator validator =
        newValidator(
            method -> false,
            methodName -> false,
            (section, method) -> false,
            (section, method) -> false,
            method -> List.of("request != null"),
            method -> false,
            method -> List.of());
    MethodInfo method = method("processOrder", "public void processOrder()");
    String document =
        document(
            "- None", "- Completed.", "- Completed.", "- None", "- None", "- None", "processOrder");
    List<String> reasons = new ArrayList<>();

    validator.validate(document, List.of(method), reasons, false, "n/a");

    assertReasonContains(reasons, "missing source-backed conditions");
    assertReasonContains(reasons, "request != null");
  }

  @Test
  void validate_shouldRejectEarlyReturnWhenIncompatible() {
    LlmMethodSectionValidator validator =
        newValidator(
            method -> false,
            methodName -> false,
            (section, method) -> false,
            (section, method) -> false,
            method -> List.of(),
            method -> true,
            method -> List.of());
    MethodInfo method = method("processOrder", "public void processOrder()");
    String document =
        document(
            "- None",
            "- Completed.",
            "- path-main -> early-return",
            "- None",
            "- None",
            "- None",
            "processOrder");
    List<String> reasons = new ArrayList<>();

    validator.validate(document, List.of(method), reasons, false, "n/a");

    assertReasonContains(reasons, "Early-return wording is incompatible");
  }

  @Test
  void validate_shouldRejectSuccessPostconditionForUncertainDynamicResolution() {
    LlmMethodSectionValidator validator =
        newValidator(
            method -> true,
            methodName -> false,
            (section, method) -> false,
            (section, method) -> false,
            method -> List.of(),
            method -> false,
            method -> List.of());
    MethodInfo method = method("processOrder", "public void processOrder()");
    String document =
        document(
            "- None",
            "- Expected outcome: success",
            "- None",
            "- None",
            "- None",
            "- None",
            "processOrder");
    List<String> reasons = new ArrayList<>();

    validator.validate(document, List.of(method), reasons, false, "n/a");

    assertReasonContains(
        reasons, "Postconditions assert success for a method with uncertain dynamic resolution");
  }

  @Test
  void validate_shouldRejectFailureFactorySuccessWordingInNormalFlow() {
    LlmMethodSectionValidator validator =
        newValidator(
            method -> false,
            methodName -> true,
            (section, method) -> false,
            (section, method) -> false,
            method -> List.of(),
            method -> false,
            method -> List.of());
    MethodInfo method = method("processOrder", "public void processOrder()");
    String document =
        document(
            "- None", "- None", "- Result: success", "- None", "- None", "- None", "processOrder");
    List<String> reasons = new ArrayList<>();

    validator.validate(document, List.of(method), reasons, false, "n/a");

    assertReasonContains(reasons, "Failure-factory Normal Flow uses success wording");
  }

  @Test
  void validate_shouldRejectMissingSwitchCaseCoverage() {
    LlmMethodSectionValidator validator =
        newValidator(
            method -> false,
            methodName -> false,
            (section, method) -> false,
            (section, method) -> false,
            method -> List.of(),
            method -> false,
            method ->
                List.of(
                    new SwitchCaseFact(
                        "switch-status-paid", "Switch case status=\"PAID\"", "case-\"PAID\"")));
    MethodInfo method = method("processOrder", "public void processOrder()");
    String document =
        document("- None", "- None", "- None", "- None", "- None", "- None", "processOrder");
    List<String> reasons = new ArrayList<>();

    validator.validate(document, List.of(method), reasons, false, "n/a");

    assertReasonContains(reasons, "Switch-case coverage is incomplete");
    assertReasonContains(reasons, "Switch case status=\"PAID\"");
  }

  @Test
  void validate_shouldRejectAmbiguousDependencyPlaceholder() {
    LlmMethodSectionValidator validator = newValidator();
    MethodInfo method = method("processOrder", "public void processOrder()");
    String document =
        document("- None", "- None", "- None", "- None", "- others", "- None", "processOrder");
    List<String> reasons = new ArrayList<>();

    validator.validate(document, List.of(method), reasons, false, "n/a");

    assertReasonContains(reasons, "Dependencies contain ambiguous placeholders");
  }

  @Test
  void validate_shouldRejectFailureLikeNormalFlowForRegularMethod() {
    LlmMethodSectionValidator validator = newValidator();
    MethodInfo method = method("processOrder", "public void processOrder()");
    String document =
        document(
            "- None",
            "- None",
            "- failure path when input is invalid",
            "- None",
            "- None",
            "- None",
            "processOrder");
    List<String> reasons = new ArrayList<>();

    validator.validate(document, List.of(method), reasons, false, "n/a");

    assertReasonContains(reasons, "Normal Flow includes early-return/failure/boundary paths");
  }

  @Test
  void validate_shouldAllowFailureLikeNormalFlowForConstructorHeading() {
    LlmMethodSectionValidator validator = newValidator();
    MethodInfo method = method("OrderService", "public OrderService()");
    String document =
        document(
            "- None",
            "- None",
            "- failure path when state is invalid",
            "- None",
            "- None",
            "- None",
            "OrderService");
    List<String> reasons = new ArrayList<>();

    validator.validate(document, List.of(method), reasons, false, "n/a");

    assertThat(reasons).isEmpty();
  }

  @Test
  void validate_shouldRejectSuccessNormalFlowForUncertainDynamicResolution() {
    LlmMethodSectionValidator validator =
        newValidator(
            method -> true,
            methodName -> false,
            (section, method) -> false,
            (section, method) -> false,
            method -> List.of(),
            method -> false,
            method -> List.of());
    MethodInfo method = method("processOrder", "public void processOrder()");
    String document =
        document(
            "- None",
            "- None",
            "- Expected outcome: success",
            "- None",
            "- None",
            "- None",
            "processOrder");
    List<String> reasons = new ArrayList<>();

    validator.validate(document, List.of(method), reasons, false, "n/a");

    assertReasonContains(reasons, "Normal Flow asserts success for a method with uncertain");
  }

  @Test
  void validate_shouldRejectSuccessTestViewpointsForUncertainDynamicResolution() {
    LlmMethodSectionValidator validator =
        newValidator(
            method -> true,
            methodName -> false,
            (section, method) -> false,
            (section, method) -> false,
            method -> List.of(),
            method -> false,
            method -> List.of());
    MethodInfo method = method("processOrder", "public void processOrder()");
    String document =
        document(
            "- None", "- None", "- None", "- None", "- None", "- Result (success)", "processOrder");
    List<String> reasons = new ArrayList<>();

    validator.validate(document, List.of(method), reasons, false, "n/a");

    assertReasonContains(reasons, "Test Viewpoints assert success for a method with uncertain");
  }

  @Test
  void validate_shouldRejectFailureFactorySuccessWordingInPostconditions() {
    LlmMethodSectionValidator validator =
        newValidator(
            method -> false,
            methodName -> true,
            (section, method) -> false,
            (section, method) -> false,
            method -> List.of(),
            method -> false,
            method -> List.of());
    MethodInfo method = method("processOrder", "public void processOrder()");
    String document =
        document(
            "- None", "- Result: success", "- None", "- None", "- None", "- None", "processOrder");
    List<String> reasons = new ArrayList<>();

    validator.validate(document, List.of(method), reasons, false, "n/a");

    assertReasonContains(reasons, "Failure-factory postconditions use success wording");
  }

  @Test
  void validate_shouldRejectFailureFactorySuccessWordingInTestViewpoints() {
    LlmMethodSectionValidator validator =
        newValidator(
            method -> false,
            methodName -> true,
            (section, method) -> false,
            (section, method) -> false,
            method -> List.of(),
            method -> false,
            method -> List.of());
    MethodInfo method = method("processOrder", "public void processOrder()");
    String document =
        document(
            "- None",
            "- None",
            "- None",
            "- None",
            "- None",
            "- outcome (success)",
            "processOrder");
    List<String> reasons = new ArrayList<>();

    validator.validate(document, List.of(method), reasons, false, "n/a");

    assertReasonContains(reasons, "Failure-factory Test Viewpoints use success wording");
  }

  @Test
  void containsSuccessOutcomeToken_shouldRecognizeAllSupportedPhrases() throws Exception {
    LlmMethodSectionValidator validator = newValidator();
    List<String> successLines =
        List.of(
            "期待結果: success",
            "expected outcome: success",
            "結果: success",
            "result: success",
            "結果（success）",
            "result (success)",
            "outcome (success)",
            "path-main -> success");

    for (String line : successLines) {
      assertThat(
              invokePrivateBoolean(
                  validator, "containsSuccessOutcomeToken", new Class<?>[] {String.class}, line))
          .isTrue();
    }
    assertThat(
            invokePrivateBoolean(
                validator,
                "containsSuccessOutcomeToken",
                new Class<?>[] {String.class},
                "result: pending"))
        .isFalse();
    assertThat(
            invokePrivateBoolean(
                validator,
                "containsSuccessOutcomeToken",
                new Class<?>[] {String.class},
                (Object) null))
        .isFalse();
  }

  @Test
  void containsFailureSidePrecondition_shouldDetectFailurePatterns() throws Exception {
    LlmMethodSectionValidator validator = newValidator();
    assertThat(
            invokePrivateBoolean(
                validator,
                "containsFailureSidePrecondition",
                new Class<?>[] {String.class},
                "- !result.isSuccess()"))
        .isTrue();
    assertThat(
            invokePrivateBoolean(
                validator,
                "containsFailureSidePrecondition",
                new Class<?>[] {String.class},
                "- response.isPresent()"))
        .isTrue();
    assertThat(
            invokePrivateBoolean(
                validator,
                "containsFailureSidePrecondition",
                new Class<?>[] {String.class},
                "- !userOpt.isEmpty()"))
        .isTrue();
    assertThat(
            invokePrivateBoolean(
                validator,
                "containsFailureSidePrecondition",
                new Class<?>[] {String.class},
                "- amount.compareTo(limit) > 0"))
        .isTrue();
    assertThat(
            invokePrivateBoolean(
                validator,
                "containsFailureSidePrecondition",
                new Class<?>[] {String.class},
                "- amount.compareTo(BigDecimal.ZERO) > 0"))
        .isFalse();
    assertThat(
            invokePrivateBoolean(
                validator,
                "containsFailureSidePrecondition",
                new Class<?>[] {String.class},
                "- amount.compareTo(limit) > (0)"))
        .isFalse();
  }

  @Test
  void containsEquivalentNonNullPrecondition_shouldAcceptEquivalentForms() throws Exception {
    LlmMethodSectionValidator validator = newValidator();
    List<String> equivalentSections =
        List.of(
            "request!=null",
            "requestはnullでない",
            "requestがnullでない",
            "requestはnullではない",
            "requestがnullではない",
            "requestnull不可",
            "requestnull禁止",
            "requestmustnotbenull",
            "requestmustbenon-null",
            "requestisnotnull",
            "requestnon-null");

    for (String section : equivalentSections) {
      assertThat(
              invokePrivateBoolean(
                  validator,
                  "containsEquivalentNonNullPrecondition",
                  new Class<?>[] {String.class, String.class},
                  section,
                  "request != null"))
          .isTrue();
    }
    assertThat(
            invokePrivateBoolean(
                validator,
                "containsEquivalentNonNullPrecondition",
                new Class<?>[] {String.class, String.class},
                "responsenon-null",
                "request != null"))
        .isFalse();
    assertThat(
            invokePrivateBoolean(
                validator,
                "containsEquivalentNonNullPrecondition",
                new Class<?>[] {String.class, String.class},
                "request!=null",
                "request > 0"))
        .isFalse();
  }

  @Test
  void containsSwitchCaseCoverage_shouldSupportAllFallbackPatterns() throws Exception {
    LlmMethodSectionValidator validator = newValidator();
    SwitchCaseFact caseFact =
        new SwitchCaseFact("switch-status-paid", "Switch case status=\"PAID\"", "case-\"PAID\"");
    SwitchCaseFact defaultFact =
        new SwitchCaseFact("switch-default-status", "Switch default status", "default");

    assertThat(
            invokePrivateBoolean(
                validator,
                "containsSwitchCaseCoverage",
                new Class<?>[] {String.class, SwitchCaseFact.class},
                "switch-status-paid is covered",
                caseFact))
        .isTrue();
    assertThat(
            invokePrivateBoolean(
                validator,
                "containsSwitchCaseCoverage",
                new Class<?>[] {String.class, SwitchCaseFact.class},
                "switch status handles PAID value",
                caseFact))
        .isTrue();
    assertThat(
            invokePrivateBoolean(
                validator,
                "containsSwitchCaseCoverage",
                new Class<?>[] {String.class, SwitchCaseFact.class},
                "case paid branch",
                caseFact))
        .isTrue();
    assertThat(
            invokePrivateBoolean(
                validator,
                "containsSwitchCaseCoverage",
                new Class<?>[] {String.class, SwitchCaseFact.class},
                "default status branch",
                defaultFact))
        .isTrue();
    assertThat(
            invokePrivateBoolean(
                validator,
                "containsSwitchCaseCoverage",
                new Class<?>[] {String.class, SwitchCaseFact.class},
                "unknown branch",
                caseFact))
        .isFalse();
    assertThat(
            invokePrivateBoolean(
                validator,
                "containsSwitchCaseCoverage",
                new Class<?>[] {String.class, SwitchCaseFact.class},
                null,
                caseFact))
        .isFalse();
  }

  @Test
  void containsSwitchCaseCoverage_shouldHandleBlankAndNonMatchingInputs() throws Exception {
    LlmMethodSectionValidator validator = newValidator();
    SwitchCaseFact caseFact =
        new SwitchCaseFact("switch-status-paid", "Switch case status=\"PAID\"", "case-\"PAID\"");
    SwitchCaseFact caseDescriptionFact = new SwitchCaseFact("", "Switch case status=\"PAID\"", "");
    SwitchCaseFact defaultDescriptionFact =
        new SwitchCaseFact("id", "Switch default   ", "default");
    SwitchCaseFact caseOutcomeFact = new SwitchCaseFact("", "", "case-\"PAID\"");
    SwitchCaseFact defaultOutcomeFact = new SwitchCaseFact("", "", "DEFAULT");

    assertThat(
            invokePrivateBoolean(
                validator,
                "containsSwitchCaseCoverage",
                new Class<?>[] {String.class, SwitchCaseFact.class},
                "",
                caseFact))
        .isFalse();
    assertThat(
            invokePrivateBoolean(
                validator,
                "containsSwitchCaseCoverage",
                new Class<?>[] {String.class, SwitchCaseFact.class},
                "case paid",
                null))
        .isFalse();
    assertThat(
            invokePrivateBoolean(
                validator,
                "containsSwitchCaseCoverage",
                new Class<?>[] {String.class, SwitchCaseFact.class},
                "status unknown",
                caseDescriptionFact))
        .isFalse();
    assertThat(
            invokePrivateBoolean(
                validator,
                "containsSwitchCaseCoverage",
                new Class<?>[] {String.class, SwitchCaseFact.class},
                "default branch",
                defaultDescriptionFact))
        .isTrue();
    assertThat(
            invokePrivateBoolean(
                validator,
                "containsSwitchCaseCoverage",
                new Class<?>[] {String.class, SwitchCaseFact.class},
                "status branch",
                defaultDescriptionFact))
        .isFalse();
    assertThat(
            invokePrivateBoolean(
                validator,
                "containsSwitchCaseCoverage",
                new Class<?>[] {String.class, SwitchCaseFact.class},
                "case paid",
                caseOutcomeFact))
        .isTrue();
    assertThat(
            invokePrivateBoolean(
                validator,
                "containsSwitchCaseCoverage",
                new Class<?>[] {String.class, SwitchCaseFact.class},
                "paid only",
                caseOutcomeFact))
        .isFalse();
    assertThat(
            invokePrivateBoolean(
                validator,
                "containsSwitchCaseCoverage",
                new Class<?>[] {String.class, SwitchCaseFact.class},
                "fallback only",
                defaultOutcomeFact))
        .isFalse();
    assertThat(
            invokePrivateBoolean(
                validator,
                "containsSwitchCaseCoverage",
                new Class<?>[] {String.class, SwitchCaseFact.class},
                "default fallback",
                defaultOutcomeFact))
        .isTrue();
  }

  @Test
  void containsRequiredPreconditionInSection_shouldUseDirectAndEquivalentMatches()
      throws Exception {
    LlmMethodSectionValidator validator = newValidator();
    assertThat(
            invokePrivateBoolean(
                validator,
                "containsRequiredPreconditionInSection",
                new Class<?>[] {String.class, String.class},
                "- request != null",
                "request != null"))
        .isTrue();
    assertThat(
            invokePrivateBoolean(
                validator,
                "containsRequiredPreconditionInSection",
                new Class<?>[] {String.class, String.class},
                "- request は null でない",
                "request != null"))
        .isTrue();
    assertThat(
            invokePrivateBoolean(
                validator,
                "containsRequiredPreconditionInSection",
                new Class<?>[] {String.class, String.class},
                "- request > 0",
                "request != null"))
        .isFalse();
  }

  @Test
  void isConstructorMethod_shouldHandleGenericAndInvalidSignatures() throws Exception {
    LlmMethodSectionValidator validator = newValidator();
    MethodInfo constructor =
        method("OrderService", "public <T extends Number> com.example.OrderService()");
    MethodInfo withInvalidModifier = method("OrderService", "public static OrderService()");
    MethodInfo withoutParenthesis = method("OrderService", "public OrderService");

    assertThat(
            invokePrivateBoolean(
                validator,
                "isConstructorMethod",
                new Class<?>[] {MethodInfo.class, String.class},
                constructor,
                "OrderService"))
        .isTrue();
    assertThat(
            invokePrivateBoolean(
                validator,
                "isConstructorMethod",
                new Class<?>[] {MethodInfo.class, String.class},
                withInvalidModifier,
                "OrderService"))
        .isFalse();
    assertThat(
            invokePrivateBoolean(
                validator,
                "isConstructorMethod",
                new Class<?>[] {MethodInfo.class, String.class},
                withoutParenthesis,
                "OrderService"))
        .isFalse();
    assertThat(
            invokePrivateBoolean(
                validator,
                "isConstructorMethod",
                new Class<?>[] {MethodInfo.class, String.class},
                constructor,
                "AnotherName"))
        .isFalse();
  }

  @Test
  void containsAmbiguousDependencyPlaceholder_shouldNormalizePlaceholders() throws Exception {
    LlmMethodSectionValidator validator = newValidator();
    assertThat(
            invokePrivateBoolean(
                validator,
                "containsAmbiguousDependencyPlaceholder",
                new Class<?>[] {String.class},
                "- others"))
        .isTrue();
    assertThat(
            invokePrivateBoolean(
                validator,
                "containsAmbiguousDependencyPlaceholder",
                new Class<?>[] {String.class},
                "- Other."))
        .isTrue();
    assertThat(
            invokePrivateBoolean(
                validator,
                "containsAmbiguousDependencyPlaceholder",
                new Class<?>[] {String.class},
                "- その他"))
        .isTrue();
    assertThat(
            invokePrivateBoolean(
                validator,
                "containsAmbiguousDependencyPlaceholder",
                new Class<?>[] {String.class},
                "- PaymentGateway.call()"))
        .isFalse();
    assertThat(
            invokePrivateBoolean(
                validator,
                "containsAmbiguousDependencyPlaceholder",
                new Class<?>[] {String.class},
                "- None"))
        .isFalse();
  }

  @Test
  void containsEarlyReturnOutcomeLabel_shouldDetectEnglishAndJapanese() throws Exception {
    LlmMethodSectionValidator validator = newValidator();
    assertThat(
            invokePrivateBoolean(
                validator,
                "containsEarlyReturnOutcomeLabel",
                new Class<?>[] {String.class},
                "path-main -> early-return"))
        .isTrue();
    assertThat(
            invokePrivateBoolean(
                validator,
                "containsEarlyReturnOutcomeLabel",
                new Class<?>[] {String.class},
                "早期リターンで終了"))
        .isTrue();
    assertThat(
            invokePrivateBoolean(
                validator,
                "containsEarlyReturnOutcomeLabel",
                new Class<?>[] {String.class},
                "normal completion"))
        .isFalse();
  }

  @Test
  void containsFailureLikeNormalFlow_shouldDetectJapaneseMarkers() throws Exception {
    LlmMethodSectionValidator validator = newValidator();
    assertThat(
            invokePrivateBoolean(
                validator,
                "containsFailureLikeNormalFlow",
                new Class<?>[] {String.class},
                "- 異常系で処理が中断する"))
        .isTrue();
    assertThat(
            invokePrivateBoolean(
                validator,
                "containsFailureLikeNormalFlow",
                new Class<?>[] {String.class},
                "- 境界値で失敗する"))
        .isTrue();
    assertThat(
            invokePrivateBoolean(
                validator,
                "containsFailureLikeNormalFlow",
                new Class<?>[] {String.class},
                "- completed successfully"))
        .isFalse();
  }

  private LlmMethodSectionValidator newValidator() {
    return newValidator(
        method -> false,
        methodName -> false,
        (section, method) -> false,
        (section, method) -> false,
        method -> List.of(),
        method -> false,
        method -> List.of());
  }

  private LlmMethodSectionValidator newValidator(
      Predicate<MethodInfo> uncertainDynamicResolutionChecker,
      Predicate<String> failureFactoryMethodNameChecker,
      BiPredicate<String, MethodInfo> unsupportedNoArgPreconditionChecker,
      BiPredicate<String, MethodInfo> unsupportedNonNullPreconditionChecker,
      Function<MethodInfo, List<String>> sourceBackedPreconditionCollector,
      Predicate<MethodInfo> earlyReturnIncompatibleChecker,
      Function<MethodInfo, List<SwitchCaseFact>> switchCaseFactsCollector) {
    return new LlmMethodSectionValidator(
        MethodInfo::getName,
        LlmDocumentTextUtils::normalizeMethodName,
        uncertainDynamicResolutionChecker,
        failureFactoryMethodNameChecker,
        unsupportedNoArgPreconditionChecker,
        unsupportedNonNullPreconditionChecker,
        sourceBackedPreconditionCollector,
        earlyReturnIncompatibleChecker,
        switchCaseFactsCollector);
  }

  private MethodInfo method(String name, String signature) {
    MethodInfo method = new MethodInfo();
    method.setName(name);
    method.setSignature(signature);
    return method;
  }

  private String document(
      String preconditions,
      String postconditions,
      String normalFlow,
      String errorBoundary,
      String dependencies,
      String testViewpoints,
      String methodName) {
    return """
        ### 3.1 %s
        #### 3.1.1 Inputs/Outputs
        - Inputs/Outputs: `public void processOrder()`
        #### 3.1.2 Preconditions
        %s
        #### 3.1.3 Postconditions
        %s
        #### 3.1.4 Normal Flow
        %s
        #### 3.1.5 Error/Boundary Handling
        %s
        #### 3.1.6 Dependencies
        %s
        #### 3.1.7 Test Viewpoints
        %s
        """
        .formatted(
            methodName,
            preconditions,
            postconditions,
            normalFlow,
            errorBoundary,
            dependencies,
            testViewpoints);
  }

  private void assertReasonContains(List<String> reasons, String snippet) {
    assertThat(reasons).anySatisfy(reason -> assertThat(reason).contains(snippet));
  }

  private boolean invokePrivateBoolean(
      LlmMethodSectionValidator validator,
      String methodName,
      Class<?>[] parameterTypes,
      Object... args)
      throws Exception {
    Method method = LlmMethodSectionValidator.class.getDeclaredMethod(methodName, parameterTypes);
    method.setAccessible(true);
    return (boolean) method.invoke(validator, args);
  }
}
