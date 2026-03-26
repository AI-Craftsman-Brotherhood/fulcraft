package com.craftsmanbro.fulcraft.plugins.document.core.llm.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class LlmFallbackDocumentBuilderTest {

  private final LlmFallbackDocumentBuilder builder = new LlmFallbackDocumentBuilder();

  @Test
  void build_shouldReturnEmptyWhenInputIsNull() {
    assertThat(builder.build(null)).isEmpty();
  }

  @Test
  void build_shouldRenderPurposeLinesFromInput() {
    LlmFallbackDocumentBuilder.FallbackDocumentInput input =
        new LlmFallbackDocumentBuilder.FallbackDocumentInput(
            true,
            "OrderService",
            List.of("`OrderService` は主に サービス層 を担うクラスである。", "主要操作は `processOrder`, `cancelOrder`。"),
            "com.example",
            "src/main/java/com/example/OrderService.java",
            42,
            2,
            "class",
            "なし",
            "なし",
            "- non-final",
            "- String repository",
            List.of(),
            "- なし",
            List.of());

    String document = builder.build(input);

    assertThat(document).contains("- `OrderService` は主に サービス層 を担うクラスである。");
    assertThat(document).contains("- 主要操作は `processOrder`, `cancelOrder`。");
    assertThat(document).doesNotContain("外部契約とメソッド挙動を解析結果に基づいて整理する");
  }

  @Test
  void build_shouldUseGenericPurposeLineWhenInputPurposeLinesAreEmpty() {
    LlmFallbackDocumentBuilder.FallbackDocumentInput input =
        new LlmFallbackDocumentBuilder.FallbackDocumentInput(
            true,
            "OrderService",
            List.of(),
            "com.example",
            "src/main/java/com/example/OrderService.java",
            42,
            2,
            "class",
            "なし",
            "なし",
            "- non-final",
            "- String repository",
            List.of(),
            "- なし",
            List.of());

    String document = builder.build(input);

    assertThat(document).contains("`OrderService` の外部契約とメソッド挙動を解析結果に基づいて整理する");
  }

  @Test
  void build_shouldWrapDependenciesAndFallbackWhenSectionItemsAreBlank() {
    LlmFallbackDocumentBuilder.FallbackMethodSection section =
        new LlmFallbackDocumentBuilder.FallbackMethodSection(
            "execute",
            "void execute()",
            List.of("   "),
            List.of(""),
            List.of(""),
            List.of(""),
            List.of("com.example.Helper#run()", "`com.example.Helper#safe()`"),
            List.of(" "));

    LlmFallbackDocumentBuilder.FallbackDocumentInput input =
        new LlmFallbackDocumentBuilder.FallbackDocumentInput(
            false,
            "OrderService",
            List.of("OrderService handles requests."),
            "com.example",
            "src/main/java/com/example/OrderService.java",
            42,
            1,
            "class",
            "None",
            "None",
            "final",
            "status",
            List.of(section),
            "- None",
            List.of());

    String document = builder.build(input);

    assertThat(document).contains("- OrderService handles requests.");
    assertThat(document).contains("- No analysis data");
    assertThat(document).contains("- `com.example.Helper#run()`");
    assertThat(document).contains("- `com.example.Helper#safe()`");
    assertThat(document).contains("- None");
  }

  @Test
  void build_shouldRenderOpenQuestionsAndAcceptPreformattedPurposeBullet() {
    LlmFallbackDocumentBuilder.FallbackMethodSection section =
        new LlmFallbackDocumentBuilder.FallbackMethodSection(
            "sync",
            "void sync()",
            List.of("input must be valid"),
            List.of("status updated"),
            List.of("synchronize state"),
            List.of("none"),
            List.of("com.example.SyncGateway#execute()"),
            List.of("verify status"));

    LlmFallbackDocumentBuilder.FallbackDocumentInput input =
        new LlmFallbackDocumentBuilder.FallbackDocumentInput(
            true,
            "SyncService",
            Arrays.asList("- 既存バレットを維持する。", " 外部連携を調整する。 "),
            "com.example",
            "src/main/java/com/example/SyncService.java",
            18,
            1,
            "class",
            "なし",
            "なし",
            "final",
            "state\n- mode",
            List.of(section),
            "- 注意事項なし",
            List.of("入力境界値の追加確認", "外部依存失敗時の復旧方針"));

    String document = builder.build(input);

    assertThat(document).contains("- 既存バレットを維持する。");
    assertThat(document).contains("- 外部連携を調整する。");
    assertThat(document).contains("  - state");
    assertThat(document).contains("  - mode");
    assertThat(document).contains("- 入力境界値の追加確認");
    assertThat(document).contains("- 外部依存失敗時の復旧方針");
  }

  @Test
  void build_shouldUseFallbackPurposeWhenPurposeLinesContainOnlyBlankEntries() {
    LlmFallbackDocumentBuilder.FallbackDocumentInput input =
        new LlmFallbackDocumentBuilder.FallbackDocumentInput(
            true,
            "BlankPurposeService",
            Arrays.asList(" ", "\t"),
            "com.example",
            "src/main/java/com/example/BlankPurposeService.java",
            10,
            0,
            "class",
            "",
            "",
            "",
            "",
            List.of(),
            "",
            List.of());

    String document = builder.build(input);

    assertThat(document).contains("`BlankPurposeService` の外部契約とメソッド挙動を解析結果に基づいて整理する");
  }

  @Test
  void fallbackRecords_shouldNormalizeNullInputsAndExposeImmutableLists() {
    LlmFallbackDocumentBuilder.FallbackMethodSection methodSection =
        new LlmFallbackDocumentBuilder.FallbackMethodSection(
            null, null, null, null, null, null, null, null);

    assertThat(methodSection.methodName()).isEmpty();
    assertThat(methodSection.signature()).isEmpty();
    assertThat(methodSection.preconditions()).isEmpty();
    assertThat(methodSection.postconditions()).isEmpty();
    assertThat(methodSection.normalFlows()).isEmpty();
    assertThat(methodSection.errorBoundaries()).isEmpty();
    assertThat(methodSection.dependencyCalls()).isEmpty();
    assertThat(methodSection.testViewpoints()).isEmpty();
    assertThatThrownBy(() -> methodSection.preconditions().add("x"))
        .isInstanceOf(UnsupportedOperationException.class);

    LlmFallbackDocumentBuilder.FallbackDocumentInput input =
        new LlmFallbackDocumentBuilder.FallbackDocumentInput(
            true, null, null, null, null, 0, 0, null, null, null, null, null, null, null, null);

    assertThat(input.className()).isEmpty();
    assertThat(input.purposeLines()).isEmpty();
    assertThat(input.packageName()).isEmpty();
    assertThat(input.filePath()).isEmpty();
    assertThat(input.classType()).isEmpty();
    assertThat(input.extendsInfo()).isEmpty();
    assertThat(input.implementsInfo()).isEmpty();
    assertThat(input.classAttributes()).isEmpty();
    assertThat(input.fieldsInfo()).isEmpty();
    assertThat(input.methodSections()).isEmpty();
    assertThat(input.cautionsInfo()).isEmpty();
    assertThat(input.openQuestions()).isEmpty();
    assertThatThrownBy(() -> input.purposeLines().add("x"))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
