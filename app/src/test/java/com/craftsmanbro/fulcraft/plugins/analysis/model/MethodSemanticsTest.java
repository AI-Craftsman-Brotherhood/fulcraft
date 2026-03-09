package com.craftsmanbro.fulcraft.plugins.analysis.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class MethodSemanticsTest {

  @Test
  void methodsExcludingImplicitDefaultConstructors_filtersOnlyImplicitDefaultConstructor() {
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.billing.InvoiceService");

    MethodInfo implicitDefaultConstructor = method("InvoiceService", "InvoiceService()", 0, 0);
    MethodInfo explicitConstructor = method("InvoiceService", "public InvoiceService()", 0, 3);
    MethodInfo businessMethod = method("calculate", "public int calculate()", 0, 8);
    classInfo.setMethods(List.of(implicitDefaultConstructor, explicitConstructor, businessMethod));

    assertThat(MethodSemantics.methodsExcludingImplicitDefaultConstructors(classInfo))
        .extracting(MethodInfo::getName)
        .containsExactly("InvoiceService", "calculate");
    assertThat(MethodSemantics.countMethodsExcludingImplicitDefaultConstructors(classInfo))
        .isEqualTo(2);
  }

  @Test
  void isConstructor_detectsInitNameAndQualifiedSignature() {
    MethodInfo initNamed = method("<init>", null, 0, 1);
    MethodInfo qualifiedSignature =
        method(
            "ignored", "public com.example.billing.Outer$PaymentResult(java.lang.String id)", 1, 4);

    assertThat(MethodSemantics.isConstructor(initNamed, "PaymentResult")).isTrue();
    assertThat(MethodSemantics.isConstructor(qualifiedSignature, "PaymentResult")).isTrue();
  }

  @Test
  void simpleClassName_normalizesNestedSeparatorsAndHandlesBlankInput() {
    assertThat(MethodSemantics.simpleClassName("com.example.Outer$Inner")).isEqualTo("Inner");
    assertThat(MethodSemantics.simpleClassName("   ")).isEmpty();
    assertThat(MethodSemantics.methodsExcludingImplicitDefaultConstructors(null)).isEmpty();
  }

  private MethodInfo method(String name, String signature, int parameterCount, int loc) {
    MethodInfo methodInfo = new MethodInfo();
    methodInfo.setName(name);
    methodInfo.setSignature(signature);
    methodInfo.setParameterCount(parameterCount);
    methodInfo.setLoc(loc);
    return methodInfo;
  }
}
