package com.craftsmanbro.fulcraft.plugins.document.core.llm.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LlmPathConsistencyValidatorCoverageTest {

  @Test
  void extractMethodHeadingName_shouldReturnCustomUnavailableValueForNullBlock() throws Exception {
    LlmPathConsistencyValidator validator = new LlmPathConsistencyValidator("unknown-method");
    Method method =
        LlmPathConsistencyValidator.class.getDeclaredMethod(
            "extractMethodHeadingName", String.class);
    method.setAccessible(true);

    Object result = method.invoke(validator, new Object[] {null});

    assertThat(result).isEqualTo("unknown-method");
  }

  @Test
  void validate_shouldNormalizeResultLabelOutcome() {
    LlmPathConsistencyValidator validator = new LlmPathConsistencyValidator();
    String document =
        """
        ### 3.1 processOrder
        #### 3.1.3 Postconditions
        - path-main 結果: 失敗
        #### 3.1.4 Normal Flow
        - path-main -> failure
        #### 3.1.5 Error/Boundary Handling
        - path-error -> boundary
        #### 3.1.6 Dependencies
        - None
        #### 3.1.7 Test Viewpoints
        - Verify path-main and path-error
        ## 4. Cautions
        - None
        """;
    List<String> reasons = new ArrayList<>();

    validator.validate(document, reasons, false);

    assertThat(reasons).isEmpty();
  }

  @Test
  void validate_shouldNormalizeEnglishOutcomeLine() {
    LlmPathConsistencyValidator validator = new LlmPathConsistencyValidator();
    String document =
        """
        ### 3.1 processOrder
        #### 3.1.3 Postconditions
        - Outcome for branch path-main: boundary
        #### 3.1.4 Normal Flow
        - path-main -> boundary
        #### 3.1.5 Error/Boundary Handling
        - path-error -> failure
        #### 3.1.6 Dependencies
        - None
        #### 3.1.7 Test Viewpoints
        - Verify PATH-MAIN and PATH-ERROR
        ## 4. Cautions
        - None
        """;
    List<String> reasons = new ArrayList<>();

    validator.validate(document, reasons, false);

    assertThat(reasons).isEmpty();
  }

  @Test
  void validate_shouldIgnoreBlankOutcomeLineInPostconditions() {
    LlmPathConsistencyValidator validator = new LlmPathConsistencyValidator();
    String document =
        """
        ### 3.1 processOrder
        #### 3.1.3 Postconditions
        - [path-main]
        #### 3.1.4 Normal Flow
        - [path-main] -> failure
        #### 3.1.5 Error/Boundary Handling
        - [path-error] -> boundary
        #### 3.1.6 Dependencies
        - None
        #### 3.1.7 Test Viewpoints
        - Verify path-main and path-error
        ## 4. Cautions
        - None
        """;
    List<String> reasons = new ArrayList<>();

    validator.validate(document, reasons, false);

    assertThat(reasons).isEmpty();
  }

  @Test
  void validate_shouldAllowWhenNoRepresentativePathsAreDeclared() {
    LlmPathConsistencyValidator validator = new LlmPathConsistencyValidator();
    String document =
        """
        ### 3.1 processOrder
        #### 3.1.3 Postconditions
        - Completed.
        #### 3.1.4 Normal Flow
        - Completed.
        #### 3.1.5 Error/Boundary Handling
        - None
        #### 3.1.6 Dependencies
        - None
        #### 3.1.7 Test Viewpoints
        - None
        ## 4. Cautions
        - None
        """;
    List<String> reasons = new ArrayList<>();

    validator.validate(document, reasons, false);

    assertThat(reasons).isEmpty();
  }

  @Test
  void validate_shouldDetectOutcomeMismatchFromResultLine() {
    LlmPathConsistencyValidator validator = new LlmPathConsistencyValidator();
    String document =
        """
        ### 3.1 processOrder
        #### 3.1.3 Postconditions
        - path-main 結果: success
        #### 3.1.4 Normal Flow
        - path-main -> failure
        #### 3.1.5 Error/Boundary Handling
        - path-error -> boundary
        #### 3.1.6 Dependencies
        - None
        #### 3.1.7 Test Viewpoints
        - Verify path-main and path-error
        ## 4. Cautions
        - None
        """;
    List<String> reasons = new ArrayList<>();

    validator.validate(document, reasons, false);

    assertThat(reasons)
        .anySatisfy(
            reason ->
                assertThat(reason)
                    .contains("inconsistent between Postconditions and Normal Flow")
                    .contains("path-main"));
  }

  @Test
  void normalizePathOutcomeLabel_shouldMapAllKnownOutcomeFamilies() throws Exception {
    assertThat(invokeNormalizePathOutcomeLabel("returns a failure result object"))
        .isEqualTo("failure-result");
    assertThat(invokeNormalizePathOutcomeLabel("early return")).isEqualTo("early-return");
    assertThat(invokeNormalizePathOutcomeLabel("boundary condition")).isEqualTo("boundary");
    assertThat(invokeNormalizePathOutcomeLabel("failed with error")).isEqualTo("failure");
    assertThat(invokeNormalizePathOutcomeLabel("operation success")).isEqualTo("success");
    assertThat(invokeNormalizePathOutcomeLabel("custom outcome.")).isEqualTo("custom outcome");
    assertThat(invokeNormalizePathOutcomeLabel("   ")).isEmpty();
    assertThat(invokeNormalizePathOutcomeLabel(null)).isEmpty();
  }

  @Test
  void validate_shouldAllowWhenNormalFlowOutcomeIsMissing() {
    LlmPathConsistencyValidator validator = new LlmPathConsistencyValidator();
    String document =
        """
        ### 3.1 processOrder
        #### 3.1.3 Postconditions
        - path-main -> success
        #### 3.1.4 Normal Flow
        - path-main
        #### 3.1.5 Error/Boundary Handling
        - path-error -> failure
        #### 3.1.6 Dependencies
        - None
        #### 3.1.7 Test Viewpoints
        - Verify path-main and path-error
        ## 4. Cautions
        - None
        """;
    List<String> reasons = new ArrayList<>();

    validator.validate(document, reasons, false);

    assertThat(reasons).isEmpty();
  }

  @Test
  void validate_shouldTreatUnknownOutcomeLabelAsRawNormalizedValue() {
    LlmPathConsistencyValidator validator = new LlmPathConsistencyValidator();
    String document =
        """
        ### 3.1 processOrder
        #### 3.1.3 Postconditions
        - path-main 結果: Degraded Mode.
        #### 3.1.4 Normal Flow
        - path-main -> degraded mode
        #### 3.1.5 Error/Boundary Handling
        - path-error -> boundary
        #### 3.1.6 Dependencies
        - None
        #### 3.1.7 Test Viewpoints
        - Verify path-main and path-error
        ## 4. Cautions
        - None
        """;
    List<String> reasons = new ArrayList<>();

    validator.validate(document, reasons, false);

    assertThat(reasons).isEmpty();
  }

  private String invokeNormalizePathOutcomeLabel(String value) throws Exception {
    LlmPathConsistencyValidator validator = new LlmPathConsistencyValidator();
    Method method =
        LlmPathConsistencyValidator.class.getDeclaredMethod(
            "normalizePathOutcomeLabel", String.class);
    method.setAccessible(true);
    return (String) method.invoke(validator, value);
  }
}
