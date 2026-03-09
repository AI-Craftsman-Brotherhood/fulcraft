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

class ResultMergerMethodIdTest {

  @Test
  void mergesMethodsSharingCanonicalId() {
    AnalysisResult primary = new AnalysisResult();
    primary.setClasses(List.of(classInfoWithMethods(primaryMethod())));

    AnalysisResult secondary = new AnalysisResult();
    secondary.setClasses(List.of(classInfoWithMethods(secondaryMethod())));

    AnalysisResult merged = new ResultMerger().merge(primary, secondary);

    ClassInfo cls = merged.getClasses().get(0);
    assertThat(cls.getMethods()).hasSize(1);

    MethodInfo method = cls.getMethods().get(0);
    assertThat(method.getMethodId())
        .isEqualTo(
            "com.example.legacy.ComplexInvoiceService#calculateInvoice("
                + "com.example.legacy.SimpleCustomerData,java.util.List,java.lang.String,boolean,java.time.LocalDate)");
    assertThat(method.getRawSignatures())
        .containsExactly(
            "calculateInvoice(SimpleCustomerData, List, String, boolean, LocalDate)",
            "calculateInvoice(com.example.legacy.SimpleCustomerData,java.util.List,java.lang.String,boolean,java.time.LocalDate)");
    assertThat(method.getCyclomaticComplexity()).isEqualTo(12);
    assertThat(method.getMaxNestingDepth()).isEqualTo(3);
    assertThat(method.getLoc()).isEqualTo(15);
    assertThat(method.hasLoops()).isTrue();
    assertThat(method.hasConditionals()).isTrue();
    assertThat(method.getCalledMethods()).containsExactlyInAnyOrder("spoonCall", "jpCall");
    assertThat(method.getAnnotations()).containsExactlyInAnyOrder("A", "B");
    assertThat(method.getThrownExceptions())
        .containsExactly("java.lang.Exception", "java.io.IOException");
    assertThat(method.getSourceCode()).contains("calculateInvoice");
  }

  @Test
  void mergesCalledMethodRefsIgnoringWhitespaceDifferences() {
    MethodInfo primaryMethod = new MethodInfo();
    primaryMethod.setName("handle");
    primaryMethod.setSignature("handle()");
    primaryMethod.setCalledMethodRefs(
        List.of(
            calledRef(
                "com.example.dep.Helper#process(java.lang.String, int)",
                ResolutionStatus.RESOLVED)));

    MethodInfo secondaryMethod = new MethodInfo();
    secondaryMethod.setName("handle");
    secondaryMethod.setSignature("handle()");
    secondaryMethod.setCalledMethodRefs(
        List.of(
            calledRef(
                "com.example.dep.Helper#process(java.lang.String,int)",
                ResolutionStatus.RESOLVED)));

    AnalysisResult primary = new AnalysisResult();
    primary.setClasses(List.of(classInfoWithMethods(primaryMethod)));
    AnalysisResult secondary = new AnalysisResult();
    secondary.setClasses(List.of(classInfoWithMethods(secondaryMethod)));

    AnalysisResult merged = new ResultMerger().merge(primary, secondary);
    MethodInfo mergedMethod = merged.getClasses().getFirst().getMethods().getFirst();

    assertThat(mergedMethod.getCalledMethodRefs()).hasSize(1);
    assertThat(mergedMethod.getCalledMethods())
        .containsExactly("com.example.dep.Helper#process(java.lang.String, int)");
  }

  private ClassInfo classInfoWithMethods(MethodInfo method) {
    ClassInfo cls = new ClassInfo();
    cls.setFqn("com.example.legacy.ComplexInvoiceService");
    cls.setMethods(new ArrayList<>(List.of(method)));
    cls.setMethodCount(cls.getMethods().size());
    return cls;
  }

  private MethodInfo primaryMethod() {
    MethodInfo mi = new MethodInfo();
    mi.setName("calculateInvoice");
    mi.setSignature("calculateInvoice(SimpleCustomerData, List, String, boolean, LocalDate)");
    mi.setLoc(15);
    mi.setCyclomaticComplexity(2);
    mi.setMaxNestingDepth(1);
    mi.setAnnotations(new ArrayList<>(List.of("A")));
    mi.setThrownExceptions(new ArrayList<>(List.of("Exception")));
    mi.setCalledMethods(new ArrayList<>(List.of("jpCall")));
    mi.setSourceCode(
        """
            public Invoice calculateInvoice(SimpleCustomerData customer, java.util.List<Item> items, String currency, boolean applyDiscount, java.time.LocalDate date) {
                for (Item item : items) {
                    if (item.isBillable()) {
                        continue;
                    }
                }
                return applyDiscount ? applyDiscount(0) : 0;
            }
            """);
    return mi;
  }

  private MethodInfo secondaryMethod() {
    MethodInfo mi = new MethodInfo();
    mi.setName("calculateInvoice");
    mi.setSignature(
        "calculateInvoice(com.example.legacy.SimpleCustomerData,java.util.List,java.lang.String,boolean,java.time.LocalDate)");
    mi.setCyclomaticComplexity(12);
    mi.setMaxNestingDepth(3);
    mi.setAnnotations(new ArrayList<>(List.of("B")));
    mi.setThrownExceptions(new ArrayList<>(List.of("java.lang.Exception", "java.io.IOException")));
    mi.setCalledMethods(new ArrayList<>(List.of("spoonCall")));
    return mi;
  }

  private CalledMethodRef calledRef(String resolved, ResolutionStatus status) {
    CalledMethodRef ref = new CalledMethodRef();
    ref.setResolved(resolved);
    ref.setRaw(resolved);
    ref.setStatus(status);
    ref.setConfidence(1.0);
    ref.setSource("test");
    return ref;
  }
}
