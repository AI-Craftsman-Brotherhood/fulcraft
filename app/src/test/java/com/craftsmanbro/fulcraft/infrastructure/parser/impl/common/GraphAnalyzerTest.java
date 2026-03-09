package com.craftsmanbro.fulcraft.infrastructure.parser.impl.common;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.infrastructure.parser.model.AnalysisContext;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.MethodInfo;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GraphAnalyzerTest {

  @Test
  void markCycles_throwsWhenContextIsNull() {
    assertThrows(NullPointerException.class, () -> GraphAnalyzer.markCycles(null));
  }

  @Test
  void markCycles_marksMutualRecursionAndSelfLoop() {
    AnalysisContext context = new AnalysisContext();
    MethodInfo methodA = new MethodInfo();
    MethodInfo methodB = new MethodInfo();
    MethodInfo methodC = new MethodInfo();
    MethodInfo methodD = new MethodInfo();

    context.getMethodInfos().put("A", methodA);
    context.getMethodInfos().put("B", methodB);
    context.getMethodInfos().put("C", methodC);
    context.getMethodInfos().put("D", methodD);

    context.getCallGraph().put("A", Set.of("B"));
    context.getCallGraph().put("B", Set.of("A"));
    context.getCallGraph().put("C", Set.of("C"));

    GraphAnalyzer.markCycles(context);

    assertTrue(methodA.isPartOfCycle());
    assertTrue(methodB.isPartOfCycle());
    assertTrue(methodC.isPartOfCycle());
    assertFalse(methodD.isPartOfCycle());
  }

  @Test
  void markCycles_marksKnownNodeWhenCycleIncludesExternalNode() {
    AnalysisContext context = new AnalysisContext();
    MethodInfo methodA = new MethodInfo();
    MethodInfo methodB = new MethodInfo();

    context.getMethodInfos().put("A", methodA);
    context.getMethodInfos().put("B", methodB);

    context.getCallGraph().put("A", Set.of("external.Helper#run()"));
    context.getCallGraph().put("external.Helper#run()", Set.of("A"));

    GraphAnalyzer.markCycles(context);

    assertTrue(methodA.isPartOfCycle());
    assertFalse(methodB.isPartOfCycle());
  }
}
