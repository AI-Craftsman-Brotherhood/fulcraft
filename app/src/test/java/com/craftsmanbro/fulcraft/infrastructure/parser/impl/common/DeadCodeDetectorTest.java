package com.craftsmanbro.fulcraft.infrastructure.parser.impl.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.infrastructure.parser.model.AnalysisContext;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.AnalysisResult;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.ClassInfo;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.MethodInfo;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeadCodeDetectorTest {

  @Test
  void markDeadCode_marksOnlyPrivateNonEntryPointWithBodyAndNoIncoming() {
    AnalysisContext context = new AnalysisContext();

    MethodInfo privateMethod = method("privateMethod", "private", List.of());
    MethodInfo publicMethod = method("publicMethod", "public", List.of());
    MethodInfo entryPointMethod = method("entryPoint", "private", List.of("GetMapping"));
    MethodInfo noBodyMethod = method("noBody", "private", List.of());
    MethodInfo usedPrivateMethod = method("usedPrivate", "private", List.of());

    context.getMethodInfos().put("privateMethod()", privateMethod);
    context.getMethodInfos().put("publicMethod()", publicMethod);
    context.getMethodInfos().put("entryPoint()", entryPointMethod);
    context.getMethodInfos().put("noBody()", noBodyMethod);
    context.getMethodInfos().put("usedPrivate()", usedPrivateMethod);

    context.getIncomingCounts().put("publicMethod()", 2);
    context.getIncomingCounts().put("usedPrivate()", 1);
    context.getMethodHasBody().put("noBody()", false);

    DeadCodeDetector detector = new DeadCodeDetector();
    detector.markDeadCode(context);

    assertTrue(privateMethod.isDeadCode());
    assertFalse(publicMethod.isDeadCode());
    assertFalse(entryPointMethod.isDeadCode());
    assertFalse(noBodyMethod.isDeadCode());
    assertFalse(usedPrivateMethod.isDeadCode());

    assertEquals(0, privateMethod.getUsageCount());
    assertEquals(2, publicMethod.getUsageCount());
    assertEquals(1, usedPrivateMethod.getUsageCount());
  }

  @Test
  void markDeadCode_usesArityFallbackIncomingForUniquePrivateMethod() {
    AnalysisContext context = new AnalysisContext();

    MethodInfo privateMethod = method("isValidPaymentMethod", "private", List.of());
    privateMethod.setSignature("isValidPaymentMethod(String)");

    String methodKey = "com.example.PaymentService#isValidPaymentMethod(String)";
    context.getMethodInfos().put(methodKey, privateMethod);
    context.getIncomingCounts().put("com.example.PaymentService#isValidPaymentMethod(1)", 1);

    DeadCodeDetector detector = new DeadCodeDetector();
    detector.markDeadCode(context);

    assertFalse(privateMethod.isDeadCode());
    assertEquals(1, privateMethod.getUsageCount());
  }

  @Test
  void markDeadCode_doesNotMapArityFallbackWhenOverloadIsAmbiguous() {
    AnalysisContext context = new AnalysisContext();

    MethodInfo first = method("format", "private", List.of());
    first.setSignature("format(String)");
    MethodInfo second = method("format", "private", List.of());
    second.setSignature("format(Integer)");

    context.getMethodInfos().put("com.example.Foo#format(String)", first);
    context.getMethodInfos().put("com.example.Foo#format(Integer)", second);
    context.getIncomingCounts().put("com.example.Foo#format(1)", 1);

    DeadCodeDetector detector = new DeadCodeDetector();
    detector.markDeadCode(context);

    assertTrue(first.isDeadCode());
    assertTrue(second.isDeadCode());
    assertEquals(0, first.getUsageCount());
    assertEquals(0, second.getUsageCount());
  }

  @Test
  void markDeadCode_mapsQualifiedParameterTypeToSimpleSignature() {
    AnalysisContext context = new AnalysisContext();

    MethodInfo privateMethod = method("load", "private", List.of());
    privateMethod.setSignature("load(String)");

    context.getMethodInfos().put("com.example.Loader#load(String)", privateMethod);
    context.getIncomingCounts().put("com.example.Loader#load(java.lang.String)", 2);

    DeadCodeDetector detector = new DeadCodeDetector();
    detector.markDeadCode(context);

    assertFalse(privateMethod.isDeadCode());
    assertEquals(2, privateMethod.getUsageCount());
  }

  @Test
  void markDeadClasses_marksDeadOnlyWhenAllMethodsDead_andSkipsInterfaces() {
    MethodInfo dead1 = methodWithDeadCode(true);
    MethodInfo dead2 = methodWithDeadCode(true);
    MethodInfo alive = methodWithDeadCode(false);

    ClassInfo interfaceClass = new ClassInfo();
    interfaceClass.setInterface(true);
    interfaceClass.setMethods(List.of(dead1));

    ClassInfo allDeadClass = new ClassInfo();
    allDeadClass.setMethods(List.of(dead1, dead2));

    ClassInfo mixedClass = new ClassInfo();
    mixedClass.setMethods(List.of(dead1, alive));

    ClassInfo emptyClass = new ClassInfo();
    emptyClass.setMethods(List.of());

    AnalysisResult result = new AnalysisResult("test");
    result.setClasses(List.of(interfaceClass, allDeadClass, mixedClass, emptyClass));

    DeadCodeDetector detector = new DeadCodeDetector();
    detector.markDeadClasses(result);

    assertFalse(interfaceClass.isDeadCode());
    assertTrue(allDeadClass.isDeadCode());
    assertFalse(mixedClass.isDeadCode());
    assertFalse(emptyClass.isDeadCode());
  }

  private static MethodInfo method(String name, String visibility, List<String> annotations) {
    MethodInfo method = new MethodInfo();
    method.setName(name);
    method.setSignature(name + "()");
    method.setVisibility(visibility);
    method.setAnnotations(annotations);
    return method;
  }

  private static MethodInfo methodWithDeadCode(boolean deadCode) {
    MethodInfo method = new MethodInfo();
    method.setDeadCode(deadCode);
    return method;
  }
}
