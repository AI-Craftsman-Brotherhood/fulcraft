package com.craftsmanbro.fulcraft.infrastructure.parser.impl.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.infrastructure.parser.model.AnalysisContext;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.AnalysisResult;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.CalledMethodRef;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.ClassInfo;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.MethodInfo;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.ResolutionStatus;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CommonPostProcessorTest {

  @Test
  void finalizePostProcessing_throwsWhenResultIsNull() {
    AnalysisContext context = new AnalysisContext();

    assertThrows(
        NullPointerException.class,
        () -> CommonPostProcessor.finalizePostProcessing(null, context));
  }

  @Test
  void finalizePostProcessing_throwsWhenContextIsNull() {
    AnalysisResult result = new AnalysisResult("project");

    assertThrows(
        NullPointerException.class, () -> CommonPostProcessor.finalizePostProcessing(result, null));
  }

  @Test
  void finalizePostProcessing_honorsPerCallStatusesWhenDefaultResolutionIsFalse() {
    AnalysisContext context = new AnalysisContext();
    context.setCallGraphSource("unit-test");
    context.setCallGraphResolved(false);

    String callerKey = "com.example.Caller#invoke()";
    String resolvedCalleeKey = "com.example.Dependency#execute()";
    String unresolvedCalleeKey = "com.example.External#missing()";

    MethodInfo caller = methodWithVisibility("public");
    MethodInfo dependency = methodWithVisibility("public");
    context.getMethodInfos().put(callerKey, caller);
    context.getMethodInfos().put(resolvedCalleeKey, dependency);
    context.getCallGraph().put(callerKey, Set.of(resolvedCalleeKey, unresolvedCalleeKey));
    context.recordCallStatus(callerKey, resolvedCalleeKey, ResolutionStatus.RESOLVED);
    context.recordCallStatus(callerKey, unresolvedCalleeKey, ResolutionStatus.UNRESOLVED);

    AnalysisResult result = new AnalysisResult("project");
    result.setClasses(List.of());

    CommonPostProcessor.finalizePostProcessing(result, context);

    List<CalledMethodRef> refs = caller.getCalledMethodRefs();
    assertEquals(2, refs.size());

    CalledMethodRef resolved = findByRaw(refs, resolvedCalleeKey);
    assertNotNull(resolved);
    assertEquals(ResolutionStatus.RESOLVED, resolved.getStatus());
    assertEquals(resolvedCalleeKey, resolved.getResolved());

    CalledMethodRef unresolved = findByRaw(refs, unresolvedCalleeKey);
    assertNotNull(unresolved);
    assertEquals(ResolutionStatus.UNRESOLVED, unresolved.getStatus());
    assertNull(unresolved.getResolved());
  }

  @Test
  void finalizePostProcessing_appliesCallRefsCyclesDuplicatesAndDeadCode() {
    AnalysisContext context = new AnalysisContext();
    context.setCallGraphSource("unit-test");
    context.setCallGraphResolved(true);

    String keyA = "com.example.Foo#foo()";
    String keyB = "com.example.Bar#bar()";
    String keyC = "com.example.Baz#baz()";
    String keyD = "com.example.Dup#dup()";
    String keyE = "com.example.Dup2#dup2()";

    MethodInfo methodA = methodWithVisibility("public");
    MethodInfo methodB = methodWithVisibility("private");
    MethodInfo methodC = methodWithVisibility("public");
    MethodInfo methodD = methodWithVisibility("private");
    MethodInfo methodE = methodWithVisibility("private");

    context.getMethodInfos().put(keyA, methodA);
    context.getMethodInfos().put(keyB, methodB);
    context.getMethodInfos().put(keyC, methodC);
    context.getMethodInfos().put(keyD, methodD);
    context.getMethodInfos().put(keyE, methodE);

    context.getCallGraph().put(keyA, Set.of(keyB, "unknown#missing()"));
    context.getCallGraph().put(keyB, Set.of(keyA));
    context.recordCallArgumentLiterals(keyA, keyB, List.of("\"Order is being processed\""));
    context.recordCallArgumentLiterals(keyA, "unknown#missing()", List.of("\"fallback\""));

    context.getIncomingCounts().put(keyA, 1);
    context.getIncomingCounts().put(keyB, 0);
    context.getIncomingCounts().put(keyC, 0);
    context.getIncomingCounts().put(keyD, 0);
    context.getIncomingCounts().put(keyE, 0);

    context.getMethodHasBody().put(keyA, true);
    context.getMethodHasBody().put(keyB, true);
    context.getMethodHasBody().put(keyC, true);
    context.getMethodHasBody().put(keyD, true);
    context.getMethodHasBody().put(keyE, true);

    context.getMethodCodeHash().put(keyD, "dup-hash");
    context.getMethodCodeHash().put(keyE, "dup-hash");

    ClassInfo classOne = new ClassInfo();
    classOne.setInterface(false);
    classOne.setMethods(List.of(methodB, methodC));

    ClassInfo classTwo = new ClassInfo();
    classTwo.setInterface(false);
    classTwo.setMethods(List.of(methodD, methodE));

    AnalysisResult result = new AnalysisResult("project");
    result.setClasses(List.of(classOne, classTwo));

    CommonPostProcessor.finalizePostProcessing(result, context);

    List<CalledMethodRef> refsA = methodA.getCalledMethodRefs();
    assertEquals(2, refsA.size());
    CalledMethodRef refToB = findByRaw(refsA, keyB);
    assertNotNull(refToB);
    assertEquals(keyB, refToB.getResolved());
    assertEquals(ResolutionStatus.RESOLVED, refToB.getStatus());
    assertEquals(List.of("\"Order is being processed\""), refToB.getArgumentLiterals());

    CalledMethodRef refUnknown = findByRaw(refsA, "unknown#missing()");
    assertNotNull(refUnknown);
    assertNull(refUnknown.getResolved());
    assertEquals(ResolutionStatus.UNRESOLVED, refUnknown.getStatus());
    assertEquals(List.of("\"fallback\""), refUnknown.getArgumentLiterals());

    assertTrue(methodA.isPartOfCycle());
    assertTrue(methodB.isPartOfCycle());
    assertFalse(methodC.isPartOfCycle());

    assertTrue(methodB.isDeadCode());
    assertFalse(methodC.isDeadCode());
    assertTrue(methodD.isDeadCode());
    assertTrue(methodE.isDeadCode());

    assertTrue(methodD.isDuplicate());
    assertTrue(methodE.isDuplicate());
    assertEquals("dup-hash", methodD.getDuplicateGroup());
    assertEquals("dup-hash", methodE.getDuplicateGroup());

    assertFalse(classOne.isDeadCode());
    assertTrue(classTwo.isDeadCode());

    assertTrue(methodC.getCalledMethodRefs().isEmpty());
  }

  private static MethodInfo methodWithVisibility(String visibility) {
    MethodInfo methodInfo = new MethodInfo();
    methodInfo.setVisibility(visibility);
    return methodInfo;
  }

  private static CalledMethodRef findByRaw(List<CalledMethodRef> refs, String raw) {
    for (CalledMethodRef ref : refs) {
      if (ref != null && raw.equals(ref.getRaw())) {
        return ref;
      }
    }
    return null;
  }
}
