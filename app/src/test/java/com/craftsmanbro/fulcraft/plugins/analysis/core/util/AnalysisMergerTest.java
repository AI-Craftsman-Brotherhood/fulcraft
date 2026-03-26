package com.craftsmanbro.fulcraft.plugins.analysis.core.util;

import static org.junit.jupiter.api.Assertions.*;

import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisError;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalysisMergerTest {

  @Test
  void throwsWhenBothResultsNull() {
    ResultMerger merger = new ResultMerger();
    assertThrows(IllegalArgumentException.class, () -> merger.merge(null, null));
  }

  @Test
  void carriesOverErrorsAndPrimaryClasses() {
    AnalysisResult primary = new AnalysisResult();
    primary.setProjectId("proj");
    primary.setCommitHash("abc");
    primary.setClasses(new ArrayList<>());
    primary.setAnalysisErrors(List.of(error("primary error")));
    primary.getClasses().add(classInfo("com.example.Foo", method("a()")));

    AnalysisResult secondary = new AnalysisResult();
    secondary.setAnalysisErrors(List.of(error("secondary error")));

    AnalysisResult merged = new ResultMerger().merge(primary, secondary);

    assertEquals("proj", merged.getProjectId());
    assertEquals("abc", merged.getCommitHash());
    assertEquals(1, merged.getClasses().size(), "Should include primary classes");
    assertEquals(2, merged.getAnalysisErrors().size(), "Should concatenate analysis errors");
  }

  @Test
  void mergesMissingMethodsFromSecondary() {
    ClassInfo priCls = classInfo("com.example.Foo", method("bar(java.lang.String)"));
    ClassInfo secCls = classInfo("com.example.Foo", method("bar(String)"), method("baz(int)"));

    AnalysisResult primary = new AnalysisResult();
    primary.setClasses(List.of(priCls));
    AnalysisResult secondary = new AnalysisResult();
    secondary.setClasses(List.of(secCls));

    AnalysisResult merged = new ResultMerger().merge(primary, secondary);

    ClassInfo mergedFoo =
        merged.getClasses().stream()
            .filter(c -> "com.example.Foo".equals(c.getFqn()))
            .findFirst()
            .orElseThrow();

    assertEquals(2, mergedFoo.getMethods().size(), "Should include unique methods from secondary");
    assertTrue(mergedFoo.getMethods().stream().anyMatch(m -> "baz".equals(m.getName())));
    assertEquals(2, mergedFoo.getMethodCount());
  }

  @Test
  void addsSecondaryClassWhenMissingInPrimary() {
    AnalysisResult primary = new AnalysisResult();
    primary.setClasses(List.of(classInfo("com.example.Foo", method("a()"))));

    AnalysisResult secondary = new AnalysisResult();
    secondary.setClasses(List.of(classInfo("com.example.Bar", method("b()"))));

    AnalysisResult merged = new ResultMerger().merge(primary, secondary);

    assertEquals(2, merged.getClasses().size(), "Should include classes from both analyses");
    assertTrue(merged.getClasses().stream().anyMatch(c -> "com.example.Bar".equals(c.getFqn())));
  }

  private ClassInfo classInfo(String fqn, MethodInfo... methods) {
    ClassInfo ci = new ClassInfo();
    ci.setFqn(fqn);
    ci.setMethods(new ArrayList<>(List.of(methods)));
    ci.setMethodCount(ci.getMethods().size());
    return ci;
  }

  private MethodInfo method(String signature) {
    MethodInfo mi = new MethodInfo();
    String name =
        signature.contains("(") ? signature.substring(0, signature.indexOf('(')) : signature;
    mi.setName(name);
    mi.setSignature(signature);
    mi.setSourceCode("void " + name + "() {}");
    return mi;
  }

  private AnalysisError error(String message) {
    return new AnalysisError("test-file", message, null);
  }
}
