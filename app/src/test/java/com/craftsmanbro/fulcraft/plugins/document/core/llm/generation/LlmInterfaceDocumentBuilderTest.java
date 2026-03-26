package com.craftsmanbro.fulcraft.plugins.document.core.llm.generation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class LlmInterfaceDocumentBuilderTest {

  @Test
  void build_shouldReturnEmptyWhenInputIsNull() {
    LlmInterfaceDocumentBuilder builder = new LlmInterfaceDocumentBuilder();

    assertThat(builder.build(null)).isEmpty();
  }

  @Test
  void build_shouldUseImplementationDefinedLabelWhenNoDeclaredExceptionsExist() {
    LlmInterfaceDocumentBuilder builder = new LlmInterfaceDocumentBuilder();

    LlmInterfaceDocumentBuilder.InterfaceDocumentInput input =
        new LlmInterfaceDocumentBuilder.InterfaceDocumentInput(
            true,
            "PaymentGateway",
            "com.example",
            "src/main/java/com/example/PaymentGateway.java",
            20,
            1,
            "インターフェース",
            "なし",
            "なし",
            "- nested_class: false",
            "- なし",
            List.of(
                new LlmInterfaceDocumentBuilder.InterfaceMethodSection(
                    "authorize", "boolean authorize(String id)", List.of())),
            "- なし",
            List.of("なし"));

    String document = builder.build(input);

    assertThat(document).contains("宣言例外なし（実装依存）");
    assertThat(document).doesNotContain("解析情報なし");
  }

  @Test
  void build_shouldListDeclaredExceptionsWhenAvailable() {
    LlmInterfaceDocumentBuilder builder = new LlmInterfaceDocumentBuilder();

    LlmInterfaceDocumentBuilder.InterfaceDocumentInput input =
        new LlmInterfaceDocumentBuilder.InterfaceDocumentInput(
            false,
            "PaymentGateway",
            "com.example",
            "src/main/java/com/example/PaymentGateway.java",
            20,
            1,
            "Interface",
            "None",
            "None",
            "- nested_class: false",
            "- None",
            List.of(
                new LlmInterfaceDocumentBuilder.InterfaceMethodSection(
                    "authorize", "boolean authorize(String id)", List.of("java.io.IOException"))),
            "- None",
            List.of("None"));

    String document = builder.build(input);

    assertThat(document).contains("- Exceptions: `java.io.IOException`");
  }

  @Test
  void build_shouldNormalizeFieldBulletsAndHandleNullCollections() {
    LlmInterfaceDocumentBuilder builder = new LlmInterfaceDocumentBuilder();

    LlmInterfaceDocumentBuilder.InterfaceDocumentInput input =
        new LlmInterfaceDocumentBuilder.InterfaceDocumentInput(
            false,
            "SamplePort",
            "com.example",
            "src/main/java/com/example/SamplePort.java",
            10,
            0,
            "Interface",
            "None",
            "None",
            "- nested_class: false",
            "- id\nstatus",
            List.of(
                new LlmInterfaceDocumentBuilder.InterfaceMethodSection(
                    "execute", "void execute()", null)),
            "- None",
            null);

    String document = builder.build(input);

    assertThat(document).contains("  - id");
    assertThat(document).contains("  - status");
    assertThat(document).doesNotContain("  - - id");
    assertThat(document).contains("None declared (implementation-defined)");
  }
}
