package com.craftsmanbro.fulcraft.plugins.document.core.llm.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LlmPathConsistencyValidatorTest {

  private final LlmPathConsistencyValidator validator = new LlmPathConsistencyValidator();

  @Test
  void validate_shouldRejectDuplicatedRepresentativePathBetweenNormalAndErrorSections() {
    String document =
        """
        ### 3.1 processOrder
        #### 3.1.3 Postconditions
        - [path-main] -> success
        #### 3.1.4 Normal Flow
        - [path-main] -> success
        #### 3.1.5 Error/Boundary Handling
        - [path-main] -> failure
        #### 3.1.6 Dependencies
        - None
        #### 3.1.7 Test Viewpoints
        - Verify path-main
        ## 4. Cautions
        - None
        """;
    List<String> reasons = new ArrayList<>();

    validator.validate(document, reasons, false);

    assertThat(reasons)
        .anySatisfy(
            reason ->
                assertThat(reason)
                    .contains("duplicated representative paths")
                    .contains("path-main"));
  }

  @Test
  void validate_shouldRejectOutcomeMismatchBetweenPostconditionsAndNormalFlow() {
    String document =
        """
        ### 3.1 processOrder
        #### 3.1.3 Postconditions
        - [path-main] -> success
        #### 3.1.4 Normal Flow
        - [path-main] -> failure
        #### 3.1.5 Error/Boundary Handling
        - [path-error] -> failure
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
  void validate_shouldRejectMissingRepresentativePathInTestViewpoints() {
    String document =
        """
        ### 3.1 processOrder
        #### 3.1.3 Postconditions
        - [path-main] -> success
        #### 3.1.4 Normal Flow
        - [path-main] -> success
        #### 3.1.5 Error/Boundary Handling
        - [path-error] -> failure
        #### 3.1.6 Dependencies
        - None
        #### 3.1.7 Test Viewpoints
        - Verify path-main
        ## 4. Cautions
        - None
        """;
    List<String> reasons = new ArrayList<>();

    validator.validate(document, reasons, false);

    assertThat(reasons)
        .anySatisfy(
            reason ->
                assertThat(reason)
                    .contains("Test Viewpoints are missing representative paths")
                    .contains("path-error"));
  }

  @Test
  void validate_shouldAllowWhenPathSectionsAreConsistent() {
    String document =
        """
        ### 3.1 processOrder
        #### 3.1.3 Postconditions
        - [path-main] -> success
        #### 3.1.4 Normal Flow
        - [path-main] -> success
        #### 3.1.5 Error/Boundary Handling
        - [path-error] -> failure
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
}
