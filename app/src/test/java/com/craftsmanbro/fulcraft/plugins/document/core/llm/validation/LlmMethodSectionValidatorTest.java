package com.craftsmanbro.fulcraft.plugins.document.core.llm.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.plugins.analysis.model.BranchSummary;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmDocumentTextUtils;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LlmMethodSectionValidatorTest {

  private final LlmMethodSectionValidator validator =
      new LlmMethodSectionValidator(
          MethodInfo::getName,
          LlmDocumentTextUtils::normalizeMethodName,
          method -> false,
          methodName -> false,
          (section, method) -> false,
          (section, method) -> false,
          method -> List.of(),
          method -> false,
          method -> List.of());

  @Test
  void validate_shouldRejectNonCanonicalOrderedListInMethodSection() {
    String document =
        """
        ### 3.1 processOrder
        #### 3.1.1 入出力
        - 入力/出力: `public void processOrder()`
        #### 3.1.2 事前条件
        - なし
        #### 3.1.3 事後条件
        - 1. 成功時に完了する
        2. 副作用を記録する
        #### 3.1.4 正常フロー
        - なし
        #### 3.1.5 異常・境界
        - なし
        #### 3.1.6 依存呼び出し
        - なし
        #### 3.1.7 テスト観点
        - なし
        """;
    MethodInfo method = new MethodInfo();
    method.setName("processOrder");
    method.setSignature("public void processOrder()");
    List<String> reasons = new ArrayList<>();

    validator.validate(document, List.of(method), reasons, true, "N/A");

    assertThat(reasons).anySatisfy(reason -> assertThat(reason).contains("連番リストの形式が不正"));
  }

  @Test
  void validate_shouldAllowCanonicalOrderedListInMethodSection() {
    String document =
        """
        ### 3.1 processOrder
        #### 3.1.1 入出力
        - 入力/出力: `public void processOrder()`
        #### 3.1.2 事前条件
        - なし
        #### 3.1.3 事後条件
        1. 成功時に完了する
        2. 副作用を記録する
        #### 3.1.4 正常フロー
        - なし
        #### 3.1.5 異常・境界
        - なし
        #### 3.1.6 依存呼び出し
        - なし
        #### 3.1.7 テスト観点
        - なし
        """;
    MethodInfo method = new MethodInfo();
    method.setName("processOrder");
    method.setSignature("public void processOrder()");
    List<String> reasons = new ArrayList<>();

    validator.validate(document, List.of(method), reasons, true, "N/A");

    assertThat(reasons).isEmpty();
  }

  @Test
  void validate_shouldTreatNoneSpecifiedInAnalysisDataAsFallbackOnlySection() {
    String document =
        """
        ### 3.1 processOrder
        #### 3.1.1 Inputs/Outputs
        - Inputs/Outputs: `public void processOrder()`
        #### 3.1.2 Preconditions
        - None
        #### 3.1.3 Postconditions
        - Completed.
        #### 3.1.4 Normal Flow
        - None specified in analysis data.
        #### 3.1.5 Error/Boundary Handling
        - None specified in analysis data.
        #### 3.1.6 Dependencies
        - None
        #### 3.1.7 Test Viewpoints
        - None
        """;
    MethodInfo method = new MethodInfo();
    method.setName("processOrder");
    method.setSignature("public void processOrder()");
    BranchSummary branchSummary = new BranchSummary();
    branchSummary.setPredicates(List.of("pathA", "pathB"));
    method.setBranchSummary(branchSummary);
    List<String> reasons = new ArrayList<>();

    validator.validate(document, List.of(method), reasons, false, "N/A");

    assertThat(reasons)
        .anySatisfy(
            reason ->
                assertThat(reason)
                    .contains("fallback content despite analysis data being available"));
  }

  @Test
  void validate_shouldRejectMalformedInlineInputNoneLine() {
    String document =
        """
        ### 3.1 processOrder
        #### 3.1.1 Inputs/Outputs
        - Inputs: - None
        #### 3.1.2 Preconditions
        - None
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
        """;
    MethodInfo method = new MethodInfo();
    method.setName("processOrder");
    method.setSignature("public void processOrder()");
    List<String> reasons = new ArrayList<>();

    validator.validate(document, List.of(method), reasons, false, "N/A");

    assertThat(reasons)
        .anySatisfy(
            reason ->
                assertThat(reason)
                    .contains("Inputs/Outputs contains malformed inline list syntax"));
  }
}
