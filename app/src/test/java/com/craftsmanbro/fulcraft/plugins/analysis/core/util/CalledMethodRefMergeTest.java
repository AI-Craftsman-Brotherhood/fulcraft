package com.craftsmanbro.fulcraft.plugins.analysis.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.CalledMethodRef;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ResolutionStatus;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CalledMethodRefMergeTest {

  @Test
  void preservesUnknownAsUnresolved() {
    MethodInfo method = new MethodInfo();
    method.setName("m");
    method.setSignature("m()");
    method.setCalledMethods(List.of("unknown#startsWith(java.lang.String)"));

    ClassInfo cls = new ClassInfo();
    cls.setFqn("C");
    cls.setMethods(new ArrayList<>(List.of(method)));

    AnalysisResult result = new AnalysisResult();
    result.setClasses(List.of(cls));

    AnalysisResult merged = new ResultMerger().merge(result, new AnalysisResult());
    MethodInfo mergedMethod = merged.getClasses().get(0).getMethods().get(0);

    assertThat(mergedMethod.getCalledMethodRefs())
        .hasSize(1)
        .allSatisfy(ref -> assertThat(ref.getStatus()).isEqualTo(ResolutionStatus.UNRESOLVED));
    assertThat(mergedMethod.getCalledMethods())
        .containsExactly("unknown#startsWith(java.lang.String)");
  }

  @Test
  void prefersResolvedFromSpoonOverJpRaw() {
    MethodInfo jpMethod = new MethodInfo();
    jpMethod.setName("m");
    jpMethod.setSignature("m()");
    jpMethod.setCalledMethodRefs(
        List.of(
            buildRef(
                "unknown#startsWith(java.lang.String)",
                null,
                "javaparser",
                0.3,
                List.of("\"prefix\""))));

    MethodInfo spoonMethod = new MethodInfo();
    spoonMethod.setName("m");
    spoonMethod.setSignature("m()");
    spoonMethod.setCalledMethodRefs(
        List.of(
            buildRef(
                "java.lang.String#startsWith(java.lang.String)",
                "java.lang.String#startsWith(java.lang.String)",
                "spoon",
                1.0,
                List.of("\"prefix\""))));

    AnalysisResult primary = resultWith(jpMethod);
    AnalysisResult secondary = resultWith(spoonMethod);

    AnalysisResult merged = new ResultMerger().merge(primary, secondary);
    MethodInfo mergedMethod = merged.getClasses().get(0).getMethods().get(0);

    assertThat(mergedMethod.getCalledMethodRefs())
        .hasSize(1)
        .first()
        .satisfies(
            ref -> {
              assertThat(ref.getResolved())
                  .isEqualTo("java.lang.String#startsWith(java.lang.String)");
              assertThat(ref.getStatus()).isEqualTo(ResolutionStatus.RESOLVED);
              assertThat(ref.getConfidence()).isEqualTo(1.0);
              assertThat(ref.getArgumentLiterals()).containsExactly("\"prefix\"");
            });
    assertThat(mergedMethod.getCalledMethods())
        .containsExactly("java.lang.String#startsWith(java.lang.String)");
  }

  @Test
  void marksAmbiguousWhenResolvedDiffers() {
    MethodInfo m1 = new MethodInfo();
    m1.setName("m");
    m1.setSignature("m()");
    m1.setCalledMethodRefs(
        List.of(buildRef("a.Foo#bar()", "a.Foo#bar()", "spoon", 1.0, List.of("\"A\""))));

    MethodInfo m2 = new MethodInfo();
    m2.setName("m");
    m2.setSignature("m()");
    m2.setCalledMethodRefs(
        List.of(buildRef("b.Bar#bar()", "b.Bar#bar()", "spoon", 1.0, List.of("\"B\""))));

    AnalysisResult merged = new ResultMerger().merge(resultWith(m1), resultWith(m2));
    MethodInfo mergedMethod = merged.getClasses().get(0).getMethods().get(0);

    assertThat(mergedMethod.getCalledMethodRefs())
        .hasSize(1)
        .first()
        .satisfies(
            ref -> {
              assertThat(ref.getStatus()).isEqualTo(ResolutionStatus.AMBIGUOUS);
              assertThat(ref.getConfidence()).isEqualTo(0.5);
              assertThat(ref.getCandidates())
                  .containsExactlyInAnyOrder("a.Foo#bar()", "b.Bar#bar()");
              assertThat(ref.getArgumentLiterals()).containsExactly("\"A\"", "\"B\"");
            });
    // Deprecated view falls back to raw when ambiguous
    assertThat(mergedMethod.getCalledMethods()).containsExactly("a.Foo#bar()");
  }

  private CalledMethodRef buildRef(
      String raw,
      String resolved,
      String source,
      double confidence,
      List<String> argumentLiterals) {
    CalledMethodRef ref = new CalledMethodRef();
    ref.setRaw(raw);
    ref.setResolved(resolved);
    ref.setStatus(resolved != null ? ResolutionStatus.RESOLVED : ResolutionStatus.UNRESOLVED);
    ref.setConfidence(confidence);
    ref.setSource(source);
    ref.setArgumentLiterals(argumentLiterals);
    return ref;
  }

  private AnalysisResult resultWith(MethodInfo method) {
    ClassInfo cls = new ClassInfo();
    cls.setFqn("C");
    cls.setMethods(new ArrayList<>(List.of(method)));
    AnalysisResult result = new AnalysisResult();
    result.setClasses(List.of(cls));
    return result;
  }
}
