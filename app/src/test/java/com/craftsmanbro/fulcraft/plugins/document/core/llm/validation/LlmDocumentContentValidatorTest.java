package com.craftsmanbro.fulcraft.plugins.document.core.llm.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LlmDocumentContentValidatorTest {

  private final LlmDocumentContentValidator validator = new LlmDocumentContentValidator();

  @Test
  void validate_shouldRejectKnownConstructorAsUnresolvedInOpenQuestions() {
    String document =
        """
        ## 2. クラス外部仕様
        - クラス名: `PaymentResult`
        - パッケージ: `com.example.legacy`
        - ファイルパス: `src/main/java/com/example/legacy/PaymentService.java`
        - クラス種別: `class`
        - 継承: `なし`
        - 実装インターフェース: `なし`
        ## 3. メソッド仕様
        ### 3.1 failure
        - 仕様記述
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - `PaymentResult(boolean, String, BigDecimal, String)` コンストラクタの存在確認が必要
        """;
    List<String> reasons = new ArrayList<>();

    validator.validate(document, validationContext(), reasons, true);

    assertThat(reasons).anySatisfy(reason -> assertThat(reason).contains("既知コンストラクタを未確認扱い"));
  }

  @Test
  void validate_shouldRejectKnownConstructorAsUnresolvedOutsideOpenQuestions() {
    String document =
        """
        ## 2. クラス外部仕様
        - クラス名: `PaymentResult`
        - パッケージ: `com.example.legacy`
        - ファイルパス: `src/main/java/com/example/legacy/PaymentService.java`
        - クラス種別: `class`
        - 継承: `なし`
        - 実装インターフェース: `なし`
        ## 3. メソッド仕様
        ### 3.1 failure
        - `PaymentResult(boolean, String, BigDecimal, String)` コンストラクタは解析情報に提示されていない
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    List<String> reasons = new ArrayList<>();

    validator.validate(document, validationContext(), reasons, true);

    assertThat(reasons).anySatisfy(reason -> assertThat(reason).contains("既知コンストラクタを未確認扱い"));
  }

  @Test
  void validate_shouldAllowUnknownConstructorSignature() {
    String document =
        """
        ## 2. クラス外部仕様
        - クラス名: `PaymentResult`
        - パッケージ: `com.example.legacy`
        - ファイルパス: `src/main/java/com/example/legacy/PaymentService.java`
        - クラス種別: `class`
        - 継承: `なし`
        - 実装インターフェース: `なし`
        ## 3. メソッド仕様
        ### 3.1 failure
        - 仕様記述
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - `PaymentResult(String)` コンストラクタの存在確認が必要
        """;
    List<String> reasons = new ArrayList<>();

    validator.validate(document, validationContext(), reasons, true);

    assertThat(reasons).noneSatisfy(reason -> assertThat(reason).contains("既知コンストラクタを未確認扱い"));
  }

  @Test
  void validate_shouldRejectKnownConstructorWithDefinitionUncertaintyPhrasing() {
    String document =
        """
        ## 2. クラス外部仕様
        - クラス名: `PaymentResult`
        - パッケージ: `com.example.legacy`
        - ファイルパス: `src/main/java/com/example/legacy/PaymentService.java`
        - クラス種別: `class`
        - 継承: `なし`
        - 実装インターフェース: `なし`
        ## 3. メソッド仕様
        ### 3.1 failure
        - 仕様記述
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - `PaymentResult(boolean, String, BigDecimal, String)` コンストラクタの定義有無が不明
        """;
    List<String> reasons = new ArrayList<>();

    validator.validate(document, validationContext(), reasons, true);

    assertThat(reasons).anySatisfy(reason -> assertThat(reason).contains("既知コンストラクタを未確認扱い"));
  }

  @Test
  void validate_shouldRejectKnownConstructorWhenMarkedAsNotIncluded() {
    String document =
        """
        ## 2. クラス外部仕様
        - クラス名: `PaymentResult`
        - パッケージ: `com.example.legacy`
        - ファイルパス: `src/main/java/com/example/legacy/PaymentService.java`
        - クラス種別: `class`
        - 継承: `なし`
        - 実装インターフェース: `なし`
        ## 3. メソッド仕様
        ### 3.1 failure
        - `PaymentResult(boolean, String, BigDecimal, String)` コンストラクタが分析結果に含まれていない
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    List<String> reasons = new ArrayList<>();

    validator.validate(document, validationContext(), reasons, true);

    assertThat(reasons).anySatisfy(reason -> assertThat(reason).contains("既知コンストラクタを未確認扱い"));
  }

  @Test
  void validate_shouldRejectAnalysisGapStatementWhenOpenQuestionsIsNone() {
    String document =
        """
        ## 2. クラス外部仕様
        - クラス名: `PaymentResult`
        - パッケージ: `com.example.legacy`
        - ファイルパス: `src/main/java/com/example/legacy/PaymentService.java`
        - クラス種別: `class`
        - 継承: `なし`
        - 実装インターフェース: `なし`
        ## 3. メソッド仕様
        ### 3.1 failure
        - 解析情報にメソッド全体のソースコードが含まれていないため、返却条件を特定できない
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - なし
        """;
    List<String> reasons = new ArrayList<>();

    validator.validate(document, validationContext(), reasons, true);

    assertThat(reasons).anySatisfy(reason -> assertThat(reason).contains("本文に解析情報不足・特定不可の記述"));
  }

  @Test
  void validate_shouldAllowAnalysisGapStatementWhenOpenQuestionsHasEntry() {
    String document =
        """
        ## 2. クラス外部仕様
        - クラス名: `PaymentResult`
        - パッケージ: `com.example.legacy`
        - ファイルパス: `src/main/java/com/example/legacy/PaymentService.java`
        - クラス種別: `class`
        - 継承: `なし`
        - 実装インターフェース: `なし`
        ## 3. メソッド仕様
        ### 3.1 failure
        - 解析情報にメソッド全体のソースコードが含まれていないため、返却条件を特定できない
        ## 4. 要注意事項
        - なし
        ## 5. 改善提案（任意）
        - なし
        ## 6. 未確定事項（解析情報不足）
        - 返却条件の確定には追加解析が必要
        """;
    List<String> reasons = new ArrayList<>();

    validator.validate(document, validationContext(), reasons, true);

    assertThat(reasons).noneSatisfy(reason -> assertThat(reason).contains("本文に解析情報不足・特定不可の記述"));
  }

  @Test
  void validate_shouldRejectNoAnalysisDataStatementWhenOpenQuestionsIsNone() {
    String document =
        """
        ## 2. External Class Specification
        - Class Name: `PaymentResult`
        - Package: `com.example.legacy`
        - File Path: `src/main/java/com/example/legacy/PaymentService.java`
        - Class Type: `class`
        - Extends: `none`
        - Implements: `none`
        ## 3. Method Specifications
        ### 3.1 failure
        - No analysis data
        ## 4. Cautions
        - None
        ## 5. Recommendations (Optional)
        - None
        ## 6. Open Questions (Insufficient Analysis Data)
        - None
        """;
    List<String> reasons = new ArrayList<>();

    validator.validate(document, validationContext(), reasons, false);

    assertThat(reasons)
        .anySatisfy(
            reason ->
                assertThat(reason)
                    .contains("Main sections include analysis-insufficiency statements"));
  }

  @Test
  void validate_shouldRejectNoneSpecifiedInAnalysisDataStatementWhenOpenQuestionsIsNone() {
    String document =
        """
        ## 2. External Class Specification
        - Class Name: `PaymentResult`
        - Package: `com.example.legacy`
        - File Path: `src/main/java/com/example/legacy/PaymentService.java`
        - Class Type: `class`
        - Extends: `none`
        - Implements: `none`
        ## 3. Method Specifications
        ### 3.1 failure
        - None specified in analysis data.
        ## 4. Cautions
        - None
        ## 5. Recommendations (Optional)
        - None
        ## 6. Open Questions (Insufficient Analysis Data)
        - None
        """;
    List<String> reasons = new ArrayList<>();

    validator.validate(document, validationContext(), reasons, false);

    assertThat(reasons)
        .anySatisfy(
            reason ->
                assertThat(reason)
                    .contains("Main sections include analysis-insufficiency statements"));
  }

  @Test
  void validate_shouldRejectExcerptGapStatementWhenOpenQuestionsIsNone() {
    String document =
        """
        ## 2. External Class Specification
        - Class Name: `PaymentResult`
        - Package: `com.example.legacy`
        - File Path: `src/main/java/com/example/legacy/PaymentService.java`
        - Class Type: `class`
        - Extends: `none`
        - Implements: `none`
        ## 3. Method Specifications
        ### 3.1 failure
        - Details beyond the provided excerpt are not fully available.
        ## 4. Cautions
        - None
        ## 5. Recommendations (Optional)
        - None
        ## 6. Open Questions (Insufficient Analysis Data)
        - None
        """;
    List<String> reasons = new ArrayList<>();

    validator.validate(document, validationContext(), reasons, false);

    assertThat(reasons)
        .anySatisfy(
            reason ->
                assertThat(reason)
                    .contains("Main sections include analysis-insufficiency statements"));
  }

  @Test
  void validate_shouldRejectSourceExcerptShownGapStatementWhenOpenQuestionsIsNone() {
    String document =
        """
        ## 2. External Class Specification
        - Class Name: `PaymentResult`
        - Package: `com.example.legacy`
        - File Path: `src/main/java/com/example/legacy/PaymentService.java`
        - Class Type: `class`
        - Extends: `none`
        - Implements: `none`
        ## 3. Method Specifications
        ### 3.1 failure
        - A loop guard indicates break (the exact downstream effects are not fully shown in the provided source excerpt)
        ## 4. Cautions
        - None
        ## 5. Recommendations (Optional)
        - None
        ## 6. Open Questions (Insufficient Analysis Data)
        - None
        """;
    List<String> reasons = new ArrayList<>();

    validator.validate(document, validationContext(), reasons, false);

    assertThat(reasons)
        .anySatisfy(
            reason ->
                assertThat(reason)
                    .contains("Main sections include analysis-insufficiency statements"));
  }

  private LlmDocumentContentValidator.ValidationContext validationContext() {
    return new LlmDocumentContentValidator.ValidationContext(
        List.of("failure"),
        Set.of(),
        Set.of(),
        Set.of(),
        Set.of(),
        Set.of(),
        Set.of(),
        Set.of("failure"),
        Set.of("paymentresult(boolean,string,bigdecimal,string)"),
        false,
        false);
  }
}
