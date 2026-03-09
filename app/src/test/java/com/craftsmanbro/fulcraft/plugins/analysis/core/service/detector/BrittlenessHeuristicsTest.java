package com.craftsmanbro.fulcraft.plugins.analysis.core.service.detector;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.BrittlenessSignal;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import java.util.List;
import org.junit.jupiter.api.Test;

class BrittlenessHeuristicsTest {

  @Test
  void marksSignalsForNonDeterministicSources() {
    MethodInfo method = new MethodInfo();
    method.setName("risky");
    method.setSourceCode(
        """
        public String risky(java.nio.file.Path path) {
          java.time.Instant.now();
          System.currentTimeMillis();
          new java.util.Random().nextInt();
          java.util.UUID.randomUUID();
          System.getenv("HOME");
          java.util.concurrent.Executors.newFixedThreadPool(2);
          new Thread(() -> {}).start();
          java.nio.file.Files.readAllLines(path);
          java.util.HashMap<String, String> map = new java.util.HashMap<>();
          return map.toString();
        }
        """);

    AnalysisResult result = buildResult(method);
    BrittlenessHeuristics heuristics = new BrittlenessHeuristics();

    assertTrue(heuristics.apply(result));
    assertTrue(method.isBrittle());
    assertTrue(method.getBrittlenessSignals().contains(BrittlenessSignal.TIME));
    assertTrue(method.getBrittlenessSignals().contains(BrittlenessSignal.RANDOM));
    assertTrue(method.getBrittlenessSignals().contains(BrittlenessSignal.ENVIRONMENT));
    assertTrue(method.getBrittlenessSignals().contains(BrittlenessSignal.CONCURRENCY));
    assertTrue(method.getBrittlenessSignals().contains(BrittlenessSignal.IO));
    assertTrue(method.getBrittlenessSignals().contains(BrittlenessSignal.COLLECTION_ORDER));
  }

  @Test
  void leavesStableMethodUnmarked() {
    MethodInfo method = new MethodInfo();
    method.setName("stable");
    method.setSourceCode(
        """
        public int stable(int a, int b) {
          int sum = a + b;
          return sum * 2;
        }
        """);

    AnalysisResult result = buildResult(method);
    BrittlenessHeuristics heuristics = new BrittlenessHeuristics();

    assertFalse(heuristics.apply(result));
    assertFalse(method.isBrittle());
    assertTrue(method.getBrittlenessSignals().isEmpty());
  }

  @Test
  void detectsSignalsFromCalledMethodsWhenSourceMissing() {
    MethodInfo method = new MethodInfo();
    method.setName("fromCalls");
    method.setSourceCode("");
    method.setCalledMethods(
        List.of(
            "java.time.Instant#now()",
            "java.util.UUID#randomUUID()",
            "java.lang.System#getenv(java.lang.String)",
            "java.util.concurrent.Executors#newFixedThreadPool(int)",
            "java.lang.Thread#start()",
            "java.nio.file.Files#readAllBytes(java.nio.file.Path)"));

    AnalysisResult result = buildResult(method);
    BrittlenessHeuristics heuristics = new BrittlenessHeuristics();

    assertTrue(heuristics.apply(result));
    assertTrue(method.getBrittlenessSignals().contains(BrittlenessSignal.TIME));
    assertTrue(method.getBrittlenessSignals().contains(BrittlenessSignal.RANDOM));
    assertTrue(method.getBrittlenessSignals().contains(BrittlenessSignal.ENVIRONMENT));
    assertTrue(method.getBrittlenessSignals().contains(BrittlenessSignal.CONCURRENCY));
    assertTrue(method.getBrittlenessSignals().contains(BrittlenessSignal.IO));
  }

  private AnalysisResult buildResult(MethodInfo method) {
    ClassInfo cls = new ClassInfo();
    cls.setFqn("com.example.Demo");
    cls.setMethods(List.of(method));

    AnalysisResult result = new AnalysisResult();
    result.setClasses(List.of(cls));
    return result;
  }
}
