package com.craftsmanbro.fulcraft.plugins.document.core.llm.generation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LlmGeneratedDocumentPostProcessorTest {

  private final LlmGeneratedDocumentPostProcessor postProcessor =
      new LlmGeneratedDocumentPostProcessor();

  @Test
  void sanitize_shouldCollapseRedundantBulletPrefix() {
    String raw =
        """
        ## 4. 要注意事項
        - - なし
        ## 5. 改善提案（任意）
        - - None
        ## 6. 未確定事項（解析情報不足）
        - - 未確定事項はありません
        """;

    String normalized = postProcessor.sanitize(raw);

    assertThat(normalized).contains("- なし");
    assertThat(normalized).contains("- None");
    assertThat(normalized).doesNotContain("- - なし");
    assertThat(normalized).doesNotContain("- - None");
  }

  @Test
  void sanitize_shouldRemoveEmptyRedundantBulletSeparator() {
    String raw =
        """
        ## 3. メソッド仕様
        ### 3.1 sample
        #### 3.1.7 テスト観点
        - 分岐Aを確認する
        - -
        ## 4. 要注意事項
        - なし
        """;

    String normalized = postProcessor.sanitize(raw);

    assertThat(normalized).contains("- 分岐Aを確認する");
    assertThat(normalized).doesNotContain("- -");
  }

  @Test
  void sanitize_shouldNormalizeMixedOrderedListNumbering() {
    String raw =
        """
        ## 3. メソッド仕様
        ### 3.1 sample
        #### 3.1.7 テスト観点
        - 1. 条件Aを確認
        2. 条件Bを確認
        4. 条件Cを確認
        ## 4. 要注意事項
        - なし
        """;

    String normalized = postProcessor.sanitize(raw);

    assertThat(normalized).contains("1. 条件Aを確認");
    assertThat(normalized).contains("2. 条件Bを確認");
    assertThat(normalized).contains("3. 条件Cを確認");
    assertThat(normalized).doesNotContain("- 1. 条件Aを確認");
    assertThat(normalized).doesNotContain("4. 条件Cを確認");
  }

  @Test
  void sanitize_shouldNormalizeMalformedInlineInputNoneLine() {
    String raw =
        """
        ## 3. Method Specifications
        ### 3.1 sample
        #### 3.1.1 Inputs/Outputs
        - Inputs: - None
        #### 3.1.2 Preconditions
        - None
        ## 4. Cautions
        - None
        """;

    String normalized = postProcessor.sanitize(raw);

    assertThat(normalized).contains("- Inputs: None");
    assertThat(normalized).doesNotContain("- Inputs: - None");
  }

  @Test
  void sanitize_shouldCleanupCodeFenceAndNormalizeMetadataAndDependencies() {
    String raw =
        """
        ```markdown
        - Package: com.example.sample
        - File Path: src/main/java/com/example/sample/OrderService.java
        - クラスの種別: class
        #### 3.1.6 Dependencies
        - com.example.sample.Repository#save()
        - Name: Value
        #### 3.1.7 Test Viewpoints
        - com.example.sample.Repository#save()
        ```
        """;

    String normalized = postProcessor.sanitize(raw);

    assertThat(normalized).contains("- Package: `com.example.sample`");
    assertThat(normalized)
        .contains("- File Path: `src/main/java/com/example/sample/OrderService.java`");
    assertThat(normalized).contains("- クラス種別: class");
    assertThat(normalized).contains("#### 3.1.6 Dependencies");
    assertThat(normalized).contains("- `com.example.sample.Repository#save()`");
    assertThat(normalized).contains("- Name: Value");
    assertThat(normalized).contains("#### 3.1.7 Test Viewpoints");
    assertThat(normalized).contains("- com.example.sample.Repository#save()");
  }

  @Test
  void sanitize_shouldNormalizeStandaloneAndOpenQuestionsNoneLines() {
    String raw =
        """
        ## 4. Cautions
        none
        ## 6. Open Questions
        未確定事項はありません
        - Open Questions: none
        """;

    String normalized = postProcessor.sanitize(raw);

    assertThat(normalized).contains("## 4. Cautions");
    assertThat(normalized).contains("- None");
    assertThat(normalized).contains("## 6. Open Questions");
    assertThat(normalized).contains("- なし");
    assertThat(normalized).contains("- None");
  }

  @Test
  void sanitize_shouldKeepQuotedMetadataAndSkipNonDependencyBullets() {
    String raw =
        """
        - Package: `com.example`
        #### 3.1.6 Dependencies
        - 依存なし
        - com.example.Dependency#doWork()
        """;

    String normalized = postProcessor.sanitize(raw);

    assertThat(normalized).contains("- Package: `com.example`");
    assertThat(normalized).contains("- 依存なし");
    assertThat(normalized).doesNotContain("- `依存なし`");
    assertThat(normalized).contains("- `com.example.Dependency#doWork()`");
  }

  @Test
  void sanitize_shouldReturnEmptyForNullAndFenceOnlyResponses() {
    assertThat(postProcessor.sanitize(null)).isEmpty();
    assertThat(postProcessor.sanitize("```")).isEmpty();
    assertThat(postProcessor.sanitize("```markdown\n```")).isEmpty();
  }

  @Test
  void sanitize_shouldNormalizeOpenQuestionsVariantsAndKeepUnmatchedLines() {
    String raw =
        """
        ## 6. Open Questions
        - No open questions without methods.
        - Open Questions: none
        - Open questions remain for later
        ## 2. Purpose
        none
        """;

    String normalized = postProcessor.sanitize(raw);

    assertThat(normalized).contains("- None");
    assertThat(normalized).contains("- Open questions remain for later");
    assertThat(normalized).contains("## 2. Purpose");
    assertThat(normalized).contains("none");
  }

  @Test
  void sanitize_shouldNotTreatOverflowTopLevelHeadingAsValidSection() {
    String raw = """
        ## 999999999999999999999. Oversized
        none
        """;

    String normalized = postProcessor.sanitize(raw);

    assertThat(normalized).contains("## 999999999999999999999. Oversized");
    assertThat(normalized).doesNotContain("- None");
    assertThat(normalized).contains("none");
  }

  @Test
  void sanitize_shouldNormalizeAdditionalJapaneseAndEnglishOpenQuestionNoneVariants() {
    String raw =
        """
        ## 6. 未確定事項（解析情報不足）
        未確定事項はない
        未確定事項はなし
        ## 6. Open Questions
        Open questions: none with no methods
        """;

    String normalized = postProcessor.sanitize(raw);

    assertThat(normalized).contains("- なし");
    assertThat(normalized).contains("- None");
  }

  @Test
  void sanitize_shouldKeepDependencyBulletsWhenTheyDoNotLookLikeReferences() {
    String raw =
        """
        #### 3.1.6 Dependencies
        - simpleToken
        - 項目A
        - name: value
        - com.example.Service#doWork()
        """;

    String normalized = postProcessor.sanitize(raw);

    assertThat(normalized).contains("- simpleToken");
    assertThat(normalized).contains("- 項目A");
    assertThat(normalized).contains("- name: value");
    assertThat(normalized).contains("- `com.example.Service#doWork()`");
    assertThat(normalized).doesNotContain("- `simpleToken`");
    assertThat(normalized).doesNotContain("- `項目A`");
    assertThat(normalized).doesNotContain("- `name: value`");
  }

  @Test
  void sanitize_shouldHandleFenceWithoutNewlineAfterOpeningMarker() {
    String raw = "```markdown body";

    String normalized = postProcessor.sanitize(raw);

    assertThat(normalized).isEqualTo("markdown body");
  }

  @Test
  void sanitize_shouldNormalizeMetadataWithEmbeddedBackticksAndPreserveQuotedOrBlankValues() {
    String raw =
        """
        - Package: service`core
        - File Path: `src/main/java/com/example/A.java`
        - Package:
        """;

    String normalized = postProcessor.sanitize(raw);

    assertThat(normalized).contains("- Package: `servicecore`");
    assertThat(normalized).contains("- File Path: `src/main/java/com/example/A.java`");
    assertThat(normalized).containsPattern("(?m)^- Package:\\s*$");
  }

  @Test
  void sanitize_shouldHandleDependencySubsectionStateTransitions() {
    String raw =
        """
        #### 3.1.6 Dependencies
        note-line-without-bullet
        - None
        - `com.example.Already#quoted()`
        - com.example.Call#invoke()
        #### 3.1.7 Test Viewpoints
        - com.example.Call#invoke()
        ## 4. Cautions
        - com.example.Next#call()
        """;

    String normalized = postProcessor.sanitize(raw);

    assertThat(normalized).contains("note-line-without-bullet");
    assertThat(normalized).contains("- None");
    assertThat(normalized).contains("- `com.example.Already#quoted()`");
    assertThat(normalized).contains("- `com.example.Call#invoke()`");
    assertThat(normalized).contains("#### 3.1.7 Test Viewpoints");
    assertThat(normalized).contains("- com.example.Call#invoke()");
    assertThat(normalized).contains("## 4. Cautions");
    assertThat(normalized).contains("- com.example.Next#call()");
  }

  @Test
  void sanitize_shouldNormalizeIndentedOpenQuestionNoneLines() {
    String raw =
        """
        ## 6. Open Questions
          - 未確定事項はない
          - no open questions
        """;

    String normalized = postProcessor.sanitize(raw);

    assertThat(normalized).contains("  - なし");
    assertThat(normalized).contains("  - None");
  }

  @Test
  void sanitize_shouldNormalizeOpenQuestionsNoneWhenNoMethodsAreMentioned() {
    String raw =
        """
        ## 6. Open Questions
        - Open questions: none, no methods require follow-up.
        """;

    String normalized = postProcessor.sanitize(raw);

    assertThat(normalized).contains("- None");
  }
}
