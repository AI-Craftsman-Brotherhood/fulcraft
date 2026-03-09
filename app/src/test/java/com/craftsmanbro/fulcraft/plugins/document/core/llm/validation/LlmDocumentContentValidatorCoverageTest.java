package com.craftsmanbro.fulcraft.plugins.document.core.llm.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LlmDocumentContentValidatorCoverageTest {

  private final LlmDocumentContentValidator validator = new LlmDocumentContentValidator();

  @Test
  void validate_shouldRejectEmptyOutput() {
    List<String> reasons = new ArrayList<>();

    validator.validate("   ", defaultContext(), reasons, false);

    assertReasonContains(reasons, "Output is empty");
  }

  @Test
  void validate_shouldRejectMissingContext() {
    List<String> reasons = new ArrayList<>();

    validator.validate(
        baseDocument(validExternalSection(), validMethodSection(), "- None", "- None", "- None"),
        null,
        reasons,
        false);

    assertReasonContains(reasons, "Validation context is missing");
  }

  @Test
  void validate_shouldRejectMissingExternalSpecificationItems() {
    List<String> reasons = new ArrayList<>();

    validator.validate(
        baseDocument(
            "- Class Name: `OrderService`", validMethodSection(), "- None", "- None", "- None"),
        defaultContext(),
        reasons,
        false);

    assertReasonContains(reasons, "External Class Specification is missing required items");
  }

  @Test
  void validate_shouldRejectNestedTrueForNonNestedClass() {
    List<String> reasons = new ArrayList<>();

    validator.validate(
        baseDocument(
            validExternalSection() + "\n- Nested Class: true",
            validMethodSection(),
            "- None",
            "- None",
            "- None"),
        defaultContext(),
        reasons,
        false);

    assertReasonContains(reasons, "nested_class=true");
  }

  @Test
  void validate_shouldRejectLowComplexityInCautions() {
    List<String> reasons = new ArrayList<>();

    validator.validate(
        baseDocument(
            validExternalSection(), validMethodSection(), "- complexity: 5", "- None", "- None"),
        context(
            List.of("knownmethod"),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of("knownmethod"),
            true,
            false),
        reasons,
        false);

    assertReasonContains(reasons, "Cautions include sub-threshold complexity");
  }

  @Test
  void validate_shouldRejectDeadCodeClaimMismatch() {
    List<String> reasons = new ArrayList<>();

    validator.validate(
        baseDocument(
            validExternalSection(),
            validMethodSection(),
            "- dead code: ghostMethod",
            "- None",
            "- None"),
        context(
            List.of("knownmethod"),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of("knownmethod"),
            true,
            false),
        reasons,
        false);

    assertReasonContains(reasons, "Dead-code claim contradicts input data");
  }

  @Test
  void validate_shouldRejectRecommendationsWithContent() {
    List<String> reasons = new ArrayList<>();

    validator.validate(
        baseDocument(
            validExternalSection(),
            validMethodSection(),
            "- None",
            "- Improve null checks",
            "- None"),
        defaultContext(),
        reasons,
        false);

    assertReasonContains(reasons, "Recommendations section contains disallowed content");
  }

  @Test
  void validate_shouldRejectInferenceMarkers() {
    List<String> reasons = new ArrayList<>();

    validator.validate(
        baseDocument(
            validExternalSection(),
            "### 3.1 knownMethod\n- [Inference] this behavior was inferred",
            "- None",
            "- None",
            "- None"),
        defaultContext(),
        reasons,
        false);

    assertReasonContains(reasons, "Inference markers");
  }

  @Test
  void validate_shouldRejectUncertainDynamicMethodAssertion() {
    List<String> reasons = new ArrayList<>();

    validator.validate(
        baseDocument(
            validExternalSection(),
            "### 3.1 knownMethod\n- method `mysteryhook` exists",
            "- None",
            "- None",
            "- None"),
        context(
            List.of("knownmethod"),
            Set.of(),
            Set.of(),
            Set.of("mysteryhook"),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of("knownmethod"),
            false,
            false),
        reasons,
        false);

    assertReasonContains(reasons, "Uncertain dynamic resolution was asserted as fact");
  }

  @Test
  void validate_shouldRejectExternalMethodAssertionWithoutUncertaintyMarker() {
    List<String> reasons = new ArrayList<>();

    validator.validate(
        baseDocument(
            validExternalSection(),
            "### 3.1 knownMethod\n- method `externalCall` exists",
            "- None",
            "- None",
            "- None"),
        defaultContext(),
        reasons,
        false);

    assertReasonContains(reasons, "External method existence asserted without uncertainty marker");
  }

  @Test
  void validate_shouldRequireNoneOnlyForMethodlessOpenQuestions() {
    List<String> reasons = new ArrayList<>();

    validator.validate(
        baseDocument(
            validExternalSection(),
            validMethodSection(),
            "- None",
            "- None",
            "- Need to verify dependency wiring"),
        context(
            List.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), false,
            false),
        reasons,
        false);

    assertReasonContains(reasons, "Methodless classes must keep Open Questions as 'None' only");
  }

  @Test
  void validate_shouldRejectMissingUncertainCandidatesInOpenQuestions() {
    List<String> reasons = new ArrayList<>();

    validator.validate(
        baseDocument(
            validExternalSection(),
            validMethodSection(),
            "- None",
            "- None",
            "- Additional verification is needed"),
        context(
            List.of("knownmethod"),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of("candidateMethod"),
            Set.of(),
            Set.of(),
            Set.of("knownmethod"),
            false,
            false),
        reasons,
        false);

    assertReasonContains(
        reasons, "Open Questions do not include any uncertain candidate method names");
  }

  @Test
  void validate_shouldRejectKnownMissingMethodInOpenQuestions() {
    List<String> reasons = new ArrayList<>();

    validator.validate(
        baseDocument(
            validExternalSection(),
            validMethodSection(),
            "- None",
            "- None",
            "- ghostMethod should be checked"),
        context(
            List.of("knownmethod"),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of("ghostMethod"),
            Set.of("knownmethod"),
            false,
            false),
        reasons,
        false);

    assertReasonContains(reasons, "Open Questions include a known-missing dynamic method");
  }

  @Test
  void validate_shouldRejectKnownMethodAsUnresolvedInOpenQuestions() {
    List<String> reasons = new ArrayList<>();

    validator.validate(
        baseDocument(
            validExternalSection(),
            validMethodSection(),
            "- None",
            "- None",
            "- method `knownmethod` must exist but is not provided"),
        defaultContext(),
        reasons,
        false);

    assertReasonContains(reasons, "Open Questions treat a known method as unresolved");
  }

  @Test
  void validate_shouldRejectUncertainDynamicMethodOutsideOpenQuestions() {
    List<String> reasons = new ArrayList<>();

    validator.validate(
        baseDocument(
            validExternalSection(),
            "### 3.1 knownMethod\n- mysteryhook is referenced in the flow description",
            "- None",
            "- None",
            "- None"),
        context(
            List.of("knownmethod"),
            Set.of(),
            Set.of(),
            Set.of("mysteryhook"),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of("knownmethod"),
            false,
            false),
        reasons,
        false);

    assertReasonContains(reasons, "Uncertain dynamic candidate appears outside Open Questions");
  }

  @Test
  void validate_shouldRejectAssertedUncertainMethodInOpenQuestions() {
    List<String> reasons = new ArrayList<>();

    validator.validate(
        baseDocument(
            validExternalSection(),
            validMethodSection(),
            "- None",
            "- None",
            "- mysteryhook executes the fallback path"),
        context(
            List.of("knownmethod"),
            Set.of(),
            Set.of(),
            Set.of("mysteryhook"),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of("knownmethod"),
            false,
            false),
        reasons,
        false);

    assertReasonContains(reasons, "Open Questions assert an uncertain method as a fact");
  }

  @Test
  void validate_shouldRejectNestedUncertaintyWhenClassIsNested() {
    List<String> reasons = new ArrayList<>();

    validator.validate(
        baseDocument(
            validExternalSection(),
            validMethodSection(),
            "- None",
            "- None",
            "- nested class status is unknown"),
        context(
            List.of("knownmethod"),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of("knownmethod"),
            false,
            true),
        reasons,
        false);

    assertReasonContains(reasons, "analysis marks class as nested");
  }

  @Test
  void validate_shouldRejectNestedUncertaintyWhenClassIsNotNested() {
    List<String> reasons = new ArrayList<>();

    validator.validate(
        baseDocument(
            validExternalSection(),
            validMethodSection(),
            "- None",
            "- None",
            "- nested class status is unknown"),
        defaultContext(),
        reasons,
        false);

    assertReasonContains(reasons, "analysis marks class as non-nested");
  }

  @Test
  void containsDefinitionUncertaintyMarker_shouldRecognizeAllKnownMarkers() throws Exception {
    List<String> markers =
        List.of(
            "未確定",
            "不明",
            "明示なし",
            "記載なし",
            "記載がない",
            "未提示",
            "未確認",
            "確認が必要",
            "含まれていない",
            "提示されていない",
            "確定できない",
            "unknown",
            "uncertain",
            "unclear",
            "not confirmed",
            "cannot determine",
            "not provided",
            "not shown",
            "not available",
            "not included",
            "missing",
            "not documented",
            "undefined");

    for (String marker : markers) {
      assertThat(invokeBoolean("containsDefinitionUncertaintyMarker", "constructor is " + marker))
          .isTrue();
    }
    assertThat(invokeBoolean("containsDefinitionUncertaintyMarker", "all details are known"))
        .isFalse();
    assertThat(invokeBoolean("containsDefinitionUncertaintyMarker", null)).isFalse();
  }

  @Test
  void containsConstructorExistenceIntent_shouldRecognizeAllKnownMarkers() throws Exception {
    List<String> markers = List.of("定義", "存在", "有無", "確認", "exist", "defined", "whether");

    for (String marker : markers) {
      assertThat(invokeBoolean("containsConstructorExistenceIntent", "constructor " + marker))
          .isTrue();
    }
    assertThat(invokeBoolean("containsConstructorExistenceIntent", "constructor details only"))
        .isFalse();
    assertThat(invokeBoolean("containsConstructorExistenceIntent", "   ")).isFalse();
  }

  @Test
  void isLikelyParameterName_shouldValidateCharacterRules() throws Exception {
    assertThat(invokeBoolean("isLikelyParameterName", "request")).isTrue();
    assertThat(invokeBoolean("isLikelyParameterName", "request_1")).isTrue();
    assertThat(invokeBoolean("isLikelyParameterName", "Request")).isFalse();
    assertThat(invokeBoolean("isLikelyParameterName", "req-name")).isFalse();
    assertThat(invokeBoolean("isLikelyParameterName", "1request")).isFalse();
    assertThat(invokeBoolean("isLikelyParameterName", null)).isFalse();
  }

  @Test
  void hasPositiveCautionClaim_shouldRespectNegation() throws Exception {
    assertThat(invokeBoolean("hasPositiveCautionClaim", "- duplicate issue is detected")).isTrue();
    assertThat(invokeBoolean("hasPositiveCautionClaim", "- 複雑度が高い")).isTrue();
    assertThat(invokeBoolean("hasPositiveCautionClaim", "- dead code not found")).isFalse();
    assertThat(invokeBoolean("hasPositiveCautionClaim", "- デッドコードなし")).isFalse();
    assertThat(invokeBoolean("hasPositiveCautionClaim", "   ")).isFalse();
  }

  @Test
  void isMethodExistenceQuestionLine_shouldRecognizeKeywordVariants() throws Exception {
    List<String> markers =
        List.of(
            "実在確認",
            "存在確認",
            "存在と詳細",
            "有無",
            "存在しない",
            "提示されていない",
            "明示なし",
            "記載なし",
            "記載がない",
            "未提示",
            "未確認",
            "確認が必要",
            "定義有無",
            "定義が不明",
            "含まれていない",
            "verify existence",
            "existence of",
            "whether",
            "must exist",
            "needs verification",
            "not provided",
            "not shown",
            "not available",
            "not included",
            "missing",
            "not documented",
            "undefined",
            "unknown",
            "unclear");

    for (String marker : markers) {
      assertThat(invokeBoolean("isMethodExistenceQuestionLine", "method state: " + marker))
          .isTrue();
    }
    assertThat(invokeBoolean("isMethodExistenceQuestionLine", "all methods are confirmed"))
        .isFalse();
  }

  @Test
  void normalizeConstructorParameterType_shouldNormalizeComplexTypeTokens() throws Exception {
    assertThat(
            invokeString(
                "normalizeConstructorParameterType",
                "@Nullable final java.util.Map<java.lang.String, com.example.Type> value"))
        .isEqualTo("map");
    assertThat(invokeString("normalizeConstructorParameterType", "com.example.Foo... args"))
        .isEqualTo("foo[]");
    assertThat(invokeString("normalizeConstructorParameterType", "String")).isEqualTo("string");
  }

  @Test
  void validate_shouldAllowUncertainMethodAsExistenceQuestionInOpenQuestions() {
    List<String> reasons = new ArrayList<>();

    validator.validate(
        baseDocument(
            validExternalSection(),
            validMethodSection(),
            "- None",
            "- None",
            "- verify existence of `mysteryhook`"),
        context(
            List.of("knownmethod"),
            Set.of(),
            Set.of(),
            Set.of("mysteryhook"),
            Set.of("mysteryhook"),
            Set.of(),
            Set.of(),
            Set.of("knownmethod"),
            false,
            false),
        reasons,
        false);

    assertThat(reasons).isEmpty();
  }

  @Test
  void validate_shouldAllowWhenOpenQuestionSectionIsMissing() {
    List<String> reasons = new ArrayList<>();
    String documentWithoutOpenQuestions =
        """
        ## 2. External Class Specification
        %s
        ## 3. Method Specifications
        %s
        ## 4. Cautions
        - None
        ## 5. Recommendations (Optional)
        - None
        """
            .formatted(validExternalSection(), validMethodSection());

    validator.validate(
        documentWithoutOpenQuestions,
        context(
            List.of("knownmethod"),
            Set.of(),
            Set.of(),
            Set.of("mysteryhook"),
            Set.of("mysteryhook"),
            Set.of(),
            Set.of(),
            Set.of("knownmethod"),
            false,
            false),
        reasons,
        false);

    assertThat(reasons).isEmpty();
  }

  @Test
  void validate_shouldAllowUncertainMethodWhenOpenQuestionContainsUncertaintyMarker() {
    List<String> reasons = new ArrayList<>();

    validator.validate(
        baseDocument(
            validExternalSection(),
            validMethodSection(),
            "- None",
            "- None",
            "- mysteryhook remains uncertain candidate for fallback"),
        context(
            List.of("knownmethod"),
            Set.of(),
            Set.of(),
            Set.of("mysteryhook"),
            Set.of("mysteryhook"),
            Set.of(),
            Set.of(),
            Set.of("knownmethod"),
            false,
            false),
        reasons,
        false);

    assertThat(reasons).isEmpty();
  }

  private String baseDocument(
      String externalSection,
      String methodSection,
      String cautions,
      String recommendations,
      String openQuestions) {
    return """
        ## 2. External Class Specification
        %s
        ## 3. Method Specifications
        %s
        ## 4. Cautions
        %s
        ## 5. Recommendations (Optional)
        %s
        ## 6. Open Questions (Insufficient Analysis Data)
        %s
        """
        .formatted(externalSection, methodSection, cautions, recommendations, openQuestions);
  }

  private String validExternalSection() {
    return """
        - Class Name: `OrderService`
        - Package: `com.example`
        - File Path: `src/main/java/com/example/OrderService.java`
        - Class Type: `class`
        - Extends: `none`
        - Implements: `none`
        """;
  }

  private String validMethodSection() {
    return """
        ### 3.1 knownMethod
        - Baseline description
        """;
  }

  private LlmDocumentContentValidator.ValidationContext defaultContext() {
    return context(
        List.of("knownmethod"),
        Set.of(),
        Set.of(),
        Set.of(),
        Set.of(),
        Set.of(),
        Set.of(),
        Set.of("knownmethod"),
        false,
        false);
  }

  private LlmDocumentContentValidator.ValidationContext context(
      List<String> methodNames,
      Set<String> deadCodeMethods,
      Set<String> duplicateMethods,
      Set<String> uncertainDynamicMethodNames,
      Set<String> uncertainDynamicMethodDisplayNames,
      Set<String> knownMissingDynamicMethodNames,
      Set<String> knownMissingDynamicMethodDisplayNames,
      Set<String> knownMethodNames,
      boolean hasAnyCautions,
      boolean nestedClass) {
    return new LlmDocumentContentValidator.ValidationContext(
        methodNames,
        deadCodeMethods,
        duplicateMethods,
        uncertainDynamicMethodNames,
        uncertainDynamicMethodDisplayNames,
        knownMissingDynamicMethodNames,
        knownMissingDynamicMethodDisplayNames,
        knownMethodNames,
        Set.of(),
        hasAnyCautions,
        nestedClass);
  }

  private void assertReasonContains(List<String> reasons, String snippet) {
    assertThat(reasons).anySatisfy(reason -> assertThat(reason).contains(snippet));
  }

  private boolean invokeBoolean(String methodName, String argument) throws Exception {
    Method method = LlmDocumentContentValidator.class.getDeclaredMethod(methodName, String.class);
    method.setAccessible(true);
    return (boolean) method.invoke(validator, argument);
  }

  private String invokeString(String methodName, String argument) throws Exception {
    Method method = LlmDocumentContentValidator.class.getDeclaredMethod(methodName, String.class);
    method.setAccessible(true);
    return (String) method.invoke(validator, argument);
  }
}
