package com.craftsmanbro.fulcraft.plugins.analysis.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.plugins.analysis.core.model.MethodId;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MethodSignatureNormalizerTest {

  private final MethodSignatureNormalizer normalizer = new MethodSignatureNormalizer();

  @Test
  void normalizesSimpleAndFullyQualifiedSignatures() {
    MethodInfo simple =
        method(
            "calculateInvoice",
            "calculateInvoice(SimpleCustomerData, List, String, boolean, LocalDate)");
    MethodInfo fqn =
        method(
            "calculateInvoice",
            "calculateInvoice(com.example.legacy.SimpleCustomerData,java.util.List,java.lang.String,boolean,java.time.LocalDate)");

    MethodId id1 = normalizer.toMethodId("com.example.legacy.ComplexInvoiceService", simple);
    MethodId id2 = normalizer.toMethodId("com.example.legacy.ComplexInvoiceService", fqn);

    assertThat(id1).isEqualTo(id2);
    assertThat(id1.toString())
        .isEqualTo(
            "com.example.legacy.ComplexInvoiceService#calculateInvoice("
                + "com.example.legacy.SimpleCustomerData,java.util.List,java.lang.String,boolean,java.time.LocalDate)");
  }

  @Test
  void erasesGenericsAndNormalizesInnerClasses() {
    MethodInfo generic = method("process", "process(Map<String, Outer.Inner>, Outer$Inner)");

    MethodId id = normalizer.toMethodId("com.acme.Service$Impl", generic);

    // Note: Outer.Inner becomes "Outer.Inner" (not fully qualified) since it
    // contains a dot
    // and is treated as already qualified. $-style inner class is normalized to dot
    // notation.
    assertThat(id.toString()).isEqualTo("com.acme.Service.Impl#process(java.util.Map,Outer.Inner)");
  }

  @Test
  void preservesArrayDimensionsAndVarargs() {
    MethodInfo method = method("handle", "handle(String[][], int[][], String...)");

    MethodId id = normalizer.toMethodId("com.acme.ArrayService", method);

    assertThat(id.toString())
        .isEqualTo("com.acme.ArrayService#handle(java.lang.String[][],int[][],java.lang.String[])");
  }

  @Test
  void derivesMethodNameWhenProvidedNameIsBlank() {
    MethodInfo method = method("  ", "public Result calculate (String, Integer)");

    MethodId id = normalizer.toMethodId("com.acme.Worker", method);

    assertThat(id.methodName()).isEqualTo("calculate");
    assertThat(id.parameterTypes()).containsExactly("java.lang.String", "java.lang.Integer");
  }

  @Test
  void resolvesNestedTypesAndDefaultPackageFallback() {
    MethodInfo method = method(" ", "run(InnerType, CustomType, INT)");

    MethodId id = normalizer.toMethodId("TopLevel", method, Set.of("InnerType"));

    assertThat(id.methodName()).isEqualTo("run");
    assertThat(id.parameterTypes()).containsExactly("TopLevel.InnerType", "CustomType", "int");
  }

  @Test
  void parsesColonReturnTypeAndHandlesInvalidSignature() {
    MethodId withReturn =
        normalizer.toMethodId(
            "com.acme.Worker", method(null, "compute(Map<String, Integer>) : Integer"));
    MethodId invalid = normalizer.toMethodId("com.acme.Worker", method(null, "noParensSignature"));

    assertThat(withReturn.toString())
        .isEqualTo("com.acme.Worker#compute(java.util.Map):java.lang.Integer");
    assertThat(invalid.toString()).isEqualTo("com.acme.Worker#noParensSignature()");
  }

  @Test
  void handlesNullDeclaringClassAndEmptySignature() {
    MethodId id = normalizer.toMethodId(null, method("", " "));

    assertThat(id.declaringClassFqn()).isEmpty();
    assertThat(id.methodName()).isEmpty();
    assertThat(id.parameterTypes()).isEmpty();
    assertThat(id.returnType()).isNull();
  }

  private MethodInfo method(String name, String signature) {
    MethodInfo method = new MethodInfo();
    method.setName(name);
    method.setSignature(signature);
    return method;
  }
}
