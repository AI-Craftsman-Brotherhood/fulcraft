package com.craftsmanbro.fulcraft.plugins.analysis.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.plugins.analysis.core.model.MethodId;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import java.util.List;
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

  @Test
  void resolvesSimpleParameterTypesViaImports() {
    MethodInfo simple = method("describe", "describe(Shape)");
    MethodInfo fqn = method("describe", "describe(com.demo.model.Shape)");
    List<String> imports =
        List.of("com.demo.model.Circle", "com.demo.model.Shape", "java.util.List");

    MethodId fromSimple =
        normalizer.toMethodId("com.demo.service.ShapeService", simple, null, imports);
    MethodId fromFqn = normalizer.toMethodId("com.demo.service.ShapeService", fqn, null, imports);

    assertThat(fromSimple).isEqualTo(fromFqn);
    assertThat(fromSimple.parameterTypes()).containsExactly("com.demo.model.Shape");
  }

  @Test
  void importsTakePrecedenceOverDefaultSimpleTypeMap() {
    // A file importing java.sql.Date must not be coerced to java.util.Date.
    MethodInfo method = method("at", "at(Date)");
    List<String> imports = List.of("java.sql.Date");

    MethodId id = normalizer.toMethodId("com.demo.Repo", method, null, imports);

    assertThat(id.parameterTypes()).containsExactly("java.sql.Date");
  }

  @Test
  void fallsBackToSamePackageWhenImportAbsent() {
    MethodInfo method = method("describe", "describe(Shape)");

    MethodId id = normalizer.toMethodId("com.demo.service.ShapeService", method, null, List.of());

    assertThat(id.parameterTypes()).containsExactly("com.demo.service.Shape");
  }

  private MethodInfo method(String name, String signature) {
    MethodInfo method = new MethodInfo();
    method.setName(name);
    method.setSignature(signature);
    return method;
  }
}
