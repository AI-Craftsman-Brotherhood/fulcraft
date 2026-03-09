package com.craftsmanbro.fulcraft.plugins.document.core.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class LlmAnalysisGapInspectorTest {

  private final LlmAnalysisGapInspector inspector = new LlmAnalysisGapInspector();

  @Test
  void inspectMainSections_shouldCollectMethodWhenGapExistsInsideMethodBlock() {
    String document =
        """
        ## 3. Method Specifications
        ### 3.1 doWork
        #### 3.1.3 Postconditions
        - A loop guard indicates break (the exact downstream effects are not fully shown in the provided source excerpt)
        ## 4. Cautions
        - None
        ## 5. Recommendations (Optional)
        - None
        ## 6. Open Questions (Insufficient Analysis Data)
        - None
        """;

    LlmAnalysisGapInspector.AnalysisGapInspection inspection =
        inspector.inspectMainSections(document);

    assertThat(inspection.hasGap()).isTrue();
    assertThat(inspection.methodNames()).containsExactly("doWork");
  }

  @Test
  void inspectMainSections_shouldMarkGapWithoutMethodWhenOnlyGeneralStatementExists() {
    String document =
        """
        ## 1. Purpose and Responsibilities (Facts)
        - Details beyond the provided excerpt are not fully available.
        ## 6. Open Questions (Insufficient Analysis Data)
        - None
        """;

    LlmAnalysisGapInspector.AnalysisGapInspection inspection =
        inspector.inspectMainSections(document);

    assertThat(inspection.hasGap()).isTrue();
    assertThat(inspection.methodNames()).isEmpty();
  }

  @Test
  void inspectMainSections_shouldIgnoreOpenQuestionBody() {
    String document =
        """
        ## 3. Method Specifications
        ### 3.1 doWork
        #### 3.1.4 Normal Flow
        - Execute logic and return.
        ## 6. Open Questions (Insufficient Analysis Data)
        - Details beyond the provided excerpt are not fully available.
        """;

    LlmAnalysisGapInspector.AnalysisGapInspection inspection =
        inspector.inspectMainSections(document);

    assertThat(inspection.hasGap()).isFalse();
    assertThat(inspection.methodNames()).isEmpty();
  }

  @Test
  void inspectMainSections_shouldReturnNoneForNullOrBlankDocument() {
    assertThat(inspector.inspectMainSections(null))
        .isEqualTo(LlmAnalysisGapInspector.AnalysisGapInspection.none());
    assertThat(inspector.inspectMainSections("   "))
        .isEqualTo(LlmAnalysisGapInspector.AnalysisGapInspection.none());
  }

  @Test
  void inspectMainSections_shouldCollectOnlyMethodsContainingGapAndDeduplicate() {
    String document =
        """
        ## 3. Method Specifications
        ### 3.1 stablePath
        #### 3.1.4 Normal Flow
        - Returns deterministic value.
        ### 3.2 unresolvedPath
        #### 3.2.5 Error / Boundary
        - Details beyond the provided excerpt are not fully available.
        ### 3.3 unresolvedPath
        #### 3.3.6 Dependency Calls
        - analysis details are missing from the provided excerpt.
        ## 6. Open Questions (Insufficient Analysis Data)
        - None
        """;

    LlmAnalysisGapInspector.AnalysisGapInspection inspection =
        inspector.inspectMainSections(document);

    assertThat(inspection.hasGap()).isTrue();
    assertThat(inspection.methodNames()).containsExactly("unresolvedPath");
  }

  @Test
  void inspectMainSections_shouldInspectWholeDocumentWhenSectionSixIsMissing() {
    String document =
        """
        ## 3. Method Specifications
        ### 3.1 resolve
        #### 3.1.4 Normal Flow
        - Details beyond the provided excerpt are not fully available.
        """;

    LlmAnalysisGapInspector.AnalysisGapInspection inspection =
        inspector.inspectMainSections(document);

    assertThat(inspection.hasGap()).isTrue();
    assertThat(inspection.methodNames()).containsExactly("resolve");
  }

  @Test
  void privateHelpers_shouldHandleInvalidInput() throws Exception {
    assertThat(invokeFindSectionHeadingStart(null, 6)).isEqualTo(-1);
    assertThat(invokeFindSectionHeadingStart("   ", 6)).isEqualTo(-1);
    assertThat(invokeFindSectionHeadingStart("## 1. Intro", 0)).isEqualTo(-1);
    assertThat(invokeExtractMethodHeadingName(null)).isEmpty();
    assertThat(invokeExtractMethodHeadingName("   ")).isEmpty();
    assertThat(invokeExtractMethodHeadingName("#### 3.1.3 Postconditions")).isEmpty();
  }

  @Test
  void analysisGapInspectionRecord_shouldNormalizeNullMethodNames() {
    LlmAnalysisGapInspector.AnalysisGapInspection inspection =
        new LlmAnalysisGapInspector.AnalysisGapInspection(true, null);

    assertThat(inspection.hasGap()).isTrue();
    assertThat(inspection.methodNames()).isEmpty();
    assertThat(LlmAnalysisGapInspector.AnalysisGapInspection.none().hasGap()).isFalse();
  }

  private int invokeFindSectionHeadingStart(String document, int sectionNo) throws Exception {
    Method method =
        LlmAnalysisGapInspector.class.getDeclaredMethod(
            "findSectionHeadingStart", String.class, int.class);
    method.setAccessible(true);
    return (int) method.invoke(inspector, document, sectionNo);
  }

  private String invokeExtractMethodHeadingName(String methodBlock) throws Exception {
    Method method =
        LlmAnalysisGapInspector.class.getDeclaredMethod("extractMethodHeadingName", String.class);
    method.setAccessible(true);
    return (String) method.invoke(inspector, methodBlock);
  }
}
