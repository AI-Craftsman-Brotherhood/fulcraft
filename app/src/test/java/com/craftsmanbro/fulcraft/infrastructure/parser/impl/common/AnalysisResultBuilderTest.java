package com.craftsmanbro.fulcraft.infrastructure.parser.impl.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.infrastructure.parser.model.CalledMethodRef;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.FieldInfo;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.MethodInfo;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.ResolutionStatus;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalysisResultBuilderTest {

  @Test
  void methodInfo_setsAllProvidedFields() {
    List<String> thrown = List.of("java.io.IOException");
    List<String> annotations = List.of("org.junit.jupiter.api.Test");

    MethodInfo methodInfo =
        ResultBuilder.methodInfo()
            .name("doIt")
            .signature("doIt(int,java.lang.String)")
            .loc(42)
            .visibility("public")
            .cyclomaticComplexity(7)
            .usesRemovedApis(true)
            .parameterCount(2)
            .maxNestingDepth(3)
            .thrownExceptions(thrown)
            .annotations(annotations)
            .build();

    assertEquals("doIt", methodInfo.getName());
    assertEquals("doIt(int,java.lang.String)", methodInfo.getSignature());
    assertEquals(42, methodInfo.getLoc());
    assertEquals("public", methodInfo.getVisibility());
    assertEquals(7, methodInfo.getCyclomaticComplexity());
    assertTrue(methodInfo.isUsesRemovedApis());
    assertEquals(2, methodInfo.getParameterCount());
    assertEquals(3, methodInfo.getMaxNestingDepth());
    assertEquals(thrown, methodInfo.getThrownExceptions());
    assertEquals(annotations, methodInfo.getAnnotations());

    assertNotNull(methodInfo.getCalledMethods());
    assertTrue(methodInfo.getCalledMethods().isEmpty());
    assertFalse(methodInfo.isPartOfCycle());
    assertFalse(methodInfo.isDeadCode());
    assertFalse(methodInfo.isDuplicate());
  }

  @Test
  void methodInfo_allowsNullListsAndOverwritesDefaults() {
    MethodInfo methodInfo =
        ResultBuilder.methodInfo()
            .name("doIt")
            .signature("doIt()")
            .loc(1)
            .visibility("private")
            .cyclomaticComplexity(0)
            .usesRemovedApis(false)
            .parameterCount(0)
            .maxNestingDepth(0)
            .thrownExceptions(null)
            .annotations(null)
            .build();

    assertNotNull(methodInfo.getThrownExceptions());
    assertTrue(methodInfo.getThrownExceptions().isEmpty());
    assertNotNull(methodInfo.getAnnotations());
    assertTrue(methodInfo.getAnnotations().isEmpty());
    assertNotNull(methodInfo.getCalledMethods());
    assertTrue(methodInfo.getCalledMethods().isEmpty());
    assertNotNull(methodInfo.getRawSignatures());
    assertTrue(methodInfo.getRawSignatures().isEmpty());
  }

  @Test
  void fieldInfo_setsAllProvidedFields() {
    FieldInfo fieldInfo = ResultBuilder.fieldInfo("X", "int", "private", true, false);

    assertEquals("X", fieldInfo.getName());
    assertEquals("int", fieldInfo.getType());
    assertEquals("private", fieldInfo.getVisibility());
    assertTrue(fieldInfo.isStatic());
    assertFalse(fieldInfo.isFinal());
  }

  @Test
  void methodInfo_setsCalledMethods_whenProvided() {
    List<String> calls = new ArrayList<>(List.of("com.acme.Service#run()", "unknown#fallback()"));

    MethodInfo methodInfo = ResultBuilder.methodInfo().calledMethods(calls).build();
    calls.add("com.acme.Service#ignored()");

    assertEquals(
        List.of("com.acme.Service#run()", "unknown#fallback()"), methodInfo.getCalledMethods());
    assertEquals(2, methodInfo.getCalledMethodRefs().size());
    assertEquals("com.acme.Service#run()", methodInfo.getCalledMethodRefs().get(0).getResolved());
    assertEquals(ResolutionStatus.RESOLVED, methodInfo.getCalledMethodRefs().get(0).getStatus());
    assertEquals("unknown#fallback()", methodInfo.getCalledMethodRefs().get(1).getRaw());
    assertNull(methodInfo.getCalledMethodRefs().get(1).getResolved());
    assertEquals(ResolutionStatus.UNRESOLVED, methodInfo.getCalledMethodRefs().get(1).getStatus());
  }

  @Test
  void methodInfo_prefersCalledMethodRefs_whenBothProvided() {
    CalledMethodRef ref = new CalledMethodRef();
    ref.setRaw("legacy#raw()");
    ref.setResolved("com.acme.Dependency#doWork()");

    MethodInfo methodInfo =
        ResultBuilder.methodInfo()
            .calledMethods(List.of("legacy#shouldNotBeUsed()"))
            .calledMethodRefs(List.of(ref))
            .build();

    assertEquals(1, methodInfo.getCalledMethodRefs().size());
    assertEquals(
        "com.acme.Dependency#doWork()", methodInfo.getCalledMethodRefs().get(0).getResolved());
    assertEquals(List.of("com.acme.Dependency#doWork()"), methodInfo.getCalledMethods());
  }

  @Test
  void methodInfo_setsFlowAndStaticFlags() {
    MethodInfo methodInfo =
        ResultBuilder.methodInfo().hasLoops(true).hasConditionals(true).isStatic(true).build();

    assertTrue(methodInfo.hasLoops());
    assertTrue(methodInfo.hasConditionals());
    assertTrue(methodInfo.isStatic());
  }

  @Test
  void methodInfo_copiesThrownExceptionsAndAnnotations() {
    List<String> thrown = new ArrayList<>(List.of("java.io.IOException"));
    List<String> annotations = new ArrayList<>(List.of("org.junit.jupiter.api.Test"));

    MethodInfo methodInfo =
        ResultBuilder.methodInfo().thrownExceptions(thrown).annotations(annotations).build();

    thrown.add("java.lang.IllegalStateException");
    annotations.clear();

    assertEquals(List.of("java.io.IOException"), methodInfo.getThrownExceptions());
    assertEquals(List.of("org.junit.jupiter.api.Test"), methodInfo.getAnnotations());
  }
}
