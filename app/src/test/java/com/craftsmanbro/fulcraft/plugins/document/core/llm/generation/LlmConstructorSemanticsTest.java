package com.craftsmanbro.fulcraft.plugins.document.core.llm.generation;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import org.junit.jupiter.api.Test;

class LlmConstructorSemanticsTest {

  private final LlmConstructorSemantics semantics = new LlmConstructorSemantics();

  @Test
  void isConstructor_shouldReturnTrueForAnnotatedGenericConstructorSignature() {
    MethodInfo method = new MethodInfo();
    method.setName("OrderService");
    method.setSignature("@Inject public <T> OrderService(T repository)");

    assertThat(semantics.isConstructor(method)).isTrue();
  }

  @Test
  void isConstructor_shouldReturnFalseWhenReturnTypeExists() {
    MethodInfo method = new MethodInfo();
    method.setName("OrderService");
    method.setSignature("public int OrderService()");

    assertThat(semantics.isConstructor(method)).isFalse();
  }

  @Test
  void isConstructor_shouldHandleQualifiedBacktickSignature() {
    MethodInfo method = new MethodInfo();
    method.setName("Inner");
    method.setSignature("protected `com.example.Outer.Inner`(String value)");

    assertThat(semantics.isConstructor(method)).isTrue();
  }

  @Test
  void isTrivialEmptyConstructor_shouldReturnTrueForIgnorableStatementsOnly() {
    MethodInfo method = new MethodInfo();
    method.setName("OrderService");
    method.setSignature("public OrderService(String id)");
    method.setSourceCode(
        """
            public OrderService(String id) {
              super();
              Objects.requireNonNull(id);
              Preconditions.checkNotNull(id);
              Preconditions.checkArgument(!id.isBlank());
              assert id != null;
            }
            """);

    assertThat(semantics.isTrivialEmptyConstructor(method)).isTrue();
  }

  @Test
  void isTrivialEmptyConstructor_shouldReturnFalseWhenStateIsModified() {
    MethodInfo method = new MethodInfo();
    method.setName("OrderService");
    method.setSignature("public OrderService(String id)");
    method.setSourceCode(
        """
            public OrderService(String id) {
              this.id = id;
            }
            """);

    assertThat(semantics.isTrivialEmptyConstructor(method)).isFalse();
  }

  @Test
  void isConstructor_shouldReturnFalseWhenUnexpectedModifierAppears() {
    MethodInfo method = new MethodInfo();
    method.setName("OrderService");
    method.setSignature("public static OrderService()");

    assertThat(semantics.isConstructor(method)).isFalse();
  }

  @Test
  void isTrivialEmptyConstructor_shouldReturnTrueWhenConstructorSourceIsMissing() {
    MethodInfo method = new MethodInfo();
    method.setName("OrderService");
    method.setSignature("public OrderService()");

    assertThat(semantics.isTrivialEmptyConstructor(method)).isTrue();
  }

  @Test
  void isTrivialEmptyConstructor_shouldReturnFalseForNonConstructorMethod() {
    MethodInfo method = new MethodInfo();
    method.setName("process");
    method.setSignature("void process()");
    method.setSourceCode("void process() {}");

    assertThat(semantics.isTrivialEmptyConstructor(method)).isFalse();
  }

  @Test
  void isConstructor_shouldReturnFalseForNullOrBlankInputs() {
    assertThat(semantics.isConstructor(null)).isFalse();

    MethodInfo blankName = new MethodInfo();
    blankName.setName(" ");
    blankName.setSignature("public OrderService()");
    assertThat(semantics.isConstructor(blankName)).isFalse();

    MethodInfo blankSignature = new MethodInfo();
    blankSignature.setName("OrderService");
    blankSignature.setSignature(" ");
    assertThat(semantics.isConstructor(blankSignature)).isFalse();

    MethodInfo noParen = new MethodInfo();
    noParen.setName("OrderService");
    noParen.setSignature("public OrderService");
    assertThat(semantics.isConstructor(noParen)).isFalse();
  }

  @Test
  void isConstructor_shouldHandleMultiTokenTypeParameterClause() {
    MethodInfo method = new MethodInfo();
    method.setName("OrderService");
    method.setSignature("public <T extends Number > OrderService(T value)");

    assertThat(semantics.isConstructor(method)).isTrue();
  }

  @Test
  void isConstructor_shouldReturnFalseWhenNameTokenDoesNotMatch() {
    MethodInfo method = new MethodInfo();
    method.setName("OrderService");
    method.setSignature("public AnotherService()");

    assertThat(semantics.isConstructor(method)).isFalse();
  }

  @Test
  void isTrivialEmptyConstructor_shouldTreatThisDelegationAsIgnorable() {
    MethodInfo method = new MethodInfo();
    method.setName("OrderService");
    method.setSignature("public OrderService(String id)");
    method.setSourceCode(
        """
            public OrderService(String id) {
              this();
            }
            """);

    assertThat(semantics.isTrivialEmptyConstructor(method)).isTrue();
  }

  @Test
  void isTrivialEmptyConstructor_shouldReturnFalseWhenUnclosedBodyContainsStatement() {
    MethodInfo method = new MethodInfo();
    method.setName("OrderService");
    method.setSignature("public OrderService(String id)");
    method.setSourceCode("public OrderService(String id) { this.id = id;");

    assertThat(semantics.isTrivialEmptyConstructor(method)).isFalse();
  }
}
